package com.fieldstory.farm.persistence;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * SQLite 结构版本迁移器（E 模块 P1；验收规范 §七十一~§七十五）。
 *
 * <p><b>为什么要它：</b>P1 起 {@code data/farm.db} 是唯一正式运行存档。后续版本新增字段/表时，
 * 必须让旧存档“原地升级”而不是丢弃重建，否则玩家的农场数据会丢。
 * 本类用 SQLite 内置的 {@code PRAGMA user_version} 记录<b>当前结构版本</b>，
 * 启动时把低于程序版本的旧库逐级升级到最新版（每级迁移只做增量 DDL，不重建已有表）。
 *
 * <p><b>升级规则：</b>
 * <ul>
 *   <li>每个 {@link MigrationStep} 的 {@code version} 是“执行完该步后库应处于的版本”；</li>
 *   <li>只执行版本号大于当前 {@code user_version} 的步骤，因此对同一库重复调用是幂等的；</li>
 *   <li>若库版本高于程序支持的 {@link #SCHEMA_VERSION}（用户装了更新版程序后又回退），
 *       直接抛错拒绝启动，避免用旧程序写坏新结构。</li>
 * </ul>
 *
 * <p><b>P1 v1 建表清单（验收规范 §七十二 最低表 + E 持久化内部辅助表）：</b>
 * player / player_seed / unlocked / farm / soil / crop / decoration / world_state / meta。
 * 其中 player_seed、unlocked、meta 是 E 侧持久化辅助表（种子库存、已解锁内容、迁移标记），
 * 不属于额外游戏系统。
 *
 * <p><b>P2 v2 增量（验收规范 §九十一/§九十三/§一百零四）：</b>
 * <ul>
 *   <li>{@code crop_memory}：作物生命记忆档案（收获后永久保留，§九十五）；</li>
 *   <li>{@code active_event}：当前随机事件快照（事件期间退出，回来不能凭空消失，§九十一）；</li>
 *   <li>{@code player_item}：玩家背包物品（C 模块 {@code Inventory} 的持久化映射）；</li>
 *   <li>{@code world_state.world_total_minutes}：世界时钟总分钟，用于把"退出瞬间"精确恢复。</li>
 * </ul>
 *
 * <p><b>P2 v3 增量（验收规范 §一百零四 日志表）：</b>
 * <ul>
 *   <li>{@code event_log}：事件日志（可解释、可追溯，概要设计 §11.2）；</li>
 *   <li>{@code offline_log}：离线日志（按游戏日解释离线期间变化，§一百零五）。</li>
 * </ul>
 * 两张日志表为<b>追加式</b>记录，不属于全量覆盖的会话状态，故不经
 * {@code SqliteSaveService} 的清表—重写流程。
 *
 * <p><b>P3 v4 增量（验收规范 §一百一十~§一百二十九）：</b>
 * <ul>
 *   <li>{@code crop_collection} / {@code decoration_collection} / {@code legendary_collection}：
 *       收集图鉴（作物 15 / 装饰 14 / 传说 3），永久保存；</li>
 *   <li>{@code set_collection}：套装 setCollected / setActive 两个独立状态；</li>
 *   <li>{@code graduation}：毕业状态单行表（FarmScore == 147 时写入）。</li>
 * </ul>
 *
 * <p><b>第二轮收口 v5 增量：</b>补齐作物运行态施肥字段与 CropMemory 最近施肥日，
 * 让“每日一次 / 生命周期三次 / 成长 +15%”在退出重进后仍保持一致。
 *
 * <p><b>条件加列语法：</b>迁移语句以 {@value #ADD_COLUMN_PREFIX} 开头时表示"若该列不存在才加"，
 * 形如 {@code ADD COLUMN world_state.world_total_minutes INTEGER NOT NULL DEFAULT -1}。
 * SQLite 的 {@code ADD COLUMN} 在列已存在时会直接报错，而这个语法让迁移对
 * "被回退过版本号的库"、"半成品库" 依旧幂等（见 {@code MigrationTest}）。
 */
public final class SchemaMigrator {

    /** 程序当前支持的数据库结构版本。新增表/字段时必须 +1 并追加迁移步骤。 */
    public static final int SCHEMA_VERSION = 5;

    /** 「条件加列」语句前缀：列已存在时跳过，保证迁移幂等。 */
    static final String ADD_COLUMN_PREFIX = "ADD COLUMN ";

    /** 全部迁移步骤，按版本升序。 */
    private static final List<MigrationStep> STEPS =
            List.of(stepToV1(), stepToV2(), stepToV3(), stepToV4(), stepToV5());

    private SchemaMigrator() {
        // 工具类，禁止实例化
    }

    /** 读取数据库当前结构版本（{@code PRAGMA user_version}，全新库为 0）。 */
    public static int readVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** 把数据库逐级升级到 {@link #SCHEMA_VERSION}；已是最新则什么都不做。 */
    public static void migrate(Connection connection) throws SQLException {
        int current = readVersion(connection);
        if (current > SCHEMA_VERSION) {
            throw new IllegalStateException("数据库结构版本(" + current + ")高于程序支持版本("
                    + SCHEMA_VERSION + ")，请使用更新版本的程序打开");
        }
        for (MigrationStep step : STEPS) {
            if (step.version() > current) {
                for (String statement : step.statements()) {
                    executeStatement(connection, statement);
                }
                setVersion(connection, step.version());
                current = step.version();
            }
        }
    }

    /** 执行一条迁移语句；带 {@link #ADD_COLUMN_PREFIX} 前缀时为「条件加列」（列已存在则跳过）。 */
    private static void executeStatement(Connection connection, String statement) throws SQLException {
        if (!statement.startsWith(ADD_COLUMN_PREFIX)) {
            runStatement(connection, statement);
            return;
        }
        String rest = statement.substring(ADD_COLUMN_PREFIX.length()).trim();
        int split = rest.indexOf(' ');
        if (split < 0) {
            throw new IllegalStateException("条件加列语句缺少列定义: " + statement);
        }
        String target = rest.substring(0, split);
        String definition = rest.substring(split + 1);
        int dot = target.indexOf('.');
        if (dot < 0) {
            throw new IllegalStateException("条件加列语句缺少 表.列 目标: " + statement);
        }
        String table = target.substring(0, dot);
        String column = target.substring(dot + 1);
        if (columnExists(connection, table, column)) {
            return;
        }
        // 表名/列名来自本类常量，非外部输入，无注入风险
        runStatement(connection, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    /** 执行单条 DDL。 */
    private static void runStatement(Connection connection, String ddl) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(ddl);
        }
    }

    /** 判断表中是否已存在某列（{@code PRAGMA table_info}）。 */
    private static boolean columnExists(Connection connection, String table, String column)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 写入结构版本号（{@code PRAGMA user_version} 不支持占位符，版本为 int 无注入风险）。 */
    private static void setVersion(Connection connection, int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA user_version = " + version);
        }
    }

    /** v0 → v1：首次建立 P1 全部表（IF NOT EXISTS 保证对半成品库也安全）。 */
    private static MigrationStep stepToV1() {
        return new MigrationStep(1, List.of(
                "CREATE TABLE IF NOT EXISTS player ("
                        + " id INTEGER PRIMARY KEY CHECK (id = 1),"
                        + " name TEXT,"
                        + " gold INTEGER NOT NULL CHECK (gold >= 0))",
                "CREATE TABLE IF NOT EXISTS player_seed ("
                        + " crop_type TEXT PRIMARY KEY,"
                        + " quantity INTEGER NOT NULL CHECK (quantity >= 0))",
                "CREATE TABLE IF NOT EXISTS unlocked ("
                        + " unlocked_key TEXT PRIMARY KEY)",
                "CREATE TABLE IF NOT EXISTS farm ("
                        + " id INTEGER PRIMARY KEY CHECK (id = 1),"
                        + " map_rows INTEGER NOT NULL,"
                        + " map_cols INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS soil ("
                        + " soil_id INTEGER PRIMARY KEY,"
                        + " plot_id TEXT,"
                        + " row_index INTEGER NOT NULL,"
                        + " col_index INTEGER NOT NULL,"
                        + " state TEXT)",
                "CREATE TABLE IF NOT EXISTS crop ("
                        + " crop_uuid TEXT PRIMARY KEY,"
                        + " soil_id INTEGER NOT NULL REFERENCES soil(soil_id) ON DELETE CASCADE,"
                        + " crop_type TEXT,"
                        + " growth_stage TEXT,"
                        + " growth_progress REAL NOT NULL DEFAULT 0,"
                        + " plant_world_time TEXT,"
                        + " manual_water_count INTEGER NOT NULL DEFAULT 0,"
                        + " last_manual_water_game_day TEXT)",
                "CREATE TABLE IF NOT EXISTS decoration ("
                        + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + " decoration_type TEXT NOT NULL,"
                        + " row_index INTEGER NOT NULL,"
                        + " col_index INTEGER NOT NULL)",
                // world_state 列固定承载 §七十三：current_world_time/last_real_time/
                // current_weather/current_day_index/random_seed，P2 离线模拟无需推翻结构
                "CREATE TABLE IF NOT EXISTS world_state ("
                        + " id INTEGER PRIMARY KEY CHECK (id = 1),"
                        + " current_world_time TEXT,"
                        + " last_real_time TEXT,"
                        + " current_weather TEXT,"
                        + " current_day_index INTEGER NOT NULL DEFAULT 0,"
                        + " random_seed INTEGER)",
                // meta：E 持久化内部键值（JSON 一次性迁移标记等），不承载游戏业务
                "CREATE TABLE IF NOT EXISTS meta ("
                        + " meta_key TEXT PRIMARY KEY,"
                        + " meta_value TEXT NOT NULL)"));
    }

    /**
     * v1 → v2：P2 记忆/事件持久化增量（只加表与一列，不动既有列，旧档原地升级不丢数据）。
     *
     * <p>{@code crop_memory} 键为 {@code crop_uuid}（生命周期唯一，§九十三），
     * 与 {@code crop} 表<b>无外键</b>：收获后当前作物行会随土地清空而删除，
     * 但记忆档案必须永久保留（§九十五），因此不能级联删除。
     */
    private static MigrationStep stepToV2() {
        return new MigrationStep(2, List.of(
                "CREATE TABLE IF NOT EXISTS crop_memory ("
                        + " crop_uuid TEXT PRIMARY KEY,"
                        + " crop_type TEXT,"
                        + " plant_world_time INTEGER NOT NULL DEFAULT -1,"
                        + " mature_world_time INTEGER NOT NULL DEFAULT -1,"
                        + " harvest_world_time INTEGER NOT NULL DEFAULT -1,"
                        + " manual_water_count INTEGER NOT NULL DEFAULT 0,"
                        + " rain_count INTEGER NOT NULL DEFAULT 0,"
                        + " drought_count INTEGER NOT NULL DEFAULT 0,"
                        + " green_rain_count INTEGER NOT NULL DEFAULT 0,"
                        + " fertilizer_count INTEGER NOT NULL DEFAULT 0,"
                        + " last_drought_game_day INTEGER NOT NULL DEFAULT -1,"
                        + " water_rescue INTEGER NOT NULL DEFAULT 0,"
                        + " events TEXT,"
                        + " wither_risk INTEGER NOT NULL DEFAULT 0,"
                        + " quality TEXT,"
                        + " legendary INTEGER NOT NULL DEFAULT 0,"
                        + " final_story TEXT)",
                // active_event：单行表（id=1），字段沿用验收 §九十一
                "CREATE TABLE IF NOT EXISTS active_event ("
                        + " id INTEGER PRIMARY KEY CHECK (id = 1),"
                        + " event_type TEXT NOT NULL,"
                        + " start_world_time INTEGER NOT NULL DEFAULT 0,"
                        + " end_world_time INTEGER NOT NULL DEFAULT 0,"
                        + " target_crop_type TEXT,"
                        + " payload TEXT)",
                // player_item：玩家背包物品（C 模块 Inventory 的快照，item_type 唯一）
                "CREATE TABLE IF NOT EXISTS player_item ("
                        + " item_type TEXT PRIMARY KEY,"
                        + " quantity INTEGER NOT NULL CHECK (quantity >= 0),"
                        + " unit_price INTEGER NOT NULL DEFAULT 0)",
                // 条件加列：世界时钟总分钟（退出瞬间精确恢复；-1 = 未记录，按旧档按天恢复）
                ADD_COLUMN_PREFIX + "world_state.world_total_minutes INTEGER NOT NULL DEFAULT -1"));
    }

    /**
     * v2 → v3：P2 日志表增量（验收规范 §一百零四；DAO 归属 §一百五十一）。
     *
     * <p>只加两张<b>追加式</b>日志表，不动任何既有列，旧档原地升级不丢数据：
     * <ul>
     *   <li>{@code event_log}：记录每次随机事件发生（事件类型、起止世界时间、
     *       神秘商人目标作物、payload、所属游戏日），供追溯与测试复现；</li>
     *   <li>{@code offline_log}：按游戏日记录离线期间农场发生了什么（§一百零五 按日分组展示）。</li>
     * </ul>
     * 列取「最小可用」口径：与 {@code active_event} 对应列同名同义，接入方（D/B）需要更多字段时，
     * 按本类升级规则新增 v4 迁移即可，不改动本步。
     */
    private static MigrationStep stepToV3() {
        return new MigrationStep(3, List.of(
                "CREATE TABLE IF NOT EXISTS event_log ("
                        + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + " day_index INTEGER NOT NULL DEFAULT 0,"
                        + " event_type TEXT NOT NULL,"
                        + " start_world_time INTEGER NOT NULL DEFAULT 0,"
                        + " end_world_time INTEGER NOT NULL DEFAULT 0,"
                        + " target_crop_type TEXT,"
                        + " payload TEXT)",
                "CREATE TABLE IF NOT EXISTS offline_log ("
                        + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + " day_index INTEGER NOT NULL,"
                        + " summary TEXT NOT NULL)"));
    }

    /**
     * v3 → v4：P3 收集与毕业增量（验收规范 §一百一十~§一百二十九；DAO 归属 §一百五十一条）。
     *
     * <p>只加表，不动任何既有列，旧档原地升级不丢数据：
     * <ul>
     *   <li>{@code crop_collection}：作物图鉴（作物 × 品质联合主键，三态状态），15 项目标；</li>
     *   <li>{@code decoration_collection}：装饰图鉴（类型 id 主键，存在即已收集），14 项目标；</li>
     *   <li>{@code legendary_collection}：传说图鉴（作物类型主键，存在即已获得），3 种目标；</li>
     *   <li>{@code set_collection}：套装收集状态（collected 永久 / active 当前，两者独立）；</li>
     *   <li>{@code graduation}：毕业单行表（首次达到 147 写入，只记一次）。</li>
     * </ul>
     * 这五张表都是<b>全量覆盖</b>的会话状态，经 {@code SqliteSaveService} 清表—重写。
     */
    private static MigrationStep stepToV4() {
        return new MigrationStep(4, List.of(
                "CREATE TABLE IF NOT EXISTS crop_collection ("
                        + " crop_type TEXT NOT NULL,"
                        + " quality TEXT NOT NULL,"
                        + " status TEXT NOT NULL,"
                        + " PRIMARY KEY (crop_type, quality))",
                "CREATE TABLE IF NOT EXISTS decoration_collection ("
                        + " decoration_type TEXT PRIMARY KEY)",
                "CREATE TABLE IF NOT EXISTS legendary_collection ("
                        + " crop_type TEXT PRIMARY KEY)",
                "CREATE TABLE IF NOT EXISTS set_collection ("
                        + " set_id TEXT PRIMARY KEY,"
                        + " collected INTEGER NOT NULL DEFAULT 0,"
                        + " active INTEGER NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS graduation ("
                        + " id INTEGER PRIMARY KEY CHECK (id = 1),"
                        + " graduated INTEGER NOT NULL DEFAULT 0,"
                        + " graduation_world_time INTEGER NOT NULL DEFAULT -1,"
                        + " graduation_game_day INTEGER NOT NULL DEFAULT -1)"));
    }

    /**
     * v4 → v5：第二轮封口——施肥运行态与记忆持久化。
     *
     * <p>旧档原地加列，默认值保持“从未施肥”；不重建 crop/crop_memory，避免丢档。
     */
    private static MigrationStep stepToV5() {
        return new MigrationStep(5, List.of(
                ADD_COLUMN_PREFIX + "crop.fertilizer_count INTEGER NOT NULL DEFAULT 0",
                ADD_COLUMN_PREFIX + "crop.last_fertilized_game_day TEXT NOT NULL DEFAULT '-1'",
                ADD_COLUMN_PREFIX + "crop.drought_count INTEGER NOT NULL DEFAULT 0",
                ADD_COLUMN_PREFIX + "crop.rain_count INTEGER NOT NULL DEFAULT 0",
                ADD_COLUMN_PREFIX + "crop.green_rain_count INTEGER NOT NULL DEFAULT 0",
                ADD_COLUMN_PREFIX + "crop.last_hydrated_world_time TEXT NOT NULL DEFAULT '-1'",
                ADD_COLUMN_PREFIX + "crop.drought_streak INTEGER NOT NULL DEFAULT 0",
                ADD_COLUMN_PREFIX + "crop.event_count INTEGER NOT NULL DEFAULT 0",
                ADD_COLUMN_PREFIX + "crop_memory.last_fertilize_game_day INTEGER NOT NULL DEFAULT -1"));
    }

    /** 单个迁移步骤：执行完 {@code version} 所列 DDL 后，库结构版本应等于 {@code version}。 */
    private record MigrationStep(int version, List<String> statements) {
    }
}
