package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.service.impl.BasicLegendaryService;
import com.fieldstory.farm.util.RandomProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LegendaryService} 传说条件/概率/掷骰测试
 * （规则文档 §四十二~四十六；验收规范 §九十六~一百零一）。
 */
class LegendaryServiceTest {

    private LegendaryService legendaryService;

    @BeforeEach
    void setUp() {
        legendaryService = new BasicLegendaryService();
    }

    /** 构造指定类型作物。 */
    private Crop crop(CropType cropType) {
        Crop crop = new BasicCrop();
        crop.setCropUuid(UUID.randomUUID());
        crop.setCropType(cropType);
        return crop;
    }

    /** 空记忆档案。 */
    private CropMemory memory(CropType cropType) {
        return new CropMemory(UUID.randomUUID(), cropType, 48);
    }

    /**
     * 基础概率（规则文档 §四十二~四十四）：小麦 30 / 玉米 40 / 胡萝卜 35。
     */
    @Test
    void baseChancePerCropType() {
        assertEquals(30, legendaryService.baseChance(CropType.WHEAT));
        assertEquals(40, legendaryService.baseChance(CropType.CORN));
        assertEquals(35, legendaryService.baseChance(CropType.CARROT));
    }

    /**
     * 金色麦穗（规则文档 §四十二）：WHEAT，干旱≥1 + 干旱当天浇水救援 +
     * 浇水≥2 + Score≥110；任一条件缺失即不合格并给出未满足描述。
     */
    @Test
    void goldenWheatEligibilityAllConditionsRequired() {
        Crop crop = crop(CropType.WHEAT);
        CropMemory memory = memory(CropType.WHEAT);

        // 全缺 → 不合格，4 条未满足
        LegendaryCheck none = legendaryService.checkEligibility(crop, memory, 50);
        assertFalse(none.isEligible());
        assertEquals("金色麦穗", none.getLegendaryName());
        assertEquals(4, none.getUnmetConditions().size());

        // 补齐经历：干旱1次 + 干旱当天浇水(共2次) → 仍差 Score
        memory.setDroughtCount(1);
        memory.setWaterRescueOnDroughtDay(true);
        memory.setManualWaterCount(2);
        LegendaryCheck lowScore = legendaryService.checkEligibility(crop, memory, 109);
        assertFalse(lowScore.isEligible());
        assertEquals(1, lowScore.getUnmetConditions().size());

        // Score 达标 → 合格
        LegendaryCheck eligible = legendaryService.checkEligibility(crop, memory, 110);
        assertTrue(eligible.isEligible());
        assertTrue(eligible.getUnmetConditions().isEmpty());
    }

    /**
     * 彩虹玉米（规则文档 §四十三）：CORN，绿雨≥1 + 施肥≥1 + Score≥115。
     */
    @Test
    void rainbowCornEligibility() {
        Crop crop = crop(CropType.CORN);
        CropMemory memory = memory(CropType.CORN);
        memory.setGreenRainCount(1);
        memory.setFertilizerCount(1);

        assertFalse(legendaryService.checkEligibility(crop, memory, 114).isEligible());
        LegendaryCheck eligible = legendaryService.checkEligibility(crop, memory, 115);
        assertTrue(eligible.isEligible());
        assertEquals("彩虹玉米", eligible.getLegendaryName());
    }

    /**
     * 巨龙胡萝卜（规则文档 §四十四）：CARROT，绿雨≥1 + 浇水≥2 + Score≥120。
     */
    @Test
    void dragonCarrotEligibility() {
        Crop crop = crop(CropType.CARROT);
        CropMemory memory = memory(CropType.CARROT);
        memory.setGreenRainCount(1);
        memory.setManualWaterCount(2);

        assertFalse(legendaryService.checkEligibility(crop, memory, 119).isEligible());
        assertTrue(legendaryService.checkEligibility(crop, memory, 120).isEligible());
    }

    /**
     * 最终概率（规则文档 §四十五；验收规范 §一百~一百零一）：
     * 绿雨 +5%/次最大 +15%，流星夜 +10%，总上限 80%。
     */
    @Test
    void legendaryChanceCompositionAndCap() {
        Crop crop = crop(CropType.WHEAT);
        CropMemory memory = memory(CropType.WHEAT);
        memory.setDroughtCount(1);
        memory.setWaterRescueOnDroughtDay(true);
        memory.setManualWaterCount(2);

        // 无绿雨无流星：30 + 0 = 30
        assertEquals(30, legendaryService.legendaryChance(crop, memory, 110));
        // 绿雨2次：+10 → 40
        memory.setGreenRainCount(2);
        assertEquals(40, legendaryService.legendaryChance(crop, memory, 110));
        // 绿雨5次：+15 封顶（不是 +25）→ 45
        memory.setGreenRainCount(5);
        assertEquals(45, legendaryService.legendaryChance(crop, memory, 110));
        // 流星夜：+10 → 55
        memory.getEvents().add(EventType.METEOR_SHOWER);
        assertEquals(55, legendaryService.legendaryChance(crop, memory, 110));
        // 传奇之光套装 +10 → 65；再加 20 → 80 封顶
        legendaryService.setLegendarySetBonus(10);
        assertEquals(65, legendaryService.legendaryChance(crop, memory, 110));
        legendaryService.setLegendarySetBonus(100);
        assertEquals(80, legendaryService.legendaryChance(crop, memory, 110));
    }

    /**
     * 掷骰：条件不满足直接 false 且不掷骰（验收规范 §九十六）；
     * 条件满足时按概率判定——固定 seed 连续多轮，必须能同时
     * 复现成功与失败两种路径（统一随机源，规则文档 §三十九/§四十六）。
     */
    @Test
    void rollBreakthroughRespectsEligibilityAndChance() {
        Crop crop = crop(CropType.CORN);
        CropMemory memory = memory(CropType.CORN);
        memory.setGreenRainCount(1);
        memory.setFertilizerCount(1);

        // 条件满足但分数不足 → 不掷骰，false
        assertFalse(legendaryService.rollBreakthrough(crop, memory, 100));

        // 条件满足、45%（基础 40% + 绿雨 5%）：固定 seed 连续多轮
        // 随机序列均匀覆盖 [0,1)，必须能同时复现成功与失败
        RandomProvider.setSeed(20260911L);
        boolean sawSuccess = false;
        boolean sawFailure = false;
        for (int round = 0; round < 200 && !(sawSuccess && sawFailure); round++) {
            if (legendaryService.rollBreakthrough(crop, memory, 115)) {
                sawSuccess = true;
            } else {
                sawFailure = true;
            }
        }
        assertTrue(sawSuccess, "45% 概率下 200 轮应至少出现一次成功");
        assertTrue(sawFailure, "45% 概率下 200 轮应至少出现一次失败");
    }

    /** 传说名映射（规则文档 §四十二~四十四）。 */
    @Test
    void legendaryNameMapping() {
        assertEquals("金色麦穗", LegendaryService.legendaryName(CropType.WHEAT));
        assertEquals("彩虹玉米", LegendaryService.legendaryName(CropType.CORN));
        assertEquals("巨龙胡萝卜", LegendaryService.legendaryName(CropType.CARROT));
    }

    /** 套装加成不得为负。 */
    @Test
    void setBonusRejectsNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> legendaryService.setLegendarySetBonus(-1));
    }
}
