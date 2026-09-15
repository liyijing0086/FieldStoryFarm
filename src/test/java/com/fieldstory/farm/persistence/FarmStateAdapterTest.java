package com.fieldstory.farm.persistence;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.PlotState;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicFarm;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 适配层 {@link FarmStateAdapter} 测试（E 模块 P1；验收标准 4「退出保存 / 进入读档」的地块闭环）。
 *
 * <p>覆盖：整块农场快照生成、地块与作物的“采集 → 还原”无损往返、
 * 还原的幂等与整体重置、以及坏数据（未知枚举/非法 UUID/越界坐标）不阻断读档。
 */
class FarmStateAdapterTest {

    private static GameState emptyState() {
        return new GameState(new Player("农夫", 500), 1L);
    }

    private static Crop crop(CropType type, GrowthStage stage, double progress) {
        BasicCrop crop = new BasicCrop();
        crop.setCropUuid(UUID.randomUUID());
        crop.setCropType(type);
        crop.setGrowthStage(stage);
        crop.setGrowthProgress(progress);
        crop.setPlantWorldTime(3L * 24 + 8);
        crop.setManualWaterCount(2);
        crop.setLastManualWaterGameDay(3);
        return crop;
    }

    @Test
    void captureWritesEveryFarmPlotIncludingEmptyOnes() {
        Farm farm = new BasicFarm();
        GameState state = emptyState();

        FarmStateAdapter.capture(state, farm);

        assertEquals(farm.getSoils().size(), state.getPlots().size(), "应写入每块 FARM_PLOT");
        assertEquals(64, state.getPlots().size(), "中心 8×8 共 64 块");
        for (PlotState plot : state.getPlots()) {
            assertEquals("EMPTY", plot.getState());
            assertEquals(plot.getRow() + "," + plot.getColumn(), plot.getPlotId());
            assertNull(plot.getCropUuid(), "空格子不应带作物");
        }
    }

    @Test
    void captureThenRestoreRoundTripsSoilStateAndCrop() {
        Farm farm = new BasicFarm();
        Soil tilled = farm.getSoil(2, 2);
        tilled.setState(SoilState.TILLED);

        Soil planted = farm.getSoil(9, 9);
        planted.setState(SoilState.PLANTED);
        Crop original = crop(CropType.CORN, GrowthStage.GROWING, 63.5);
        planted.setCrop(original);

        GameState state = emptyState();
        FarmStateAdapter.capture(state, farm);

        Farm reloaded = new BasicFarm();
        FarmStateAdapter.restore(state, reloaded);

        assertEquals(SoilState.TILLED, reloaded.getSoil(2, 2).getState());
        assertNull(reloaded.getSoil(2, 2).getCrop());

        Soil restored = reloaded.getSoil(9, 9);
        assertEquals(SoilState.PLANTED, restored.getState());
        Crop restoredCrop = restored.getCrop();
        assertNotNull(restoredCrop);
        assertEquals(original.getCropUuid(), restoredCrop.getCropUuid());
        assertEquals(CropType.CORN, restoredCrop.getCropType());
        assertEquals(GrowthStage.GROWING, restoredCrop.getGrowthStage());
        assertEquals(63.5, restoredCrop.getGrowthProgress());
        assertEquals(original.getPlantWorldTime(), restoredCrop.getPlantWorldTime());
        assertEquals(2, restoredCrop.getManualWaterCount());
        assertEquals(3L, restoredCrop.getLastManualWaterGameDay());
    }

    @Test
    void captureClearsStaleSnapshotBeforeRebuilding() {
        Farm farm = new BasicFarm();
        GameState state = emptyState();
        FarmStateAdapter.capture(state, farm);
        int firstSize = state.getPlots().size();

        FarmStateAdapter.capture(state, farm);

        assertEquals(firstSize, state.getPlots().size(), "重复采集不应累积幽灵地块");
    }

    @Test
    void restoreResetsFarmSoPreviousSessionDoesNotLeak() {
        Farm farm = new BasicFarm();
        Soil dirty = farm.getSoil(4, 4);
        dirty.setState(SoilState.PLANTED);
        dirty.setCrop(crop(CropType.WHEAT, GrowthStage.MATURE, 100.0));

        // 空快照（例如新档）应把农场整体重置
        FarmStateAdapter.restore(emptyState(), farm);

        assertEquals(SoilState.EMPTY, farm.getSoil(4, 4).getState());
        assertNull(farm.getSoil(4, 4).getCrop());
    }

    @Test
    void restoreIsTolerantOfUnknownEnumValuesAndMalformedUuid() {
        GameState state = emptyState();
        PlotState plot = new PlotState();
        plot.setRow(3);
        plot.setColumn(3);
        plot.setState("NOT_A_STATE");
        plot.setCropUuid("not-a-uuid");
        plot.setCropType("NOT_A_CROP");
        plot.setGrowthStage("NOT_A_STAGE");
        plot.setGrowthProgress(50.0);
        plot.setPlantWorldTime("abc");
        plot.setLastManualWaterGameDay("");
        state.getPlots().add(plot);

        Farm farm = new BasicFarm();
        FarmStateAdapter.restore(state, farm);

        Soil soil = farm.getSoil(3, 3);
        assertEquals(SoilState.EMPTY, soil.getState(), "未知状态应保持缺省 EMPTY");
        Crop crop = soil.getCrop();
        assertNotNull(crop, "作物存在性由 cropUuid 非空判定");
        assertNotNull(crop.getCropUuid(), "非法作物 id 应补生成而非丢弃作物");
        assertNull(crop.getCropType());
        assertNull(crop.getGrowthStage());
        assertEquals(0L, crop.getPlantWorldTime(), "非法时间回退到缺省值");
        assertEquals(-1L, crop.getLastManualWaterGameDay(), "空时间回退到 -1 哨兵");
    }

    @Test
    void malformedCropUuidSurvivesCaptureRestoreCapture() {
        // 模拟"存档里作物 id 非法"：还原后再次采集，作物仍须存在（不被静默丢弃）
        GameState state = emptyState();
        PlotState plot = new PlotState();
        plot.setRow(6);
        plot.setColumn(6);
        plot.setState("PLANTED");
        plot.setCropUuid("bad-id");
        plot.setCropType("WHEAT");
        plot.setGrowthStage("GROWING");
        state.getPlots().add(plot);

        Farm farm = new BasicFarm();
        FarmStateAdapter.restore(state, farm);

        GameState recaptured = emptyState();
        FarmStateAdapter.capture(recaptured, farm);

        PlotState roundTripped = recaptured.getPlots().stream()
                .filter(p -> p.getRow() == 6 && p.getColumn() == 6)
                .findFirst()
                .orElseThrow();
        assertTrue(roundTripped.hasCrop(), "作物不应因 id 非法而在再次保存时丢失");
        assertEquals("WHEAT", roundTripped.getCropType());
    }

    @Test
    void restoreSkipsDecorationAreaAndOutOfRangeCoordinates() {
        GameState state = emptyState();
        PlotState decoration = new PlotState();
        decoration.setRow(0);
        decoration.setColumn(0);
        decoration.setState("PLANTED");
        state.getPlots().add(decoration);

        PlotState outOfRange = new PlotState();
        outOfRange.setRow(99);
        outOfRange.setColumn(-1);
        outOfRange.setState("PLANTED");
        state.getPlots().add(outOfRange);

        Farm farm = new BasicFarm();
        FarmStateAdapter.restore(state, farm);

        // 装饰区不持有 Soil；越界坐标直接跳过，两者都不应污染种植区
        for (Soil soil : farm.getSoils()) {
            assertEquals(SoilState.EMPTY, soil.getState());
        }
    }
}
