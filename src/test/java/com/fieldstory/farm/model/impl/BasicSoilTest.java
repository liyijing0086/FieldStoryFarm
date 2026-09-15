package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * BasicSoil 测试：id 生成规则（行*12+列，全局坐标）、初始 EMPTY、crop 读写。
 */
class BasicSoilTest {

    @Test
    void idGeneratedFromGlobalRowAndColumn() {
        assertEquals(2 * 12L + 2, new BasicSoil(2, 2).getId());
        assertEquals(9 * 12L + 9, new BasicSoil(9, 9).getId());
        assertEquals(0 * 12L + 5, new BasicSoil(0, 5).getId());
    }

    @Test
    void newSoilStartsEmptyWithoutCrop() {
        Soil soil = new BasicSoil(3, 4);
        assertEquals(3, soil.getRow());
        assertEquals(4, soil.getColumn());
        assertEquals(SoilState.EMPTY, soil.getState());
        assertNull(soil.getCrop());
    }

    @Test
    void stateAndCropRoundTrip() {
        Soil soil = new BasicSoil(2, 2);
        Crop crop = new BasicCrop();

        soil.setState(SoilState.PLANTED);
        soil.setCrop(crop);

        assertEquals(SoilState.PLANTED, soil.getState());
        assertSame(crop, soil.getCrop());

        // 收获/铲除后置空（P0 数据层只保存状态，置空行为由 Service 执行）
        soil.setCrop(null);
        assertNull(soil.getCrop());
    }
}
