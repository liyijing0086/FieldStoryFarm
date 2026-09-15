package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.FarmPlot;
import com.fieldstory.farm.model.Soil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link Farm} 基础实现（规则文档 §10.1；验收规范 §十一；决策 D05/D10）。
 *
 * <p>构造即完成 12×12 布局：
 * 中心 8×8（全局 0-based 行 2~9、列 2~9）为 FARM_PLOT，
 * 每格挂一个初始 EMPTY 的 {@link BasicSoil}；
 * 外围 2 格宽共 80 格统一为 DECORATION_AREA；
 * SHOP / SHOWCASE 在 P0 布局中不出现。
 */
public class BasicFarm implements Farm {

    /** 地图总边长：12×12 = 144 格（规则文档 §10.1） */
    public static final int MAP_SIZE = 12;

    /** 中心种植区边长：8×8 = 64 格（规则文档 §10.1） */
    public static final int FARM_AREA_SIZE = 8;

    /** 种植区原点：全局 0-based 行/列起始坐标（决策 D10） */
    public static final int FARM_AREA_ORIGIN = 2;

    /** 全图格类型表 */
    private final FarmPlot[][] plotTypes;

    /** 全图 Soil 表：仅 FARM_PLOT 格非 null */
    private final Soil[][] soils;

    public BasicFarm() {
        this.plotTypes = new FarmPlot[MAP_SIZE][MAP_SIZE];
        this.soils = new Soil[MAP_SIZE][MAP_SIZE];
        for (int row = 0; row < MAP_SIZE; row++) {
            for (int column = 0; column < MAP_SIZE; column++) {
                if (isFarmArea(row, column)) {
                    plotTypes[row][column] = FarmPlot.FARM_PLOT;
                    soils[row][column] = new BasicSoil(row, column);
                } else {
                    plotTypes[row][column] = FarmPlot.DECORATION_AREA;
                }
            }
        }
    }

    /** 判断全局坐标是否落在中心 8×8 种植区（决策 D10） */
    private static boolean isFarmArea(int row, int column) {
        return row >= FARM_AREA_ORIGIN && row < FARM_AREA_ORIGIN + FARM_AREA_SIZE
                && column >= FARM_AREA_ORIGIN && column < FARM_AREA_ORIGIN + FARM_AREA_SIZE;
    }

    @Override
    public FarmPlot getPlotType(int row, int column) {
        return plotTypes[row][column];
    }

    @Override
    public Soil getSoil(int row, int column) {
        return soils[row][column];
    }

    @Override
    public List<Soil> getSoils() {
        List<Soil> result = new ArrayList<>();
        for (int row = 0; row < MAP_SIZE; row++) {
            for (int column = 0; column < MAP_SIZE; column++) {
                if (soils[row][column] != null) {
                    result.add(soils[row][column]);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }
}
