package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CropType;

/**
 * 逐 Crop 枯萎概率倍率解析器（A 消费 B Buff 的最小接口）。
 *
 * <p>实现通常由装配层把 B 的 BuffService 适配进来：
 * {@code (row, column, type) ->
 * buffService.getWitherProbabilityMultiplier(row, column, type)}。
 *
 * <p>A 只消费“最终枯萎概率倍率”，不识别石灯笼、不复制装饰规则。
 */
@FunctionalInterface
public interface WitherProbabilityMultiplierResolver {

    /**
     * 返回指定地块/作物当前应使用的最终枯萎概率倍率。
     * 无 Buff 时应返回 1.0；例如石灯笼生效时 B 返回 0.7。
     */
    double witherProbabilityMultiplier(int row, int column, CropType cropType);
}
