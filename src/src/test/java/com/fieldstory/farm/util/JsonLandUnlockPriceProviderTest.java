package com.fieldstory.farm.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P3 balance-config 土地价格读取测试。 */
class JsonLandUnlockPriceProviderTest {

    @Test
    void classpathConfigProvidesOnlyConfiguredPlotPrice() {
        JsonLandUnlockPriceProvider provider = JsonLandUnlockPriceProvider.fromClasspath();

        assertEquals(100, provider.findUnlockPrice(2, 2).orElseThrow());
        assertTrue(provider.findUnlockPrice(2, 3).isEmpty(),
                "未配置格不得由代码擅自发明价格");
    }

    @Test
    void directMapProviderHasNoHiddenDefaultPrice() {
        JsonLandUnlockPriceProvider provider = new JsonLandUnlockPriceProvider(
                Map.of("5,6", 321));
        assertEquals(321, provider.findUnlockPrice(5, 6).orElseThrow());
        assertTrue(provider.findUnlockPrice(1, 1).isEmpty());
    }
}
