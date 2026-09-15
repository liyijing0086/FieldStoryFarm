package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.model.impl.BasicWeatherState;
import com.fieldstory.farm.util.RandomProvider;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 WeatherService 测试（D 模块 P1 文档 §6.1）。
 *
 * <p>覆盖：固定种子可复现、概率分布、成长倍率、品质分与上限、天气判定、写入 WeatherState。
 */
class WeatherServiceTest {

    /** 概率分布抽样次数（规则文档 §十九）。 */
    private static final int SAMPLE_SIZE = 10000;

    /** 概率容差（±3%，D 模块 P1 文档 §6.1）。 */
    private static final double TOLERANCE = 0.03;

    private BasicWeatherService newService() {
        return new BasicWeatherService(new BasicWeatherState());
    }

    @Test
    void fixedSeedProducesReproducibleSequence() {
        RandomProvider.setSeed(20240601L);
        BasicWeatherService first = newService();
        WeatherType[] firstSeq = new WeatherType[20];
        for (int i = 0; i < firstSeq.length; i++) {
            firstSeq[i] = first.rollDailyWeather(i + 1);
        }

        RandomProvider.setSeed(20240601L);
        BasicWeatherService second = newService();
        for (int i = 0; i < firstSeq.length; i++) {
            assertEquals(firstSeq[i], second.rollDailyWeather(i + 1),
                    "相同种子应产生相同天气序列（规则文档 §九十）");
        }
    }

    @Test
    void probabilityDistributionMatchesRules() {
        RandomProvider.setSeed(987654321L);
        BasicWeatherService service = newService();
        Map<WeatherType, Integer> counts = new EnumMap<>(WeatherType.class);
        for (WeatherType type : WeatherType.values()) {
            counts.put(type, 0);
        }
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            WeatherType type = service.rollDailyWeather(i + 1);
            counts.put(type, counts.get(type) + 1);
        }

        assertProbability(counts, WeatherType.SUNNY, 0.40);
        assertProbability(counts, WeatherType.RAIN, 0.25);
        assertProbability(counts, WeatherType.DROUGHT, 0.20);
        assertProbability(counts, WeatherType.GREEN_RAIN, 0.15);
    }

    private void assertProbability(Map<WeatherType, Integer> counts, WeatherType type, double expected) {
        double actual = counts.get(type) / (double) SAMPLE_SIZE;
        assertTrue(Math.abs(actual - expected) <= TOLERANCE,
                type + " 概率应约 " + expected + "，实际 " + actual);
    }

    @Test
    void growthRateMatchesRules() {
        BasicWeatherService service = newService();
        assertEquals(1.0, service.getGrowthRate(WeatherType.SUNNY), 1e-9);
        assertEquals(1.5, service.getGrowthRate(WeatherType.RAIN), 1e-9);
        assertEquals(0.5, service.getGrowthRate(WeatherType.DROUGHT), 1e-9);
        assertEquals(2.0, service.getGrowthRate(WeatherType.GREEN_RAIN), 1e-9);
    }

    @Test
    void qualityScoreMatchesRules() {
        BasicWeatherService service = newService();
        assertEquals(0, service.getQualityScore(WeatherType.SUNNY));
        assertEquals(5, service.getQualityScore(WeatherType.RAIN));
        assertEquals(8, service.getQualityScore(WeatherType.DROUGHT));
        assertEquals(15, service.getQualityScore(WeatherType.GREEN_RAIN));
    }

    @Test
    void qualityScoreCapMatchesRules() {
        BasicWeatherService service = newService();
        assertEquals(0, service.getQualityScoreCap(WeatherType.SUNNY));
        assertEquals(20, service.getQualityScoreCap(WeatherType.RAIN));
        assertEquals(24, service.getQualityScoreCap(WeatherType.DROUGHT));
        assertEquals(45, service.getQualityScoreCap(WeatherType.GREEN_RAIN));
    }

    @Test
    void weatherPredicatesMatchOnlyTheirType() {
        BasicWeatherService service = newService();
        for (WeatherType type : WeatherType.values()) {
            assertEquals(type == WeatherType.RAIN, service.isRain(type), "isRain: " + type);
            assertEquals(type == WeatherType.DROUGHT, service.isDrought(type), "isDrought: " + type);
            assertEquals(type == WeatherType.GREEN_RAIN, service.isGreenRain(type), "isGreenRain: " + type);
        }
    }

    @Test
    void rollDailyWeatherWritesWeatherState() {
        BasicWeatherState state = new BasicWeatherState();
        BasicWeatherService service = new BasicWeatherService(state);
        RandomProvider.setSeed(555L);
        WeatherType rolled = service.rollDailyWeather(9);
        assertEquals(rolled, state.getWeatherType(), "rollDailyWeather 应写入 WeatherState");
        assertEquals(9, state.getDayIndex(), "rollDailyWeather 应写入游戏日索引");
    }

    @Test
    void displayNameAndIconAreNonNull() {
        BasicWeatherService service = newService();
        for (WeatherType type : WeatherType.values()) {
            assertNotNull(service.getDisplayName(type));
            assertNotNull(service.getIcon(type));
            assertFalse(service.getDisplayName(type).isEmpty());
            assertFalse(service.getIcon(type).isEmpty());
        }
    }
}
