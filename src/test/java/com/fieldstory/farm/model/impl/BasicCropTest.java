package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.factory.CropFactory;
import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GrowthStage;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * BasicCrop 测试：纯状态容器的字段读写（验收规范 §二十 字段清单）。
 */
class BasicCropTest {

    @Test
    void newCropHasEmptyState() {
        Crop crop = new BasicCrop();
        assertNull(crop.getCropUuid());
        assertNull(crop.getCropType());
        assertNull(crop.getGrowthStage());
        assertEquals(0.0, crop.getGrowthProgress());
        assertEquals(0L, crop.getPlantWorldTime());
        assertEquals(0, crop.getManualWaterCount());
        assertEquals(-1L, crop.getLastManualWaterGameDay(), "新作物默认从未浇水（哨兵 -1，D14）");
        assertEquals(0, crop.getDroughtCount());
        assertEquals(0, crop.getRainCount());
        assertEquals(0, crop.getGreenRainCount());
        assertEquals(-1L, crop.getLastHydratedWorldTime(), "新作物默认无补水记录（哨兵 -1，D16）");
        assertEquals(0, crop.getDroughtStreak());
    }

    @Test
    void cropFactoryCreatesWithWeatherRecordDefaults() {
        Crop crop = CropFactory.create(CropType.WHEAT, 48L);
        assertEquals(0, crop.getDroughtCount());
        assertEquals(0, crop.getRainCount());
        assertEquals(0, crop.getGreenRainCount());
        assertEquals(-1L, crop.getLastHydratedWorldTime(), "工厂创建默认无补水记录（哨兵 -1，D16）");
        assertEquals(0, crop.getDroughtStreak());
    }

    @Test
    void settersRoundTrip() {
        Crop crop = new BasicCrop();
        UUID uuid = UUID.randomUUID();

        crop.setCropUuid(uuid);
        crop.setCropType(CropType.WHEAT);
        crop.setGrowthStage(GrowthStage.SPROUT);
        crop.setGrowthProgress(35.5);
        crop.setPlantWorldTime(96L);
        crop.setManualWaterCount(2);
        crop.setLastManualWaterGameDay(5L);
        crop.setDroughtCount(3);
        crop.setRainCount(2);
        crop.setGreenRainCount(1);
        crop.setLastHydratedWorldTime(120L);
        crop.setDroughtStreak(3);

        assertEquals(uuid, crop.getCropUuid());
        assertEquals(CropType.WHEAT, crop.getCropType());
        assertEquals(GrowthStage.SPROUT, crop.getGrowthStage());
        assertEquals(35.5, crop.getGrowthProgress());
        assertEquals(96L, crop.getPlantWorldTime());
        assertEquals(2, crop.getManualWaterCount());
        assertEquals(5L, crop.getLastManualWaterGameDay());
        assertEquals(3, crop.getDroughtCount());
        assertEquals(2, crop.getRainCount());
        assertEquals(1, crop.getGreenRainCount());
        assertEquals(120L, crop.getLastHydratedWorldTime());
        assertEquals(3, crop.getDroughtStreak());
    }
}
