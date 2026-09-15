package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.service.impl.BasicMemoryService;
import com.fieldstory.farm.service.impl.BasicShowcaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 展示台模拟运行测试（C 模块 P3）。
 *
 * <p>模拟真实游戏链路：开局空档 → 种植建档 → 生长经历 → 传说收获 →
 * 展示台查询 → 普通收获不混入 → 第二株传说 → 刷新幂等。覆盖
 * Controller.refresh → View.refresh → {@link ShowcaseService#listLegendaryEntries}
 * 数据链路中除 JavaFX 渲染外的全部环节（任务约束：不实例化 JavaFX 控件）。
 *
 * <p>对应验收规范 §一百二十三~一百二十四、§一百三十二 ⑥。
 */
class ShowcaseFlowTest {

    private MemoryService memoryService;
    private ShowcaseService showcaseService;

    @BeforeEach
    void setUp() {
        memoryService = new BasicMemoryService();
        showcaseService = new BasicShowcaseService(memoryService);
    }

    /**
     * 全链路模拟：新档 → 传说收获 → 展示台可见 → 再收获 → 列表更新。
     *
     * <p>时间口径（决策 D14）：种植 80h = 第3天 08:00；收获 120h = 第5天 00:00。
     */
    @Test
    @DisplayName("模拟运行：种植经历收获后展示台逐步可见，普通收获不混入")
    void fullGameplayFlowEndsWithShowcaseVisible() {
        // ── 阶段 0：新档开局，展示台为空 ──
        assertTrue(showcaseService.listLegendaryEntries().isEmpty(),
                "新档展示台应为空");

        // ── 阶段 1：种植小麦建档（第3天 08:00）──
        CropMemory wheat = new CropMemory(UUID.randomUUID(), CropType.WHEAT, 80);
        memoryService.save(wheat);

        // ── 阶段 2：生长经历（金色麦穗条件，规则文档 §四十二）──
        memoryService.recordRain(wheat);
        memoryService.recordRain(wheat);
        memoryService.recordDrought(wheat, 5);
        memoryService.recordManualWater(wheat, 5); // 干旱当天浇水 → 救援标记
        memoryService.recordManualWater(wheat, 5);
        memoryService.recordManualWater(wheat, 7);
        memoryService.recordGreenRain(wheat);
        memoryService.recordFertilizer(wheat);
        memoryService.recordEvent(wheat, EventType.METEOR_SHOWER);
        memoryService.recordEvent(wheat, EventType.ANIMAL_VISIT);
        memoryService.recordEvent(wheat, EventType.ANIMAL_VISIT);

        // 收获前：尚未落档，展示台仍为空
        assertTrue(showcaseService.listLegendaryEntries().isEmpty(),
                "未收获落档的档案不得进入展示台");

        // ── 阶段 3：传说收获落档（第5天 00:00）→ 展示台立即可见 ──
        memoryService.completeHarvest(wheat, Quality.LEGENDARY, true, 120);

        List<ShowcaseEntry> entries = showcaseService.listLegendaryEntries();
        assertEquals(1, entries.size(), "传说收获后展示台应有一条记录");
        ShowcaseEntry wheatEntry = entries.get(0);
        assertEquals("金色麦穗", wheatEntry.getLegendaryName());
        assertEquals(CropType.WHEAT, wheatEntry.getCropType());
        assertEquals(Quality.LEGENDARY, wheatEntry.getQuality());
        assertEquals("第3天 08:00", wheatEntry.getPlantTimeText());
        assertEquals("第5天 00:00", wheatEntry.getHarvestTimeText());
        assertEquals("雨天 2 次、干旱 1 天、绿雨 1 场", wheatEntry.getWeatherSummary());
        assertEquals("流星夜、小动物来访", wheatEntry.getEventSummary());
        assertEquals("浇水 3 次、施肥 1 次、干旱当天及时浇水救援",
                wheatEntry.getActionSummary());
        assertTrue(wheatEntry.getFullStory().contains("金色麦穗"),
                "生命故事应以传说名收尾：" + wheatEntry.getFullStory());

        // 收获后按 cropUuid 立即可查（验收规范 §九十三 档案生命周期）
        Optional<ShowcaseEntry> found = showcaseService.findEntry(wheat.getCropUuid());
        assertTrue(found.isPresent());
        assertEquals("金色麦穗", found.get().getLegendaryName());

        // ── 阶段 4：普通玉米收获（EPIC）→ 不混入展示台 ──
        CropMemory corn = new CropMemory(UUID.randomUUID(), CropType.CORN, 80);
        memoryService.save(corn);
        memoryService.completeHarvest(corn, Quality.EPIC, false, 120);

        assertEquals(1, showcaseService.listLegendaryEntries().size(),
                "非传说收获不得混入展示台");
        assertTrue(showcaseService.findEntry(corn.getCropUuid()).isEmpty());

        // ── 阶段 5：第二株传说（胡萝卜）收获 → 列表更新，顺序 = 建档顺序 ──
        CropMemory carrot = new CropMemory(UUID.randomUUID(), CropType.CARROT, 96);
        memoryService.save(carrot);
        memoryService.recordGreenRain(carrot);
        memoryService.completeHarvest(carrot, Quality.LEGENDARY, true, 168);

        entries = showcaseService.listLegendaryEntries();
        assertEquals(2, entries.size(), "第二株传说收获后列表应更新");
        assertEquals(List.of("金色麦穗", "巨龙胡萝卜"),
                entries.stream().map(ShowcaseEntry::getLegendaryName).toList(),
                "展示顺序应为档案创建顺序（小麦先于胡萝卜建档）");

        // ── 阶段 6：刷新幂等（模拟 View.refresh 反复拉取）──
        List<ShowcaseEntry> again = showcaseService.listLegendaryEntries();
        assertEquals(entries.size(), again.size());
        for (int i = 0; i < entries.size(); i++) {
            assertEquals(entries.get(i).getCropUuid(), again.get(i).getCropUuid());
            assertEquals(entries.get(i).getFullStory(), again.get(i).getFullStory());
        }
    }

    /**
     * 模拟运行：三种传说各经历一次完整生命周期后均可展示
     * （验收规范 §一百三十二 ⑥：三种传说可展示故事）。
     */
    @Test
    @DisplayName("模拟运行：三种传说完整生命周期后全部可展示")
    void allThreeLegendariesAfterFullLifecycle() {
        assertListedLegendary(CropType.WHEAT, "金色麦穗");
        assertListedLegendary(CropType.CORN, "彩虹玉米");
        assertListedLegendary(CropType.CARROT, "巨龙胡萝卜");

        assertEquals(3, showcaseService.listLegendaryEntries().size());
    }

    /** 为指定作物跑一遍「建档 → 经历 → 传说收获」并断言展示台可见。 */
    private void assertListedLegendary(CropType cropType, String expectedName) {
        CropMemory memory = new CropMemory(UUID.randomUUID(), cropType, 80);
        memoryService.save(memory);
        memoryService.recordRain(memory);
        memoryService.recordGreenRain(memory);
        memoryService.recordFertilizer(memory);
        memoryService.completeHarvest(memory, Quality.LEGENDARY, true, 120);

        Optional<ShowcaseEntry> entry = showcaseService.findEntry(memory.getCropUuid());
        assertTrue(entry.isPresent(), expectedName + " 应可展示");
        assertEquals(expectedName, entry.get().getLegendaryName());
    }
}
