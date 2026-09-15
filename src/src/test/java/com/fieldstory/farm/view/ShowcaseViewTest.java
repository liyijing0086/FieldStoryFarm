package com.fieldstory.farm.view;

import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.service.ShowcaseEntry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ShowcaseView} / {@link MemoryCardView} 纯函数测试（C 模块 P3）。
 *
 * <p>只测 {@link ShowcaseView#selectorLabelFor} 与 {@link MemoryCardView#infoLine}
 * 两个静态纯函数；不实例化任何 JavaFX 控件（JavaFX 节点创建需 GUI 线程，
 * 测试中禁止创建控件，任务约束：测试只测纯函数）。
 */
class ShowcaseViewTest {

    /** 辅助：构建已落档的传说展示条目（纯函数 ShowcaseEntry.of，无 JavaFX）。 */
    private static ShowcaseEntry legendaryEntry(CropType cropType) {
        CropMemory memory = new CropMemory(UUID.randomUUID(), cropType, 80);
        memory.setQuality(Quality.LEGENDARY);
        memory.setLegendary(true);
        memory.setHarvestWorldTime(120);
        memory.setFinalStory("测试故事");
        return ShowcaseEntry.of(memory);
    }

    /** 选择器文案：「传说名 · 作物」双字段拼接。 */
    @Test
    void selectorLabelCombinesLegendaryNameAndCrop() {
        assertEquals("金色麦穗 · 小麦",
                ShowcaseView.selectorLabelFor(legendaryEntry(CropType.WHEAT)));
        assertEquals("彩虹玉米 · 玉米",
                ShowcaseView.selectorLabelFor(legendaryEntry(CropType.CORN)));
        assertEquals("巨龙胡萝卜 · 胡萝卜",
                ShowcaseView.selectorLabelFor(legendaryEntry(CropType.CARROT)));
    }

    /** 信息行：「标签：值」（验收规范 §一百二十四 信息字段格式）。 */
    @Test
    void infoLineFormatsLabelAndValue() {
        assertEquals("作物：小麦", MemoryCardView.infoLine("作物", "小麦"));
        assertEquals("品质：传说", MemoryCardView.infoLine("品质", "传说"));
        assertEquals("种植时间：第3天 08:00", MemoryCardView.infoLine("种植时间", "第3天 08:00"));
    }

    /** P4 展示台传奇专属插图路径固定，避免回退成普通成熟作物贴图。 */
    @Test
    void legendaryImagePathsAreStable() {
        assertEquals("/assets/crops/legendary/golden_wheat.png",
                LegendaryImageAssets.classpathFor(CropType.WHEAT));
        assertEquals("/assets/crops/legendary/rainbow_corn.png",
                LegendaryImageAssets.classpathFor(CropType.CORN));
        assertEquals("/assets/crops/legendary/dragon_carrot.png",
                LegendaryImageAssets.classpathFor(CropType.CARROT));
    }

}
