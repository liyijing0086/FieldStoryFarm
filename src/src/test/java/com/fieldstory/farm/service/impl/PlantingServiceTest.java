package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.impl.BasicSoil;
import com.fieldstory.farm.service.PlantingResult;
import com.fieldstory.farm.service.PlantingService;
import com.fieldstory.farm.testutil.TestEconomyService;
import com.fieldstory.farm.testutil.TestGameClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BasicPlantingService} 测试（验收规范 §十六、§十八、§二十；
 * A 模块设计文档 §8.2；决策 D14）。
 *
 * <p>依赖经 {@link TestEconomyService} / {@link TestGameClock} 测试桩注入；
 * 播种时刻使用桩内可 set 的游戏日/小时，不使用系统时间；
 * 作物初始字段断言不依赖随机 UUID（测试自身不使用 CropFactory）。
 */
class PlantingServiceTest {

    private TestEconomyService economyService;
    private TestGameClock gameClock;
    private PlantingService plantingService;

    @BeforeEach
    void setUp() {
        economyService = new TestEconomyService();
        gameClock = new TestGameClock();
        plantingService = new BasicPlantingService(economyService, gameClock);
    }

    /** 辅助：以指定状态构造土地（(2,2) 为中心 8×8 种植区格）。 */
    private Soil soil(SoilState state) {
        Soil soil = new BasicSoil(2, 2);
        soil.setState(state);
        return soil;
    }

    /**
     * 播种成功：TILLED + 种子充足 → 消耗 1 种子、创建 Crop（SEED/进度 0/
     * plantWorldTime = day×24 + hour）、置 PLANTED
     * （验收规范 §十八 播种消耗种子、§二十 Crop 字段清单；决策 D14 long 游戏小时）。
     */
    @Test
    void plantSuccessCreatesCropWithPlantWorldTime() {
        economyService.setSeedCount(CropType.WHEAT, 1);
        gameClock.setGameDay(3);
        gameClock.setGameHour(7);
        Soil soil = soil(SoilState.TILLED);

        assertEquals(PlantingResult.SUCCESS, plantingService.plant(soil, CropType.WHEAT));

        Crop crop = soil.getCrop();
        assertNotNull(crop);
        assertEquals(CropType.WHEAT, crop.getCropType());
        assertEquals(GrowthStage.SEED, crop.getGrowthStage());
        assertEquals(0.0, crop.getGrowthProgress(), 1e-9);
        assertEquals(3 * 24L + 7, crop.getPlantWorldTime());
        assertEquals(SoilState.PLANTED, soil.getState());
        assertEquals(0, economyService.getSeedCount(CropType.WHEAT));
    }

    /**
     * EMPTY 直接播种 → NOT_TILLED：不耗种、土地与作物不变
     * （验收规范 §十六 禁止错误土地行为）。
     */
    @Test
    void plantOnEmptyRejected() {
        economyService.setSeedCount(CropType.WHEAT, 1);
        Soil soil = soil(SoilState.EMPTY);

        assertEquals(PlantingResult.NOT_TILLED, plantingService.plant(soil, CropType.WHEAT));

        assertNull(soil.getCrop());
        assertEquals(SoilState.EMPTY, soil.getState());
        assertEquals(1, economyService.getSeedCount(CropType.WHEAT));
    }

    /**
     * PLANTED 再次播种 → NOT_TILLED：不耗种、土地与作物不变
     * （验收规范 §十六：PLANTED 再次播种非法）。
     */
    @Test
    void plantOnPlantedRejected() {
        economyService.setSeedCount(CropType.WHEAT, 1);
        Soil soil = soil(SoilState.PLANTED);

        assertEquals(PlantingResult.NOT_TILLED, plantingService.plant(soil, CropType.WHEAT));

        assertNull(soil.getCrop());
        assertEquals(SoilState.PLANTED, soil.getState());
        assertEquals(1, economyService.getSeedCount(CropType.WHEAT));
    }

    /**
     * 种子不足 → NO_SEED：不耗种、土地与作物不变
     * （验收规范 §十八 SeedInventory 库存不足；B 文档 §24 consumeSeed 约定）。
     */
    @Test
    void plantNoSeedLeavesEverythingUnchanged() {
        Soil soil = soil(SoilState.TILLED);

        assertEquals(PlantingResult.NO_SEED, plantingService.plant(soil, CropType.WHEAT));

        assertNull(soil.getCrop());
        assertEquals(SoilState.TILLED, soil.getState());
        assertEquals(0, economyService.getSeedCount(CropType.WHEAT));
    }

    /**
     * canPlant 各分支：TILLED+有种子 → true；TILLED+无种子 → false；
     * EMPTY / PLANTED → false（A 模块设计文档 §8.2 前置校验；
     * B 文档 §17.2 hasSeed(type, 1)）。
     */
    @Test
    void canPlantBranches() {
        economyService.setSeedCount(CropType.WHEAT, 1);
        Soil tilled = soil(SoilState.TILLED);
        assertTrue(plantingService.canPlant(tilled, CropType.WHEAT));

        economyService.setSeedCount(CropType.WHEAT, 0);
        assertFalse(plantingService.canPlant(tilled, CropType.WHEAT));

        assertFalse(plantingService.canPlant(soil(SoilState.EMPTY), CropType.WHEAT));
        assertFalse(plantingService.canPlant(soil(SoilState.PLANTED), CropType.WHEAT));
    }
}
