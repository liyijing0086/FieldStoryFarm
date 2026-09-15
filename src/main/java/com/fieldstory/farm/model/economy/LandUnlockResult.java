package com.fieldstory.farm.model.economy;

/**
 * B 模块 P3 土地解锁结果。
 *
 * <p>只表达一次 LOCKED → EMPTY 尝试的结果，不包含 UI 文案。
 */
public enum LandUnlockResult {

    /** 解锁成功：已扣除配置价格，土地已变为 EMPTY。 */
    SUCCESS,

    /** 目标不是 LOCKED 土地（包括 null）。 */
    NOT_LOCKED,

    /** 当前格没有可用的解锁价格配置。 */
    PRICE_NOT_CONFIGURED,

    /** 金币不足。 */
    NO_GOLD
}
