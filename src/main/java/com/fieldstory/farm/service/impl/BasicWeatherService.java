package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.WeatherState;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.service.WeatherService;
import com.fieldstory.farm.util.RandomProvider;

import static com.fieldstory.farm.util.GameConstants.WEATHER_PROB_DROUGHT;
import static com.fieldstory.farm.util.GameConstants.WEATHER_PROB_RAIN;
import static com.fieldstory.farm.util.GameConstants.WEATHER_PROB_SUNNY;
import static com.fieldstory.farm.util.GameConstants.WEATHER_QUALITY_DROUGHT;
import static com.fieldstory.farm.util.GameConstants.WEATHER_QUALITY_DROUGHT_CAP;
import static com.fieldstory.farm.util.GameConstants.WEATHER_QUALITY_GREEN_RAIN;
import static com.fieldstory.farm.util.GameConstants.WEATHER_QUALITY_GREEN_RAIN_CAP;
import static com.fieldstory.farm.util.GameConstants.WEATHER_QUALITY_RAIN;
import static com.fieldstory.farm.util.GameConstants.WEATHER_QUALITY_RAIN_CAP;
import static com.fieldstory.farm.util.GameConstants.WEATHER_RATE_DROUGHT;
import static com.fieldstory.farm.util.GameConstants.WEATHER_RATE_GREEN_RAIN;
import static com.fieldstory.farm.util.GameConstants.WEATHER_RATE_RAIN;
import static com.fieldstory.farm.util.GameConstants.WEATHER_RATE_SUNNY;

/**
 * {@link WeatherService} 基础实现（D 模块 P1：世界环境 · 天气系统）。
 *
 * <p>依据《D模块 P1 接口与类设计文档》§4.5、《游戏规则与数值设计文档》§十九/§三十五/§九十。
 *
 * <p>概率抽取：使用 {@link RandomProvider#nextInt(int)} 得到 {@code [0, 100)} 整数，
 * 区间划分 {@code [0,40)} 晴、{@code [40,65)} 雨、{@code [65,85)} 旱、{@code [85,100)} 绿雨，
 * 区间宽度严格等于 40/25/20/15（合计 100）。随机必须经 {@code RandomProvider}，
 * 禁止 {@code new Random()}（规则文档 §九十）。
 *
 * <p>本实现只提供天气数据与倍率，不参与成长/品质计算（验收规范 §3.1）。
 */
public class BasicWeatherService implements WeatherService {

    /** 天气状态（持有，1 对 1）。 */
    private final WeatherState weatherState;

    /**
     * 构造器注入天气状态。
     *
     * @param weatherState 天气状态
     */
    public BasicWeatherService(WeatherState weatherState) {
        this.weatherState = weatherState;
    }

    @Override
    public WeatherType rollDailyWeather(int dayIndex) {
        int roll = RandomProvider.nextInt(100);
        WeatherType type;
        if (roll < WEATHER_PROB_SUNNY) {                       // [0, 40) → 40%
            type = WeatherType.SUNNY;
        } else if (roll < WEATHER_PROB_SUNNY + WEATHER_PROB_RAIN) {   // [40, 65) → 25%
            type = WeatherType.RAIN;
        } else if (roll < WEATHER_PROB_SUNNY + WEATHER_PROB_RAIN
                + WEATHER_PROB_DROUGHT) {                      // [65, 85) → 20%
            type = WeatherType.DROUGHT;
        } else {                                               // [85, 100) → 15%
            type = WeatherType.GREEN_RAIN;
        }
        weatherState.setWeatherType(type);
        weatherState.setDayIndex(dayIndex);
        return type;
    }

    @Override
    public double getGrowthRate(WeatherType weatherType) {
        switch (weatherType) {
            case RAIN:
                return WEATHER_RATE_RAIN;
            case DROUGHT:
                return WEATHER_RATE_DROUGHT;
            case GREEN_RAIN:
                return WEATHER_RATE_GREEN_RAIN;
            case SUNNY:
            default:
                return WEATHER_RATE_SUNNY;
        }
    }

    @Override
    public int getQualityScore(WeatherType weatherType) {
        switch (weatherType) {
            case RAIN:
                return WEATHER_QUALITY_RAIN;
            case DROUGHT:
                return WEATHER_QUALITY_DROUGHT;
            case GREEN_RAIN:
                return WEATHER_QUALITY_GREEN_RAIN;
            case SUNNY:
            default:
                return 0;
        }
    }

    @Override
    public int getQualityScoreCap(WeatherType weatherType) {
        switch (weatherType) {
            case RAIN:
                return WEATHER_QUALITY_RAIN_CAP;
            case DROUGHT:
                return WEATHER_QUALITY_DROUGHT_CAP;
            case GREEN_RAIN:
                return WEATHER_QUALITY_GREEN_RAIN_CAP;
            case SUNNY:
            default:
                return 0;
        }
    }

    @Override
    public boolean isRain(WeatherType weatherType) {
        return weatherType == WeatherType.RAIN;
    }

    @Override
    public boolean isDrought(WeatherType weatherType) {
        return weatherType == WeatherType.DROUGHT;
    }

    @Override
    public boolean isGreenRain(WeatherType weatherType) {
        return weatherType == WeatherType.GREEN_RAIN;
    }

    @Override
    public String getDisplayName(WeatherType weatherType) {
        if (weatherType == null) {
            return "未知";
        }
        switch (weatherType) {
            case RAIN:
                return "雨天";
            case DROUGHT:
                return "干旱";
            case GREEN_RAIN:
                return "绿雨";
            case SUNNY:
            default:
                return "晴天";
        }
    }

    @Override
    public String getIcon(WeatherType weatherType) {
        if (weatherType == null) {
            return "\u2753";
        }
        switch (weatherType) {
            case RAIN:
                return "\uD83C\uDF27";
            case DROUGHT:
                return "\uD83C\uDF21";
            case GREEN_RAIN:
                return "\uD83C\uDF3F";
            case SUNNY:
            default:
                return "\u2600";
        }
    }
}
