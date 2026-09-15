package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.service.WateringResult;
import com.fieldstory.farm.service.WateringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link BasicWateringService} 测试（验收规范 §二十六、§二十七、§二十八；
 * 决策 D11、D14；规则文档 §二十七）。
 *
 * <p>三重校验：阶段 ∈ {SPROUT, GROWING, MATURE}、当日未浇、count &lt; 5；
 * 浇水加成 min(count × 0.05, 0.20)。
 *
 * <p>不使用 CropFactory（其内部含 UUID.randomUUID），作物状态手动构造；
 * 不使用系统时间，游戏日直接传 long。
 */
class WateringServiceTest {

    private WateringService wateringService;

    @BeforeEach
    void setUp() {
        wateringService = new BasicWateringService();
    }

    /**
     * 辅助：SPROUT 阶段作物，count=0，从未浇水
     * （lastManualWaterGameDay=-1 哨兵，D14）。
     */
    private Crop sproutCrop() {
        Crop crop = new BasicCrop();
        crop.setGrowthStage(GrowthStage.SPROUT);
        crop.setManualWaterCount(0);
        crop.setLastManualWaterGameDay(-1);
        return crop;
    }

    /**
     * SEED 阶段浇水 → SEED_STAGE 且 count 不变（验收规范 §二十六）。
     */
    @Test
    void waterOnSeedStageRejected() {
        Crop crop = new BasicCrop();
        crop.setGrowthStage(GrowthStage.SEED);
        crop.setManualWaterCount(0);
        crop.setLastManualWaterGameDay(-1);

        assertEquals(WateringResult.SEED_STAGE, wateringService.water(crop, 0L));
        assertEquals(0, crop.getManualWaterCount());
    }

    /**
     * 同一游戏日第二次浇水 → ALREADY_WATERED_TODAY 且 count 不变
     * （验收规范 §二十七：每个游戏日最多 1 次有效主动浇水）。
     */
    @Test
    void secondWaterSameDayRejected() {
        Crop crop = sproutCrop();

        assertEquals(WateringResult.SUCCESS, wateringService.water(crop, 0L));
        assertEquals(1, crop.getManualWaterCount());

        assertEquals(WateringResult.ALREADY_WATERED_TODAY, wateringService.water(crop, 0L));
        assertEquals(1, crop.getManualWaterCount());
    }

    /**
     * 连续 5 个游戏日（第 0~4 天）各浇 1 次 → 全部 SUCCESS，count=5
     * （验收规范 §二十八；决策 D11：第 5 次仍有效）。
     */
    @Test
    void fiveConsecutiveDaysAllSucceed() {
        Crop crop = sproutCrop();

        for (long day = 0; day < 5; day++) {
            assertEquals(WateringResult.SUCCESS, wateringService.water(crop, day));
        }

        assertEquals(5, crop.getManualWaterCount());
        assertEquals(4L, crop.getLastManualWaterGameDay());
    }

    /**
     * 第 6 次浇水 → WATER_LIMIT_REACHED（决策 D11：第 6 次起拒绝，
     * 加成维持 +20% 封顶）。
     */
    @Test
    void sixthWaterRejectedByLimit() {
        Crop crop = sproutCrop();
        for (long day = 0; day < 5; day++) {
            wateringService.water(crop, day);
        }

        assertEquals(WateringResult.WATER_LIMIT_REACHED, wateringService.water(crop, 5L));
        assertEquals(5, crop.getManualWaterCount());
    }

    /**
     * 第 0 游戏日首次浇水成功：-1 哨兵 != 0，不误判"当日已浇"（决策 D14）。
     */
    @Test
    void firstWaterOnGameDayZeroSucceeds() {
        Crop crop = sproutCrop();

        assertEquals(WateringResult.SUCCESS, wateringService.water(crop, 0L));
        assertEquals(1, crop.getManualWaterCount());
        assertEquals(0L, crop.getLastManualWaterGameDay());
    }

    /**
     * calculateWaterGrowthBonus：count=0→0.0、count=4→0.20、count=5→0.20 封顶
     * （规则文档 §二十七：每次 +5%、最多 +20%）。
     */
    @Test
    void waterBonusCappedAt20Percent() {
        Crop crop = sproutCrop();

        assertEquals(0.0, wateringService.calculateWaterGrowthBonus(crop), 1e-9);

        crop.setManualWaterCount(4);
        assertEquals(0.20, wateringService.calculateWaterGrowthBonus(crop), 1e-9);

        crop.setManualWaterCount(5);
        assertEquals(0.20, wateringService.calculateWaterGrowthBonus(crop), 1e-9);
    }
}
