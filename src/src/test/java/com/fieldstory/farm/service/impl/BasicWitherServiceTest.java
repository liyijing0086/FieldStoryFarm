package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.service.WitherResult;
import com.fieldstory.farm.service.WitherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BasicWitherService} 测试（A 模块 P1 设计文档 §9.2；规则文档 §二十八~§三十一；
 * 验收规范 §五十一~§五十四）。
 *
 * <p>概率与掷骰为纯函数（决策 D19），roll 值直接入参；不使用 CropFactory
 * （其内部含 UUID.randomUUID），作物状态手动构造；不使用系统时间，
 * 游戏日/世界时间直接传 long。
 */
class BasicWitherServiceTest {

    private WitherService witherService;

    @BeforeEach
    void setUp() {
        witherService = new BasicWitherService();
    }

    /**
     * 辅助：指定类型与阶段的作物，count=0，从未浇水
     * （lastManualWaterGameDay=-1 哨兵，D14）；天气记录 5 字段用 BasicCrop 默认哨兵。
     */
    private Crop crop(CropType type, GrowthStage stage) {
        Crop crop = new BasicCrop();
        crop.setCropType(type);
        crop.setGrowthStage(stage);
        crop.setManualWaterCount(0);
        crop.setLastManualWaterGameDay(-1);
        return crop;
    }

    // ===== 1. 概率表（mitigation=1.0，规则文档 §三十）=====

    /**
     * 普通生长期（玉米 SPROUT）：streak 0/1→0、2→0.30、3→0.70、4→1.0、5→1.0。
     */
    @Test
    void probabilityTableNormalGrowth() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);

        assertEquals(0.0, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
        crop.setDroughtStreak(1);
        assertEquals(0.0, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
        crop.setDroughtStreak(2);
        assertEquals(0.30, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
        crop.setDroughtStreak(3);
        assertEquals(0.70, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
        crop.setDroughtStreak(4);
        assertEquals(1.0, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
        crop.setDroughtStreak(5);
        assertEquals(1.0, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
    }

    /**
     * 小麦耐性档（SPROUT）：streak 2→0、3→0.30、4→0.70、5→1.0、6→1.0。
     */
    @Test
    void probabilityTableWheat() {
        Crop crop = crop(CropType.WHEAT, GrowthStage.SPROUT);

        crop.setDroughtStreak(2);
        assertEquals(0.0, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
        crop.setDroughtStreak(3);
        assertEquals(0.30, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
        crop.setDroughtStreak(4);
        assertEquals(0.70, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
        crop.setDroughtStreak(5);
        assertEquals(1.0, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
        crop.setDroughtStreak(6);
        assertEquals(1.0, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
    }

    /**
     * 成熟作物耐性档（玉米 MATURE，同小麦档）：streak 2→0、3→0.30、4→0.70、5→1.0
     * （规则文档 §16.4：成熟作物仍可枯萎）。
     */
    @Test
    void probabilityTableMature() {
        Crop crop = crop(CropType.CORN, GrowthStage.MATURE);

        crop.setDroughtStreak(2);
        assertEquals(0.0, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
        crop.setDroughtStreak(3);
        assertEquals(0.30, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
        crop.setDroughtStreak(4);
        assertEquals(0.70, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
        crop.setDroughtStreak(5);
        assertEquals(1.0, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
    }

    /**
     * SEED 阶段豁免：streak=5 仍返回 0（规则文档 §16.1；验收规范 §五十三）。
     */
    @Test
    void probabilitySeedExemptZero() {
        Crop crop = crop(CropType.CORN, GrowthStage.SEED);
        crop.setDroughtStreak(5);

        assertEquals(0.0, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
    }

    /**
     * WITHERED 跳过：streak=5 仍返回 0（验收规范 §五十三）。
     */
    @Test
    void probabilityWitheredZero() {
        Crop crop = crop(CropType.CORN, GrowthStage.WITHERED);
        crop.setDroughtStreak(5);

        assertEquals(0.0, witherService.calculateWitherProbability(crop, 1.0), 1e-9);
    }

    // ===== 2. 抗性倍率（规则文档 §三十一）=====

    /**
     * mitigation=0.7（石灯笼）：普通档 0.30→0.21、0.70→0.49、1.0→0.70
     * （规则文档 §三十一 示例逐值断言）。
     */
    @Test
    void mitigationRateScalesProbability() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);

        crop.setDroughtStreak(2);
        assertEquals(0.21, witherService.calculateWitherProbability(crop, 0.7), 1e-9);
        crop.setDroughtStreak(3);
        assertEquals(0.49, witherService.calculateWitherProbability(crop, 0.7), 1e-9);
        crop.setDroughtStreak(4);
        assertEquals(0.70, witherService.calculateWitherProbability(crop, 0.7), 1e-9);
    }

    // ===== 3. 掷骰边界（决策 D19：roll < probability 触发）=====

    /**
     * prob=0.30：roll=0.29 触发、roll=0.30 不触发（开区间边界）。
     */
    @Test
    void rollBoundaryAt30Percent() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);
        crop.setDroughtStreak(2);

        assertTrue(witherService.rollWither(crop, 1.0, 0.29));
        assertFalse(witherService.rollWither(crop, 1.0, 0.30));
    }

    /**
     * prob=0.70：roll=0.69 触发、roll=0.70 不触发（开区间边界）。
     */
    @Test
    void rollBoundaryAt70Percent() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);
        crop.setDroughtStreak(3);

        assertTrue(witherService.rollWither(crop, 1.0, 0.69));
        assertFalse(witherService.rollWither(crop, 1.0, 0.70));
    }

    /**
     * prob=0（streak=1）：任意 roll 不触发。
     */
    @Test
    void rollZeroProbabilityNeverTriggers() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);
        crop.setDroughtStreak(1);

        assertFalse(witherService.rollWither(crop, 1.0, 0.0));
    }

    /**
     * prob=1.0（streak=4 普通档，mitigation=1.0）：roll=0.9999 必触发。
     */
    @Test
    void rollFullProbabilityAlwaysTriggers() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);
        crop.setDroughtStreak(4);

        assertTrue(witherService.rollWither(crop, 1.0, 0.9999));
    }

    // ===== 4. 天气记录（规则文档 §二十一~§二十三、§二十九）=====

    /**
     * RAIN：rainCount+1、lastHydratedWorldTime=传入 worldTime、streak=0，
     * manualWaterCount 不变（验收规范 §五十一）。
     */
    @Test
    void recordRainIncrementsAndResetsStreak() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);
        crop.setDroughtStreak(3);
        crop.setManualWaterCount(2);

        witherService.recordDailyWeather(crop, WeatherType.RAIN, 5L, 128L);

        assertEquals(1, crop.getRainCount());
        assertEquals(128L, crop.getLastHydratedWorldTime());
        assertEquals(0, crop.getDroughtStreak());
        assertEquals(2, crop.getManualWaterCount());
    }

    /**
     * DROUGHT 无补水：droughtCount+1、streak+1，其余计数不变
     * （规则文档 §二十二、§二十九）。
     */
    @Test
    void recordDroughtWithoutWateringIncrementsStreak() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);
        crop.setDroughtStreak(2);
        crop.setManualWaterCount(1);

        witherService.recordDailyWeather(crop, WeatherType.DROUGHT, 3L, 80L);

        assertEquals(1, crop.getDroughtCount());
        assertEquals(3, crop.getDroughtStreak());
        assertEquals(0, crop.getRainCount());
        assertEquals(-1L, crop.getLastHydratedWorldTime());
        assertEquals(1, crop.getManualWaterCount());
    }

    /**
     * DROUGHT 当日主动浇水：droughtCount+1、streak=0
     * （规则文档 §二十二：有有效补水则 streak 归零）。
     */
    @Test
    void recordDroughtWithManualWaterSameDayResetsStreak() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);
        crop.setDroughtStreak(2);
        crop.setLastManualWaterGameDay(3L);

        witherService.recordDailyWeather(crop, WeatherType.DROUGHT, 3L, 80L);

        assertEquals(1, crop.getDroughtCount());
        assertEquals(0, crop.getDroughtStreak());
    }

    /**
     * GREEN_RAIN：greenRainCount+1、streak=0，lastHydratedWorldTime 不变
     * （规则文档 §二十三；绿雨不属于补水）。
     */
    @Test
    void recordGreenRainIncrementsAndResetsStreak() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);
        crop.setDroughtStreak(4);

        witherService.recordDailyWeather(crop, WeatherType.GREEN_RAIN, 2L, 55L);

        assertEquals(1, crop.getGreenRainCount());
        assertEquals(0, crop.getDroughtStreak());
        assertEquals(-1L, crop.getLastHydratedWorldTime());
    }

    /**
     * SUNNY：streak=0，三个 count 与 lastHydratedWorldTime 均不变
     * （验收规范 §五十二：任何非干旱日终止 streak）。
     */
    @Test
    void recordSunnyResetsStreakOnly() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);
        crop.setDroughtStreak(4);
        crop.setDroughtCount(1);
        crop.setRainCount(1);
        crop.setGreenRainCount(1);

        witherService.recordDailyWeather(crop, WeatherType.SUNNY, 2L, 55L);

        assertEquals(0, crop.getDroughtStreak());
        assertEquals(1, crop.getDroughtCount());
        assertEquals(1, crop.getRainCount());
        assertEquals(1, crop.getGreenRainCount());
        assertEquals(-1L, crop.getLastHydratedWorldTime());
    }

    /**
     * 有效补水判定：RAIN→true；当日主动浇水→true；其余→false
     * （A 模块 P1 设计文档 §3.2 写死条款）。
     */
    @Test
    void isEffectivelyHydratedByRainOrManualWater() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);

        assertTrue(witherService.isEffectivelyHydrated(crop, WeatherType.RAIN, 5L));
        assertFalse(witherService.isEffectivelyHydrated(crop, WeatherType.SUNNY, 5L));

        crop.setLastManualWaterGameDay(5L);
        assertTrue(witherService.isEffectivelyHydrated(crop, WeatherType.DROUGHT, 5L));
        assertFalse(witherService.isEffectivelyHydrated(crop, WeatherType.DROUGHT, 6L));
    }

    // ===== 5. judgeWither 全结果码（设计文档 §5.5 判定流程）=====

    /**
     * crop == null → NOT_PLANTED（防御分支）。
     */
    @Test
    void judgeNullCropNotPlanted() {
        assertEquals(WitherResult.NOT_PLANTED,
                witherService.judgeWither(null, WeatherType.DROUGHT, 0L, 1.0, 0.0));
    }

    /**
     * stage == WITHERED → ALREADY_WITHERED（验收规范 §五十三 跳过）。
     */
    @Test
    void judgeWitheredAlreadyWithered() {
        Crop crop = crop(CropType.CORN, GrowthStage.WITHERED);
        crop.setDroughtStreak(5);

        assertEquals(WitherResult.ALREADY_WITHERED,
                witherService.judgeWither(crop, WeatherType.DROUGHT, 0L, 1.0, 0.0));
    }

    /**
     * stage == SEED → SEED_EXEMPT（规则文档 §16.1）：即使 DROUGHT、streak=5、
     * roll=0 也不参与判定。
     */
    @Test
    void judgeSeedExempt() {
        Crop crop = crop(CropType.CORN, GrowthStage.SEED);
        crop.setDroughtStreak(5);

        assertEquals(WitherResult.SEED_EXEMPT,
                witherService.judgeWither(crop, WeatherType.DROUGHT, 0L, 1.0, 0.0));
    }

    /**
     * 当日非 DROUGHT → NO_DROUGHT_RISK（四条件②"当日存在干旱风险"不满足）。
     */
    @Test
    void judgeNonDroughtNoRisk() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);
        crop.setDroughtStreak(5);

        assertEquals(WitherResult.NO_DROUGHT_RISK,
                witherService.judgeWither(crop, WeatherType.SUNNY, 0L, 1.0, 0.0));
    }

    /**
     * DROUGHT 但当日主动浇水 → NO_DROUGHT_RISK（四条件③"没有有效补水"不满足）。
     */
    @Test
    void judgeHydratedNoRisk() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);
        crop.setDroughtStreak(5);
        crop.setLastManualWaterGameDay(2L);

        assertEquals(WitherResult.NO_DROUGHT_RISK,
                witherService.judgeWither(crop, WeatherType.DROUGHT, 2L, 1.0, 0.0));
    }

    /**
     * streak=1（概率 0）→ NO_DROUGHT_RISK（四条件④"streak 达风险区间"不满足）。
     */
    @Test
    void judgeStreakBelowRiskNoRisk() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);
        crop.setDroughtStreak(1);

        assertEquals(WitherResult.NO_DROUGHT_RISK,
                witherService.judgeWither(crop, WeatherType.DROUGHT, 0L, 1.0, 0.0));
    }

    /**
     * 普通 streak=2、roll=0.29 < 0.30 → WITHERED 且 stage 变 WITHERED
     * （验收规范 §五十四）。
     */
    @Test
    void judgeRollTriggersWither() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);
        crop.setDroughtStreak(2);

        assertEquals(WitherResult.WITHERED,
                witherService.judgeWither(crop, WeatherType.DROUGHT, 0L, 1.0, 0.29));
        assertEquals(GrowthStage.WITHERED, crop.getGrowthStage());
    }

    /**
     * 普通 streak=2、roll=0.30（不 < 概率）→ SURVIVED 且 stage 不变
     * （开区间边界）。
     */
    @Test
    void judgeRollSurvives() {
        Crop crop = crop(CropType.CORN, GrowthStage.SPROUT);
        crop.setDroughtStreak(2);

        assertEquals(WitherResult.SURVIVED,
                witherService.judgeWither(crop, WeatherType.DROUGHT, 0L, 1.0, 0.30));
        assertEquals(GrowthStage.SPROUT, crop.getGrowthStage());
    }
}
