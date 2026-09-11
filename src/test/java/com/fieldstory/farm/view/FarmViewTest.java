package com.fieldstory.farm.view;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.FarmPlot;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicSoil;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link FarmView} 纯函数测试（UI规范 §10、§14；验收规范 §三十七）。
 *
 * <p>只测 {@link FarmView#tileColorFor} / {@link FarmView#cropBlockSizeFor} /
 * {@link FarmView#tooltipTextFor} 三个静态纯函数；
 * 不实例化 FarmView（JavaFX 节点创建需 GUI 线程，测试中禁止创建控件）。
 *
 * <p>颜色断言用 {@link Color#rgb} 数值构造，与实现同一口径，不依赖 GUI 线程。
 */
class FarmViewTest {

    /** 草地 #7FAE55（UI规范 §14） */
    private static final Color GRASS = Color.rgb(0x7F, 0xAE, 0x55);

    /** 土地 #A97850（UI规范 §14） */
    private static final Color SOIL = Color.rgb(0xA9, 0x78, 0x50);

    /** 木色 #8B5E3C（UI规范 §14） */
    private static final Color WOOD = Color.rgb(0x8B, 0x5E, 0x3C);

    /** 高亮 #E8C45C（UI规范 §14） */
    private static final Color HIGHLIGHT = Color.rgb(0xE8, 0xC4, 0x5C);

    /** 辅助：指定状态的空土地（全局坐标 2,2，中心种植区）。 */
    private static Soil soil(SoilState state) {
        Soil soil = new BasicSoil(2, 2);
        soil.setState(state);
        return soil;
    }

    /**
     * 辅助：PLANTED 土地 + 指定阶段作物（小麦、成长 50%）。
     * lastManualWaterGameDay=-1 哨兵表示从未浇水（决策 D14）。
     */
    private static Soil plantedSoil(GrowthStage stage, long lastManualWaterGameDay) {
        Soil soil = soil(SoilState.PLANTED);
        Crop crop = new BasicCrop();
        crop.setCropType(CropType.WHEAT);
        crop.setGrowthStage(stage);
        crop.setGrowthProgress(50);
        crop.setLastManualWaterGameDay(lastManualWaterGameDay);
        soil.setCrop(crop);
        return soil;
    }

    // ==================== tileColorFor：五态 + 装饰区（验收规范 §三十七） ====================

    @Test
    void tileColorForDecorationAreaIsGrass() {
        assertEquals(GRASS, FarmView.tileColorFor(FarmPlot.DECORATION_AREA, null));
    }

    @Test
    void tileColorForEmptyIsWood() {
        assertEquals(WOOD, FarmView.tileColorFor(FarmPlot.FARM_PLOT, soil(SoilState.EMPTY)));
    }

    @Test
    void tileColorForTilledIsSoil() {
        assertEquals(SOIL, FarmView.tileColorFor(FarmPlot.FARM_PLOT, soil(SoilState.TILLED)));
    }

    @Test
    void tileColorForPlantedIsSoil() {
        assertEquals(SOIL, FarmView.tileColorFor(FarmPlot.FARM_PLOT, plantedSoil(GrowthStage.SEED, -1)));
    }

    @Test
    void tileColorForMatureIsHighlight() {
        assertEquals(HIGHLIGHT, FarmView.tileColorFor(FarmPlot.FARM_PLOT, plantedSoil(GrowthStage.MATURE, -1)));
    }

    @Test
    void tileColorForLockedIsWoodPlaceholder() {
        // P0 不产生 LOCKED（验收规范 §十四）；未解锁格以木色占位（同 EMPTY）
        assertEquals(WOOD, FarmView.tileColorFor(FarmPlot.FARM_PLOT, soil(SoilState.LOCKED)));
    }

    // ==================== cropBlockSizeFor：三阶段（任务指定：SEED 8 / SPROUT 16 / GROWING 24） ====================

    @Test
    void cropBlockSizeForSeedIs8() {
        assertEquals(8, FarmView.cropBlockSizeFor(GrowthStage.SEED));
    }

    @Test
    void cropBlockSizeForSproutIs16() {
        assertEquals(16, FarmView.cropBlockSizeFor(GrowthStage.SPROUT));
    }

    @Test
    void cropBlockSizeForGrowingIs24() {
        assertEquals(24, FarmView.cropBlockSizeFor(GrowthStage.GROWING));
    }

    // ==================== tooltipTextFor：五种文案（UI规范 §10） ====================

    @Test
    void tooltipTextForDecorationArea() {
        assertEquals("装饰区（P0 占位）", FarmView.tooltipTextFor(null, 0L));
    }

    @Test
    void tooltipTextForEmpty() {
        assertEquals("未开垦", FarmView.tooltipTextFor(soil(SoilState.EMPTY), 0L));
    }

    @Test
    void tooltipTextForTilled() {
        assertEquals("已开垦，可播种", FarmView.tooltipTextFor(soil(SoilState.TILLED), 0L));
    }

    @Test
    void tooltipTextForPlantedNotWateredToday() {
        assertEquals("小麦 成长50% 今日未浇",
                FarmView.tooltipTextFor(plantedSoil(GrowthStage.SEED, -1), 0L));
    }

    @Test
    void tooltipTextForPlantedWateredToday() {
        assertEquals("小麦 成长50% 今日已浇",
                FarmView.tooltipTextFor(plantedSoil(GrowthStage.SEED, 0L), 0L));
    }

    @Test
    void tooltipTextForPlantedWateredTodayWhenDayEqualsLastManualWaterGameDay() {
        // 当前游戏日 == lastManualWaterGameDay → 文案含"今日已浇"（决策 D14 long 用 ==）
        assertEquals("小麦 成长50% 今日已浇",
                FarmView.tooltipTextFor(plantedSoil(GrowthStage.GROWING, 3L), 3L));
    }

    @Test
    void tooltipTextForMature() {
        assertEquals("已成熟，可收获",
                FarmView.tooltipTextFor(plantedSoil(GrowthStage.MATURE, -1), 0L));
    }

    // ==================== 坏数据兜底：null 枚举不抛 NPE（P1 设计文档 §3：crop_type 允许 NULL） ====================

    /**
     * growth_stage 无法识别 → 适配层降级 null（FarmStateAdapter.parseEnum）；
     * cropBlockSizeFor(null) 不得抛 NPE，按不可绘制处理返回 0。
     */
    @Test
    void cropBlockSizeForNullStageReturnsZero() {
        assertEquals(0, FarmView.cropBlockSizeFor(null));
    }

    /**
     * crop_type 无法识别 → 适配层降级 null；tooltipTextFor 不得抛 NPE，
     * 以占位名显示（回归「开始游戏」读档时 FarmView.buildTiles 崩溃）。
     */
    @Test
    void tooltipTextForUnknownCropTypeShowsPlaceholder() {
        Soil soil = soil(SoilState.PLANTED);
        Crop crop = new BasicCrop();
        crop.setCropType(null);
        crop.setGrowthStage(GrowthStage.SPROUT);
        crop.setGrowthProgress(50);
        crop.setLastManualWaterGameDay(-1);
        soil.setCrop(crop);

        assertEquals("未知作物 成长50% 今日未浇", FarmView.tooltipTextFor(soil, 0L));
    }

    /** growth_stage 为 null 时 tooltipTextFor 也不得抛 NPE。 */
    @Test
    void tooltipTextForNullStageDoesNotThrow() {
        Soil soil = soil(SoilState.PLANTED);
        Crop crop = new BasicCrop();
        crop.setCropType(CropType.WHEAT);
        crop.setGrowthStage(null);
        crop.setGrowthProgress(50);
        crop.setLastManualWaterGameDay(-1);
        soil.setCrop(crop);

        assertEquals("小麦 成长50% 今日未浇", FarmView.tooltipTextFor(soil, 0L));
    }
}
