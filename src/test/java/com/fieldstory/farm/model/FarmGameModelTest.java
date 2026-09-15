package com.fieldstory.farm.model;

import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.util.RandomProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * P0 FarmGameModel 测试（D 模块 P0 文档 §三、团队裁决 ①/②；P1 天气聚合）。
 *
 * <p>覆盖：时钟聚合、tick 委托、存档恢复、土地字段类型为 A 的 {@link Farm} 接口、
 * 不持有 Player，以及 P1 天气系统聚合（{@code getWeatherService} / {@code getWeatherState}）。
 */
class FarmGameModelTest {

    @Test
    void defaultConstructorAggregatesBasicGameClock() {
        FarmGameModel model = new FarmGameModel();
        assertNotNull(model.getGameClock());
        assertEquals(360, model.getWorldTimeTotalMinutes());
        assertEquals(1, model.getGameClock().getGameDay());
    }

    @Test
    void tickDelegatesToGameClock() {
        FarmGameModel model = new FarmGameModel();
        model.tick();
        assertEquals(361, model.getWorldTimeTotalMinutes(),
                "正式时钟每 tick 只推进 1 游戏分钟");
    }

    @Test
    void restoreWorldTimeSetsClock() {
        FarmGameModel model = new FarmGameModel();
        model.restoreWorldTime(1800);
        assertEquals(1800, model.getWorldTimeTotalMinutes());
        assertEquals(2, model.getGameClock().getGameDay());
    }

    @Test
    void injectedClockIsUsed() {
        GameClock clock = new BasicGameClock(720);
        FarmGameModel model = new FarmGameModel(clock);
        assertSame(clock, model.getGameClock());
        assertEquals(720, model.getWorldTimeTotalMinutes());
    }

    @Test
    void farmFieldIsTypedAsFarmInterfaceAndDefaultsToNull() {
        FarmGameModel model = new FarmGameModel();
        assertNull(model.getFarm(), "未装配时土地字段应为 null");
        Farm farm = new StubFarm();
        model.setFarm(farm);
        assertSame(farm, model.getFarm());
    }

    @Test
    void weatherServiceAndStateAreAggregatedAndNonNull() {
        FarmGameModel model = new FarmGameModel();
        assertNotNull(model.getWeatherService(), "P1 应聚合 WeatherService");
        assertNotNull(model.getWeatherState(), "P1 应聚合 WeatherState");
    }

    @Test
    void defaultWeatherIsSunnyDayOne() {
        FarmGameModel model = new FarmGameModel();
        assertEquals(WeatherType.SUNNY, model.getWeatherState().getWeatherType(),
                "默认天气应为晴天（验收规范 §七十六）");
        assertEquals(1, model.getWeatherState().getDayIndex());
    }

    @Test
    void weatherServiceWritesToAggregatedState() {
        FarmGameModel model = new FarmGameModel();
        RandomProvider.setSeed(20240601L);
        WeatherType rolled = model.getWeatherService().rollDailyWeather(5);
        assertSame(model.getWeatherState().getWeatherType(), rolled,
                "rollDailyWeather 应写入聚合的 WeatherState");
        assertEquals(5, model.getWeatherState().getDayIndex());
    }

    @Test
    void injectedClockConstructorStillAggregatesWeather() {
        FarmGameModel model = new FarmGameModel(new BasicGameClock(720));
        assertNotNull(model.getWeatherService());
        assertNotNull(model.getWeatherState());
        assertEquals(WeatherType.SUNNY, model.getWeatherState().getWeatherType());
    }

    @Test
    void restoreWeatherSetsTypeAndDayIndex() {
        FarmGameModel model = new FarmGameModel();
        model.restoreWeather("DROUGHT", 9);
        assertEquals(WeatherType.DROUGHT, model.getWeatherState().getWeatherType(),
                "restoreWeather 应还原天气枚举（验收 §七十三）");
        assertEquals(9, model.getWeatherState().getDayIndex(),
                "restoreWeather 应还原天气日索引（验收 §七十三）");
    }

    @Test
    void restoreWeatherWithNullKeepsDefaultSunny() {
        FarmGameModel model = new FarmGameModel();
        model.restoreWeather((String) null, 3);
        assertEquals(WeatherType.SUNNY, model.getWeatherState().getWeatherType(),
                "无天气记录时应保持默认晴天（D 模块 P1 文档 §4.1）");
        assertEquals(3, model.getWeatherState().getDayIndex());
    }

    @Test
    void restoreWeatherWithUnknownNameDegradesToSunny() {
        FarmGameModel model = new FarmGameModel();
        model.restoreWeather("NOT_A_WEATHER", 4);
        assertEquals(WeatherType.SUNNY, model.getWeatherState().getWeatherType(),
                "无法识别的天气名应降级为晴天，不抛异常（坏数据不阻断读档）");
        assertEquals(4, model.getWeatherState().getDayIndex());
    }

    @Test
    void eventServiceAndStateAreAggregatedAndNonNull() {
        FarmGameModel model = new FarmGameModel();
        assertNotNull(model.getEventService(), "P2 应聚合 EventService");
        assertNotNull(model.getEventState(), "P2 应聚合 EventState");
    }

    @Test
    void defaultEventIsNone() {
        FarmGameModel model = new FarmGameModel();
        assertEquals(EventType.NONE, model.getEventState().getEventType(),
                "默认事件应为无事件（规则文档 §四十七）");
    }

    @Test
    void eventServiceWritesToAggregatedState() {
        FarmGameModel model = new FarmGameModel();
        RandomProvider.setSeed(20240601L);
        EventType rolled = model.getEventService().rollDailyEvent(5);
        assertSame(model.getEventState().getEventType(), rolled,
                "rollDailyEvent 应写入聚合的 EventState");
    }

    @Test
    void injectedClockConstructorStillAggregatesEvent() {
        FarmGameModel model = new FarmGameModel(new BasicGameClock(720));
        assertNotNull(model.getEventService());
        assertNotNull(model.getEventState());
        assertEquals(EventType.NONE, model.getEventState().getEventType());
    }

    /** 最小 Farm 桩，仅用于验证字段类型为 A 的 Farm 接口（裁决 ①）。 */
    private static final class StubFarm implements Farm {
        @Override
        public FarmPlot getPlotType(int row, int column) {
            return FarmPlot.FARM_PLOT;
        }

        @Override
        public Soil getSoil(int row, int column) {
            return null;
        }

        @Override
        public List<Soil> getSoils() {
            return List.of();
        }
    }
}
