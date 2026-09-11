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
 */
public final class SchemaMigrator {

    /** 程序当前支持的数据库结构版本。新增表/字段时必须 +1 并追加迁移步骤。 */
    public static final int SCHEMA_VERSION = 1;

    /** 全部迁移步骤，按版本升序。 */
    private static final List<MigrationStep> STEPS = List.of(stepToV1());

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
                for (String ddl : step.statements()) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate(ddl);
                    }
                }
                setVersion(connection, step.version());
                current = step.version();
            }
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

    /** 单个迁移步骤：执行完 {@code version} 所列 DDL 后，库结构版本应等于 {@code version}。 */
    private record MigrationStep(int version, List<String> statements) {
    }
}
