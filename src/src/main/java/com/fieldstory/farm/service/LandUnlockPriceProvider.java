package com.fieldstory.farm.service;

import java.util.OptionalInt;

/**
 * B 模块 P3 土地解锁价格读取边界。
 *
 * <p>正式价格必须来自 balance-config，不允许写死在 Controller、Soil 或
 * LandUnlockService 中。当前规则文档尚未冻结各格具体价格，因此 B 只定义消费接口；
 * E/配置层负责提供正式实现，测试可使用 fake provider。
 */
@FunctionalInterface
public interface LandUnlockPriceProvider {

    /**
     * 查询指定全局地图坐标的解锁价格。
     *
     * @param row    全局 0-based 行坐标
     * @param column 全局 0-based 列坐标
     * @return 有配置时返回价格；无配置时返回 OptionalInt.empty()
     */
    OptionalInt findUnlockPrice(int row, int column);
}
