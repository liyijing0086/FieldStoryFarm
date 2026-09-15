package com.fieldstory.farm.service;

/**
 * 枯萎判定结果码（A 模块 P1 设计文档 §5.6；验收规范 §五十三、§五十四）。
 *
 * <p>纯枚举值，不挂文案字段；文案由 Controller 层按结果码映射
 * （决策 D13：结果枚举随 Service 契约放 service 包根）。
 */
public enum WitherResult {

    /**
     * 无作物（防御分支）：crop 为 null，不参与枯萎判定
     * （A 模块 P1 设计文档 §5.5 判定流程①）。
     */
    NOT_PLANTED,

    /**
     * SEED 阶段豁免：种子不参与枯萎判定
     * （规则文档 §16.1；验收规范 §五十三）。
     */
    SEED_EXEMPT,

    /**
     * 已枯萎：stage == WITHERED，跳过重复判定
     * （验收规范 §五十三）。
     */
    ALREADY_WITHERED,

    /**
     * 无干旱风险：四条件②③④任一不满足
     * （当日非 DROUGHT / 已有效补水 / streak 未达风险区间）
     * （规则文档 §二十八；验收规范 §五十三）。
     */
    NO_DROUGHT_RISK,

    /**
     * 幸存：概率判定未触发，作物保持原阶段
     * （A 模块 P1 设计文档 §5.5 判定流程⑧）。
     */
    SURVIVED,

    /**
     * 判定触发：stage 置为 WITHERED，必须玩家主动铲除
     * （规则文档 §16.5；验收规范 §五十四）。
     */
    WITHERED
}
