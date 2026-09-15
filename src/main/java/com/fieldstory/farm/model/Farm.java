package com.fieldstory.farm.model;

import java.util.List;

/**
 * 农场地图模型接口（规则文档 §10.1；验收规范 §十一）。
 *
 * <p>12×12 共 144 格：中心 8×8（全局 0-based 行 2~9、列 2~9）为 FARM_PLOT，
 * 每格持有一个 Soil（初始 EMPTY）；外围 2 格宽统一为 DECORATION_AREA；
 * SHOP / SHOWCASE 在 P0 布局中不出现。
 */
public interface Farm {

    /** 查询任意格的地图格类型（全局 0-based 坐标） */
    FarmPlot getPlotType(int row, int column);

    /** 查询任意格的 Soil：非 FARM_PLOT 格返回 null（规则文档 §十一） */
    Soil getSoil(int row, int column);

    /** 遍历农场全部 Soil（共 64 块，对应中心 8×8 种植区） */
    List<Soil> getSoils();
}
