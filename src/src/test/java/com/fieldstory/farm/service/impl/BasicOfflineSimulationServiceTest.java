package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.BuffSnapshot;
import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventState;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.OfflineSimulationResult;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.WeatherState;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicEventState;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.model.impl.BasicWeatherState;
import com.fieldstory.farm.model.impl.TestGameClock;
import com.fieldstory.farm.service.BuffService;
import com.fieldstory.farm.service.DailySimulationResult;
import com.fieldstory.farm.service.DaySettlementInput;
import com.fieldstory.farm.service.DecorationRateResolver;
import com.fieldstory.farm.service.EventService;
import com.fieldstory.farm.service.GrowthRates;
import com.fieldstory.farm.service.GrowthService;
import com.fieldstory.farm.service.WeatherService;
import com.fieldstory.farm.service.WorldSimulationService;
import com.fieldstory.farm.service.WitherProbabilityMultiplierResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B 模块 P2 离线循环、切段与跨模块委托测试。 */
class BasicOfflineSimulationServiceTest {

    private Farm farm;
    private TestGameClock clock;
    private WeatherState weatherState;
    private EventState eventState;
    private StubWeatherService weatherService;
    private StubEventService eventService;
    private RecordingWorldSimulationService worldSimulation;
    private StubGrowthService growthService;
    private BuffService buffService;

    @BeforeEach
    void setUp() {
        farm = new BasicFarm();
        clock = new TestGameClock(); // 第 1 天 06:00；D14 worldHour = 30
        weatherState = new BasicWeatherState(WeatherType.SUNNY, 1);
        eventState = new BasicEventState(EventType.NONE, 0L, 0L);
        weatherService = new StubWeatherService();
        eventService = new StubEventService(eventState);
        worldSimulation = new RecordingWorldSimulationService(clock, eventService);
        growthService = new StubGrowthService();
        buffService = (row, column, cropType) -> BuffSnapshot.neutral();
    }


    @Test
    void thirtyMinutesAdvanceThirtyGameHours() {
        BasicOfflineSimulationService service = service();

        OfflineSimulationResult result = service.simulate(30L);

        assertEquals(30L, result.effectiveOfflineMinutes());
        assertEquals(30L, result.simulatedGameHours());
        assertEquals(360 + 30 * 60, clock.getTotalMinutes(),
                "验收 §一百零七：离开30现实分钟应推进30游戏小时");
    }

    @Test
    void eightHoursAreCappedAt72AndClockAdvancesOnlyAfterLoop() {
        BasicOfflineSimulationService service = service();

        OfflineSimulationResult result = service.simulate(8L * 60L);

        assertEquals(480L, result.rawOfflineMinutes());
        assertEquals(72L, result.effectiveOfflineMinutes());
        assertEquals(72L, result.simulatedGameHours());
        assertTrue(result.wasCapped());

        // 第 1 天 06:00(world 30) +72h → world 102；日界 48/72/96。
        assertEquals(List.of(18.0, 24.0, 24.0, 6.0), worldSimulation.segmentHours);
        assertEquals(List.of(1L, 2L, 3L), worldSimulation.settledDays);

        // D 方案 A：全部 settleDay 执行时 GameClock 仍保持离线前值，最后统一推进。
        assertEquals(List.of(360, 360, 360), worldSimulation.clockMinutesAtSettlement);
        assertEquals(4, clock.getGameDay());
        assertEquals(6, clock.getGameHour());
        assertEquals(360 + 72 * 60, clock.getTotalMinutes());
    }

    @Test
    void eventEndIsAnIndependentSegmentCutPoint() {
        eventState.setEventType(EventType.RAINBOW_DAY);
        eventState.setStartWorldTime(30L);
        eventState.setEndWorldTime(36L);

        BasicOfflineSimulationService service = service();
        service.simulate(20L);

        // world 30 → event end 36 → day boundary 48 → end 50
        assertEquals(List.of(6.0, 12.0, 2.0), worldSimulation.segmentHours);
        assertEquals(2.0, worldSimulation.eventRates.get(0), 1e-9);
        assertEquals(1.0, worldSimulation.eventRates.get(1), 1e-9);
        assertEquals(1.0, worldSimulation.eventRates.get(2), 1e-9);
    }


    @Test
    void eventEndingExactlyAtDayBoundaryIsStillRecordedAsThatDaysEvent() {
        eventState.setEventType(EventType.RAINBOW_DAY);
        eventState.setStartWorldTime(24L);
        eventState.setEndWorldTime(48L);

        BasicOfflineSimulationService service = service();
        OfflineSimulationResult result = service.simulate(18L); // world 30 -> 48

        assertEquals(List.of(EventType.RAINBOW_DAY), worldSimulation.eventsAtSettlement,
                "D29: 日界结束的事件仍属于刚结算的当日事件，不能因 end-exclusive active 判定提前丢失");
        assertTrue(result.dailySummaries().stream()
                .flatMap(day -> day.occurrences().stream())
                .anyMatch(occurrence -> occurrence instanceof com.fieldstory.farm.model.OfflineOccurrence.Event e
                        && e.eventType() == EventType.RAINBOW_DAY));
    }


    @Test
    void instantEventRolledAtDayBoundaryIsNotLostFromOfflineLog() {
        eventState.setEventType(EventType.NONE);
        eventService.eventToRoll = EventType.ANIMAL_VISIT;

        BasicOfflineSimulationService service = service();
        OfflineSimulationResult result = service.simulate(18L); // world30 -> day2 00:00

        boolean logged = result.dailySummaries().stream()
                .filter(day -> day.gameDay() == 2L)
                .flatMap(day -> day.occurrences().stream())
                .anyMatch(occurrence -> occurrence instanceof com.fieldstory.farm.model.OfflineOccurrence.Event e
                        && e.eventType() == EventType.ANIMAL_VISIT);

        assertTrue(logged, "日界抽到的即时事件必须进入下一日离线日志，不能因 isInstant 而丢失");
    }

    @Test
    void perCropDecorationRateIsForwardedThroughResolver() {
        plant(CropType.WHEAT, 2, 2, GrowthStage.SPROUT, 30.0);
        buffService = (row, column, cropType) -> new BuffSnapshot(
                row == 2 && column == 2 && cropType == CropType.WHEAT ? 1.15 : 1.0,
                0, 1.0, 1.0, 1.0, 1.0);

        BasicOfflineSimulationService service = service();
        service.simulate(1L);

        assertEquals(List.of(1.15), worldSimulation.resolvedDecorationRates);
        assertTrue(worldSimulation.usedFourArgGrowSegment,
                "B 必须走 A 的 4 参 resolver 入口，不能把逐 Crop Buff 压成一个全局值");
    }


    @Test
    void cropMaturityCreatesItsOwnCutPoint() {
        Crop crop = plant(CropType.CORN, 2, 2, GrowthStage.GROWING, 90.0);
        growthService.dailyDelta = 48.0; // 还差 10；10/48*24 = 5 游戏小时
        worldSimulation.matureOnFirstGrow = crop;

        BasicOfflineSimulationService service = service();
        service.simulate(10L);

        assertEquals(List.of(5.0, 5.0), worldSimulation.segmentHours,
                "作物成熟时刻必须独立切段");
        assertEquals(GrowthStage.MATURE, crop.getGrowthStage());
    }

    @Test
    void witherMitigationIsResolvedPerCropAndRollCountComesFromBAssembly() {
        plant(CropType.WHEAT, 2, 2, GrowthStage.SPROUT, 30.0);
        plant(CropType.CORN, 2, 3, GrowthStage.SPROUT, 30.0);
        buffService = (row, column, cropType) -> new BuffSnapshot(
                1.0, 0, 1.0, cropType == CropType.WHEAT ? 0.70 : 1.0, 1.0, 1.0);

        BasicOfflineSimulationService service = service();
        service.simulate(18L); // 第 1 天 06:00 → 次日 00:00，触发一次日结

        assertTrue(worldSimulation.usedPerCropWitherResolver,
                "离线必须与在线共用逐 Crop wither resolver，不能压成全局最小倍率");
        assertEquals(List.of(0.70, 1.0), worldSimulation.resolvedWitherMultipliers);
        assertEquals(List.of(1.0), worldSimulation.witherMitigationRates,
                "DaySettlementInput 只保留兼容回退值；正式离线应消费逐 Crop resolver");
        assertEquals(List.of(2), worldSimulation.witherRollCounts,
                "witherRolls 必须按 Farm.getSoils() 中 PLANTED 作物数量准备");
    }

    @Test
    void matureAndDailyWeatherFactsAreRecordedIntoMemoryService() {
        Crop crop = plant(CropType.WHEAT, 2, 2, GrowthStage.GROWING, 90.0);
        growthService.dailyDelta = 48.0; // 5h 后成熟
        worldSimulation.matureOnFirstGrow = crop;
        weatherState.setWeatherType(WeatherType.RAIN);

        BasicMemoryService memoryService = new BasicMemoryService();
        BasicOfflineSimulationService service = new BasicOfflineSimulationService(
                farm, clock, new BasicWorldTimeService(), worldSimulation, growthService,
                weatherService, weatherState, eventService, eventState, buffService, memoryService);

        service.simulate(18L);

        var memory = memoryService.findMemory(crop.getCropUuid()).orElseThrow();
        assertEquals(35L, memory.getMatureWorldTime(),
                "第1日06:00(world30) + 5h 应记录 world35 成熟");
        assertEquals(1, memory.getRainCount(), "日结 RAIN 应记录一次生命雨天经历");
    }

    @Test
    void zeroOrNegativeDurationDoesNotTouchWorldOrClock() {
        BasicOfflineSimulationService service = service();

        OfflineSimulationResult zero = service.simulate(0L);
        OfflineSimulationResult negative = service.simulate(-5L);

        assertFalse(zero.hasOfflineProgress());
        assertFalse(negative.hasOfflineProgress());
        assertEquals(0L, negative.rawOfflineMinutes(), "异常负时长按 WorldTimeService 口径归零");
        assertTrue(worldSimulation.segmentHours.isEmpty());
        assertEquals(360, clock.getTotalMinutes());
    }

    private BasicOfflineSimulationService service() {
        return new BasicOfflineSimulationService(
                farm,
                clock,
                new BasicWorldTimeService(),
                worldSimulation,
                growthService,
                weatherService,
                weatherState,
                eventService,
                eventState,
                buffService);
    }

    private Crop plant(CropType type, int row, int column,
            GrowthStage stage, double progress) {
        Soil soil = farm.getSoil(row, column);
        soil.setState(SoilState.PLANTED);
        BasicCrop crop = new BasicCrop();
        crop.setCropUuid(UUID.randomUUID());
        crop.setCropType(type);
        crop.setGrowthStage(stage);
        crop.setGrowthProgress(progress);
        soil.setCrop(crop);
        return crop;
    }

    private static final class RecordingWorldSimulationService
            implements WorldSimulationService {
        private final TestGameClock clock;
        private final EventService eventService;
        private final List<Double> segmentHours = new ArrayList<>();
        private final List<Double> eventRates = new ArrayList<>();
        private final List<Double> resolvedDecorationRates = new ArrayList<>();
        private final List<Long> settledDays = new ArrayList<>();
        private final List<Integer> clockMinutesAtSettlement = new ArrayList<>();
        private final List<Double> witherMitigationRates = new ArrayList<>();
        private final List<Double> resolvedWitherMultipliers = new ArrayList<>();
        private final List<Integer> witherRollCounts = new ArrayList<>();
        private final List<EventType> eventsAtSettlement = new ArrayList<>();
        private boolean usedFourArgGrowSegment;
        private boolean usedPerCropWitherResolver;
        private Crop matureOnFirstGrow;

        private RecordingWorldSimulationService(TestGameClock clock, EventService eventService) {
            this.clock = clock;
            this.eventService = eventService;
        }

        @Override
        public List<Crop> growSegment(Farm farm, double gameHours, GrowthRates rates) {
            throw new AssertionError("B P2 不应退回 3 参 growSegment");
        }

        @Override
        public List<Crop> growSegment(Farm farm, double gameHours, GrowthRates rates,
                DecorationRateResolver decorationResolver) {
            usedFourArgGrowSegment = true;
            segmentHours.add(gameHours);
            eventRates.add(rates.eventRate());
            for (Soil soil : farm.getSoils()) {
                if (soil.getState() == SoilState.PLANTED && soil.getCrop() != null
                        && soil.getCrop().getCropType() != null) {
                    resolvedDecorationRates.add(decorationResolver.decorationRate(
                            soil.getRow(), soil.getColumn(), soil.getCrop().getCropType()));
                }
            }
            if (matureOnFirstGrow != null) {
                Crop crop = matureOnFirstGrow;
                matureOnFirstGrow = null;
                crop.setGrowthProgress(100.0);
                crop.setGrowthStage(GrowthStage.MATURE);
                return List.of(crop);
            }
            return List.of();
        }

        @Override
        public DailySimulationResult settleDay(Farm farm, DaySettlementInput input) {
            return settleDay(farm, input, null);
        }

        @Override
        public DailySimulationResult settleDay(
                Farm farm,
                DaySettlementInput input,
                WitherProbabilityMultiplierResolver witherResolver) {
            usedPerCropWitherResolver = witherResolver != null;
            settledDays.add(input.gameDay());
            eventsAtSettlement.add(input.eventInEffect());
            clockMinutesAtSettlement.add(clock.getTotalMinutes());
            witherMitigationRates.add(input.witherMitigationRate());
            witherRollCounts.add(input.witherRolls().size());
            int planted = 0;
            for (Soil soil : farm.getSoils()) {
                if (soil.getState() == SoilState.PLANTED && soil.getCrop() != null) {
                    planted++;
                    if (witherResolver != null && soil.getCrop().getCropType() != null) {
                        resolvedWitherMultipliers.add(witherResolver.witherProbabilityMultiplier(
                                soil.getRow(), soil.getColumn(), soil.getCrop().getCropType()));
                    }
                }
            }
            DailySimulationResult result = new DailySimulationResult(
                    input.gameDay(), input.weather(), input.eventInEffect(),
                    0, 0, input.weather() == WeatherType.RAIN ? planted : 0);

            // 忠实模拟 A 的 settleDay 第 8 / 13 步：先关闭到期事件，再抽取下一日事件。
            // B 的即时事件日志测试依赖 settleDay 返回后 EventState 已经写入次日事件。
            eventService.expireIfNeeded(input.worldTimeAtSettle());
            eventService.rollDailyEvent((int) (input.gameDay() + 1));
            return result;
        }
    }

    private static final class StubGrowthService implements GrowthService {
        private double dailyDelta;

        @Override
        public double calculateGrowthDelta(Crop crop, double elapsedGameDays) {
            return dailyDelta * elapsedGameDays;
        }

        @Override
        public void applyGrowth(Crop crop, double elapsedGameDays) {
            // no-op
        }

        @Override
        public double calculateGrowthDelta(Crop crop, double elapsedGameDays,
                GrowthRates rates) {
            return dailyDelta * elapsedGameDays;
        }
    }

    private static final class StubWeatherService implements WeatherService {
        @Override
        public WeatherType rollDailyWeather(int dayIndex) {
            return WeatherType.SUNNY;
        }

        @Override
        public double getGrowthRate(WeatherType weatherType) {
            return 1.0;
        }

        @Override
        public int getQualityScore(WeatherType weatherType) {
            return 0;
        }

        @Override
        public int getQualityScoreCap(WeatherType weatherType) {
            return 0;
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
            return String.valueOf(weatherType);
        }

        @Override
        public String getIcon(WeatherType weatherType) {
            return "";
        }
    }

    private static final class StubEventService implements EventService {
        private final EventState state;
        private EventType eventToRoll;

        private StubEventService(EventState state) {
            this.state = state;
        }

        @Override
        public EventType rollDailyEvent(int dayIndex) {
            EventType rolled = eventToRoll == null ? EventType.NONE : eventToRoll;
            eventToRoll = null;
            state.setEventType(rolled);

            if (rolled == EventType.NONE) {
                state.setStartWorldTime(0L);
                state.setEndWorldTime(0L);
                return rolled;
            }

            long start = (long) dayIndex * 24L;
            state.setStartWorldTime(start);
            state.setEndWorldTime(rolled.isInstant() ? start : start + rolled.getDurationHours());
            return rolled;
        }

        @Override
        public boolean isEventActive(long currentWorldTime) {
            EventType type = state.getEventType();
            return type != null
                    && type != EventType.NONE
                    && !type.isInstant()
                    && currentWorldTime >= state.getStartWorldTime()
                    && currentWorldTime < state.getEndWorldTime();
        }

        @Override
        public void expireIfNeeded(long currentWorldTime) {
            EventType type = state.getEventType();
            if (type != null
                    && type != EventType.NONE
                    && !type.isInstant()
                    && currentWorldTime >= state.getEndWorldTime()) {
                state.setEventType(EventType.NONE);
                state.setStartWorldTime(0L);
                state.setEndWorldTime(0L);
            }
        }

        @Override
        public String getDisplayName(EventType type) {
            return String.valueOf(type);
        }

        @Override
        public String getIcon(EventType type) {
            return "";
        }

        @Override
        public boolean isMeteorShower(EventType type) {
            return type == EventType.METEOR_SHOWER;
        }

        @Override
        public boolean isMysteryMerchant(EventType type) {
            return type == EventType.MYSTERY_MERCHANT;
        }

        @Override
        public boolean isRainbowDay(EventType type) {
            return type == EventType.RAINBOW_DAY;
        }
    }
}
