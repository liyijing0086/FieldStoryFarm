package com.fieldstory.farm.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P0 WeatherType 枚举测试（D 模块 P0 文档 §8.1）。
 *
 * <p>P0 阶段仅定义枚举常量、禁止业务引用（验收规范 §10），
 * 因此本测试只校验常量集与 {@code name()} 稳定性，防止后续阶段误改枚举名
 * 破坏存档字符串映射（E 模块以枚举 {@code name()} 存取）。
 */
class WeatherTypeTest {

    @Test
    void containsExactlyFourP0ReservedConstants() {
        assertEquals(4, WeatherType.values().length);
    }

    @Test
    void constantNamesAreStable() {
        // 名称即存档字符串（E 模块以 name() 存取），不得随意改动
        assertEquals("SUNNY", WeatherType.SUNNY.name());
        assertEquals("RAIN", WeatherType.RAIN.name());
        assertEquals("DROUGHT", WeatherType.DROUGHT.name());
        assertEquals("GREEN_RAIN", WeatherType.GREEN_RAIN.name());
    }

    @Test
    void valueOfRoundTrips() {
        for (WeatherType type : WeatherType.values()) {
            assertNotNull(type);
            assertEquals(type, WeatherType.valueOf(type.name()));
        }
    }
}
