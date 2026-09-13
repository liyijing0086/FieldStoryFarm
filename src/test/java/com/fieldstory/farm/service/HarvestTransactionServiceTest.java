package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.model.impl.BasicSoil;
import com.fieldstory.farm.model.item.EventPriceRateProvider;
import com.fieldstory.farm.model.item.Inventory;
import com.fieldstory.farm.model.item.ItemType;
import com.fieldstory.farm.service.impl.BasicHarvestTransactionService;
import com.fieldstory.farm.service.impl.BasicLandService;
import com.fieldstory.farm.service.impl.BasicLegendaryService;
import com.fieldstory.farm.service.impl.BasicMemoryService;
import com.fieldstory.farm.service.impl.BasicQualityService;
import com.fieldstory.farm.testutil.TestEconomyService;
import com.fieldstory.farm.util.RandomProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HarvestTransactionService} 完整收获事务测试
 * （验收规范 §一百零三 流程；规则文档 §六十五~六十六 售价与肥料奖励）。
 *
 * <p>土地回退经 A 的 {@link BasicLandService}（决策 D09）、金币经
 * {@link TestEconomyService}（B 契约）、时间经 {@link BasicGameClock}
 * （D 正式接口）。传说判定注入固定桩以隔离随机。
 */
class HarvestTransactionServiceTest {

    /** 第 5 天 12:00（总分钟 4×1440+720 = 6480） */
    private static final int WORLD_TOTAL_MINUTES = 6480;

    private TestEconomyService economyService;
    private MemoryService memoryService;
    private GameClock gameClock;
    private QualityService qualityService;

    @BeforeEach
    void setUp() {
        economyService = new TestEconomyService();
        economyService.setGold(100);
        memoryService = new BasicMemoryService();
        gameClock = new BasicGameClock(WORLD_TOTAL_MINUTES);
        qualityService = new BasicQualityService();
        RandomProvider.setSeed(42L);
    }

    /** 固定传说桩：rollBreakthrough 恒为指定值。 */
    private LegendaryService legendaryStub(boolean roll) {
        return new LegendaryService() {
            @Override
            public LegendaryCheck checkEligibility(Crop crop, CropMemory memory, int qualityScore) {
                return new LegendaryCheck(true,
                        LegendaryService.legendaryName(crop.getCropType()), List.of());
            }

            @Override
            public int baseChance(CropType cropType) {
                return 30;
            }

            @Override
            public int legendaryChance(Crop crop, CropMemory memory, int qualityScore) {
                return 80;
            }

            @Override
            public boolean rollBreakthrough(Crop crop, CropMemory memory, int qualityScore) {
                return roll;
            }

            @Override
            public void setLegendarySetBonus(int bonusPercent) {
                // 桩：无套装
            }
        };
    }

    /** 默认事务服务（事件倍率 1.0）。 */
    private HarvestTransactionService service(LegendaryService legendary) {
        return new BasicHarvestTransactionService(economyService,
                new BasicLandService(economyService), qualityService,
                legendary, memoryService, gameClock);
    }

    /** 指定事件倍率的事务服务。 */
    private HarvestTransactionService service(LegendaryService legendary,
                                              EventPriceRateProvider provider) {
        return new BasicHarvestTransactionService(economyService,
                new BasicLandService(economyService), qualityService,
                legendary, memoryService, gameClock, provider);
    }

    /** 成熟作物土地。 */
    private Soil plantedMatureSoil(CropType cropType) {
        Soil soil = new BasicSoil(2, 2);
        soil.setState(SoilState.PLANTED);
        Crop crop = new BasicCrop();
        crop.setCropUuid(UUID.randomUUID());
        crop.setCropType(cropType);
        crop.setGrowthStage(GrowthStage.MATURE);
        crop.setPlantWorldTime(80);
        soil.setCrop(crop);
        return soil;
    }

    /**
     * 无经历小麦收获（传说桩失败）：score ∈ [50,59] → COMMON，
     * 售价 = 50 × 1.0 = 50（规则文档 §六十五），肥料奖励 0
     * （规则文档 §六十六），土地回退 TILLED、作物清除（验收规范 §一百零三）。
     */
    @Test
    void harvestWithoutExperienceGivesCommonPrice() {
        Soil soil = plantedMatureSoil(CropType.WHEAT);

        HarvestOutcome outcome = service(legendaryStub(false)).harvest(soil, new Inventory());

        assertTrue(outcome.isSuccess());
        assertEquals(Quality.COMMON, outcome.getQuality());
        assertEquals(50, outcome.getSellPrice());
        assertEquals(0, outcome.getFertilizerReward());
        assertFalse(outcome.isLegendary());
        assertEquals(100 + 50, economyService.getGold());
        assertEquals(SoilState.TILLED, soil.getState());
        assertNull(soil.getCrop());
        assertTrue(outcome.getMemory().isCompleted());
        assertEquals(outcome.getMemory().getFinalStory(), outcome.getFinalStory());
    }

    /**
     * 丰富经历玉米（传说桩失败）：经历分 104 保证 score ≥150 → EPIC
     * （规则文档 §四十），售价 = round(70 × 3) = 210（§六十五），
     * 肥料奖励 3 入包（§六十六）。
     */
    @Test
    void harvestEpicCornPaysQualityPriceAndFertilizer() {
        Soil soil = plantedMatureSoil(CropType.CORN);
        CropMemory memory = memoryService.createMemory(soil.getCrop());
        memory.setManualWaterCount(5);
        memory.setRainCount(4);
        memory.setGreenRainCount(3);
        memory.setFertilizerCount(3);

        Inventory inventory = new Inventory();
        HarvestOutcome outcome = service(legendaryStub(false)).harvest(soil, inventory);

        assertTrue(outcome.isSuccess());
        assertEquals(Quality.EPIC, outcome.getQuality());
        assertEquals(210, outcome.getSellPrice());
        assertEquals(3, outcome.getFertilizerReward());
        assertEquals(3, inventory.getQuantity(ItemType.FERTILIZER));
        assertEquals(100 + 210, economyService.getGold());
    }

    /**
     * 传说突破成功（验收规范 §一百零二 顺序）：LEGENDARY 不走普通档位，
     * 售价 ×5 = 250，肥料奖励 5（规则文档 §六十五、§六十六）。
     */
    @Test
    void harvestLegendaryWheatPaysQuintuplePrice() {
        Soil soil = plantedMatureSoil(CropType.WHEAT);

        Inventory inventory = new Inventory();
        HarvestOutcome outcome = service(legendaryStub(true)).harvest(soil, inventory);

        assertTrue(outcome.isSuccess());
        assertEquals(Quality.LEGENDARY, outcome.getQuality());
        assertTrue(outcome.isLegendary());
        assertEquals(250, outcome.getSellPrice());
        assertEquals(5, outcome.getFertilizerReward());
        assertEquals(5, inventory.getQuantity(ItemType.FERTILIZER));
        assertEquals(100 + 250, economyService.getGold());
        assertTrue(outcome.getMemory().isLegendary());
        assertTrue(outcome.getFinalStory().contains("金色麦穗"));
    }

    /**
     * 神秘商人事件倍率 ×2（规则文档 §四十九）：无经历小麦
     * 售价 = round(50 × 1.0 × 2.0) = 100；其他作物倍率 1.0。
     */
    @Test
    void harvestAppliesEventPriceRate() {
        Soil wheat = plantedMatureSoil(CropType.WHEAT);
        EventPriceRateProvider mysteryMerchant = cropType ->
                cropType == CropType.WHEAT ? 2.0 : 1.0;

        HarvestOutcome outcome = service(legendaryStub(false), mysteryMerchant)
                .harvest(wheat, null);

        assertTrue(outcome.isSuccess());
        assertEquals(100, outcome.getSellPrice());
        assertEquals(100 + 100, economyService.getGold());

        Soil corn = plantedMatureSoil(CropType.CORN);
        HarvestOutcome cornOutcome = service(legendaryStub(false), mysteryMerchant)
                .harvest(corn, null);
        assertEquals(Quality.COMMON, cornOutcome.getQuality());
        assertEquals(70, cornOutcome.getSellPrice());
    }

    /**
     * 未成熟收获 → NOT_MATURE 且金币、土地、作物、记忆均不变
     * （概要设计说明书 §11.1 无效操作必须失败但不改状态）。
     */
    @Test
    void harvestNotMatureChangesNothing() {
        Soil soil = plantedMatureSoil(CropType.WHEAT);
        soil.getCrop().setGrowthStage(GrowthStage.GROWING);

        HarvestOutcome outcome = service(legendaryStub(false)).harvest(soil, new Inventory());

        assertFalse(outcome.isSuccess());
        assertEquals(HarvestResult.NOT_MATURE, outcome.getResult());
        assertEquals(100, economyService.getGold());
        assertEquals(SoilState.PLANTED, soil.getState());
        assertEquals(GrowthStage.GROWING, soil.getCrop().getGrowthStage());
        assertTrue(memoryService.listAll().isEmpty());
    }

    /**
     * 非 PLANTED 收获 → NOT_PLANTED（验收规范 §三十一 检查成熟前置）。
     */
    @Test
    void harvestOnNotPlantedFails() {
        Soil soil = new BasicSoil(2, 2);
        soil.setState(SoilState.TILLED);

        assertEquals(HarvestResult.NOT_PLANTED,
                service(legendaryStub(false)).harvest(soil, null).getResult());
    }

    /**
     * 收获时无记忆档案：自动补建档案并完成落档
     * （档案是品质计算的经历数据权威来源，验收规范 §九十四）。
     */
    @Test
    void harvestWithoutMemoryCreatesOne() {
        Soil soil = plantedMatureSoil(CropType.WHEAT);

        HarvestOutcome outcome = service(legendaryStub(false)).harvest(soil, null);

        assertTrue(outcome.isSuccess());
        assertEquals(outcome.getMemory(), memoryService.findMemory(
                outcome.getMemory().getCropUuid()).orElse(null));
        assertEquals(1, memoryService.listAll().size());
    }

    /**
     * canHarvest：PLANTED+MATURE → true；未成熟/非 PLANTED → false
     * （验收规范 §三十一"检查成熟"）。
     */
    @Test
    void canHarvestChecksMaturityOnly() {
        HarvestTransactionService harvestService = service(legendaryStub(false));

        assertTrue(harvestService.canHarvest(plantedMatureSoil(CropType.WHEAT)));

        Soil growing = plantedMatureSoil(CropType.WHEAT);
        growing.getCrop().setGrowthStage(GrowthStage.GROWING);
        assertFalse(harvestService.canHarvest(growing));

        Soil tilled = new BasicSoil(2, 2);
        tilled.setState(SoilState.TILLED);
        assertFalse(harvestService.canHarvest(tilled));
    }

    /** 收获时刻世界时间落档（第 5 天 12:00 = 5×24+12 = 132，决策 D14 口径）。 */
    @Test
    void harvestWorldTimeIsRecorded() {
        Soil soil = plantedMatureSoil(CropType.WHEAT);

        HarvestOutcome outcome = service(legendaryStub(false)).harvest(soil, null);

        assertEquals(5 * 24 + 12, outcome.getMemory().getHarvestWorldTime());
    }
}
