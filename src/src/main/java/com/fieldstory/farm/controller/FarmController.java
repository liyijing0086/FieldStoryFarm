package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.FarmGameModel;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.service.DailySimulationResult;
import com.fieldstory.farm.service.DaySettlementInput;
import com.fieldstory.farm.service.DecorationRateResolver;
import com.fieldstory.farm.service.GrowthRates;
import com.fieldstory.farm.service.GrowthService;
import com.fieldstory.farm.service.WitherProbabilityMultiplierResolver;
import com.fieldstory.farm.service.WorldSimulationService;
import com.fieldstory.farm.util.RandomProvider;
import com.fieldstory.farm.view.StatusView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static com.fieldstory.farm.util.GameConstants.*;

/**
 * 农场主循环控制器。
 *
 * <p>正式在线推进路径统一为：
 * {@code FarmController -> WorldSimulationService}。
 * FarmController 只负责“什么时候推进、当前世界状态是什么”，不再在在线主循环
 * 自己实现成长/枯萎算法。
 *
 * <p>成长 Buff 通过 {@link DecorationRateResolver} 消费 B 已经汇总好的
 * Decoration + Set Growth Buff；枯萎通过
 * {@link WitherProbabilityMultiplierResolver} 消费 B 的
 * {@code witherProbabilityMultiplier}。A 不识别具体装饰或套装。
 *
 * <p>旧的 GrowthService 构造器与 {@link #advanceCrops} 仅保留给既有测试/早期装配兼容；
 * 正式装配应使用 WorldSimulationService 构造器。
 */
public class FarmController {

    private final FarmGameModel model;
    private final StatusView statusView;

    /** 旧 P0/P1 兼容入口；正式生产装配不再使用。 */
    private final GrowthService legacyGrowthService;

    /** 正式在线/离线共享的世界模拟引擎。 */
    private final WorldSimulationService worldSimulationService;

    /** B 提供：Decoration + Set 的最终成长倍率。 */
    private final DecorationRateResolver decorationRateResolver;

    /** B 提供：最终枯萎概率倍率。 */
    private final WitherProbabilityMultiplierResolver witherProbabilityMultiplierResolver;

    private final Timeline gameLoopTimeline;

    /** 跨天通知，仅用于 UI/保存等外部协作，不承载成长/枯萎业务。 */
    private Runnable onDayChanged = () -> { };

    /** 每次世界推进后的 UI 刷新出口。 */
    private Runnable onWorldAdvanced = () -> { };

    /** 本段新成熟作物事实出口：只暴露结果，不把 Memory/UI 逻辑塞回 A 世界引擎。 */
    private Consumer<List<Crop>> onCropsMatured = crops -> { };

    /** 每日日结摘要事实出口：供 Memory/日志/UI 薄桥接消费。 */
    private Consumer<DailySimulationResult> onDaySettled = result -> { };

    /** 上一次观察到的游戏日；构造时以当前 GameClock 游戏日为基线。 */
    private int lastGameDay;

    /** 兼容早期装配：只推进时间，不执行正式世界模拟。 */
    public FarmController(FarmGameModel model, StatusView statusView) {
        this(model, statusView, (GrowthService) null);
    }

    /** 兼容既有跨天回调构造。 */
    public FarmController(FarmGameModel model, StatusView statusView,
                          Runnable onDayChanged) {
        this(model, statusView, (GrowthService) null);
        setOnDayChanged(onDayChanged);
    }

    /**
     * 兼容旧 P0/P1 测试/装配。
     *
     * @deprecated 正式在线推进请使用
     * {@link #FarmController(FarmGameModel, StatusView, WorldSimulationService,
     * DecorationRateResolver, WitherProbabilityMultiplierResolver)}。
     */
    @Deprecated
    public FarmController(FarmGameModel model, StatusView statusView,
                          GrowthService growthService) {
        this.model = model;
        this.statusView = statusView;
        this.legacyGrowthService = growthService;
        this.worldSimulationService = null;
        this.decorationRateResolver = null;
        this.witherProbabilityMultiplierResolver = null;
        this.lastGameDay = model.getGameClock().getGameDay();
        this.gameLoopTimeline = initGameLoop();
    }

    /**
     * 正式在线世界装配构造器。
     *
     * @param model 游戏聚合模型（提供 Clock / Weather / Event / Farm）
     * @param statusView 状态栏
     * @param worldSimulationService A 的统一世界模拟引擎
     * @param decorationRateResolver B 的 Decoration + Set Growth Buff 解析器；可为 null（倍率 1.0）
     * @param witherProbabilityMultiplierResolver B 的枯萎倍率解析器；可为 null（倍率 1.0）
     */
    public FarmController(
            FarmGameModel model,
            StatusView statusView,
            WorldSimulationService worldSimulationService,
            DecorationRateResolver decorationRateResolver,
            WitherProbabilityMultiplierResolver witherProbabilityMultiplierResolver) {
        this.model = model;
        this.statusView = statusView;
        this.legacyGrowthService = null;
        this.worldSimulationService = Objects.requireNonNull(
                worldSimulationService, "正式世界模拟服务不能为空");
        this.decorationRateResolver = decorationRateResolver;
        this.witherProbabilityMultiplierResolver = witherProbabilityMultiplierResolver;
        this.lastGameDay = model.getGameClock().getGameDay();
        this.gameLoopTimeline = initGameLoop();
    }

    private Timeline initGameLoop() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(REAL_SECONDS_PER_TICK), e -> handleTick()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        return timeline;
    }

    /**
     * 单次在线 tick：记录推进前时间 → GameClock.tick() → 用同一个
     * WorldSimulationService 推进本段成长 → 若跨日则调用同一个 settleDay → 刷新 UI。
     */
    void handleTick() {
        int beforeTotalMinutes = model.getGameClock().getTotalMinutes();
        int beforeDay = model.getGameClock().getGameDay();
        int beforeHour = model.getGameClock().getGameHour();

        model.tick();

        int afterTotalMinutes = model.getGameClock().getTotalMinutes();
        int afterDay = model.getGameClock().getGameDay();

        if (worldSimulationService != null) {
            advanceWorld(beforeTotalMinutes, afterTotalMinutes, beforeDay, beforeHour, afterDay);
        } else {
            // 仅用于旧测试/早期装配兼容；正式生产代码不得走这条路径。
            advanceCrops();
        }

        if (lastGameDay < 0) {
            lastGameDay = afterDay;
        } else if (afterDay > lastGameDay) {
            lastGameDay = afterDay;
            onDayChanged.run();
        }

        onWorldAdvanced.run();
        statusView.update();
    }

    /** 正式在线推进：成长段与跨日日结全部委托 WorldSimulationService。 */
    private void advanceWorld(
            int beforeTotalMinutes,
            int afterTotalMinutes,
            int beforeDay,
            int beforeHour,
            int afterDay) {

        Farm farm = model.getFarm();
        if (farm == null || afterTotalMinutes <= beforeTotalMinutes) {
            return;
        }

        double gameHours = (afterTotalMinutes - beforeTotalMinutes) / 60.0;
        long segmentStartWorldHour = toWorldHour(beforeDay, beforeHour);
        GrowthRates rates = currentGrowthRates(segmentStartWorldHour);

        List<Crop> newlyMatured = worldSimulationService.growSegment(
                farm,
                gameHours,
                rates,
                decorationRateResolver);
        if (newlyMatured != null && !newlyMatured.isEmpty()) {
            onCropsMatured.accept(List.copyOf(newlyMatured));
        }

        // MINUTES_PER_TICK 当前远小于 1 游戏日；正常在线 tick 最多跨 1 个日界。
        if (afterDay > beforeDay) {
            WeatherType weather = currentWeather();
            EventType eventInEffect = currentEvent();
            long worldTimeAtSettle = toWorldHour(
                    afterDay,
                    model.getGameClock().getGameHour());

            long eventStart = model.getEventState() == null
                    ? -1L : model.getEventState().getStartWorldTime();
            long eventEnd = model.getEventState() == null
                    ? -1L : model.getEventState().getEndWorldTime();

            DaySettlementInput input = new DaySettlementInput(
                    beforeDay,
                    worldTimeAtSettle,
                    weather,
                    eventInEffect,
                    eventStart,
                    eventEnd,
                    rates,
                    1.0,
                    createWitherRolls(farm));

            DailySimulationResult settlement = worldSimulationService.settleDay(
                    farm,
                    input,
                    witherProbabilityMultiplierResolver);
            if (settlement != null) {
                onDaySettled.accept(settlement);
            }
        }
    }

    /** 当前段 WeatherRate / EventRate；DecorationRate 由逐 Crop resolver 提供。 */
    private GrowthRates currentGrowthRates(long currentWorldHour) {
        double weatherRate = WEATHER_RATE_P0;
        if (model.getWeatherService() != null && model.getWeatherState() != null
                && model.getWeatherState().getWeatherType() != null) {
            weatherRate = model.getWeatherService().getGrowthRate(
                    model.getWeatherState().getWeatherType());
        }

        double eventRate = 1.0;
        if (model.getEventService() != null && model.getEventState() != null
                && model.getEventState().getEventType() != null
                && model.getEventService().isEventActive(currentWorldHour)
                && model.getEventService().isRainbowDay(model.getEventState().getEventType())) {
            eventRate = EVENT_RAINBOW_EVENT_RATE;
        }

        // DecorationRate 的占位值为 1.0；growSegment 的 resolver 会逐株覆盖。
        return new GrowthRates(weatherRate, 1.0, eventRate);
    }

    private WeatherType currentWeather() {
        if (model.getWeatherState() == null || model.getWeatherState().getWeatherType() == null) {
            return WeatherType.SUNNY;
        }
        return model.getWeatherState().getWeatherType();
    }

    private EventType currentEvent() {
        if (model.getEventState() == null || model.getEventState().getEventType() == null) {
            return EventType.NONE;
        }
        return model.getEventState().getEventType();
    }

    /** 与既有 D14 / WorldTimeService 口径保持一致：gameDay * 24 + gameHour。 */
    private static long toWorldHour(int gameDay, int gameHour) {
        return (long) gameDay * 24L + gameHour;
    }

    /** 按 Farm.getSoils() 顺序准备枯萎随机值，顺序与 WorldSimulationService 日结一致。 */
    private static List<Double> createWitherRolls(Farm farm) {
        List<Double> rolls = new ArrayList<>();
        if (farm == null || farm.getSoils() == null) {
            return rolls;
        }
        for (Soil soil : farm.getSoils()) {
            if (soil != null
                    && soil.getState() == SoilState.PLANTED
                    && soil.getCrop() != null) {
                rolls.add(RandomProvider.nextDouble());
            }
        }
        return rolls;
    }

    /** 旧兼容成长路径；正式生产装配不使用。 */
    private void advanceCrops() {
        advanceCrops(model.getFarm(), legacyGrowthService, GAME_DAYS_PER_TICK,
                currentWeatherRate());
    }

    private double currentWeatherRate() {
        if (model.getWeatherService() == null || model.getWeatherState() == null) {
            return WEATHER_RATE_P0;
        }
        return model.getWeatherService().getGrowthRate(model.getWeatherState().getWeatherType());
    }

    /** 旧纯函数，保留既有测试兼容。 */
    static void advanceCrops(Farm farm, GrowthService growthService,
                             double elapsedGameDays) {
        advanceCrops(farm, growthService, elapsedGameDays, WEATHER_RATE_P0);
    }

    /** 旧纯函数，保留既有测试兼容；正式在线主循环不再调用。 */
    static void advanceCrops(Farm farm, GrowthService growthService,
                             double elapsedGameDays, double weatherRate) {
        if (farm == null || growthService == null) {
            return;
        }
        List<Soil> soils = farm.getSoils();
        if (soils == null) {
            return;
        }
        for (Soil soil : soils) {
            if (soil == null) {
                continue;
            }
            Crop crop = soil.getCrop();
            if (crop != null) {
                growthService.applyGrowth(crop, elapsedGameDays, weatherRate);
            }
        }
    }

    public void startGameLoop() {
        gameLoopTimeline.play();
    }

    public void stopGameLoop() {
        gameLoopTimeline.pause();
    }

    public void setOnDayChanged(Runnable onDayChanged) {
        if (onDayChanged != null) {
            this.onDayChanged = onDayChanged;
        }
    }

    /** 每次在线世界推进后的刷新出口；null 表示恢复为空操作。 */
    public void setOnWorldAdvanced(Runnable onWorldAdvanced) {
        this.onWorldAdvanced = onWorldAdvanced == null ? () -> { } : onWorldAdvanced;
    }

    /**
     * 注册“本段新成熟作物”事实回调。
     *
     * <p>这是生产主链的最小桥接点：A 只告诉外层“哪些作物刚成熟”，
     * C/E 再决定如何写 Memory、日志或刷新界面。null 恢复为空操作。
     */
    public void setOnCropsMatured(Consumer<List<Crop>> onCropsMatured) {
        this.onCropsMatured = onCropsMatured == null ? crops -> { } : onCropsMatured;
    }

    /**
     * 注册“日结完成”摘要回调。
     *
     * <p>回调只消费 {@link DailySimulationResult}，不反向参与成长/枯萎计算，
     * 因此不会重新制造第二套在线算法。null 恢复为空操作。
     */
    public void setOnDaySettled(Consumer<DailySimulationResult> onDaySettled) {
        this.onDaySettled = onDaySettled == null ? result -> { } : onDaySettled;
    }
}
