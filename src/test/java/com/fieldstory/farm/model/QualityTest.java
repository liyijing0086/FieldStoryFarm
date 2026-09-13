package com.fieldstory.farm.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Quality} 品质枚举测试（规则文档 §三十二、§六十五、§六十六）。
 */
class QualityTest {

    /**
     * 售价倍率（规则文档 §六十五）：
     * 普通 ×1 / 优秀 ×1.5 / 稀有 ×2 / 史诗 ×3 / 传说 ×5。
     */
    @Test
    void priceMultiplierPerBand() {
        assertEquals(1.0, Quality.COMMON.getPriceMultiplier(), 0.0001);
        assertEquals(1.5, Quality.EXCELLENT.getPriceMultiplier(), 0.0001);
        assertEquals(2.0, Quality.RARE.getPriceMultiplier(), 0.0001);
        assertEquals(3.0, Quality.EPIC.getPriceMultiplier(), 0.0001);
        assertEquals(5.0, Quality.LEGENDARY.getPriceMultiplier(), 0.0001);
    }

    /**
     * 肥料奖励（规则文档 §六十六）：普通 0 / 优秀 1 / 稀有 2 /
     * 史诗 3 / 传说 5。
     */
    @Test
    void fertilizerRewardPerBand() {
        assertEquals(0, Quality.COMMON.getFertilizerReward());
        assertEquals(1, Quality.EXCELLENT.getFertilizerReward());
        assertEquals(2, Quality.RARE.getFertilizerReward());
        assertEquals(3, Quality.EPIC.getFertilizerReward());
        assertEquals(5, Quality.LEGENDARY.getFertilizerReward());
    }

    /** 仅 LEGENDARY 为传说品质（规则文档 §三十二）。 */
    @Test
    void onlyLegendaryIsLegendary() {
        assertFalse(Quality.COMMON.isLegendary());
        assertFalse(Quality.EXCELLENT.isLegendary());
        assertFalse(Quality.RARE.isLegendary());
        assertFalse(Quality.EPIC.isLegendary());
        assertTrue(Quality.LEGENDARY.isLegendary());
    }

    /** 显示名称完整。 */
    @Test
    void displayNames() {
        assertEquals("普通", Quality.COMMON.getDisplayName());
        assertEquals("优秀", Quality.EXCELLENT.getDisplayName());
        assertEquals("稀有", Quality.RARE.getDisplayName());
        assertEquals("史诗", Quality.EPIC.getDisplayName());
        assertEquals("传说", Quality.LEGENDARY.getDisplayName());
    }
}
