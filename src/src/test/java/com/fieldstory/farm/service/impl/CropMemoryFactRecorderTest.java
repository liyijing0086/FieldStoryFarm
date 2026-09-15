package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicEventState;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.service.DailySimulationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第三轮：在线/离线共用 CropMemory 事实记录器测试。 */
class CropMemoryFactRecorderTest {

    @Test
    void dailyWeatherAndWitherRiskComeFromOneSimulationFact() {
        Farm farm = new BasicFarm();
        Crop risky = plant(farm, 2, 2, CropType.CORN, 10L);
        Crop safe = plant(farm, 2, 3, CropType.WHEAT, 10L);
        BasicMemoryService memoryService = new BasicMemoryService();
        CropMemoryFactRecorder recorder = new CropMemoryFactRecorder(memoryService);

        recorder.recordDaily(farm, new DailySimulationResult(
                7L, WeatherType.DROUGHT, EventType.NONE,
                0, 0, 0, -1L, -1L, List.of(risky.getCropUuid())));

        CropMemory riskyMemory = memoryService.findMemory(risky.getCropUuid()).orElseThrow();
        CropMemory safeMemory = memoryService.findMemory(safe.getCropUuid()).orElseThrow();
        assertEquals(1, riskyMemory.getDroughtCount());
        assertEquals(1, safeMemory.getDroughtCount());
        assertTrue(riskyMemory.isWitherRisk(), "世界引擎上报 risk UUID 才能标记枯萎风险");
        assertFalse(safeMemory.isWitherRisk(), "Memory 不得自己重算枯萎概率");
    }

    @Test
    void meteorOnlyBelongsToCropsPlantedInsideActualEventWindow() {
        Farm farm = new BasicFarm();
        Crop before = plant(farm, 2, 2, CropType.WHEAT, 99L);
        Crop during = plant(farm, 2, 3, CropType.CORN, 100L);
        Crop lastValid = plant(farm, 2, 4, CropType.CARROT, 123L);
        Crop after = plant(farm, 2, 5, CropType.CORN, 124L);
        BasicMemoryService memoryService = new BasicMemoryService();
        CropMemoryFactRecorder recorder = new CropMemoryFactRecorder(memoryService);

        DailySimulationResult meteor = new DailySimulationResult(
                5L, WeatherType.SUNNY, EventType.METEOR_SHOWER,
                0, 0, 0, 100L, 124L, List.of());
        recorder.recordDaily(farm, meteor);

        assertFalse(events(memoryService, before).contains(EventType.METEOR_SHOWER));
        assertTrue(events(memoryService, during).contains(EventType.METEOR_SHOWER));
        assertTrue(events(memoryService, lastValid).contains(EventType.METEOR_SHOWER));
        assertFalse(events(memoryService, after).contains(EventType.METEOR_SHOWER),
                "流星窗口使用 [start,end)，end 时刻播种不享受流星加成");
    }

    @Test
    void activeEventCanBeRecordedBeforeHarvestWithoutTickDuplicates() {
        Farm farm = new BasicFarm();
        Crop crop = plant(farm, 2, 2, CropType.CORN, 110L);
        BasicMemoryService memoryService = new BasicMemoryService();
        CropMemoryFactRecorder recorder = new CropMemoryFactRecorder(memoryService);
        BasicEventState active = new BasicEventState(EventType.METEOR_SHOWER, 100L, 124L);

        recorder.recordActiveEvent(farm, active);
        recorder.recordActiveEvent(farm, active);
        recorder.recordDaily(farm, new DailySimulationResult(
                5L, WeatherType.SUNNY, EventType.METEOR_SHOWER,
                0, 0, 0, 100L, 124L, List.of()));

        assertEquals(List.of(EventType.METEOR_SHOWER), events(memoryService, crop),
                "在线 tick + 日结不能把同一个事件事实重复写入 CropMemory");
    }

    @Test
    void matureFactUsesSimulationCutPointTime() {
        Farm farm = new BasicFarm();
        Crop crop = plant(farm, 2, 2, CropType.CARROT, 10L);
        BasicMemoryService memoryService = new BasicMemoryService();
        CropMemoryFactRecorder recorder = new CropMemoryFactRecorder(memoryService);

        recorder.recordMatured(List.of(crop), 77L);

        assertEquals(77L, memoryService.findMemory(crop.getCropUuid()).orElseThrow()
                .getMatureWorldTime());
    }

    private static List<EventType> events(BasicMemoryService memoryService, Crop crop) {
        return memoryService.findMemory(crop.getCropUuid()).orElseThrow().getEvents();
    }

    private static Crop plant(Farm farm, int row, int col, CropType type, long plantWorldTime) {
        Soil soil = farm.getSoil(row, col);
        soil.setState(SoilState.PLANTED);
        BasicCrop crop = new BasicCrop();
        crop.setCropUuid(UUID.randomUUID());
        crop.setCropType(type);
        crop.setGrowthStage(GrowthStage.SPROUT);
        crop.setGrowthProgress(30.0);
        crop.setPlantWorldTime(plantWorldTime);
        soil.setCrop(crop);
        return crop;
    }
}
