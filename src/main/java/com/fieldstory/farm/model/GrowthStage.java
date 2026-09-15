package com.fieldstory.farm.model;

/**
 * 作物成长阶段（规则文档 §十五、§十六；验收规范 §二十一、§二十二）。
 *
 * <p>进度区间（growthProgress 内部口径 0~100，验收规范 §二十二）：
 * SEED 0~20%、SPROUT 20~50%、GROWING 50~100%、MATURE ≥100%。
 *
 * <p>WITHERED 为 P1 枯萎系统预留占位，P0 不启用、不产生（验收规范 §二十一）。
 */
public enum GrowthStage {

    /** 种子阶段：0% ≤ progress &lt; 20%；不可浇水、不可收获（规则文档 §16.1） */
    SEED,

    /** 幼苗阶段：20% ≤ progress &lt; 50%；可主动浇水、可施肥（规则文档 §16.2） */
    SPROUT,

    /** 成长阶段：50% ≤ progress &lt; 100%（规则文档 §16.3） */
    GROWING,

    /** 成熟阶段：progress ≥ 100%；可收获（规则文档 §16.4） */
    MATURE,

    /** 枯萎：占位，P1 枯萎系统启用（规则文档 §16.5） */
    WITHERED
}
