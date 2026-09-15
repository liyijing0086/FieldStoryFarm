package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.WeatherType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P1 BasicWeatherState 测试（D 模块 P1 文档 §6.2）。
 *
 * <p>覆盖：无参构造默认值、双参构造、setter/getter 往返。
 */
class BasicWeatherStateTest {

    @Test
    void defaultConstructorIsSunnyDayOne() {
        BasicWeatherState state = new BasicWeatherState();
        assertEquals(WeatherType.SUNNY, state.getWeatherType());
        assertEquals(1, state.getDayIndex());
    }

    @Test
    void parameterizedConstructorAssignsFields() {
        BasicWeatherState state = new BasicWeatherState(WeatherType.GREEN_RAIN, 7);
        assertEquals(WeatherType.GREEN_RAIN, state.getWeatherType());
        assertEquals(7, state.getDayIndex());
    }

    @Test
    void settersRoundTrip() {
        BasicWeatherState state = new BasicWeatherState();
        state.setWeatherType(WeatherType.DROUGHT);
        state.setDayIndex(12);
        assertEquals(WeatherType.DROUGHT, state.getWeatherType());
        assertEquals(12, state.getDayIndex());
    }
}
