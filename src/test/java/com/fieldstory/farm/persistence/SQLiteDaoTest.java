package com.fieldstory.farm.persistence;

import com.fieldstory.farm.model.CropType;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 SQLite DAO 集成测试（验收规范 §七十八 SQLiteDaoTest、§七十九“SQLite关闭重开数据正确”）。
 *
 * <p>覆盖 6 个 DAO 的增删改查与 {@code SqliteSaveService} 的整档往返；
 * 每个用例用临时数据库文件，并在“关闭连接 → 重新打开”后校验数据仍在。
 */
class SQLiteDaoTest {

    @TempDir
    Path tempDir;

    private DatabaseService database(String name) {
        return new DatabaseService(tempDir.resolve(name));
    }

    private static boolean tableExists(Connection connection, String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Test
    void schemaIsMigratedAndAllTablesExist() throws Exception {
        DatabaseService database = database("schema.db");
        try (Connection connection = database.openConnection()) {
            assertEquals(SchemaMigrator.SCHEMA_VERSION, SchemaMigrator.readVersion(connection));
            for (String table : new String[]{
                    "player", "player_seed", "unlocked", "farm", "soil", "crop", "decoration",
                    "world_state", "meta"}) {
                assertTrue(tableExists(connection, table), "缺少表: " + table);
            }
        }
    }

    @Test
    void playerSeedsAndUnlockedRoundTripAcrossReopen() throws Exception {
        DatabaseService database = database("player.db");
        Player player = new Player("测试农夫", 888);
        player.getSeedInventory().put(CropType.WHEAT, 4);
        player.getSeedInventory().put(CropType.CORN, 2);

        try (Connection connection = database.openConnection()) {
            PlayerDao dao = new PlayerDao(connection);
            dao.insert(player);
            dao.replaceSeeds(player.getSeedInventory());
            dao.replaceUnlocked(Set.of("shop", "land-2x2"));
        }

        try (Connection connection = database.openConnection()) {
            PlayerDao dao = new PlayerDao(connection);
            Player loaded = dao.find();
            assertNotNull(loaded);
            assertEquals("测试农夫", loaded.getName());
            assertEquals(888, loaded.getGold());

            Map<CropType, Integer> seeds = dao.findSeeds();
            assertEquals(4, seeds.get(CropType.WHEAT));
            assertEquals(2, seeds.get(CropType.CORN));
            assertEquals(0, seeds.get(CropType.CARROT));
            assertEquals(CropType.values().length, seeds.size(), "D15：空库存=全 CropType 0 计数");

            assertEquals(Set.of("shop", "land-2x2"), dao.findUnlocked());
        }
    }

    @Test
    void playerUpdateOverwritesRow() throws Exception {
        DatabaseService database = database("player-update.db");
        try (Connection connection = database.openConnection()) {
            PlayerDao dao = new PlayerDao(connection);
            dao.insert(new Player("旧名", 100));
            dao.update(new Player("新名", 250));
            Player loaded = dao.find();
            assertEquals("新名", loaded.getName());
            assertEquals(250, loaded.getGold());
        }
    }

    @Test
    void soilAndCropRoundTripAcrossReopen() throws Exception {
        DatabaseService database = database("crop.db");
        try (Connection connection = database.openConnection()) {
            new SoilDao(connection).insert(new SoilDao.SoilRow(66L, "5,6", 5, 6, "PLANTED"));
            new CropDao(connection).insert(new CropDao.CropRow(
                    "uuid-1", 66L, "WHEAT", "SPROUT", 0.35, "2026-09-09T10:00:00", 1, "2"));
        }

        try (Connection connection = database.openConnection()) {
            SoilDao.SoilRow soil = new SoilDao(connection).findById(66L);
            assertNotNull(soil);
            assertEquals("5,6", soil.plotId());
            assertEquals(5, soil.row());
            assertEquals(6, soil.column());
            assertEquals("PLANTED", soil.state());

            CropDao.CropRow crop = new CropDao(connection).findBySoilId(66L);
            assertNotNull(crop);
            assertEquals("uuid-1", crop.cropUuid());
            assertEquals(66L, crop.soilId());
            assertEquals("WHEAT", crop.cropType());
            assertEquals("SPROUT", crop.growthStage());
            assertEquals(0.35, crop.growthProgress(), 1e-9);
            assertEquals("2026-09-09T10:00:00", crop.plantWorldTime());
            assertEquals(1, crop.manualWaterCount());
            assertEquals("2", crop.lastManualWaterGameDay());
        }
    }

    @Test
    void deletingSoilCascadesToCrop() throws Exception {
        DatabaseService database = database("cascade.db");
        try (Connection connection = database.openConnection()) {
            SoilDao soilDao = new SoilDao(connection);
            CropDao cropDao = new CropDao(connection);
            soilDao.insert(new SoilDao.SoilRow(26L, "2,2", 2, 2, "PLANTED"));
            cropDao.insert(new CropDao.CropRow(
                    "uuid-9", 26L, "CORN", "GROWING", 12.5, "100", 0, "-1"));
            assertEquals(1, cropDao.count());

            soilDao.deleteAll();
            assertEquals(0, soilDao.count());
            assertEquals(0, cropDao.count(), "外键 ON DELETE CASCADE 应清理该土地的作物");
        }
    }

    @Test
    void decorationAssignsIdAndRoundTrips() throws Exception {
        DatabaseService database = database("decoration.db");
        DecorationState lantern = new DecorationState("STONE_LANTERN", 0, 1);
        DecorationState fountain = new DecorationState("FOUNTAIN", 1, 0);

        try (Connection connection = database.openConnection()) {
            DecorationDao dao = new DecorationDao(connection);
            dao.insert(lantern);
            dao.insert(fountain);
            assertTrue(lantern.getId() > 0, "未指定 id 时应回填自增主键");
            assertTrue(fountain.getId() > lantern.getId());
        }

        try (Connection connection = database.openConnection()) {
            DecorationDao dao = new DecorationDao(connection);
            assertEquals(2, dao.count());
            List<DecorationState> all = dao.findAll();
            assertEquals("STONE_LANTERN", all.get(0).getDecorationType());
            assertEquals("FOUNTAIN", all.get(1).getDecorationType());

            DecorationState found = dao.findById(lantern.getId());
            assertNotNull(found);
            assertEquals(0, found.getRow());
            assertEquals(1, found.getColumn());

            dao.deleteAll();
            assertEquals(0, dao.count());
        }
    }

    @Test
    void worldStateRoundTripIncludingNullColumns() throws Exception {
        DatabaseService database = database("world.db");
        try (Connection connection = database.openConnection()) {
            WorldStateDao dao = new WorldStateDao(connection);
            dao.insert(new WorldStateDao.WorldStateRow("2026-09-09T08:30:00", null, null, 12L, null));

            WorldStateDao.WorldStateRow row = dao.find();
            assertNotNull(row);
            assertEquals("2026-09-09T08:30:00", row.currentWorldTime());
            assertNull(row.lastRealTime());
            assertNull(row.currentWeather());
            assertEquals(12L, row.currentDayIndex());
            assertNull(row.randomSeed());

            // 更新：P1 天气/随机种子就位后写入（列结构 §七十三 提前预留）
            dao.update(new WorldStateDao.WorldStateRow("2026-09-10T06:00:00", "2026-09-10T05:00:00",
                    "RAIN", 13L, 42L));
            WorldStateDao.WorldStateRow updated = dao.find();
            assertEquals("RAIN", updated.currentWeather());
            assertEquals("2026-09-10T05:00:00", updated.lastRealTime());
            assertEquals(13L, updated.currentDayIndex());
            assertEquals(42L, updated.randomSeed());
        }
    }

    @Test
    void farmMapSizeRoundTrip() throws Exception {
        DatabaseService database = database("farm.db");
        try (Connection connection = database.openConnection()) {
            FarmDao dao = new FarmDao(connection);
            assertNull(dao.findMapSize());
            dao.insert(12, 12);
            assertEquals(12, dao.findMapSize()[0]);
            assertEquals(12, dao.findMapSize()[1]);
            dao.update(16, 16);
            assertEquals(16, dao.findMapSize()[0]);
            dao.deleteAll();
            assertNull(dao.findMapSize());
        }
    }

    @Test
    void saveServiceRoundTripSurvivesReopen() throws Exception {
        Path dbFile = tempDir.resolve("save.db");
        DatabaseService database = new DatabaseService(dbFile);
        SqliteSaveService service = new SqliteSaveService(database);
        assertFalse(service.hasSave(), "空库不应报告有存档");

        Player player = new Player("测试农夫", 888);
        player.getSeedInventory().put(CropType.WHEAT, 4);
        player.getSeedInventory().put(CropType.CORN, 2);
        GameState state = new GameState(player, 12L);
        state.setCurrentWorldTime("2026-09-09T08:30:00");
        state.getUnlocked().add("shop");
        state.getUnlocked().add("land-2x2");

        PlotState growing = new PlotState();
        growing.setPlotId("special-1");
        growing.setRow(5);
        growing.setColumn(6);
        growing.setState("PLANTED");
        growing.setCropUuid("uuid-1");
        growing.setCropType("WHEAT");
        growing.setGrowthStage("SPROUT");
        growing.setGrowthProgress(0.35);
        growing.setPlantWorldTime("2026-09-09T10:00:00");
        growing.setManualWaterCount(1);
        growing.setLastManualWaterGameDay("2");
        state.getPlots().add(growing);

        PlotState idle = new PlotState();
        idle.setRow(2);
        idle.setColumn(2);
        idle.setState("EMPTY");
        state.getPlots().add(idle);

        state.getDecorations().add(new DecorationState("STONE_LANTERN", 0, 1));

        service.save(state);

        // 关闭连接后重开（同一文件、新的 DatabaseService/Service 实例）
        SqliteSaveService reopened = new SqliteSaveService(new DatabaseService(dbFile));
        assertTrue(reopened.hasSave());
        GameState loaded = reopened.load();
        assertNotNull(loaded);

        assertEquals("测试农夫", loaded.getPlayer().getName());
        assertEquals(888, loaded.getPlayer().getGold());
        assertEquals(4, loaded.getPlayer().getSeedInventory().get(CropType.WHEAT));
        assertEquals(2, loaded.getPlayer().getSeedInventory().get(CropType.CORN));
        assertEquals(0, loaded.getPlayer().getSeedInventory().get(CropType.CARROT));
        assertEquals(12L, loaded.getGameDay());
        assertEquals("2026-09-09T08:30:00", loaded.getCurrentWorldTime());
        assertEquals(Set.of("shop", "land-2x2"), loaded.getUnlocked());

        // 地块按 soil_id 升序还原：(2,2)=26 在前，(5,6)=66 在后
        assertEquals(2, loaded.getPlots().size());
        PlotState idleLoaded = loaded.getPlots().get(0);
        assertEquals("2,2", idleLoaded.getPlotId());
        assertEquals(2, idleLoaded.getRow());
        assertEquals(2, idleLoaded.getColumn());
        assertEquals("EMPTY", idleLoaded.getState());
        assertFalse(idleLoaded.hasCrop());

        PlotState growingLoaded = loaded.getPlots().get(1);
        assertEquals("special-1", growingLoaded.getPlotId());
        assertEquals(5, growingLoaded.getRow());
        assertEquals(6, growingLoaded.getColumn());
        assertEquals("PLANTED", growingLoaded.getState());
        assertTrue(growingLoaded.hasCrop());
        assertEquals("uuid-1", growingLoaded.getCropUuid());
        assertEquals("WHEAT", growingLoaded.getCropType());
        assertEquals("SPROUT", growingLoaded.getGrowthStage());
        assertEquals(0.35, growingLoaded.getGrowthProgress(), 1e-9);
        assertEquals("2026-09-09T10:00:00", growingLoaded.getPlantWorldTime());
        assertEquals(1, growingLoaded.getManualWaterCount());
        assertEquals("2", growingLoaded.getLastManualWaterGameDay());

        assertEquals(1, loaded.getDecorations().size());
        assertEquals("STONE_LANTERN", loaded.getDecorations().get(0).getDecorationType());
    }

    @Test
    void saveRejectsNullState() {
        SqliteSaveService service = new SqliteSaveService(database("null.db"));
        assertThrows(IllegalArgumentException.class, () -> service.save(null));
    }

    @Test
    void nullableFieldsRoundTripWithoutConstraintFailure() throws Exception {
        // JSON 模型里 name/state 可缺失，迁入 SQLite 时不得因 NOT NULL 约束而失败
        Path dbFile = tempDir.resolve("nullable.db");
        SqliteSaveService service = new SqliteSaveService(new DatabaseService(dbFile));

        Player player = new Player("农夫", 100);
        player.setName(null);
        GameState state = new GameState(player, 0L);
        PlotState plot = new PlotState();
        plot.setRow(2);
        plot.setColumn(2);
        plot.setState(null);
        state.getPlots().add(plot);

        service.save(state);
        GameState loaded = service.load();

        assertNull(loaded.getPlayer().getName());
        assertEquals(1, loaded.getPlots().size());
        assertNull(loaded.getPlots().get(0).getState());
        assertFalse(loaded.getPlots().get(0).hasCrop());
    }

    @Test
    void savingTwiceReplacesPreviousData() throws Exception {
        Path dbFile = tempDir.resolve("replace.db");
        SqliteSaveService service = new SqliteSaveService(new DatabaseService(dbFile));

        GameState first = new GameState(new Player("农夫", 500), 1L);
        first.getPlots().add(plot(2, 2, "TILLED"));
        service.save(first);

        GameState second = new GameState(new Player("农夫", 700), 2L);
        service.save(second);

        GameState loaded = service.load();
        assertEquals(700, loaded.getPlayer().getGold());
        assertEquals(2L, loaded.getGameDay());
        assertTrue(loaded.getPlots().isEmpty(), "全量替换后旧地块不应残留");
    }

    private static PlotState plot(int row, int column, String state) {
        PlotState plot = new PlotState();
        plot.setRow(row);
        plot.setColumn(column);
        plot.setState(state);
        return plot;
    }
}
