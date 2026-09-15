package com.fieldstory.farm.model.economy;

/** B 模块 P1 装饰购买结果。失败时不得改变金币或装饰库存。 */
public enum DecorationPurchaseResult {
    SUCCESS,
    INSUFFICIENT_GOLD,
    INVALID_QUANTITY
}
