package com.fieldstory.farm.service;

/**
 * 浇水结果码（A 模块设计文档 §8.5）。
 *
 * <p>纯枚举值，不挂文案字段；文案由 Controller 层按结果码映射
 * （与 PlantingResult、ReclaimResult 同一约定，设计文档 D13）。
 */
public enum WateringResult {

    /**
     * 浇水成功：manualWaterCount+1（≤5）并记录当日游戏日
     * （验收规范 §二十七、§二十八；决策 D11 第 5 次仍有效）。
     */
    SUCCESS,

    /**
     * 作物处于 SEED 阶段，不可主动浇水（验收规范 §二十六）。
     */
    SEED_STAGE,

    /**
     * 当日已浇过水：同一游戏日不重复计数、不重复加成
     * （验收规范 §二十七）。
     */
    ALREADY_WATERED_TODAY,

    /**
     * 浇水次数已达上限：manualWaterCount ≥ 5，第 6 次起拒绝，
     * 加成维持 +20% 封顶（验收规范 §二十八；决策 D11）。
     */
    WATER_LIMIT_REACHED
}
