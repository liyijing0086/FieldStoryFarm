package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicSoil;
import com.fieldstory.farm.service.HarvestResult;
import com.fieldstory.farm.service.HarvestService;
import com.fieldstory.farm.service.LandService;
import com.fieldstory.farm.testutil.TestEconomyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BasicHarvestService} 测试（C 模块 P0 基础收获；
 * 验收规范 §三十一、§三十二、§三十三；决策 D03、D09）。
 *
 * <p>依赖经 {@link TestEconomyService} 测试桩注入，土地回退用
 * {@link BasicLandService}（D09：C 调 A 的 removeCropAndSetTilled）。
 * 只测纯业务逻辑，不创建任何 JavaFX 控件（无 GUI 线程）。
 */
class BasicHarvestServiceTest {

    private TestEconomyService economyService;
    private HarvestService harvestService;

    @BeforeEach
    void setUp() {
        economyService = new TestEconomyService();
        LandService landService = new BasicLandService(economyService);
        harvestService = new BasicHarvestService(economyService, landService);
    }

    /** 辅助：指定状态的空地（(2,2) 为中心 8×8 种植区格）。 */
    private Soil soil(SoilState state) {
        Soil soil = new BasicSoil(2, 2);
        soil.setState(state);
        return soil;
    }

    /** 辅助：PLANTED 土地 + 指定阶段/类型的作物。 */
    private Soil plantedSoil(GrowthStage stage, CropType type) {
        Soil soil = soil(SoilState.PLANTED);
        Crop crop = new BasicCrop();
        crop.setCropType(type);
        crop.setGrowthStage(stage);
        soil.setCrop(crop);
        return soil;
    }

    // ==================== canHarvest：仅 MATURE 为 true ====================

    @Test
    void canHarvestNullSoilIsFalse() {
        assertFalse(harvestService.canHarvest(null));
    }

    @Test
    void canHarvestNonPlantedIsFalse() {
        assertFalse(harvestService.canHarvest(soil(SoilState.EMPTY)));
        assertFalse(harvestService.canHarvest(soil(SoilState.TILLED)));
        assertFalse(harvestService.canHarvest(soil(SoilState.LOCKED)));
    }

    @Test
    void canHarvestPlantedWithoutCropIsFalse() {
        assertFalse(harvestService.canHarvest(soil(SoilState.PLANTED)));
    }

    @Test
    void canHarvestPlantedNotMatureIsFalse() {
        assertFalse(harvestService.canHarvest(plantedSoil(GrowthStage.SEED, CropType.WHEAT)));
        assertFalse(harvestService.canHarvest(plantedSoil(GrowthStage.SPROUT, CropType.WHEAT)));
        assertFalse(harvestService.canHarvest(plantedSoil(GrowthStage.GROWING, CropType.WHEAT)));
    }

    @Test
    void canHarvestMatureIsTrue() {
        assertTrue(harvestService.canHarvest(plantedSoil(GrowthStage.MATURE, CropType.WHEAT)));
    }

    // ==================== 成功：售价入账 + 土地回退 TILLED ====================

    /**
     * 成熟小麦收获：FinalPrice = BasePrice = 50（验收规范 §三十二）、
     * Crop 离开土地、SoilState 置 TILLED（验收规范 §三十三）。
     */
    @Test
    void harvestMatureWheatAddsBaseSellPriceAndTills() {
        economyService.setGold(0);
        Soil soil = plantedSoil(GrowthStage.MATURE, CropType.WHEAT);

        assertEquals(HarvestResult.SUCCESS, harvestService.harvest(soil));

        assertEquals(50, economyService.getGold());
        assertNull(soil.getCrop());
        assertEquals(SoilState.TILLED, soil.getState());
    }

    /**
     * 三种作物售价分别按基础售价入账：玉米 70、胡萝卜 60（规则文档 §十三；
     * 验收规范 §三十二）。
     */
    @Test
    void harvestPricesFollowBaseSellPrice() {
        economyService.setGold(0);

        harvestService.harvest(plantedSoil(GrowthStage.MATURE, CropType.CORN));
        assertEquals(70, economyService.getGold());

        harvestService.harvest(plantedSoil(GrowthStage.MATURE, CropType.CARROT));
        assertEquals(130, economyService.getGold());
    }

    // ==================== 失败：不加金币、不改状态（概要设计说明书 §11.1） ====================

    /** 非 PLANTED（TILLED 收获为无效操作）→ NOT_PLANTED，状态不变。 */
    @Test
    void harvestNotPlantedRejectedWithoutChange() {
        economyService.setGold(10);
        Soil soil = soil(SoilState.TILLED);

        assertEquals(HarvestResult.NOT_PLANTED, harvestService.harvest(soil));
        assertEquals(10, economyService.getGold());
        assertEquals(SoilState.TILLED, soil.getState());
        assertNull(soil.getCrop());
    }

    /** PLANTED 但无作物（防御性校验）→ NO_CROP，不加金币、状态不变。 */
    @Test
    void harvestPlantedWithoutCropRejected() {
        economyService.setGold(10);
        Soil soil = soil(SoilState.PLANTED);

        assertEquals(HarvestResult.NO_CROP, harvestService.harvest(soil));
        assertEquals(10, economyService.getGold());
        assertEquals(SoilState.PLANTED, soil.getState());
    }

    /** 未成熟 → NOT_MATURE，不加金币、作物仍在、土地仍 PLANTED。 */
    @Test
    void harvestNotMatureRejectedWithoutChange() {
        economyService.setGold(10);
        Soil soil = plantedSoil(GrowthStage.GROWING, CropType.WHEAT);

        assertEquals(HarvestResult.NOT_MATURE, harvestService.harvest(soil));
        assertEquals(10, economyService.getGold());
        assertEquals(SoilState.PLANTED, soil.getState());
        assertNotNull(soil.getCrop());
    }
}
