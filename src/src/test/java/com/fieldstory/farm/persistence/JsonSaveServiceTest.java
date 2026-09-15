package com.fieldstory.farm.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.PlotState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 JsonSaveService 测试（验收规范 §四十三 至少 JsonSaveServiceTest）。
 */
class JsonSaveServiceTest {

    @TempDir
    Path tempDir;

    private JsonSaveService service(String name) {
        return new JsonSaveService(tempDir.resolve(name));
    }

    /** 读取唯一种子库存中的数量（缺失作物视为 0）。 */
    private static int seedCount(Player player, CropType type) {
        Integer count = player.getSeedInventory().get(type);
        return count == null ? 0 : count;
    }

    @Test
    void loadReturnsNullWhenNoSaveExists() {
        JsonSaveService svc = service("missing/save.json");
        assertFalse(svc.hasSave());
        assertNull(svc.load());
    }

    @Test
    void saveCreatesFileWithVersionAndSchema() throws Exception {
        JsonSaveService svc = service("save.json");
        Player player = new Player("农夫A", 500);
        player.getSeedInventory().put(CropType.WHEAT, 4);
        GameState state = new GameState(player, 3L);
        svc.save(state);

        Path file = tempDir.resolve("save.json");
        assertTrue(svc.hasSave());
        assertTrue(Files.isRegularFile(file));

        JsonNode root = new ObjectMapper().readTree(Files.readString(file, StandardCharsets.UTF_8));
        assertEquals(2, root.path("version").asInt());
        assertEquals("P0-json", root.path("schema").asText());
        assertEquals(3L, root.path("gameDay").asLong());
        assertEquals("农夫A", root.path("player").path("name").asText());
        assertEquals(500, root.path("player").path("gold").asInt());
        // 种子库存归属 Player（B §6.2），落盘在 player 节点下（验收 §四十一）
        assertEquals(4, root.path("player").path("seedInventory").path("WHEAT").asInt());
    }

    @Test
    void saveCreatesParentDirectories() {
        JsonSaveService svc = service("nested/dir/save.json");
        GameState state = new GameState(new Player("农夫", 500), 0L);
        svc.save(state);
        assertTrue(Files.isRegularFile(tempDir.resolve("nested/dir/save.json")));
    }

    @Test
    void roundTripPreservesPlayerDayUnlockedAndPlots() throws Exception {
        JsonSaveService svc = service("roundtrip.json");

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
        growing.setState("GROWING");
        growing.setCropUuid("uuid-1");
        growing.setCropType("WHEAT");
        growing.setGrowthStage("SPROUT");
        growing.setGrowthProgress(0.35);
        growing.setPlantWorldTime("2026-09-09T10:00:00");
        growing.setManualWaterCount(1);
        growing.setLastManualWaterGameDay("2");
        state.getPlots().add(growing);

        // 无 plotId：反序列化后按 "row,column" 补全
        PlotState idle = new PlotState();
        idle.setRow(2);
        idle.setColumn(2);
        idle.setState("EMPTY");
        state.getPlots().add(idle);

        svc.save(state);
        GameState loaded = svc.load();

        // Player 经济与种子库存
        assertEquals("测试农夫", loaded.getPlayer().getName());
        assertEquals(888, loaded.getPlayer().getGold());
        assertEquals(4, seedCount(loaded.getPlayer(), CropType.WHEAT));
        assertEquals(2, seedCount(loaded.getPlayer(), CropType.CORN));
        assertEquals(0, seedCount(loaded.getPlayer(), CropType.CARROT));
        // 天数、世界时间与已解锁
        assertEquals(12L, loaded.getGameDay());
        assertEquals("2026-09-09T08:30:00", loaded.getCurrentWorldTime());
        assertEquals(java.util.Set.of("shop", "land-2x2"), loaded.getUnlocked());
        // 土地快照
        assertEquals(2, loaded.getPlots().size());

        PlotState p0 = loaded.getPlots().get(0);
        assertEquals("special-1", p0.getPlotId());
        assertEquals(5, p0.getRow());
        assertEquals(6, p0.getColumn());
        assertEquals("GROWING", p0.getState());
        assertTrue(p0.hasCrop());
        assertEquals("uuid-1", p0.getCropUuid());
        assertEquals("WHEAT", p0.getCropType());
        assertEquals("SPROUT", p0.getGrowthStage());
        assertEquals(0.35, p0.getGrowthProgress(), 1e-9);
        assertEquals("2026-09-09T10:00:00", p0.getPlantWorldTime());
        assertEquals(1, p0.getManualWaterCount());
        assertEquals("2", p0.getLastManualWaterGameDay());

        PlotState p1 = loaded.getPlots().get(1);
        assertEquals("2,2", p1.getPlotId());
        assertEquals(2, p1.getRow());
        assertEquals(2, p1.getColumn());
        assertEquals("EMPTY", p1.getState());
        assertFalse(p1.hasCrop());
    }

    @Test
    void roundTripOfEmptyState() throws Exception {
        JsonSaveService svc = service("empty.json");
        GameState state = new GameState();
        state.setGameDay(0);
        svc.save(state);

        GameState loaded = svc.load();
        assertNull(loaded.getPlayer());
        assertEquals(0L, loaded.getGameDay());
        assertNull(loaded.getCurrentWorldTime());
        assertTrue(loaded.getUnlocked().isEmpty());
        assertTrue(loaded.getPlots().isEmpty());
    }

    @Test
    void saveRejectsNullState() {
        JsonSaveService svc = service("null.json");
        assertThrows(IllegalArgumentException.class, () -> svc.save(null));
    }

    @Test
    void seedInventoryRoundTripPreservesCounts() throws Exception {
        JsonSaveService svc = service("seeds.json");
        Player player = new Player("农夫", 500);
        player.getSeedInventory().put(CropType.WHEAT, 5);
        player.getSeedInventory().put(CropType.CARROT, 1);
        GameState state = new GameState(player, 0L);

        svc.save(state);
        GameState loaded = svc.load();

        assertEquals(5, seedCount(loaded.getPlayer(), CropType.WHEAT));
        assertEquals(1, seedCount(loaded.getPlayer(), CropType.CARROT));
        assertEquals(0, seedCount(loaded.getPlayer(), CropType.CORN));
    }

    @Test
    void playerWithoutSeedsRoundTripsAsFullZeroCountInventory() throws Exception {
        JsonSaveService svc = service("noseeds.json");
        GameState state = new GameState(new Player("农夫", 500), 0L);

        svc.save(state);
        GameState loaded = svc.load();

        // D15：库存"空"= 全部 CropType 预填 0 计数；往返后恒为「3 键 0 计数表」，
        // 键集与顺序无关，不因缺省而塌缩，故 isEmpty() 为 false
        assertNotNull(loaded.getPlayer().getSeedInventory());
        assertFalse(loaded.getPlayer().getSeedInventory().isEmpty());
        assertEquals(CropType.values().length, loaded.getPlayer().getSeedInventory().size());
        for (CropType type : CropType.values()) {
            assertNotNull(loaded.getPlayer().getSeedInventory().get(type),
                    "往返后应保留 " + type + " 键");
            assertEquals(0, seedCount(loaded.getPlayer(), type));
        }
    }

    @Test
    void loadLegacyVersion1WithoutSeedInventoryStillLoads() throws Exception {
        // v1 旧档没有 seedInventory 字段：应兼容读入
        Path file = tempDir.resolve("legacy-v1.json");
        Files.writeString(file,
                "{\"version\":1,\"schema\":\"P0-json\",\"gameDay\":7,"
                        + "\"player\":{\"name\":\"老档\",\"gold\":250},\"plots\":[]}",
                StandardCharsets.UTF_8);
        JsonSaveService svc = new JsonSaveService(file);

        GameState loaded = svc.load();
        assertEquals(7L, loaded.getGameDay());
        assertEquals(250, loaded.getPlayer().getGold());
        // D15：缺字段 → Player.setSeedInventory(null/空表) 的既有归一化行为 → 全部 CropType 预填 0 表
        assertNotNull(loaded.getPlayer().getSeedInventory());
        assertEquals(CropType.values().length, loaded.getPlayer().getSeedInventory().size());
        for (CropType type : CropType.values()) {
            assertEquals(0, seedCount(loaded.getPlayer(), type), "旧档种子库存应按 0 计数归一化");
        }
    }

    @Test
    void loadEarlyV2WithRootLevelSeedInventoryFallsBack() throws Exception {
        // 早期 v2 曾把 seedInventory 放在根节点：读入时应回退兼容
        Path file = tempDir.resolve("early-v2.json");
        Files.writeString(file,
                "{\"version\":2,\"schema\":\"P0-json\",\"gameDay\":1,"
                        + "\"player\":{\"name\":\"旧版\",\"gold\":500},"
                        + "\"seedInventory\":{\"CORN\":9},\"plots\":[]}",
                StandardCharsets.UTF_8);
        JsonSaveService svc = new JsonSaveService(file);

        GameState loaded = svc.load();
        assertEquals(9, seedCount(loaded.getPlayer(), CropType.CORN));
    }

    @Test
    void loadSkipsUnknownCropTypeName() throws Exception {
        // 未来新增作物名：旧程序应跳过而不崩溃（兼容性红线），已知作物正常读入
        Path file = tempDir.resolve("future-crop.json");
        Files.writeString(file,
                "{\"version\":2,\"schema\":\"P0-json\",\"gameDay\":0,"
                        + "\"player\":{\"name\":\"农夫\",\"gold\":500,"
                        + "\"seedInventory\":{\"WHEAT\":2,\"PUMPKIN\":99}},\"plots\":[]}",
                StandardCharsets.UTF_8);
        JsonSaveService svc = new JsonSaveService(file);

        // 未知键 PUMPKIN 被忽略（load 不抛异常即证明未崩溃）
        GameState loaded = svc.load();
        assertEquals(2, seedCount(loaded.getPlayer(), CropType.WHEAT));
        // D15：忽略未知键后按已知 CropType 归一化 → 恒为全部作物键，未知键不占用键位
        assertEquals(CropType.values().length, loaded.getPlayer().getSeedInventory().size());
        for (CropType type : CropType.values()) {
            if (type != CropType.WHEAT) {
                assertEquals(0, seedCount(loaded.getPlayer(), type), "未出现的作物应归一化为 0");
            }
        }
    }

    @Test
    void loadCorruptFileThrows() throws Exception {
        Path file = tempDir.resolve("corrupt.json");
        Files.writeString(file, "not-json{{{", StandardCharsets.UTF_8);
        JsonSaveService svc = new JsonSaveService(file);
        assertTrue(svc.hasSave());
        assertThrows(IllegalStateException.class, svc::load);
    }

    @Test
    void loadUnsupportedVersionThrows() throws Exception {
        Path file = tempDir.resolve("future.json");
        Files.writeString(file,
                "{\"version\":3,\"schema\":\"P0-json\",\"player\":null}",
                StandardCharsets.UTF_8);
        JsonSaveService svc = new JsonSaveService(file);
        IllegalStateException ex = assertThrows(IllegalStateException.class, svc::load);
        assertTrue(ex.getMessage().contains("版本"));
    }
}
