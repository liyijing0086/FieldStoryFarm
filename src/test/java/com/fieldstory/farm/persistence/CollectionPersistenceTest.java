package com.fieldstory.farm.persistence;

import com.fieldstory.farm.model.CollectionStatus;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.service.impl.BasicCollectionService;
import com.fieldstory.farm.service.impl.BasicFarmScoreService;
import com.fieldstory.farm.service.impl.BasicGraduationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3 收集与毕业持久化测试（E 模块 P3；验收规范 §一百三十二 ⑤、§一百二十一）。
 *
 * <p>验证图鉴 / 套装 / 毕业状态跨「保存 → 关闭 → 重开」无损恢复，FarmScore 不丢失。
 */
class CollectionPersistenceTest {

    @TempDir
    Path tempDir;

    private DatabaseService database(String name) {
        return new DatabaseService(tempDir.resolve(name));
    }

    @Test
    void collectionAndScoreSurviveRestart() {
        DatabaseService db = database("collect.db");

        GameState state = new GameState(new Player("农夫", 500), 9L);
        BasicCollectionService collection = new BasicCollectionService(state);
        collection.discoverCrop(CropType.WHEAT, Quality.EXCELLENT);
        collection.collectCrop(CropType.WHEAT, Quality.RARE);
        collection.collectCrop(CropType.CORN, Quality.LEGENDARY);
        collection.collectDecoration("D01");
        collection.collectDecoration("D11");
        collection.collectLegendary(CropType.CORN);
        state.getSetCollection().getCollected().add("HARVEST_SOUL");
        state.getSetCollection().getActive().add("HARVEST_SOUL");

        int expectedScore = new BasicFarmScoreService(state).totalScore();
        new SqliteSaveService(db, null).save(state);

        // ---------- 关闭重开 ----------
        GameState loaded = new SqliteSaveService(db, null).load();
        assertNotNull(loaded);

        assertEquals(CollectionStatus.DISCOVERED,
                loaded.getCollection().cropStatus(CropType.WHEAT, Quality.EXCELLENT),
                "已发现状态应恢复");
        assertEquals(CollectionStatus.COLLECTED,
                loaded.getCollection().cropStatus(CropType.WHEAT, Quality.RARE),
                "已收集状态应恢复");
        assertEquals(CollectionStatus.COLLECTED,
                loaded.getCollection().cropStatus(CropType.CORN, Quality.LEGENDARY));
        assertTrue(loaded.getCollection().getDecorations().contains("D01"));
        assertTrue(loaded.getCollection().getDecorations().contains("D11"));
        assertTrue(loaded.getCollection().getLegendaries().contains(CropType.CORN));

        assertTrue(loaded.getSetCollection().getCollected().contains("HARVEST_SOUL"));
        assertTrue(loaded.getSetCollection().getActive().contains("HARVEST_SOUL"));

        assertEquals(expectedScore, new BasicFarmScoreService(loaded).totalScore(),
                "FarmScore 不应因退出重进而丢失（§一百三十二 ⑤）");
    }

    @Test
    void graduationStateSurvivesRestart() {
        DatabaseService db = database("graduation.db");

        GameState state = new GameState(new Player("农夫", 500), 42L);
        state.setWorldTotalMinutes(1008L);
        fillFullCollection(state);
        assertTrue(new BasicGraduationService(state, new BasicFarmScoreService(state))
                .evaluateAndGraduate(), "满收集应毕业");

        new SqliteSaveService(db, null).save(state);

        GameState loaded = new SqliteSaveService(db, null).load();
        assertNotNull(loaded);
        assertTrue(loaded.getGraduation().isGraduated(), "毕业状态应恢复（§一百二十九）");
        assertEquals(1008L, loaded.getGraduation().getGraduationWorldTime());
        assertEquals(42L, loaded.getGraduation().getGraduationGameDay());
        assertEquals(147, new BasicFarmScoreService(loaded).totalScore());
    }

    @Test
    void newGameHasEmptyCollectionAndNoGraduation() {
        DatabaseService db = database("empty.db");
        new SqliteSaveService(db, null).save(new GameState(new Player("农夫", 500), 0L));

        GameState loaded = new SqliteSaveService(db, null).load();
        assertNotNull(loaded);
        assertTrue(loaded.getCollection().getCrops().isEmpty());
        assertTrue(loaded.getCollection().getDecorations().isEmpty());
        assertTrue(loaded.getCollection().getLegendaries().isEmpty());
        assertTrue(loaded.getSetCollection().getCollected().isEmpty());
        assertFalse(loaded.getGraduation().isGraduated());
        assertEquals(0, new BasicFarmScoreService(loaded).totalScore());
    }

    @Test
    void p3TablesAreCreatedByMigration() throws SQLException {
        DatabaseService db = database("schema.db");
        try (Connection connection = db.openConnection()) {
            for (String table : new String[]{
                    "crop_collection", "decoration_collection", "legendary_collection",
                    "set_collection", "graduation"}) {
                assertTrue(tableExists(connection, table), "缺少 P3 表: " + table);
            }
        }
    }

    private static void fillFullCollection(GameState state) {
        BasicCollectionService collection = new BasicCollectionService(state);
        for (int i = 1; i <= 14; i++) {
            collection.collectDecoration(String.format("D%02d", i));
        }
        for (CropType cropType : CropType.values()) {
            for (Quality quality : Quality.values()) {
                collection.collectCrop(cropType, quality);
            }
            collection.collectLegendary(cropType);
        }
        for (String setId : new String[]{"NATURAL_BREATH", "HARVEST_SOUL", "LEGEND_LIGHT"}) {
            state.getSetCollection().getCollected().add(setId);
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var ps = connection.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
