package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.service.impl.BasicMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MemoryService} 生命记忆与故事生成测试
 * （规则文档 §六十九~七十；验收规范 §九十三~九十五）。
 */
class MemoryServiceTest {

    private MemoryService memoryService;

    @BeforeEach
    void setUp() {
        memoryService = new BasicMemoryService();
    }

    /** 播种态作物（第 3 天 08:00 = 世界时间 80，决策 D14 口径）。 */
    private Crop crop(CropType cropType) {
        Crop crop = new BasicCrop();
        crop.setCropUuid(UUID.randomUUID());
        crop.setCropType(cropType);
        crop.setPlantWorldTime(80);
        return crop;
    }

    /**
     * 档案创建与查询：createMemory 登记后可 find，listAll 保持创建顺序
     * （验收规范 §九十三：cropUuid 生命周期唯一）。
     */
    @Test
    void createMemoryRegistersAndFindsByUuid() {
        Crop crop = crop(CropType.WHEAT);

        CropMemory memory = memoryService.createMemory(crop);

        assertTrue(memoryService.findMemory(crop.getCropUuid()).isPresent());
        assertEquals(crop.getCropUuid(), memory.getCropUuid());
        assertEquals(CropType.WHEAT, memory.getCropType());
        assertEquals(80, memory.getPlantWorldTime());
        assertEquals(1, memoryService.listAll().size());
    }

    /**
     * 经历记录：天气/浇水/施肥/事件计数逐一递增；
     * 干旱当天浇水 → 标记浇水救援（金色麦穗条件 2，规则文档 §四十二）。
     */
    @Test
    void recordExperiencesIncrementCounters() {
        Crop crop = crop(CropType.WHEAT);
        CropMemory memory = memoryService.createMemory(crop);

        memoryService.recordDrought(memory, 5);
        memoryService.recordManualWater(memory, 5);
        memoryService.recordManualWater(memory, 6);
        memoryService.recordRain(memory);
        memoryService.recordGreenRain(memory);
        memoryService.recordFertilizer(memory);
        memoryService.recordEvent(memory, EventType.METEOR_SHOWER);
        memoryService.recordEvent(memory, EventType.NONE);
        memoryService.markWitherRisk(memory);

        assertEquals(1, memory.getDroughtCount());
        assertEquals(2, memory.getManualWaterCount());
        assertEquals(1, memory.getRainCount());
        assertEquals(1, memory.getGreenRainCount());
        assertEquals(1, memory.getFertilizerCount());
        assertTrue(memory.isWaterRescueOnDroughtDay());
        assertEquals(List.of(EventType.METEOR_SHOWER), memory.getEvents());
        assertTrue(memory.isWitherRisk());
    }

    /**
     * 非干旱日浇水不标记救援（救援必须发生在干旱当天，
     * 金色麦穗条件 2，规则文档 §四十二）。
     */
    @Test
    void waterOnOtherDayDoesNotMarkRescue() {
        Crop crop = crop(CropType.WHEAT);
        CropMemory memory = memoryService.createMemory(crop);

        memoryService.recordDrought(memory, 5);
        memoryService.recordManualWater(memory, 7);

        assertFalse(memory.isWaterRescueOnDroughtDay());
    }

    /**
     * 从未经历干旱时浇水：lastDroughtGameDay 为 -1 哨兵，
     * 不误判救援（与游戏日 0 区分，决策 D14）。
     */
    @Test
    void waterWithoutDroughtNeverMarksRescue() {
        Crop crop = crop(CropType.WHEAT);
        CropMemory memory = memoryService.createMemory(crop);

        memoryService.recordManualWater(memory, 0);

        assertFalse(memory.isWaterRescueOnDroughtDay());
        assertEquals(-1, memory.getLastDroughtGameDay());
    }

    /**
     * 收获落档（验收规范 §九十四）：品质/传说/收获时刻写入，
     * isCompleted 为 true，最终故事一并落档。
     */
    @Test
    void completeHarvestFillsQualityAndStory() {
        Crop crop = crop(CropType.WHEAT);
        CropMemory memory = memoryService.createMemory(crop);
        memoryService.recordDrought(memory, 5);
        memoryService.recordManualWater(memory, 5);

        String story = memoryService.completeHarvest(memory, Quality.EPIC, false, 120);

        assertTrue(memory.isCompleted());
        assertEquals(Quality.EPIC, memory.getQuality());
        assertFalse(memory.isLegendary());
        assertEquals(120, memory.getHarvestWorldTime());
        assertEquals(story, memory.getFinalStory());
    }

    /**
     * 故事可追溯真实记录（规则文档 §七十）：故事中的经历标签
     * 必须与档案字段一一对应，不生成随机文学。
     */
    @Test
    void storyTracesBackToRealRecords() {
        Crop crop = crop(CropType.WHEAT);
        CropMemory memory = memoryService.createMemory(crop);
        memoryService.recordDrought(memory, 5);
        memoryService.recordManualWater(memory, 5);
        memoryService.recordGreenRain(memory);
        memoryService.recordEvent(memory, EventType.METEOR_SHOWER);

        String story = memoryService.completeHarvest(memory, Quality.LEGENDARY, true, 120);

        assertTrue(story.contains("第3天"), "应包含播种日: " + story);
        assertTrue(story.contains("1次干旱"), "应包含干旱记录: " + story);
        assertTrue(story.contains("及时为它浇下了水"), "应包含救援记录: " + story);
        assertTrue(story.contains("1场珍贵的绿雨"), "应包含绿雨记录: " + story);
        assertTrue(story.contains("流星"), "应包含流星夜记录: " + story);
        assertTrue(story.contains("金色麦穗"), "传说故事应包含传说名: " + story);
    }

    /**
     * 普通品质故事以品质名结尾；传奇故事以传说名结尾
     * （规则文档 §七十 示例）。
     */
    @Test
    void storyEndsWithQualityOrLegendaryName() {
        Crop crop = crop(CropType.WHEAT);
        CropMemory memory = memoryService.createMemory(crop);

        String commonStory = memoryService.completeHarvest(memory, Quality.RARE, false, 96);
        assertTrue(commonStory.contains("稀有品质"), commonStory);

        CropMemory legendaryMemory = memoryService.createMemory(crop(CropType.CORN));
        legendaryMemory.setGreenRainCount(1);
        String legendaryStory = memoryService.completeHarvest(
                legendaryMemory, Quality.LEGENDARY, true, 120);
        assertTrue(legendaryStory.contains("彩虹玉米"), legendaryStory);
    }

    /** 品质未落档时生成故事抛异常（故事必须基于真实收获记录）。 */
    @Test
    void storyRequiresCompletedHarvest() {
        Crop crop = crop(CropType.WHEAT);
        CropMemory memory = memoryService.createMemory(crop);

        assertThrows(IllegalStateException.class,
                () -> memoryService.generateFinalStory(memory));
    }
}
