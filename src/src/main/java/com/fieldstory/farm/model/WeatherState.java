package com.fieldstory.farm.model;

/**
 * 天气状态模型（D 模块 P1：世界环境 · 天气系统）。
 *
 * <p>依据《D模块 P1 接口与类设计文档》§4.2、《游戏规则与数值设计文档》§十九/§八十一。
 *
 * <p>职责：只保存「现在是什么天气」与「该天气所属游戏日索引」，不含任何天气生成、
 * 概率计算逻辑（统一 Model 原则，验收规范 §4）。
 *
 * <p>存档映射：对应 E 模块 {@code world_state.current_weather}（枚举 {@code name()}）
 * 与 {@code world_state.current_day_index}（验收规范 §七十三）。
 */
public interface WeatherState {

    /**
     * 获取当前天气类型（规则文档 §十九）。
     *
     * @return 当前天气类型
     */
    WeatherType getWeatherType();

    /**
     * 设置当前天气类型。
     *
     * @param weatherType 天气类型
     */
    void setWeatherType(WeatherType weatherType);

    /**
     * 获取当前天气所属游戏日索引（从 1 开始，规则文档 §八十一）。
     *
     * @return 游戏日索引
     */
    int getDayIndex();

    /**
     * 设置当前天气所属游戏日索引。
     *
     * @param dayIndex 游戏日索引
     */
    void setDayIndex(int dayIndex);
}
