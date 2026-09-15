package com.fieldstory.farm.service.economy.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.service.GrowthRates;
import com.fieldstory.farm.service.GrowthService;
import com.fieldstory.farm.service.OperationRateResolver;
import com.fieldstory.farm.service.WateringService;

/**
 * {@link GrowthService} 基础实现（A 模块设计文档 §8.3）。
 *
 * <p>P0 成长公式（验收规范 §二十四）：
 * {@code BaseDailyProgress × ElapsedGameDays × (1 + 浇水加成)}；
 * 天气/装饰/事件 Rate 在 P0 固定 1.0，公式中省略（仅注释占位，
 * 禁止在 P0 引入对应系统，验收规范 §二十四）。
 *
 * <p>P1 升级（A 模块设计文档 §6）：override 3 参重载，
 * 公式 {@code Base × Days × WeatherRate × OperationRate}（验收规范 §四十九）；
 * 2 参实现委托 3 参（rate=1.0），P0 行为不变；
 * {@code applyGrowth} 带 WITHERED 守卫（A 模块设计文档 §6.3）。
 *
 * <p>P2 升级（D 模块 P2 文档 §二；计划书 §5 六因子）：override 4 参重载，
 * 公式 {@code Base × Days × WeatherRate × DecorationRate × EventRate × OperationRate}；
 * 3 参实现委托 4 参（DecorationRate/EventRate 取 1.0），P0/P1 行为不变；
 * 4 参三率由 {@link GrowthRates} 构造时已钳制非法值（&lt;0/NaN → 0）。
 *
 * <p>本实现为纯函数服务，不依赖 GameClock、不使用系统时间：
 * {@code elapsedGameDays} 由调用方按"经过游戏小时 ÷ 24"折算传入
 * （验收规范 §二十五），支持非整日成长。
 *
 * <p>浇水加成经构造器注入的 {@link WateringService} 同层调用获取，
 * 避免公式重复（A 模块设计文档 §8.3）。
 */
public class BasicGrowthService implements GrowthService {

    /** 浇水服务：同层调用取浇水加成（A 模块设计文档 §8.3） */
    private final WateringService wateringService;

    /** 可选的完整 OperationRate 解析器；生产装配用于接入 B Buff + C 施肥。 */
    private final OperationRateResolver operationRateResolver;

    /**
     * 构造器注入浇水服务（A 模块设计文档 §8.3）。
     *
     * @param wateringService 浇水服务（提供浇水成长加成）
     */
    public BasicGrowthService(WateringService wateringService) {
        this(wateringService, null);
    }

    /**
     * 完整生产构造：A 不重算 B/C 规则，只消费装配层给出的 OperationRate。
     */
    public BasicGrowthService(WateringService wateringService,
                              OperationRateResolver operationRateResolver) {
        this.wateringService = wateringService;
        this.operationRateResolver = operationRateResolver;
    }

    @Override
    public double calculateGrowthDelta(Crop crop, double elapsedGameDays) {
        return calculateGrowthDelta(crop, elapsedGameDays, 1.0);
    }

    @Override
    public double calculateGrowthDelta(Crop crop, double elapsedGameDays,
            double weatherRate) {
        // 3 参 = 4 参(DecorationRate/EventRate=1.0)，维持 P1 行为与单公式实现点
        // （A 模块设计文档 §6 先例：低参委托高参）。
        return calculateGrowthDelta(crop, elapsedGameDays,
                new GrowthRates(weatherRate, 1.0, 1.0));
    }

    @Override
    public double calculateGrowthDelta(Crop crop, double elapsedGameDays,
            GrowthRates rates) {
        // 坏数据兜底：crop_type 无法识别时为 null（存档允许 crop_type=NULL），
        // 无法取每日基础进度，降级为 0（不成长）而非抛 NPE 中断跨天循环。
        if (crop.getCropType() == null) {
            return 0.0;
        }
        double base = crop.getCropType().getBaseDailyProgress();
        // P2 公式（计划书 §5 六因子；验收规范 §四十九；D 模块 P2 文档 §二）：
        // Base × Days × WeatherRate × DecorationRate × EventRate × OperationRate；
        // OperationRate = 1 + WaterBonus + FertilizerBonus。
        // 生产路径由装配层解析 B 的 watering/fertilizer multiplier 与 C 的施肥运行态；
        // 旧构造保持原 P1 浇水行为。
        double operationRate = operationRateResolver == null
                ? 1.0 + wateringService.calculateWaterGrowthBonus(crop)
                : operationRateResolver.operationRate(crop);
        if (operationRate < 0 || Double.isNaN(operationRate)) {
            operationRate = 0.0;
        }
        return base * elapsedGameDays * rates.weatherRate() * rates.decorationRate()
                * rates.eventRate() * operationRate;
    }

    @Override
    public void applyGrowth(Crop crop, double elapsedGameDays) {
        applyGrowth(crop, elapsedGameDays, 1.0);
    }

    @Override
    public void applyGrowth(Crop crop, double elapsedGameDays,
            double weatherRate) {
        // 3 参 = 4 参(DecorationRate/EventRate=1.0)，维持 P1 行为（守卫同源）
        applyGrowth(crop, elapsedGameDays, new GrowthRates(weatherRate, 1.0, 1.0));
    }

    @Override
    public void applyGrowth(Crop crop, double elapsedGameDays,
            GrowthRates rates) {
        // WITHERED 守卫：枯萎作物不再成长（A 模块设计文档 §6.3），
        // 防止 stageOf 把 WITHERED 重算回正常阶段；P2 延续至 4 参入口。
        if (crop.getGrowthStage() == GrowthStage.WITHERED) {
            return;
        }
        double newProgress = Math.min(100.0, crop.getGrowthProgress()
                + calculateGrowthDelta(crop, elapsedGameDays, rates));
        crop.setGrowthProgress(newProgress);
        crop.setGrowthStage(stageOf(newProgress));
    }

    /**
     * 按进度阈值映射成长阶段（验收规范 §二十二）：
     * ≥100 MATURE、≥50 GROWING、≥20 SPROUT、其余 SEED。
     *
     * @param progress 成长进度（内部口径 0~100）
     * @return 对应成长阶段
     */
    private GrowthStage stageOf(double progress) {
        if (progress >= 100.0) {
            return GrowthStage.MATURE;
        }
        if (progress >= 50.0) {
            return GrowthStage.GROWING;
        }
        if (progress >= 20.0) {
            return GrowthStage.SPROUT;
        }
        return GrowthStage.SEED;
    }
}
