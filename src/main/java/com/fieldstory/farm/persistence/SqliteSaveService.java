package com.fieldstory.farm.persistence;

import com.fieldstory.farm.model.DecorationState;
import com.fieldstory.farm.model.CollectionStatus;
import com.fieldstory.farm.model.CropQualityKey;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.GraduationState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.PlotState;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.model.item.Item;
import com.fieldstory.farm.persistence.dao.ActiveEventDao;
import com.fieldstory.farm.persistence.dao.CollectionDao;
import com.fieldstory.farm.persistence.dao.CropDao;
import com.fieldstory.farm.persistence.dao.CropMemoryDao;
import com.fieldstory.farm.persistence.dao.DecorationDao;
import com.fieldstory.farm.persistence.dao.FarmDao;
import com.fieldstory.farm.persistence.dao.GraduationDao;
import com.fieldstory.farm.persistence.dao.PlayerDao;
import com.fieldstory.farm.persistence.dao.PlayerItemDao;
import com.fieldstory.farm.persistence.dao.SetCollectionDao;
import com.fieldstory.farm.persistence.dao.SoilDao;
import com.fieldstory.farm.persistence.dao.WorldStateDao;
import com.fieldstory.farm.service.SaveService;
import com.fieldstory.farm.util.GameConstants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * P1 SQLite 正式存档实现（验收规范 §七十一~§七十五）。
 *
 * <p>P1 起 SQLite（{@code data/farm.db}）是<b>唯一正式运行存档</b>，JSON 玩家存档停止写入。
 * 本类把 E 的存档聚合 {@link GameState} 与 6 张表（player/player_seed/unlocked、
 * farm、soil、crop、decoration、world_state）通过 DAO 双向映射：
 * <ul>
 *   <li><b>保存</b>：单事务内“清空相关表 → 全量写入”，失败整体回滚，避免半截存档；</li>
 *   <li><b>读取</b>：由 soil 与 crop 还原每个地块快照，恢复退出瞬间状态，不做任何离线成长
 *       （离线模拟属 P2）；</li>
 *   <li><b>一次性迁移</b>：首次运行时若检测到旧 P0 {@code data/save.json} 且库中尚无玩家数据，
 *       读旧 JSON → 导入 SQLite → 校验 → 写迁移标记；之后只读 SQLite，
 *       <b>禁止 JSON + SQLite 双写</b>（验收规范 §七十四）。</li>
 * </ul>
 *
 * <p>业务层只依赖 {@link SaveService} 接口，替换实现不影响调用方（脚手架 §3.2）。
 *
 * <p><b>P3 增量（验收规范 §一百一十~§一百二十九）：</b>收集图鉴
 * （{@code crop_collection}/{@code decoration_collection}/{@code legendary_collection}）、
 * 套装状态（{@code set_collection}）与毕业状态（{@code graduation}）同样是「全量覆盖」的
 * 会话状态，随 {@link GameState} 一起清表—重写，保证 FarmScore 与毕业跨退出重进不丢失。
 */
public class SqliteSaveService implements SaveService {

    /** {@code meta} 键：JSON→SQLite 一次性迁移是否已完成（验收规范 §七十四“迁移完成标记”）。 */
    public static final String META_JSON_MIGRATION_DONE = "json_migration_done";

    /** 默认旧 P0 JSON 存档位置（迁移来源）。 */
    public static final Path DEFAULT_JSON_SAVE_FILE = Paths.get(JsonSaveService.DEFAULT_SAVE_FILE);

    private final DatabaseService databaseService;
    private final Path jsonSaveFile;

    /** 迁移是否已在本进程内检查过（避免每次读写都重复查询标记）。 */
    private volatile boolean migrationChecked = false;

    /** 默认构造：{@code data/farm.db} + 迁移源 {@code data/save.json}。 */
    public SqliteSaveService() {
        this(new DatabaseService(), DEFAULT_JSON_SAVE_FILE);
    }

    /** 仅指定数据库服务（不做 JSON 迁移，测试用）。 */
    public SqliteSaveService(DatabaseService databaseService) {
        this(databaseService, null);
    }

    /**
     * 完整构造。
     *
     * @param databaseService SQLite 数据库服务
     * @param jsonSaveFile    旧 P0 JSON 存档路径（迁移来源）；为 {@code null} 表示不迁移
     */
    public SqliteSaveService(DatabaseService databaseService, Path jsonSaveFile) {
        if (databaseService == null) {
            throw new IllegalArgumentException("databaseService 不能为空");
        }
        this.databaseService = databaseService;
        this.jsonSaveFile = jsonSaveFile;
    }

    /** 数据库文件位置（供测试与诊断）。 */
    public DatabaseService getDatabaseService() {
        return databaseService;
    }

    // ------------------------------------------------------------------
    // SaveService 实现
    // ------------------------------------------------------------------

    @Override
    public boolean hasSave() {
        ensureJsonMigratedIfNeeded();
        try (Connection connection = databaseService.openConnection()) {
            return new PlayerDao(connection).exists();
        } catch (SQLException e) {
            throw new IllegalStateException("读取存档状态失败: " + databaseService.getDatabaseFile(), e);
        }
    }

    @Override
    public void save(GameState state) {
        if (state == null) {
            throw new IllegalArgumentException("GameState 不能为空");
        }
        ensureJsonMigratedIfNeeded();
        try (Connection connection = databaseService.openConnection()) {
            connection.setAutoCommit(false);
            try {
                writeState(connection, state);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite 存档写入失败: " + databaseService.getDatabaseFile(), e);
        }
    }

    @Override
    public GameState load() {
        ensureJsonMigratedIfNeeded();
        try (Connection connection = databaseService.openConnection()) {
            return readState(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite 存档读取失败: " + databaseService.getDatabaseFile(), e);
        }
    }

    // ------------------------------------------------------------------
    // GameState ↔ 表 映射
    // ------------------------------------------------------------------

    /** 全量写入：清空相关表后逐表插入（须处于同一事务，由调用方控制）。 */
    void writeState(Connection connection, GameState state) throws SQLException {
        CropDao cropDao = new CropDao(connection);
        SoilDao soilDao = new SoilDao(connection);
        DecorationDao decorationDao = new DecorationDao(connection);
        PlayerDao playerDao = new PlayerDao(connection);
        FarmDao farmDao = new FarmDao(connection);
        WorldStateDao worldStateDao = new WorldStateDao(connection);
        CropMemoryDao cropMemoryDao = new CropMemoryDao(connection);
        ActiveEventDao activeEventDao = new ActiveEventDao(connection);
        PlayerItemDao playerItemDao = new PlayerItemDao(connection);
        CollectionDao collectionDao = new CollectionDao(connection);
        SetCollectionDao setCollectionDao = new SetCollectionDao(connection);
        GraduationDao graduationDao = new GraduationDao(connection);

        // 先清子表再清父表，避免外键约束（crop 依赖 soil）
        cropDao.deleteAll();
        soilDao.deleteAll();
        decorationDao.deleteAll();
        playerDao.deleteAllSeeds();
        playerDao.deleteAllUnlocked();
        playerDao.deleteAll();
        farmDao.deleteAll();
        worldStateDao.deleteAll();
        // P2：这三张表与其它表无外键关系，但同样是"全量覆盖"语义，必须一并清空，
        // 否则删除过的记忆/事件/物品会残留在旧存档里（幽灵数据）
        cropMemoryDao.deleteAll();
        activeEventDao.deleteAll();
        playerItemDao.deleteAll();
        // P3：收集图鉴 / 套装 / 毕业同样是"全量覆盖"语义，必须一并清空，
        // 否则被取消收集的残留行会成为幽灵数据（收藏本应只增，但重开新档要能清零）。
        collectionDao.deleteAll();
        setCollectionDao.deleteAll();
        graduationDao.deleteAll();

        Player player = state.getPlayer();
        if (player != null) {
            playerDao.insert(player);
            playerDao.replaceSeeds(player.getSeedInventory());
        }
        playerDao.replaceUnlocked(state.getUnlocked());

        farmDao.insert(GameConstants.MAP_ROWS, GameConstants.MAP_COLS);

        // 两趟写入：先土地，后作物（作物经外键引用土地）
        for (PlotState plot : state.getPlots()) {
            if (plot == null) {
                continue;
            }
            soilDao.insert(new SoilDao.SoilRow(
                    soilId(plot.getRow(), plot.getColumn()),
                    (plot.getPlotId() == null || plot.getPlotId().isBlank())
                            ? plot.getRow() + "," + plot.getColumn()
                            : plot.getPlotId(),
                    plot.getRow(),
                    plot.getColumn(),
                    plot.getState()));
        }
        for (PlotState plot : state.getPlots()) {
            if (plot == null || !plot.hasCrop()) {
                continue;
            }
            cropDao.insert(new CropDao.CropRow(
                    plot.getCropUuid(),
                    soilId(plot.getRow(), plot.getColumn()),
                    plot.getCropType(),
                    plot.getGrowthStage(),
                    plot.getGrowthProgress(),
                    plot.getPlantWorldTime(),
                    plot.getManualWaterCount(),
                    plot.getLastManualWaterGameDay(),
                    plot.getFertilizerCount(),
                    plot.getLastFertilizedGameDay(),
                    plot.getDroughtCount(),
                    plot.getRainCount(),
                    plot.getGreenRainCount(),
                    plot.getLastHydratedWorldTime(),
                    plot.getDroughtStreak(),
                    plot.getEventCount()));
        }

        for (DecorationState decoration : state.getDecorations()) {
            if (decoration != null) {
                decorationDao.insert(decoration);
            }
        }

        // P2：生命记忆（收获后仍永久保留，§九十五）——以 cropUuid 覆盖式写入
        for (CropMemory memory : state.getMemories()) {
            if (memory != null) {
                cropMemoryDao.upsert(memory);
            }
        }

        // P2：当前随机事件快照（事件期间退出，回来不能凭空消失，§九十一）
        if (state.getActiveEvent() != null) {
            activeEventDao.upsert(state.getActiveEvent());
        }

        // P2：玩家背包物品（种子库存不在这里，见 player_seed）
        playerItemDao.replaceAll(state.getInventory());

        // current_day_index 承载存档游戏天数；weather 承接天气；world_total_minutes 为 P2 新增，
        // 让读档精确回到"退出瞬间"（-1 = 未记录）。last_real_time 供 P2 离线模拟计算时长，
        // P1 先写存档时刻的真实时间。
        worldStateDao.insert(new WorldStateDao.WorldStateRow(
                state.getCurrentWorldTime(),
                state.getLastRealTime(),
                state.getCurrentWeather() == null ? null : state.getCurrentWeather().name(),
                state.getGameDay(),
                null,
                state.getWorldTotalMinutes()));

        // P3：收集图鉴（永久保存，退出重进不丢 FarmScore，验收规范 §一百三十二 ⑤）
        for (CropQualityKey key : state.getCollection().getCrops().keySet()) {
            CollectionStatus status = state.getCollection().getCrops().get(key);
            if (status != null && status != CollectionStatus.UNDISCOVERED) {
                collectionDao.upsertCrop(key, status);
            }
        }
        for (String decorationType : state.getCollection().getDecorations()) {
            collectionDao.upsertDecoration(decorationType);
        }
        for (CropType legendary : state.getCollection().getLegendaries()) {
            collectionDao.upsertLegendary(legendary);
        }

        // P3：套装 collected / active 两个独立状态都必须落库（验收规范 §一百一十八）
        Set<String> touchedSets = new LinkedHashSet<>();
        touchedSets.addAll(state.getSetCollection().getCollected());
        touchedSets.addAll(state.getSetCollection().getActive());
        for (String setId : touchedSets) {
            setCollectionDao.upsert(setId,
                    state.getSetCollection().getCollected().contains(setId),
                    state.getSetCollection().getActive().contains(setId));
        }

        // P3：毕业状态（未毕业不写行，读回即"未毕业"）
        if (state.getGraduation().isGraduated()) {
            graduationDao.upsert(state.getGraduation());
        }
    }

    /**
     * 全量读取：由 soil（左连 crop）还原地块快照。
     *
     * @return 存档状态；库中无玩家数据时返回 {@code null}
     */
    GameState readState(Connection connection) throws SQLException {
        PlayerDao playerDao = new PlayerDao(connection);
        Player player = playerDao.find();
        if (player == null) {
            return null;
        }
        player.setSeedInventory(playerDao.findSeeds());

        GameState state = new GameState();
        state.setPlayer(player);
        state.getUnlocked().addAll(playerDao.findUnlocked());

        WorldStateDao.WorldStateRow worldState = new WorldStateDao(connection).find();
        if (worldState != null) {
            state.setGameDay(worldState.currentDayIndex());
            state.setCurrentWorldTime(worldState.currentWorldTime());
            state.setLastRealTime(worldState.lastRealTime());
            state.setWorldTotalMinutes(worldState.worldTotalMinutes());
            state.setCurrentWeather(parseEnum(WeatherType.class, worldState.currentWeather()));
            state.setWeatherDayIndex((int) worldState.currentDayIndex());
        }

        Map<Long, CropDao.CropRow> cropsBySoil = new HashMap<>();
        for (CropDao.CropRow crop : new CropDao(connection).findAll()) {
            cropsBySoil.put(crop.soilId(), crop);
        }
        for (SoilDao.SoilRow soil : new SoilDao(connection).findAll()) {
            PlotState plot = new PlotState();
            plot.setRow(soil.row());
            plot.setColumn(soil.column());
            plot.setPlotId((soil.plotId() == null || soil.plotId().isBlank())
                    ? soil.row() + "," + soil.column()
                    : soil.plotId());
            plot.setState(soil.state());
            CropDao.CropRow crop = cropsBySoil.get(soil.soilId());
            if (crop != null) {
                applyCrop(plot, crop);
            }
            state.getPlots().add(plot);
        }

        for (DecorationState decoration : new DecorationDao(connection).findAll()) {
            state.getDecorations().add(decoration);
        }

        // P2：生命记忆 / 当前事件 / 背包物品
        state.getMemories().addAll(new CropMemoryDao(connection).findAll());
        state.setActiveEvent(new ActiveEventDao(connection).find());
        for (Item item : new PlayerItemDao(connection).load().listItems()) {
            state.getInventory().addItem(item);
        }

        // P3：收集图鉴 / 套装 / 毕业（永久状态，恢复后 FarmScore 与评价不丢失）
        CollectionDao collectionDao = new CollectionDao(connection);
        state.getCollection().getCrops().putAll(collectionDao.findAllCrops());
        state.getCollection().getDecorations().addAll(collectionDao.findAllDecorations());
        state.getCollection().getLegendaries().addAll(collectionDao.findAllLegendaries());
        for (SetCollectionDao.SetRow row : new SetCollectionDao(connection).findAll()) {
            if (row.collected()) {
                state.getSetCollection().getCollected().add(row.setId());
            }
            if (row.active()) {
                state.getSetCollection().getActive().add(row.setId());
            }
        }
        GraduationState graduation = new GraduationDao(connection).find();
        if (graduation != null) {
            state.getGraduation().setGraduated(graduation.isGraduated());
            state.getGraduation().setGraduationWorldTime(graduation.getGraduationWorldTime());
            state.getGraduation().setGraduationGameDay(graduation.getGraduationGameDay());
        }

        warnOnMapSizeMismatch(new FarmDao(connection).findMapSize());
        return state;
    }

    /** 宽容解析枚举名；null/空/非法一律返回 null（坏数据不让读档崩溃）。 */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, name.trim());
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static void applyCrop(PlotState plot, CropDao.CropRow crop) {
        plot.setCropUuid(crop.cropUuid());
        plot.setCropType(crop.cropType());
        plot.setGrowthStage(crop.growthStage());
        plot.setGrowthProgress(crop.growthProgress());
        plot.setPlantWorldTime(crop.plantWorldTime());
        plot.setManualWaterCount(crop.manualWaterCount());
        plot.setLastManualWaterGameDay(crop.lastManualWaterGameDay());
        plot.setFertilizerCount(crop.fertilizerCount());
        plot.setLastFertilizedGameDay(crop.lastFertilizedGameDay());
        plot.setDroughtCount(crop.droughtCount());
        plot.setRainCount(crop.rainCount());
        plot.setGreenRainCount(crop.greenRainCount());
        plot.setLastHydratedWorldTime(crop.lastHydratedWorldTime());
        plot.setDroughtStreak(crop.droughtStreak());
        plot.setEventCount(crop.eventCount());
    }

    /** 地图尺寸与当前版本不一致时告警（不阻断读档，仅提示可能来自旧/新版本存档）。 */
    private static void warnOnMapSizeMismatch(int[] stored) {
        if (stored != null
                && (stored[0] != GameConstants.MAP_ROWS || stored[1] != GameConstants.MAP_COLS)) {
            System.err.println("[SqliteSaveService] 存档地图尺寸(" + stored[0] + "×" + stored[1]
                    + ")与当前版本(" + GameConstants.MAP_ROWS + "×" + GameConstants.MAP_COLS + ")不一致");
        }
    }

    /** 土地 id 口径与 A 模块 {@code BasicSoil} 一致：行 × 地图列数 + 列。 */
    private static long soilId(int row, int column) {
        return row * (long) GameConstants.MAP_COLS + column;
    }

    // ------------------------------------------------------------------
    // JSON → SQLite 一次性迁移（验收规范 §七十四）
    // ------------------------------------------------------------------

    /** 进程内只检查一次；迁移失败不置位，允许下次重试。 */
    private void ensureJsonMigratedIfNeeded() {
        if (migrationChecked) {
            return;
        }
        synchronized (this) {
            if (migrationChecked) {
                return;
            }
            migrateJsonOnce();
            migrationChecked = true;
        }
    }

    /**
     * 迁移流程：库无玩家数据 且 旧 JSON 存在 → 导入 → 校验 → 写标记。
     * <ul>
     *   <li>库已有玩家数据 → 只补标记，不覆盖；</li>
     *   <li>无旧 JSON（或路径为空）→ 写 “none” 标记，避免每次启动重复探测；</li>
     *   <li>旧 JSON 损坏 → <b>不写标记</b>，保留修复后重试的机会；</li>
     *   <li>导入成功 → 写导入时间标记（验收规范 §七十四“迁移完成标记”）。</li>
     * </ul>
     */
    private void migrateJsonOnce() {
        try (Connection connection = databaseService.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (databaseService.getMeta(connection, META_JSON_MIGRATION_DONE) != null) {
                    connection.commit();
                    return;
                }
                PlayerDao playerDao = new PlayerDao(connection);
                if (playerDao.exists()) {
                    databaseService.putMeta(connection, META_JSON_MIGRATION_DONE, "existing");
                    connection.commit();
                    return;
                }
                if (jsonSaveFile == null || !Files.isRegularFile(jsonSaveFile)) {
                    databaseService.putMeta(connection, META_JSON_MIGRATION_DONE, "none");
                    connection.commit();
                    return;
                }

                GameState legacy;
                try {
                    legacy = new JsonSaveService(jsonSaveFile).load();
                } catch (IllegalStateException corrupt) {
                    System.err.println("[SqliteSaveService] 旧 JSON 存档无法迁移（保留待修复后重试）: "
                            + corrupt.getMessage());
                    connection.rollback();
                    return;
                }
                if (legacy != null && legacy.getPlayer() != null) {
                    writeState(connection, legacy);
                    databaseService.putMeta(connection, META_JSON_MIGRATION_DONE,
                            LocalDateTime.now().toString());
                    System.err.println("[SqliteSaveService] 已从旧 JSON 存档迁移玩家数据: " + jsonSaveFile);
                } else {
                    databaseService.putMeta(connection, META_JSON_MIGRATION_DONE, "empty");
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("JSON→SQLite 迁移失败: " + databaseService.getDatabaseFile(), e);
        }
    }
}
