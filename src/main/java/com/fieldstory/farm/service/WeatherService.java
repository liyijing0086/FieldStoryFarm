package com.fieldstory.farm.service;

import com.fieldstory.farm.model.WeatherType;

/**
 * 天气服务接口（D 模块 P1：世界环境 · 天气系统）。
 *
 * <p>依据《D模块 P1 接口与类设计文档》§4.4、《P0-P4功能实现与验收规范》§一百五十。
 *
 * <p>职责：生成每日天气、提供天气成长倍率与品质分、提供天气判定与显示信息。
 * 本接口只提供天气数据与倍率，不参与成长/品质计算（统一 Model / Service 归属原则，
 * 验收规范 §3.1、§一百五十）。
 *
 * <p>随机统一走 {@code RandomProvider}（规则文档 §九十），禁止 {@code new Random()}。
 */
public interface WeatherService {

    /**
     * 生成指定游戏日的天气（每天 00:00 调用一次，规则文档 §十九/§八十一）。
     *
     * <p>概率：晴 40%、雨 25%、旱 20%、绿雨 15%（规则文档 §十九）。
     * 生成结果写入内部 {@code WeatherState}。
     *
     * @param dayIndex 游戏日索引（从 1 开始）
     * @return 生成的天气类型
     */
    WeatherType rollDailyWeather(int dayIndex);

    /**
     * 天气成长倍率（验收规范 §四十九）。
     *
     * @param weatherType 天气类型
     * @return 成长倍率（晴 1.0 / 雨 1.5 / 旱 0.5 / 绿雨 2.0）
     */
    double getGrowthRate(WeatherType weatherType);

    /**
     * 天气品质分/次（规则文档 §三十五）。
     *
     * @param weatherType 天气类型
     * @return 单次品质分（晴 0 / 雨 5 / 旱 8 / 绿雨 15）
     */
    int getQualityScore(WeatherType weatherType);

    /**
     * 天气品质分上限（规则文档 §三十五）。
     *
     * @param weatherType 天气类型
     * @return 品质分上限（晴 0 / 雨 20 / 旱 24 / 绿雨 45）
     */
    int getQualityScoreCap(WeatherType weatherType);

    /**
     * 是否雨天（供 A 模块自动补水判定，规则文档 §二十一）。
     *
     * @param weatherType 天气类型
     * @return 雨天返回 true
     */
    boolean isRain(WeatherType weatherType);

    /**
     * 是否干旱（供 A 模块 droughtStreak 判定，规则文档 §二十二）。
     *
     * @param weatherType 天气类型
     * @return 干旱返回 true
     */
    boolean isDrought(WeatherType weatherType);

    /**
     * 是否绿雨（供 C 模块传说突破加成，规则文档 §二十三）。
     *
     * @param weatherType 天气类型
     * @return 绿雨返回 true
     */
    boolean isGreenRain(WeatherType weatherType);

    /**
     * 天气显示名（UI，验收规范 §七十六）。
     *
     * @param weatherType 天气类型
     * @return 中文显示名
     */
    String getDisplayName(WeatherType weatherType);

    /**
     * 天气图标（UI，验收规范 §七十六）。
     *
     * @param weatherType 天气类型
     * @return 图标字符串
     */
    String getIcon(WeatherType weatherType);
}
