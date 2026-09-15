package com.fieldstory.farm.model;

/**
 * 地图格类型（规则文档 §十一；验收规范 §十二）。
 *
 * <p>P0 布局中只出现 FARM_PLOT 与 DECORATION_AREA：
 * 中心 8×8 种植区为 FARM_PLOT（唯一持有 Soil 的格子），
 * 外围 2 格宽区域统一为 DECORATION_AREA 占位；
 * SHOP / SHOWCASE 为后阶段（P1 商店、P3 展示台）预留占位，P0 布局不出现。
 */
public enum FarmPlot {

    /** 种植格：仅此类格持有 Soil（规则文档 §十一） */
    FARM_PLOT,

    /** 装饰区：P0 占位不开放装饰，P1 放置装饰（验收规范 §十一） */
    DECORATION_AREA,

    /** 商店格：占位，P1/P3 确定具体位置 */
    SHOP,

    /** 展示台格：占位，P1/P3 确定具体位置 */
    SHOWCASE
}
