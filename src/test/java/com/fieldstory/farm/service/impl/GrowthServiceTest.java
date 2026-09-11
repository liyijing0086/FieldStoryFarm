package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.service.GrowthService;
import com.fieldstory.farm.service.WateringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link BasicGrowthService} 测试（验收规范 §二十二、§二十四、§二十五、§三十）。
 *
 * <p>P0 成长公式：{@code BaseDailyProgress × ElapsedGameDays × (1 + 浇水加成)}；
 * 天气/装饰/事件 Rate 固定 1.0。
 *
 * <p>不使用 CropFactory（其内部含 UUID.randomUUID），作物状态手动构造；
 * 不使用系统时间，elapsedGameDays 直接传游戏天数。
 */
class GrowthServiceTest {

    private GrowthService growthService;

    @BeforeEach
    void setUp() {
        growthService = new BasicGrowthService(new BasicWateringService());
    }

    /**
     * 辅助：小麦（基础 50/日，CropType 内置 100.0/2），进度 0，
     * 从未浇水（lastManualWaterGameDay=-1 哨兵，D14）。
     */
    private Crop wheat() {
        Crop crop = new BasicCrop();
        crop.setCropType(CropType.WHEAT);
        crop.setGrowthStage(GrowthStage.SEED);
        crop.setGrowthProgress(0.0);
        crop.setManualWaterCount(0);
        crop.setLastManualWaterGameDay(-1);
        return crop;
    }

    /**
     * 非整日成长：小麦 12 游戏小时 = 0.5 天 → 50 × 0.5 × 1.0 = 25
     * （验收规范 §二十五：经过游戏小时 ÷ 24 折算）。
     */
    @Test
    void calculateGrowthDeltaWithHalfDay() {
        Crop crop = wheat();
        assertEquals(25.0, growthService.calculateGrowthDelta(crop, 0.5), 1e-9);
    }

    /**
     * 浇水加成：manualWaterCount=1（+5%）时小麦 1 天 → 50 × 1 × 1.05 = 52.5
     * （验收规范 §二十四）。
     */
    @Test
    void calculateGrowthDeltaWithWaterBonus() {
        Crop crop = wheat();
        crop.setManualWaterCount(1);
        assertEquals(52.5, growthService.calculateGrowthDelta(crop, 1.0), 1e-9);
    }

    /**
     * 封顶：progress=95 再成长 1 天（+50）→ 钳制 100 且阶段 MATURE
     * （验收规范 §三十：禁止 105%、123% 等超值继续增长）。
     */
    @Test
    void applyGrowthClampsAt100AndMatures() {
        Crop crop = wheat();
        crop.setGrowthProgress(95.0);
        crop.setGrowthStage(GrowthStage.GROWING);

        growthService.applyGrowth(crop, 1.0);

        assertEquals(100.0, crop.getGrowthProgress(), 1e-9);
        assertEquals(GrowthStage.MATURE, crop.getGrowthStage());
    }

    /**
     * 阶段边界（验收规范 §二十二）：19.99→SEED、20→SPROUT、50→GROWING、100→MATURE。
     * 先 setGrowthProgress 再 applyGrowth(crop, 0) 触发阶段更新（delta=0，不改进度）。
     */
    @Test
    void applyGrowthUpdatesStageByThresholds() {
        assertStageAfterApply(19.99, GrowthStage.SEED);
        assertStageAfterApply(20.0, GrowthStage.SPROUT);
        assertStageAfterApply(50.0, GrowthStage.GROWING);
        assertStageAfterApply(100.0, GrowthStage.MATURE);
    }

    private void assertStageAfterApply(double progress, GrowthStage expected) {
        Crop crop = wheat();
        crop.setGrowthProgress(progress);
        growthService.applyGrowth(crop, 0.0);
        assertEquals(expected, crop.getGrowthStage());
    }

    // ==================== 坏数据兜底：crop_type 为 null 不抛 NPE（P1 文档 §3：crop_type 允许 NULL） ====================

    /**
     * crop_type 无法识别 → 适配层降级 null；calculateGrowthDelta 不得抛 NPE，
     * 无法取每日基础进度时降级为 0（回归跨天/浇水时 BasicGrowthService 崩溃）。
     */
    @Test
    void calculateGrowthDeltaWithNullCropTypeReturnsZero() {
        Crop crop = wheat();
        crop.setCropType(null);
        assertEquals(0.0, growthService.calculateGrowthDelta(crop, 1.0), 1e-9);
    }

    /** crop_type 为 null 时 applyGrowth 不抛 NPE，且进度不变（delta=0）。 */
    @Test
    void applyGrowthWithNullCropTypeKeepsProgress() {
        Crop crop = wheat();
        crop.setCropType(null);
        crop.setGrowthProgress(30.0);

        growthService.applyGrowth(crop, 1.0);

        assertEquals(30.0, crop.getGrowthProgress(), 1e-9);
    }
}
