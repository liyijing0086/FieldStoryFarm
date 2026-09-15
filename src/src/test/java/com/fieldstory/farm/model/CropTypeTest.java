package com.fieldstory.farm.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CropType 数值测试：三种基础作物硬编码数值
 * （规则文档 §十三 成长天数/种子价格/基础售价、§三十四 基础品质分）。
 */
class CropTypeTest {

    @Test
    void wheatHasRuleValues() {
        assertEquals("小麦", CropType.WHEAT.getDisplayName());
        assertEquals(2, CropType.WHEAT.getBaseGrowthDays());
        assertEquals(10, CropType.WHEAT.getSeedPrice());
        assertEquals(50, CropType.WHEAT.getBasePrice());
        assertEquals(50, CropType.WHEAT.getBaseScore());
        assertEquals(50.0, CropType.WHEAT.getBaseDailyProgress());
    }

    @Test
    void cornHasRuleValues() {
        assertEquals("玉米", CropType.CORN.getDisplayName());
        assertEquals(3, CropType.CORN.getBaseGrowthDays());
        assertEquals(15, CropType.CORN.getSeedPrice());
        assertEquals(70, CropType.CORN.getBasePrice());
        assertEquals(50, CropType.CORN.getBaseScore());
        assertEquals(100.0 / 3, CropType.CORN.getBaseDailyProgress(), 1e-9);
    }

    @Test
    void carrotHasRuleValues() {
        assertEquals("胡萝卜", CropType.CARROT.getDisplayName());
        assertEquals(4, CropType.CARROT.getBaseGrowthDays());
        assertEquals(20, CropType.CARROT.getSeedPrice());
        assertEquals(60, CropType.CARROT.getBasePrice());
        assertEquals(55, CropType.CARROT.getBaseScore());
        assertEquals(25.0, CropType.CARROT.getBaseDailyProgress());
    }
}
