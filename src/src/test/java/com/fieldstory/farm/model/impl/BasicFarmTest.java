package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.FarmPlot;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * BasicFarm 测试：12×12 布局结构（规则文档 §10.1；验收规范 §十一、§十二）。
 */
class BasicFarmTest {

    @Test
    void mapIsTwelveByTwelveWithCenterFarmPlots() {
        Farm farm = new BasicFarm();
        for (int row = 0; row < BasicFarm.MAP_SIZE; row++) {
            for (int column = 0; column < BasicFarm.MAP_SIZE; column++) {
                FarmPlot type = farm.getPlotType(row, column);
                if (row >= 2 && row <= 9 && column >= 2 && column <= 9) {
                    assertEquals(FarmPlot.FARM_PLOT, type, "中心种植区 (" + row + "," + column + ")");
                } else {
                    assertEquals(FarmPlot.DECORATION_AREA, type, "外围区域 (" + row + "," + column + ")");
                }
            }
        }
    }

    @Test
    void everyFarmPlotHoldsEmptySoil() {
        Farm farm = new BasicFarm();
        for (int row = 2; row <= 9; row++) {
            for (int column = 2; column <= 9; column++) {
                Soil soil = farm.getSoil(row, column);
                assertNotNull(soil);
                assertEquals(SoilState.EMPTY, soil.getState());
                assertEquals(row, soil.getRow());
                assertEquals(column, soil.getColumn());
            }
        }
    }

    @Test
    void nonFarmPlotHasNoSoil() {
        Farm farm = new BasicFarm();
        // 四角与上下边中点，均位于外围装饰区
        assertNull(farm.getSoil(0, 0));
        assertNull(farm.getSoil(0, 11));
        assertNull(farm.getSoil(11, 0));
        assertNull(farm.getSoil(11, 11));
        assertNull(farm.getSoil(5, 0));
        assertNull(farm.getSoil(5, 11));
    }

    @Test
    void iterateAllSoilsYieldsSixtyFourUniqueSoils() {
        Farm farm = new BasicFarm();
        List<Soil> soils = farm.getSoils();
        assertEquals(64, soils.size());

        Set<Long> ids = new HashSet<>();
        for (Soil soil : soils) {
            ids.add(soil.getId());
        }
        assertEquals(64, ids.size(), "64 块土地 id 应互不重复");
    }

    @Test
    void p0LayoutHasNoShopOrShowcase() {
        Farm farm = new BasicFarm();
        for (int row = 0; row < BasicFarm.MAP_SIZE; row++) {
            for (int column = 0; column < BasicFarm.MAP_SIZE; column++) {
                assertNotEquals(FarmPlot.SHOP, farm.getPlotType(row, column));
                assertNotEquals(FarmPlot.SHOWCASE, farm.getPlotType(row, column));
            }
        }
    }
}
