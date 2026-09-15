package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CropType;

/**
 * 逐 Crop 装饰倍率解析器（A 模块 P2 决策 D31）。
 *
 * <p>{@link WorldSimulationService#growSegment} 的 4 参重载经本接口逐株解析
 * DecorationRate（规则 §五十五：1 + AdjacentBonus + GlobalBonus + CropSpecificBonus
 * + SetBonus，上限 1.5）；实现由 B 模块提供（装饰是 B 的领域，A 只声明消费入口）。
 *
 * <p>B 侧典型实现：{@code (row, column, cropType) ->
 * buffService.getGrowthRate(row, column, cropType)}——B 的 BuffService 返回的正是
 * 规则 §五十五 的完整 DecorationRate（含 D01 邻格 + 全局 + 作物专属）。
 *
 * <p>解析结果为非法值（&lt;0 或 NaN）时由 {@link GrowthRates} 紧凑构造钳制为 0
 * （异常输入不破坏状态，取 0 只让该株成长暂停）。
 */
@FunctionalInterface
public interface DecorationRateResolver {

    /**
     * 返回该地块的完整 DecorationRate（规则 §五十五：1 + AdjacentBonus
     * + GlobalBonus + CropSpecificBonus + SetBonus）。
     *
     * @param row      地块全局行坐标（0-based，与 {@code Farm.getSoils()} 同口径）
     * @param column   地块全局列坐标（0-based）
     * @param cropType 该地块作物类型
     * @return 装饰成长倍率（非法值经 GrowthRates 钳制为 0）
     */
    double decorationRate(int row, int column, CropType cropType);
}
