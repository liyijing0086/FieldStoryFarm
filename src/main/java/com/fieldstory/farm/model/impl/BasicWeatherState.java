package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.WeatherState;
import com.fieldstory.farm.model.WeatherType;

/**
 * {@link WeatherState} 的默认实现（D 模块 P1：世界环境 · 天气系统）。
 *
 * <p>依据《D模块 P1 接口与类设计文档》§4.3、决策 D13（接口在包根，实现类以 Basic 前缀放 impl 子包）。
 *
 * <p>与 {@code BasicGameClock} 对称：只保存状态，不含业务逻辑。
 */
public class BasicWeatherState implements WeatherState {

    /** 当前天气类型。 */
    private WeatherType weatherType;

    /** 当前天气所属游戏日索引（从 1 开始）。 */
    private int dayIndex;

    /**
     * 默认构造：{@code SUNNY}、{@code dayIndex = 1}
     * （P0 固定晴天语义延续，验收规范 §七十六）。
     */
    public BasicWeatherState() {
        this(WeatherType.SUNNY, 1);
    }

    /**
     * 指定天气与游戏日构造，用于存档恢复（验收规范 §七十三）。
     *
     * @param weatherType 天气类型
     * @param dayIndex    游戏日索引
     */
    public BasicWeatherState(WeatherType weatherType, int dayIndex) {
        this.weatherType = weatherType;
        this.dayIndex = dayIndex;
    }

    @Override
    public WeatherType getWeatherType() {
        return weatherType;
    }

    @Override
    public void setWeatherType(WeatherType weatherType) {
        this.weatherType = weatherType;
    }

    @Override
    public int getDayIndex() {
        return dayIndex;
    }

    @Override
    public void setDayIndex(int dayIndex) {
        this.dayIndex = dayIndex;
    }
}
