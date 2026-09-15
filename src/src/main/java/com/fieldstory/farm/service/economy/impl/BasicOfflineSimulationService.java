package com.fieldstory.farm.service.economy.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventState;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.OfflineDaySummary;
import com.fieldstory.farm.model.OfflineOccurrence;
import com.fieldstory.farm.model.OfflineSimulationResult;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.WeatherState;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.service.BuffService;
import com.fieldstory.farm.service.DailySimulationResult;
import com.fieldstory.farm.service.DaySettlementInput;
import com.fieldstory.farm.service.EventService;
import com.fieldstory.farm.service.GrowthRates;
import com.fieldstory.farm.service.GrowthService;
import com.fieldstory.farm.service.MemoryService;
import com.fieldstory.farm.service.OfflineSimulationService;
import com.fieldstory.farm.service.WeatherService;
import com.fieldstory.farm.service.WorldSimulationService;
import com.fieldstory.farm.service.WorldTimeService;
import com.fieldstory.farm.util.RandomProvider;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.fieldstory.farm.util.GameConstants.EVENT_RAINBOW_EVENT_RATE;

/**
 * B 模块 P2 离线模拟默认实现。
 *
 * <p>职责边界严格按 P2 跨模块约定：B 负责 72 分钟封顶、三类切点、循环驱动、
 * GrowthRates/石灯笼抗性/witherRolls 组装以及结构化离线摘要收集；A 的
 * {@link WorldSimulationService} 负责当前时间段成长与当日日结领域逻辑。
 *
 * <p>本类不实现第二套成长/天气/事件/枯萎算法，不执行主动浇水、主动施肥、购买、
 * 移动装饰、收获或出售，也不访问数据库。
 *
 * <p>时间口径采用决策 D14：世界小时 = gameDay*24+gameHour。离线模拟全部完成后
 * 才一次性推进 {@link GameClock}；D 已冻结新事件起止时间由 dayIndex / 日结世界时间
 * 推导，不依赖离线过程中尚未推进的 GameClock。
 */
public final class BasicOfflineSimulationService implements OfflineSimulationService {

    private static final long GAME_HOURS_PER_DAY = 24L;
    private static final long GAME_MINUTES_PER_HOUR = 60L;
    private static final double EPSILON = 1.0e-9;

    private final Farm farm;
    private final GameClock gameClock;
    private final WorldTimeService worldTimeService;
    private final WorldSimulationService worldSimulationService;
    private final GrowthService growthService;
    private final WeatherService weatherService;
    private final WeatherState weatherState;
    private final EventService eventService;
    private final EventState eventState;
    private final BuffService buffService;
    private final CropMemoryFactRecorder memoryFactRecorder;

    /**
     * 兼容构造：完成离线模拟与日志事实收集，但不写 CropMemory。
     * E/C 完成 MemoryService 装配后应优先使用带 memoryService 的完整构造。
     */
    public BasicOfflineSimulationService(
            Farm farm,
            GameClock gameClock,
            WorldTimeService worldTimeService,
            WorldSimulationService worldSimulationService,
            GrowthService growthService,
            WeatherService weatherService,
            WeatherState weatherState,
            EventService eventService,
            EventState eventState,
            BuffService buffService) {
        this(farm, gameClock, worldTimeService, worldSimulationService, growthService,
                weatherService, weatherState, eventService, eventState, buffService, null);
    }

    /**
     * 完整 P2 构造：除离线模拟外，将 A/D 已产出的天气、事件与成熟事实同步到 C 的 MemoryService。
     * 本类只记录事实，不重新计算 Memory 规则；最终数据库持久化仍由 E 负责。
     */
    public BasicOfflineSimulationService(
            Farm farm,
            GameClock gameClock,
            WorldTimeService worldTimeService,
            WorldSimulationService worldSimulationService,
            GrowthService growthService,
            WeatherService weatherService,
            WeatherState weatherState,
            EventService eventService,
            EventState eventState,
            BuffService buffService,
            MemoryService memoryService) {
        this.farm = Objects.requireNonNull(farm, "farm");
        this.gameClock = Objects.requireNonNull(gameClock, "gameClock");
        this.worldTimeService = Objects.requireNonNull(worldTimeService, "worldTimeService");
        this.worldSimulationService = Objects.requireNonNull(
                worldSimulationService, "worldSimulationService");
        this.growthService = Objects.requireNonNull(growthService, "growthService");
        this.weatherService = Objects.requireNonNull(weatherService, "weatherService");
        this.weatherState = Objects.requireNonNull(weatherState, "weatherState");
        this.eventService = Objects.requireNonNull(eventService, "eventService");
        this.eventState = Objects.requireNonNull(eventState, "eventState");
        this.buffService = Objects.requireNonNull(buffService, "buffService");
        this.memoryFactRecorder = memoryService == null
                ? null
                : new CropMemoryFactRecorder(memoryService);
    }

    @Override
    public OfflineSimulationResult simulate(long rawOfflineMinutes) {
        // WorldTimeService 是 P2 唯一离线时间规则源：负数/0 → 0，正数封顶 72。
        long normalizedRawMinutes = Math.max(0L, rawOfflineMinutes);
        long effectiveOfflineMinutes = worldTimeService.capOfflineRealMinutes(rawOfflineMinutes);

        if (effectiveOfflineMinutes <= 0L) {
            return new OfflineSimulationResult(
                    normalizedRawMinutes,
                    0L,
                    0L,
                    List.of());
        }

        long startWorldHour = worldTimeService.toWorldHour(
                gameClock.getGameDay(),
                gameClock.getGameHour());
        long endWorldHour = startWorldHour + effectiveOfflineMinutes;
        long currentWorldHour = startWorldHour;
        long currentGameDay = gameClock.getGameDay();

        Map<Long, DayAccumulator> dayAccumulators = new LinkedHashMap<>();

        while (currentWorldHour < endWorldHour) {
            if (memoryFactRecorder != null) {
                memoryFactRecorder.recordActiveEvent(farm, eventState);
            }
            GrowthRates baseRates = currentGrowthRates(currentWorldHour);
            Long eventEndWorldHour = currentEventEndWorldHour(currentWorldHour);
            List<Long> cropMatureWorldHours = projectedMaturityCutPoints(
                    currentWorldHour,
                    endWorldHour,
                    baseRates);

            List<Long> cutPoints = worldTimeService.segmentCutPoints(
                    currentWorldHour,
                    endWorldHour,
                    eventEndWorldHour,
                    cropMatureWorldHours);

            long nextWorldHour = nextCutPoint(cutPoints, currentWorldHour, endWorldHour);
            double segmentHours = nextWorldHour - currentWorldHour;

            Map<Crop, GrowthStage> stagesBeforeGrowth = snapshotStages();

            List<Crop> newlyMatured = worldSimulationService.growSegment(
                    farm,
                    segmentHours,
                    baseRates,
                    (row, column, cropType) ->
                            buffService.getGrowthRate(row, column, cropType));
            if (memoryFactRecorder != null) {
                memoryFactRecorder.recordMatured(newlyMatured, nextWorldHour);
            }

            DayAccumulator day = dayAccumulators.computeIfAbsent(
                    currentGameDay,
                    DayAccumulator::new);
            // 即使离线时间没有跨到 00:00，也要让返回日志知道这段时间所处天气/事件。
            // 这里只记录 D 已经给出的状态，不重新生成天气或事件。
            day.recordWeather(weatherState.getWeatherType() == null
                    ? WeatherType.SUNNY : weatherState.getWeatherType());
            if (eventService.isEventActive(currentWorldHour)) {
                day.recordEvent(eventState.getEventType());
            }
            day.recordGrowthTransitions(stagesBeforeGrowth, farm);
            day.recordMatured(newlyMatured);

            currentWorldHour = nextWorldHour;

            if (isDayBoundary(currentWorldHour)) {
                settleCurrentDay(currentGameDay, currentWorldHour, baseRates, day);

                // A settleDay 的第 11/13 步已经生成“下一游戏日”的天气与事件。
                // B 在这里立即把它们放进下一日摘要：持续事件后续 segment 会再次
                // recordEvent，但 DayAccumulator 使用 Set 去重；即时事件（ANIMAL_VISIT）
                // 不会被 isEventActive() 命中，因此必须在抽取发生的日界当场记录，
                // 否则离线日志会完全漏掉该特殊事件。
                long nextGameDay = currentGameDay + 1L;
                DayAccumulator nextDay = dayAccumulators.computeIfAbsent(
                        nextGameDay,
                        DayAccumulator::new);
                WeatherType nextWeather = weatherState.getWeatherType();
                if (nextWeather != null) {
                    nextDay.recordWeather(nextWeather);
                }
                EventType nextEvent = eventState.getEventType();
                if (nextEvent != null && nextEvent != EventType.NONE) {
                    nextDay.recordEvent(nextEvent);
                }

                currentGameDay = nextGameDay;
            }
        }

        advanceClockAfterSimulation(effectiveOfflineMinutes);

        List<OfflineDaySummary> summaries = dayAccumulators.values().stream()
                .filter(DayAccumulator::hasOccurrences)
                .map(DayAccumulator::toSummary)
                .toList();

        return new OfflineSimulationResult(
                normalizedRawMinutes,
                effectiveOfflineMinutes,
                effectiveOfflineMinutes,
                summaries);
    }

    /** 当前段成长倍率：WeatherRate + EventRate；DecorationRate 由 4 参 resolver 逐 Crop 覆盖。 */
    private GrowthRates currentGrowthRates(long currentWorldHour) {
        WeatherType weather = weatherState.getWeatherType();
        if (weather == null) {
            weather = WeatherType.SUNNY;
        }
        double weatherRate = weatherService.getGrowthRate(weather);

        EventType event = eventState.getEventType();
        boolean rainbowActive = eventService.isEventActive(currentWorldHour)
                && eventService.isRainbowDay(event);
        double eventRate = rainbowActive ? EVENT_RAINBOW_EVENT_RATE : 1.0;

        // 1.0 只是 3 率载体中的占位值；真正 DecorationRate 在 growSegment 4 参 resolver 中逐株覆盖。
        return new GrowthRates(weatherRate, 1.0, eventRate);
    }

    /** 只有正在持续中的非即时事件才提供事件结束切点。 */
    private Long currentEventEndWorldHour(long currentWorldHour) {
        if (!eventService.isEventActive(currentWorldHour)) {
            return null;
        }
        long end = eventState.getEndWorldTime();
        return end > currentWorldHour ? end : null;
    }

    /**
     * 使用 A 的 GrowthService 预测当前倍率下各作物最早成熟的整数世界小时。
     *
     * <p>不复制成长公式：直接用 calculateGrowthDelta(crop, 1 day, perCropRates) 得到
     * 当前条件下每日成长量，再按 long 世界小时口径向上取整到第一个不会早于真实成熟时刻的小时。
     * 到天气/事件边界后会重新计算，因此不会把旧倍率跨边界外推。
     */
    private List<Long> projectedMaturityCutPoints(
            long currentWorldHour,
            long endWorldHour,
            GrowthRates baseRates) {
        List<Long> matureHours = new ArrayList<>();

        for (Soil soil : farm.getSoils()) {
            if (!isPlanted(soil)) {
                continue;
            }

            Crop crop = soil.getCrop();
            if (crop.getGrowthStage() == GrowthStage.WITHERED
                    || crop.getGrowthStage() == GrowthStage.MATURE
                    || crop.getGrowthProgress() >= 100.0
                    || crop.getCropType() == null) {
                continue;
            }

            double decorationRate = buffService.getGrowthRate(
                    soil.getRow(),
                    soil.getColumn(),
                    crop.getCropType());
            GrowthRates perCropRates = new GrowthRates(
                    baseRates.weatherRate(),
                    decorationRate,
                    baseRates.eventRate());

            double dailyDelta = growthService.calculateGrowthDelta(crop, 1.0, perCropRates);
            if (!(dailyDelta > 0.0) || Double.isInfinite(dailyDelta)) {
                continue;
            }

            double remainingProgress = 100.0 - crop.getGrowthProgress();
            double exactHoursToMature = remainingProgress / dailyDelta * GAME_HOURS_PER_DAY;
            long wholeHoursToMature = (long) Math.ceil(exactHoursToMature - EPSILON);
            wholeHoursToMature = Math.max(1L, wholeHoursToMature);

            long matureWorldHour = currentWorldHour + wholeHoursToMature;
            if (matureWorldHour <= endWorldHour) {
                matureHours.add(matureWorldHour);
            }
        }
        return matureHours;
    }

    private void settleCurrentDay(
            long gameDay,
            long worldTimeAtSettle,
            GrowthRates rates,
            DayAccumulator accumulator) {
        Map<Crop, GrowthStage> stagesBeforeSettlement = snapshotStages();
        Map<CropType, Integer> plantedByTypeBeforeSettlement = plantedCountsByType();

        // D29：DailySimulationResult 记录“结算当日生效事件”。
        // 事件可能恰好在本日 00:00 日界结束；此时 isEventActive(worldTimeAtSettle)
        // 已经为 false，但它仍然是刚结束这个游戏日的当日事件。A 的 settleDay
        // 会在第 8 步 expireIfNeeded() 关闭它，因此这里必须在 settleDay 前读取
        // EventState 当前类型，而不能用日界时刻的 active 判定把它提前丢掉。
        EventType eventInEffect = eventState.getEventType();
        if (eventInEffect == null) {
            eventInEffect = EventType.NONE;
        }

        WeatherType weather = weatherState.getWeatherType();
        if (weather == null) {
            weather = WeatherType.SUNNY;
        }

        long eventStart = eventState.getStartWorldTime();
        long eventEnd = eventState.getEndWorldTime();

        DaySettlementInput input = new DaySettlementInput(
                gameDay,
                worldTimeAtSettle,
                weather,
                eventInEffect,
                eventStart,
                eventEnd,
                rates,
                1.0,
                createWitherRolls());

        DailySimulationResult result = worldSimulationService.settleDay(
                farm,
                input,
                buffService::getWitherProbabilityMultiplier);
        if (memoryFactRecorder != null) {
            memoryFactRecorder.recordDaily(farm, result);
        }

        accumulator.recordWeather(result.weather());
        accumulator.recordEvent(result.event());
        if (result.rainHydratedCount() > 0) {
            accumulator.recordAutoWater(plantedByTypeBeforeSettlement);
        }
        accumulator.recordWithered(stagesBeforeSettlement, farm);
    }

    /** 按 Farm.getSoils() / A plantedCrops 同一顺序准备枯萎随机值。 */
    private List<Double> createWitherRolls() {
        List<Double> rolls = new ArrayList<>();
        for (Soil soil : farm.getSoils()) {
            if (isPlanted(soil)) {
                rolls.add(RandomProvider.nextDouble());
            }
        }
        return rolls;
    }

    private Map<Crop, GrowthStage> snapshotStages() {
        Map<Crop, GrowthStage> stages = new IdentityHashMap<>();
        for (Soil soil : farm.getSoils()) {
            if (isPlanted(soil)) {
                stages.put(soil.getCrop(), soil.getCrop().getGrowthStage());
            }
        }
        return stages;
    }

    private Map<CropType, Integer> plantedCountsByType() {
        Map<CropType, Integer> counts = new EnumMap<>(CropType.class);
        for (Soil soil : farm.getSoils()) {
            if (!isPlanted(soil)) {
                continue;
            }
            CropType type = soil.getCrop().getCropType();
            if (type != null) {
                counts.merge(type, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static boolean isPlanted(Soil soil) {
        return soil != null
                && soil.getState() == SoilState.PLANTED
                && soil.getCrop() != null;
    }

    private static long nextCutPoint(
            List<Long> cutPoints,
            long currentWorldHour,
            long endWorldHour) {
        if (cutPoints != null) {
            for (Long point : cutPoints) {
                if (point != null && point > currentWorldHour) {
                    return Math.min(point, endWorldHour);
                }
            }
        }
        return endWorldHour;
    }

    private static boolean isDayBoundary(long worldHour) {
        return Math.floorMod(worldHour, GAME_HOURS_PER_DAY) == 0L;
    }

    /** D 的方案 A：整个离线循环完成后才一次性推进 GameClock。 */
    private void advanceClockAfterSimulation(long simulatedGameHours) {
        long targetTotalMinutes = (long) gameClock.getTotalMinutes()
                + simulatedGameHours * GAME_MINUTES_PER_HOUR;
        gameClock.setTotalMinutes(Math.toIntExact(targetTotalMinutes));
    }

    /** 单日结构化摘要聚合器；只聚合 A/D 已产出的事实，不计算领域规则。 */
    private static final class DayAccumulator {
        private final long gameDay;
        private WeatherType weather;
        private final Map<CropType, Integer> autoWater = new EnumMap<>(CropType.class);
        private final Map<CropType, Map<GrowthStage, Integer>> growth =
                new EnumMap<>(CropType.class);
        private final Map<CropType, Integer> matured = new EnumMap<>(CropType.class);
        private final Map<CropType, Integer> withered = new EnumMap<>(CropType.class);
        private final Set<EventType> events = new LinkedHashSet<>();

        private DayAccumulator(long gameDay) {
            this.gameDay = gameDay;
        }

        void recordWeather(WeatherType weather) {
            if (weather != null) {
                this.weather = weather;
            }
        }

        void recordEvent(EventType event) {
            if (event != null && event != EventType.NONE) {
                events.add(event);
            }
        }

        void recordAutoWater(Map<CropType, Integer> counts) {
            if (counts == null) {
                return;
            }
            counts.forEach((type, count) -> {
                if (type != null && count != null && count > 0) {
                    autoWater.merge(type, count, Integer::sum);
                }
            });
        }

        void recordMatured(List<Crop> crops) {
            if (crops == null) {
                return;
            }
            for (Crop crop : crops) {
                if (crop != null && crop.getCropType() != null) {
                    matured.merge(crop.getCropType(), 1, Integer::sum);
                }
            }
        }

        void recordGrowthTransitions(Map<Crop, GrowthStage> before, Farm farm) {
            if (before == null) {
                return;
            }
            for (Soil soil : farm.getSoils()) {
                if (!isPlanted(soil)) {
                    continue;
                }
                Crop crop = soil.getCrop();
                GrowthStage oldStage = before.get(crop);
                GrowthStage newStage = crop.getGrowthStage();
                if (oldStage == null || newStage == null || oldStage == newStage
                        || newStage == GrowthStage.MATURE
                        || newStage == GrowthStage.WITHERED
                        || crop.getCropType() == null) {
                    continue;
                }
                growth.computeIfAbsent(
                                crop.getCropType(),
                                ignored -> new EnumMap<>(GrowthStage.class))
                        .merge(newStage, 1, Integer::sum);
            }
        }

        void recordWithered(Map<Crop, GrowthStage> before, Farm farm) {
            if (before == null) {
                return;
            }
            for (Soil soil : farm.getSoils()) {
                if (!isPlanted(soil)) {
                    continue;
                }
                Crop crop = soil.getCrop();
                GrowthStage oldStage = before.get(crop);
                if (oldStage != GrowthStage.WITHERED
                        && crop.getGrowthStage() == GrowthStage.WITHERED
                        && crop.getCropType() != null) {
                    withered.merge(crop.getCropType(), 1, Integer::sum);
                }
            }
        }

        boolean hasOccurrences() {
            return weather != null
                    || !autoWater.isEmpty()
                    || !growth.isEmpty()
                    || !matured.isEmpty()
                    || !withered.isEmpty()
                    || !events.isEmpty();
        }

        OfflineDaySummary toSummary() {
            List<OfflineOccurrence> occurrences = new ArrayList<>();

            if (weather != null) {
                occurrences.add(new OfflineOccurrence.Weather(weather));
            }
            autoWater.forEach((type, count) ->
                    occurrences.add(new OfflineOccurrence.AutoWater(type, count)));
            growth.forEach((type, byStage) -> byStage.forEach((stage, count) ->
                    occurrences.add(new OfflineOccurrence.Growth(type, stage, count))));
            matured.forEach((type, count) ->
                    occurrences.add(new OfflineOccurrence.Mature(type, count)));
            withered.forEach((type, count) ->
                    occurrences.add(new OfflineOccurrence.Withered(type, count)));
            events.forEach(event -> occurrences.add(new OfflineOccurrence.Event(event)));

            return new OfflineDaySummary(gameDay, occurrences);
        }
    }
}
