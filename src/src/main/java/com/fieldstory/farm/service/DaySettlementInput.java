package com.fieldstory.farm.service;

import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.WeatherType;

import java.util.List;

/**
 * 一次日结的全部入参（A 模块 P2 持续世界引擎；验收规范 §八十九）。
 *
 * <p>第三轮补充事件起止时间，使在线/离线都能把同一份“当日事件事实”交给
 * CropMemory；尤其流星夜必须只影响活动窗口内新种植的作物，不能只靠事件类型猜测。
 */
public record DaySettlementInput(long gameDay, long worldTimeAtSettle,
                                 WeatherType weather, EventType eventInEffect,
                                 long eventStartWorldTime, long eventEndWorldTime,
                                 GrowthRates rates,
                                 double witherMitigationRate,
                                 List<Double> witherRolls) {

    /** 兼容既有调用：没有事件窗口信息时使用 -1 哨兵。 */
    public DaySettlementInput(long gameDay, long worldTimeAtSettle,
                              WeatherType weather, EventType eventInEffect,
                              GrowthRates rates,
                              double witherMitigationRate,
                              List<Double> witherRolls) {
        this(gameDay, worldTimeAtSettle, weather, eventInEffect,
                -1L, -1L, rates, witherMitigationRate, witherRolls);
    }

    /** 紧凑构造：非法抗性倍率钳制为 0；掷骰列表防御性拷贝。 */
    public DaySettlementInput {
        witherMitigationRate = clamp(witherMitigationRate);
        witherRolls = witherRolls == null ? List.of() : List.copyOf(witherRolls);
    }

    private static double clamp(double rate) {
        return (rate < 0 || Double.isNaN(rate)) ? 0.0 : rate;
    }
}
