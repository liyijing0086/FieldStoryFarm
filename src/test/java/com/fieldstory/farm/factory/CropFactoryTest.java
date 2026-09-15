package com.fieldstory.farm.factory;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GrowthStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CropFactory 测试：新建作物初始状态（验收规范 §二十 字段清单）。
 */
class CropFactoryTest {

    @Test
    void createReturnsSeedStageWithZeroProgressAndCounts() {
        Crop crop = CropFactory.create(CropType.WHEAT, 48L);

        assertNotNull(crop.getCropUuid());
        assertEquals(CropType.WHEAT, crop.getCropType());
        assertEquals(GrowthStage.SEED, crop.getGrowthStage());
        assertEquals(0.0, crop.getGrowthProgress());
        assertEquals(48L, crop.getPlantWorldTime());
        assertEquals(0, crop.getManualWaterCount());
        assertEquals(-1L, crop.getLastManualWaterGameDay());
    }

    @Test
    void createGeneratesDistinctUuids() {
        Crop first = CropFactory.create(CropType.CORN, 0L);
        Crop second = CropFactory.create(CropType.CORN, 0L);
        assertNotEquals(first.getCropUuid(), second.getCropUuid());
    }
}
