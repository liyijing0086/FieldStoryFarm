package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.service.impl.BasicMemoryService;
import com.fieldstory.farm.service.impl.BasicShowcaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ShowcaseService} 展示台测试（C 模块 P3；
 * 验收规范 §一百二十三~一百二十四、§一百三十二 ⑥）。
 *
 * <p>只测纯函数与筛选逻辑，不实例化任何 JavaFX 控件
 * （任务约束：测试只测纯函数）。
 */
class ShowcaseServiceTest {

    private MemoryService memoryService;
    private ShowcaseService showcaseService;

    @BeforeEach
    void setUp() {
        memoryService = new BasicMemoryService();
        showcaseService = new BasicShowcaseService(memoryService);
    }

    /** 传说档案（WHEAT）：经历可控，供字段断言（种植 80h=第3天08:00，收获 120h=第5天00:00）。 */
    private CropMemory legendaryMemory(CropType cropType) {
        CropMemory memory = new CropMemory(UUID.randomUUID(), cropType, 80);
        memoryService.save(memory);
        memoryService.recordRain(memory);
        memoryService.recordRain(memory);
        memoryService.recordDrought(memory, 5);
        memoryService.recordGreenRain(memory);
        memoryService.recordManualWater(memory, 5); // 干旱当天浇水 → 救援标记
        memoryService.recordManualWater(memory, 5);
        memoryService.recordManualWater(memory, 7);
        memoryService.recordFertilizer(memory);
        memoryService.recordEvent(memory, EventType.METEOR_SHOWER);
        memoryService.recordEvent(memory, EventType.ANIMAL_VISIT);
        memoryService.recordEvent(memory, EventType.ANIMAL_VISIT); // 重复事件：摘要应去重
        memoryService.completeHarvest(memory, Quality.LEGENDARY, true, 120);
        return memory;
    }

    /** 普通品质档案（已收获落档，但非传说）。 */
    private CropMemory ordinaryMemory(CropType cropType) {
        CropMemory memory = new CropMemory(UUID.randomUUID(), cropType, 80);
        memoryService.save(memory);
        memoryService.completeHarvest(memory, Quality.EPIC, false, 120);
        return memory;
    }

    /** 未收获落档的档案（无任何经历记录，品质未落档）。 */
    private CropMemory incompleteMemory(CropType cropType) {
        CropMemory memory = new CropMemory(UUID.randomUUID(), cropType, 80);
        memoryService.save(memory);
        return memory;
    }

    /**
     * 空注册表 → 空展示列表（验收规范 §一百二十三：没有传说收获时展示台为空）。
     */
    @Test
    void emptyRegistryListsNothing() {
        assertTrue(showcaseService.listLegendaryEntries().isEmpty());
    }

    /**
     * 只筛选传说档案：普通品质、未落档档案一律不进展示台
     * （验收规范 §一百二十三：只展示传说作物历史记录）。
     */
    @Test
    void onlyCompletedLegendaryMemoriesAreListed() {
        legendaryMemory(CropType.WHEAT);
        ordinaryMemory(CropType.CORN);
        incompleteMemory(CropType.CARROT);

        List<ShowcaseEntry> entries = showcaseService.listLegendaryEntries();

        assertEquals(1, entries.size());
        assertEquals("金色麦穗", entries.get(0).getLegendaryName());
    }

    /**
     * 脏数据防线：传说标志与品质不一致的档案（legendary=true 但品质非
     * LEGENDARY）被过滤而不是让列表方法崩溃（与 {@code ShowcaseEntry#of}
     * 校验口径一致）；直接 buildEntry 仍然拒绝。
     */
    @Test
    void inconsistentLegendaryFlagWithNonLegendaryQualityIsFiltered() {
        CropMemory dirty = new CropMemory(UUID.randomUUID(), CropType.WHEAT, 80);
        memoryService.save(dirty);
        memoryService.completeHarvest(dirty, Quality.EPIC, true, 120);

        assertTrue(showcaseService.listLegendaryEntries().isEmpty());
        assertTrue(showcaseService.findEntry(dirty.getCropUuid()).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> showcaseService.buildEntry(dirty));
    }

    /**
     * 脏数据防线：持久化反序列化可能丢失作物类型（无参构造 + setter
     * 不完整，品质却已落档 LEGENDARY）。列表/findEntry 过滤而不是崩溃，
     * buildEntry 拒绝（视图层 getDisplayName 防 NPE）。
     */
    @Test
    void deserializedMemoryMissingCropTypeIsFiltered() {
        CropMemory dirty = new CropMemory(); // 反序列化无参构造
        dirty.setCropUuid(UUID.randomUUID());
        dirty.setQuality(Quality.LEGENDARY);
        dirty.setLegendary(true);
        dirty.setHarvestWorldTime(120);
        memoryService.save(dirty);

        assertTrue(showcaseService.listLegendaryEntries().isEmpty());
        assertTrue(showcaseService.findEntry(dirty.getCropUuid()).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> showcaseService.buildEntry(dirty));
    }

    /**
     * 条目携带全部九项内容（验收规范 §一百二十四）：传说名称、作物类型、
     * 品质、种植时间、收获时间、关键天气、关键事件、玩家操作、完整生命故事。
     */
    @Test
    void entryCarriesAllNineShowcaseFields() {
        legendaryMemory(CropType.WHEAT);

        ShowcaseEntry entry = showcaseService.listLegendaryEntries().get(0);

        assertEquals("金色麦穗", entry.getLegendaryName());
        assertEquals(CropType.WHEAT, entry.getCropType());
        assertEquals(Quality.LEGENDARY, entry.getQuality());
        assertEquals("第3天 08:00", entry.getPlantTimeText());
        assertEquals("第5天 00:00", entry.getHarvestTimeText());
        assertEquals("雨天 2 次、干旱 1 天、绿雨 1 场", entry.getWeatherSummary());
        assertEquals("流星夜、小动物来访", entry.getEventSummary());
        assertEquals("浇水 3 次、施肥 1 次、干旱当天及时浇水救援", entry.getActionSummary());
        assertTrue(entry.getFullStory().contains("金色麦穗"), entry.getFullStory());
    }

    /**
     * 三种传说均可展示（验收规范 §一百三十二 ⑥：三种传说可展示故事）。
     */
    @Test
    void allThreeLegendariesCanBeShown() {
        legendaryMemory(CropType.WHEAT);
        legendaryMemory(CropType.CORN);
        legendaryMemory(CropType.CARROT);

        List<ShowcaseEntry> entries = showcaseService.listLegendaryEntries();

        assertEquals(3, entries.size());
        assertEquals(List.of("金色麦穗", "彩虹玉米", "巨龙胡萝卜"),
                entries.stream().map(ShowcaseEntry::getLegendaryName).toList());
    }

    /** 非传说档案构建条目 → 拒绝（验收规范 §一百二十三）。 */
    @Test
    void buildEntryRejectsNonLegendaryMemory() {
        CropMemory ordinary = ordinaryMemory(CropType.CORN);

        assertThrows(IllegalArgumentException.class,
                () -> showcaseService.buildEntry(ordinary));
    }

    /** 未收获落档的档案构建条目 → 拒绝（品质未落档不能进展示台）。 */
    @Test
    void buildEntryRejectsIncompleteMemory() {
        CropMemory incomplete = incompleteMemory(CropType.CARROT);

        assertThrows(IllegalArgumentException.class,
                () -> showcaseService.buildEntry(incomplete));
    }

    /** findEntry：传说档案可找到；普通档案与未知 uuid 为空。 */
    @Test
    void findEntryOnlyReturnsLegendary() {
        CropMemory legendary = legendaryMemory(CropType.WHEAT);
        CropMemory ordinary = ordinaryMemory(CropType.CORN);

        Optional<ShowcaseEntry> found = showcaseService.findEntry(legendary.getCropUuid());
        assertTrue(found.isPresent());
        assertEquals("金色麦穗", found.get().getLegendaryName());
        assertTrue(showcaseService.findEntry(ordinary.getCropUuid()).isEmpty());
        assertTrue(showcaseService.findEntry(UUID.randomUUID()).isEmpty());
    }

    /**
     * 时间格式化纯函数（决策 D14 口径 worldTime = gameDay×24 + gameHour）：
     * 80 → 第3天 08:00；整点与 -1 哨兵。
     */
    @Test
    void formatWorldTimeConvertsByD14Rule() {
        assertEquals("第3天 08:00", ShowcaseEntry.formatWorldTime(80));
        assertEquals("第5天 00:00", ShowcaseEntry.formatWorldTime(120));
        assertEquals("第1天 00:00", ShowcaseEntry.formatWorldTime(24));
        assertEquals("第0天 00:00", ShowcaseEntry.formatWorldTime(0));
        assertEquals("——", ShowcaseEntry.formatWorldTime(-1));
    }

    /** 天气摘要：全零 → 占位文案；非零项拼接、零项跳过。 */
    @Test
    void weatherSummarySkipsZeroCounters() {
        CropMemory empty = incompleteMemory(CropType.WHEAT);
        assertEquals("无特殊天气经历", ShowcaseEntry.weatherSummaryOf(empty));

        CropMemory mixed = incompleteMemory(CropType.WHEAT);
        memoryService.recordDrought(mixed, 5);
        memoryService.recordGreenRain(mixed);
        assertEquals("干旱 1 天、绿雨 1 场", ShowcaseEntry.weatherSummaryOf(mixed));
    }

    /** 事件摘要：空 → 占位文案；重复事件去重、NONE 不参与。 */
    @Test
    void eventSummaryDeduplicatesEvents() {
        CropMemory empty = incompleteMemory(CropType.WHEAT);
        assertEquals("无特殊事件", ShowcaseEntry.eventSummaryOf(empty));

        CropMemory repeated = incompleteMemory(CropType.WHEAT);
        memoryService.recordEvent(repeated, EventType.ANIMAL_VISIT);
        memoryService.recordEvent(repeated, EventType.ANIMAL_VISIT);
        memoryService.recordEvent(repeated, EventType.NONE);
        memoryService.recordEvent(repeated, EventType.RAINBOW_DAY);
        assertEquals("小动物来访、彩虹日", ShowcaseEntry.eventSummaryOf(repeated));
    }

    /** 操作摘要：空 → 占位文案；浇水/施肥/救援逐项拼接。 */
    @Test
    void actionSummaryListsPlayerActions() {
        CropMemory empty = incompleteMemory(CropType.WHEAT);
        assertEquals("无玩家干预", ShowcaseEntry.actionSummaryOf(empty));

        CropMemory rescued = incompleteMemory(CropType.WHEAT);
        memoryService.recordDrought(rescued, 5);
        memoryService.recordManualWater(rescued, 5);
        memoryService.recordFertilizer(rescued);
        assertEquals("浇水 1 次、施肥 1 次、干旱当天及时浇水救援",
                ShowcaseEntry.actionSummaryOf(rescued));
    }

    /** 事件中文名映射（四个事件名取自验收规范 §九十二 事件效果）。 */
    @Test
    void eventNameMapping() {
        assertEquals("流星夜", ShowcaseEntry.eventName(EventType.METEOR_SHOWER));
        assertEquals("神秘商人", ShowcaseEntry.eventName(EventType.MYSTERY_MERCHANT));
        assertEquals("小动物来访", ShowcaseEntry.eventName(EventType.ANIMAL_VISIT));
        assertEquals("彩虹日", ShowcaseEntry.eventName(EventType.RAINBOW_DAY));
        assertEquals("", ShowcaseEntry.eventName(EventType.NONE));
    }
}
