package com.fieldstory.farm.service;

/**
 * 施肥结果码（C 模块 品质与传说域，P1 施肥系统，验收规范 §六十三）。
 *
 * <p>规则文档 §二十六：施肥允许阶段 SPROUT/GROWING，每株每天最多 1 次，
 * 生命周期最多 3 次，每次消耗 1 肥料（成长 +15%、品质评分 +8）。
 */
public enum FertilizeResult {

    /** 施肥成功（已扣库存、已记录施肥次数与施肥日） */
    SUCCESS,

    /** 作物当前生长阶段不允许施肥（仅 SPROUT/GROWING 可施） */
    NOT_ALLOWED_STAGE,

    /** 当天已施过肥（每株每天最多 1 次） */
    ALREADY_FERTILIZED_TODAY,

    /** 生命周期施肥次数已达上限（3 次） */
    MAX_TIMES_PER_LIFE,

    /** 背包肥料不足（每次消耗 1 肥料） */
    NOT_ENOUGH_FERTILIZER,

    /** 作物或记忆档案缺失，无法施肥 */
    NO_CROP_OR_MEMORY
}
