package com.fieldstory.farm.persistence;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.PlotState;
import com.fieldstory.farm.persistence.dao.PlayerDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 迁移测试（验收规范 §七十四 JSON 一次性迁移、§七十九“P0 JSON可成功迁移”，
 * 以及后续版本结构升级不丢数据）。
 *
 * <p>覆盖两条迁移线：
 * <ol>
 *   <li><b>数据迁移</b>：旧 P0 {@code save.json} → SQLite，一次性导入并写迁移标记，
 *       禁止 JSON + SQLite 双写；</li>
 *   <li><b>结构迁移</b>：{@code PRAGMA user_version} 驱动的增量升级，
 *       旧版本库升到最新结构时不重建已有表、不丢数据。</li>
 * </ol>
 */
class MigrationTest {

    @TempDir
    Path tempDir;

    private String jdbcUrl(Path dbFile) {
        return "jdbc:sqlite:" + dbFile.toAbsolutePath();
    }

    private static GameState legacyState(String name, int gold, long gameDay) {
        Player player = new Player(name, gold);
        player.getSeedInventory().put(CropType.WHEAT, 1);
        player.getSeedInventory().put(CropType.CORN, 2);
        player.getSeedInventory().put(CropType.CARROT, 2);
        GameState state = new GameState(player, gameDay);
        state.setCurrentWorldTime("2026-09-09T08:30:00");
        state.getUnlocked().add("shop");

        PlotState plot = new PlotState();
        plot.setRow(5);
        plot.setColumn(6);
        plot.setState("PLANTED");
        plot.setCropUuid("uuid-1");
        plot.setCropType("WHEAT");
        plot.setGrowthStage("SPROUT");
        plot.setGrowthProgress(0.35);
        plot.setPlantWorldTime("2026-09-09T10:00:00");
        plot.setManualWaterCount(1);
        plot.setLastManualWaterGameDay("2");
        state.getPlots().add(plot);
        return state;
    }

    @Test
    void legacyJsonIsImportedOnceAndMarkerIsSet() throws Exception {
        Path jsonFile = tempDir.resolve("save.json");
        new JsonSaveService(jsonFile).save(legacyState("旧档农夫", 340, 3L));

        Path dbFile = tempDir.resolve("farm.db");
        DatabaseService database = new DatabaseService(dbFile);
        SqliteSaveService service = new SqliteSaveService(database, jsonFile);

        assertTrue(service.hasSave(), "首次运行应把旧 P0 JSON 迁入 SQLite");
        GameState loaded = service.load();
        assertNotNull(loaded);
        assertEquals("旧档农夫", loaded.getPlayer().getName());
        assertEquals(340, loaded.getPlayer().getGold());
        assertEquals(1, loaded.getPlayer().getSeedInventory().get(CropType.WHEAT));
        assertEquals(3L, loaded.getGameDay());
        assertEquals("2026-09-09T08:30:00", loaded.getCurrentWorldTime());
        assertEquals(java.util.Set.of("shop"), loaded.getUnlocked());
        assertEquals(1, loaded.getPlots().size());
        PlotState plot = loaded.getPlots().get(0);
        assertEquals(5, plot.getRow());
        assertEquals(6, plot.getColumn());
        assertEquals("PLANTED", plot.getState());
        assertEquals("uuid-1", plot.getCropUuid());
        assertEquals("WHEAT", plot.getCropType());
        assertEquals(0.35, plot.getGrowthProgress(), 1e-9);

        // 迁移完成标记已写入（§七十四）
        try (Connection connection = new DatabaseService(dbFile).openConnection()) {
            assertNotNull(new DatabaseService(dbFile)
                    .getMeta(connection, SqliteSaveService.META_JSON_MIGRATION_DONE));
        }
    }

    @Test
    void sqliteDataIsNotOverwrittenByLegacyJson() throws Exception {
        Path dbFile = tempDir.resolve("farm.db");
        Path jsonFile = tempDir.resolve("save.json");

        // 先建立 SQLite 正式档（JSON 源尚不存在 → 写 “none” 标记）
        new SqliteSaveService(new DatabaseService(dbFile))
                .save(new GameState(new Player("SQLite农夫", 900), 5L));

        // 之后才出现一份内容不同的旧 JSON：不得覆盖 SQLite 正式档（禁止双写取旧）
        new JsonSaveService(jsonFile).save(legacyState("JSON农夫", 111, 1L));

        SqliteSaveService reopened = new SqliteSaveService(new DatabaseService(dbFile), jsonFile);
        GameState loaded = reopened.load();
        assertEquals("SQLite农夫", loaded.getPlayer().getName());
        assertEquals(900, loaded.getPlayer().getGold());
        assertEquals(5L, loaded.getGameDay());
    }

    @Test
    void missingLegacyJsonSkipsMigrationWithoutError() {
        SqliteSaveService service = new SqliteSaveService(
                new DatabaseService(tempDir.resolve("farm.db")),
                tempDir.resolve("absent.json"));
        assertFalse(service.hasSave());
        assertNull(service.load());
    }

    @Test
    void corruptLegacyJsonDoesNotBlockStartupAndIsNotMarked() throws Exception {
        Path jsonFile = tempDir.resolve("corrupt.json");
        Files.writeString(jsonFile, "{{{corrupt", StandardCharsets.UTF_8);
        Path dbFile = tempDir.resolve("farm.db");

        SqliteSaveService service = new SqliteSaveService(new DatabaseService(dbFile), jsonFile);
        // 损坏的旧档应降级为“无档”，不抛异常、不阻断启动
        assertFalse(service.hasSave());
        assertNull(service.load());

        // 未写迁移标记：修复 JSON 后仍可重试导入
        DatabaseService database = new DatabaseService(dbFile);
        try (Connection connection = database.openConnection()) {
            assertNull(database.getMeta(connection, SqliteSaveService.META_JSON_MIGRATION_DONE));
        }
    }

    @Test
    void olderSchemaVersionUpgradesWithoutLosingData() throws Exception {
        Path dbFile = tempDir.resolve("upgrade.db");
        DatabaseService database = new DatabaseService(dbFile);

        // 建立最新结构并写入一条数据
        try (Connection connection = database.openConnection()) {
            new PlayerDao(connection).insert(new Player("老存档农夫", 123));
        }

        // 模拟“旧版本库”：结构在，但 user_version 落后于程序版本
        try (Connection connection = DriverManager.getConnection(jdbcUrl(dbFile));
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA user_version = 0");
        }

        // 再次打开 → 迁移器把版本升回最新，且不重建表、不丢数据
        try (Connection connection = database.openConnection()) {
            assertEquals(SchemaMigrator.SCHEMA_VERSION, SchemaMigrator.readVersion(connection));
            Player player = new PlayerDao(connection).find();
            assertNotNull(player, "结构升级后原有数据必须保留");
            assertEquals("老存档农夫", player.getName());
            assertEquals(123, player.getGold());
        }
    }

    @Test
    void migrateRejectsFutureSchemaVersion() throws Exception {
        Path dbFile = tempDir.resolve("future.db");
        DatabaseService database = new DatabaseService(dbFile);
        try (Connection connection = database.openConnection()) {
            assertEquals(SchemaMigrator.SCHEMA_VERSION, SchemaMigrator.readVersion(connection));
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl(dbFile));
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA user_version = " + (SchemaMigrator.SCHEMA_VERSION + 1));
        }
        assertThrows(IllegalStateException.class, database::openConnection,
                "库结构版本高于程序支持版本时应拒绝打开");
    }
}
