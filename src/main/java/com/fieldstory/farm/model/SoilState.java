package com.fieldstory.farm.model;

/**
 * 土地状态（规则文档 §十二；验收规范 §十四）。
 *
 * <p>P0 状态机只走 EMPTY → TILLED → PLANTED →（收获后）TILLED；
 * LOCKED 为 P3 土地扩张预留占位，P0 不启用、不产生（验收规范 §十四）。
 */
public enum SoilState {

    /** 普通空地：可开垦，不可直接播种（规则文档 §12.1） */
    EMPTY,

    /** 已开垦：可播种，不可浇水/施肥/收获（规则文档 §12.2） */
    TILLED,

    /** 已播种：存在 Crop；浇水/收获是否允许由 Crop 的 GrowthStage 决定（规则文档 §12.3） */
    PLANTED,

    /** 未解锁土地：占位，P3 土地扩张启用（规则文档 §12.4） */
    LOCKED
}
