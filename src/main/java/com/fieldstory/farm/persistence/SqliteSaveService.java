package com.fieldstory.farm.persistence;

import com.fieldstory.farm.model.DecorationState;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.PlotState;
import com.fieldstory.farm.persistence.dao.CropDao;
import com.fieldstory.farm.persistence.dao.DecorationDao;
import com.fieldstory.farm.persistence.dao.FarmDao;
import com.fieldstory.farm.persistence.dao.PlayerDao;
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
import java.util.Map;

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

        // 先清子表再清父表，避免外键约束（crop 依赖 soil）
        cropDao.deleteAll();
        soilDao.deleteAll();
        decorationDao.deleteAll();
        playerDao.deleteAllSeeds();
        playerDao.deleteAllUnlocked();
        playerDao.deleteAll();
        farmDao.deleteAll();
        worldStateDao.deleteAll();

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
                    plot.getLastManualWaterGameDay()));
        }

        for (DecorationState decoration : state.getDecorations()) {
            if (decoration != null) {
                decorationDao.insert(decoration);
            }
        }

        // current_day_index 承载存档游戏天数；weather/last_real_time/random_seed 属 P1/P2，
        // P1 先写 null，等 D 模块天气接入后由适配层补齐（列结构 §七十三 已就位）
        worldStateDao.insert(new WorldStateDao.WorldStateRow(
                state.getCurrentWorldTime(), null, null, state.getGameDay(), null));
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

        warnOnMapSizeMismatch(new FarmDao(connection).findMapSize());
        return state;
    }

    private static void applyCrop(PlotState plot, CropDao.CropRow crop) {
        plot.setCropUuid(crop.cropUuid());
        plot.setCropType(crop.cropType());
        plot.setGrowthStage(crop.growthStage());
        plot.setGrowthProgress(crop.growthProgress());
        plot.setPlantWorldTime(crop.plantWorldTime());
        plot.setManualWaterCount(crop.manualWaterCount());
        plot.setLastManualWaterGameDay(crop.lastManualWaterGameDay());
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
