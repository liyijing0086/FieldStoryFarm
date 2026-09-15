package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Crop;

/**
 * A 成长链对 B/C 操作加成的只读消费口。
 *
 * <p>V4.0 §二十七：OperationRate = 1 + WaterBonus + FertilizerBonus。
 * 其中玫瑰花坛只放大主动浇水的成长 bonus，小喷泉只放大施肥成长 bonus；
 * 品质固定分 +3/+8 不被放大。具体 B Buff 与 C 施肥记忆如何组合由装配层提供，
 * A 的 GrowthService 只消费最终 OperationRate，不复制 B/C 规则。
 */
@FunctionalInterface
public interface OperationRateResolver {

    /**
     * @return 完整 OperationRate；中性值为 1.0
     */
    double operationRate(Crop crop);
}
