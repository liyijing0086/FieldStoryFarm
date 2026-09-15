package com.fieldstory.farm.service.economy.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.service.DailySimulationResult;
import com.fieldstory.farm.service.DaySettlementInput;
import com.fieldstory.farm.service.DecorationRateResolver;
import com.fieldstory.farm.service.EventService;
import com.fieldstory.farm.service.GrowthRates;
import com.fieldstory.farm.service.GrowthService;
import com.fieldstory.farm.service.WeatherService;
import com.fieldstory.farm.service.WitherProbabilityMultiplierResolver;
import com.fieldstory.farm.service.WitherResult;
import com.fieldstory.farm.service.WitherService;
import com.fieldstory.farm.service.WorldSimulationService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@link WorldSimulationService} 基础实现（A 模块 P2：持续世界引擎；
 * 验收规范 §八十九 每日离线 14 步，规则文档 §八十一）。
 *
 * <p>在线每日结算与离线模拟共用本实现（验收 §八十九：禁止 OnlineDailyService 与
 * OfflineSimulationService 两套算法）；B 模块 OfflineSimulationService 负责按分段调用
 * {@link #growSegment} 并在日末切点调用 {@link #settleDay}。
 *
 * <p>与时间约束（决策 D18/D19 精神延续）：本实现不调用 RandomProvider、
 * 不读取系统时间、不依赖 GameClock——枯萎掷骰值由调用方经
 * {@code DaySettlementInput.witherRolls} 按农场遍历顺序传入（取尽视为 1.0，
 * 异常输入不破坏状态）；天气/事件抽取由注入的 D 模块 {@link WeatherService}/
 * {@link EventService} 内部完成。
 *
 * <p>只产数据（决策 D24）：不收获、不出售、不动金币（验收 §八十六/§八十七），
 * 不碰数据库与任何 DAO；摘要经 {@link DailySimulationResult} 返回，落库与循环
 * 驱动由调用方（B 模块 OfflineSimulationService）负责。
 */
public class BasicWorldSimulationService implements WorldSimulationService {

    /** 成长服务：分段成长入口（构造器注入同层服务，BasicGrowthService 注入 WateringService 先例） */
    private final GrowthService growthService;

    /** 枯萎服务：当日天气记录与枯萎判定（构造器注入同层服务） */
    private final WitherService witherService;

    /** 天气服务：日末掷出新天气（构造器注入，D 模块交付） */
    private final WeatherService weatherService;

    /** 事件服务：关闭过期事件与日末抽取新事件（构造器注入，D 模块交付） */
    private final EventService eventService;

    /**
     * 构造器注入四个同层服务（BasicGrowthService 注入 WateringService 的先例）。
     *
     * @param growthService  成长服务
     * @param witherService  枯萎服务
     * @param weatherService 天气服务（D 模块）
     * @param eventService   事件服务（D 模块）
     */
    public BasicWorldSimulationService(GrowthService growthService,
            WitherService witherService,
            WeatherService weatherService,
            EventService eventService) {
        this.growthService = growthService;
        this.witherService = witherService;
        this.weatherService = weatherService;
        this.eventService = eventService;
    }

    @Override
    public List<Crop> growSegment(Farm farm, double gameHours, GrowthRates rates) {
        // 3 参 = 4 参(decorationResolver=null)：全部作物统一用 rates.decorationRate()
        // （P0/P1 兼容与既有单测行为不变，决策 D31）
        return growSegment(farm, gameHours, rates, null);
    }

    @Override
    public List<Crop> growSegment(Farm farm, double gameHours, GrowthRates rates,
            DecorationRateResolver decorationResolver) {
        // 验收 §八十八：离线时间必须按游戏日边界/事件结束时间/作物成熟时间切段，
        // 本方法只处理当前时间段的成长，不判枯萎、不换天气
        if (gameHours <= 0) {
            return List.of();
        }
        // 坏数据兜底：rates 为 null 时降级 P0 单位倍率（GrowthRates 已内建非法值钳制）
        GrowthRates effectiveRates = rates == null ? GrowthRates.P0 : rates;
        double elapsedGameDays = gameHours / 24.0;   // 验收 §二十五：支持非整日成长
        List<Crop> newlyMatured = new ArrayList<>();
        for (Soil soil : farm.getSoils()) {
            if (soil.getState() != SoilState.PLANTED || soil.getCrop() == null) {
                continue;
            }
            Crop crop = soil.getCrop();
            // WITHERED 跳过不成长（A P1 设计 §6.3；GrowthService 已内建守卫，此处显式跳过）
            if (crop.getGrowthStage() == GrowthStage.WITHERED) {
                continue;
            }
            // 决策 D31：逐株解析装饰倍率（实现由 B 的 BuffService 提供）；
            // resolver 为 null 回退三件套统一值；非法值经 GrowthRates 钳制为 0
            double decorationRate = decorationResolver == null
                    ? effectiveRates.decorationRate()
                    : decorationResolver.decorationRate(
                            soil.getRow(), soil.getColumn(), crop.getCropType());
            GrowthRates perCropRates = new GrowthRates(effectiveRates.weatherRate(),
                    decorationRate, effectiveRates.eventRate());
            boolean wasMature = crop.getGrowthProgress() >= 100.0;
            growthService.applyGrowth(crop, elapsedGameDays, perCropRates);
            // 收集本段新成熟（进度跨过 100）的作物；段前已成熟的不在本段重复上报
            if (!wasMature && crop.getGrowthProgress() >= 100.0) {
                newlyMatured.add(crop);
            }
        }
        return newlyMatured;
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
        List<Soil> plantedSoils = plantedSoils(farm);
        List<Crop> planted = new ArrayList<>();
        for (Soil soil : plantedSoils) {
            planted.add(soil.getCrop());
        }

        // §八十九 第 1~3 步：成长已由 growSegment 完成；这里只汇总成熟状态。
        int maturedCount = 0;
        for (Crop crop : planted) {
            if (crop.getGrowthProgress() >= 100.0) {
                maturedCount++;
            }
        }

        // §八十九 第 5~6 步：记录当日天气、补水与 droughtStreak。
        for (Crop crop : planted) {
            witherService.recordDailyWeather(crop, input.weather(), input.gameDay(),
                    input.worldTimeAtSettle());
        }
        int rainHydratedCount = input.weather() == WeatherType.RAIN ? planted.size() : 0;

        // §八十九 第 7 步：枯萎判定。
        // A 不识别石灯笼或其他装饰，只消费 B 已算好的 witherProbabilityMultiplier。
        int witheredCount = 0;
        List<UUID> witherRiskCropUuids = new ArrayList<>();
        for (int i = 0; i < plantedSoils.size(); i++) {
            Soil soil = plantedSoils.get(i);
            Crop crop = soil.getCrop();
            double roll = nextRoll(input.witherRolls(), i);
            double witherMultiplier = resolveWitherMultiplier(
                    soil, crop, input.witherMitigationRate(), witherResolver);
            WitherResult result = witherService.judgeWither(
                    crop,
                    input.weather(),
                    input.gameDay(),
                    witherMultiplier,
                    roll);
            if ((result == WitherResult.SURVIVED || result == WitherResult.WITHERED)
                    && crop.getCropUuid() != null) {
                witherRiskCropUuids.add(crop.getCropUuid());
            }
            if (result == WitherResult.WITHERED) {
                witheredCount++;
            }
        }

        // §八十九 第 8 步：关闭过期事件。
        eventService.expireIfNeeded(input.worldTimeAtSettle());

        // §八十九 第 11 步：生成下一日天气。
        WeatherType newWeather = weatherService.rollDailyWeather((int) (input.gameDay() + 1));

        // §八十九 第 12 步：下一日为雨时，在 00:00 只写补水时刻；计数留到该雨日日结。
        if (newWeather == WeatherType.RAIN) {
            for (Crop crop : planted) {
                crop.setLastHydratedWorldTime(input.worldTimeAtSettle());
            }
        }

        // §八十九 第 13 步：抽取下一日事件。
        eventService.rollDailyEvent((int) (input.gameDay() + 1));

        return new DailySimulationResult(
                input.gameDay(),
                input.weather(),
                input.eventInEffect(),
                maturedCount,
                witheredCount,
                rainHydratedCount,
                input.eventStartWorldTime(),
                input.eventEndWorldTime(),
                witherRiskCropUuids);
    }

    /**
     * 逐株解析枯萎概率倍率；resolver 缺失时保持旧版统一倍率语义。
     * 非法结果按项目既有倍率防御约定钳制为 0，避免 NaN 污染概率。
     */
    private static double resolveWitherMultiplier(
            Soil soil,
            Crop crop,
            double fallbackRate,
            WitherProbabilityMultiplierResolver resolver) {
        if (resolver == null) {
            return fallbackRate;
        }
        double rate = resolver.witherProbabilityMultiplier(
                soil.getRow(),
                soil.getColumn(),
                crop.getCropType());
        return (rate < 0.0 || Double.isNaN(rate)) ? 0.0 : rate;
    }

    /**
     * 按 {@link Farm#getSoils()} 遍历顺序收集全部 PLANTED 作物
     * （SoilState == PLANTED 且 crop != null，防御存档恢复异常）。
     * 该顺序即 witherRolls 的消费顺序。
     */
    private List<Soil> plantedSoils(Farm farm) {
        List<Soil> soils = new ArrayList<>();
        for (Soil soil : farm.getSoils()) {
            if (soil != null
                    && soil.getState() == SoilState.PLANTED
                    && soil.getCrop() != null) {
                soils.add(soil);
            }
        }
        return soils;
    }

    /**
     * 取第 index 个枯萎掷骰值：列表为 null 或取尽（index ≥ size）视为 1.0
     * （必不枯萎，异常输入不破坏状态）；null 元素同视为 1.0。
     */
    private static double nextRoll(List<Double> rolls, int index) {
        if (rolls == null || index >= rolls.size()) {
            return 1.0;
        }
        Double roll = rolls.get(index);
        return roll == null ? 1.0 : roll;
    }
}
