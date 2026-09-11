package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.service.GrowthService;
import com.fieldstory.farm.service.WateringService;

/**
 * {@link GrowthService} 基础实现（A 模块设计文档 §8.3）。
 *
 * <p>P0 成长公式（验收规范 §二十四）：
 * {@code BaseDailyProgress × ElapsedGameDays × (1 + 浇水加成)}；
 * 天气/装饰/事件 Rate 在 P0 固定 1.0，公式中省略（仅注释占位，
 * 禁止在 P0 引入对应系统，验收规范 §二十四）。
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

    /**
     * 构造器注入浇水服务（A 模块设计文档 §8.3）。
     *
     * @param wateringService 浇水服务（提供浇水成长加成）
     */
    public BasicGrowthService(WateringService wateringService) {
        this.wateringService = wateringService;
    }

    @Override
    public double calculateGrowthDelta(Crop crop, double elapsedGameDays) {
        // 坏数据兜底：crop_type 无法识别时为 null（存档允许 crop_type=NULL），
        // 无法取每日基础进度，降级为 0（不成长）而非抛 NPE 中断跨天循环。
        if (crop.getCropType() == null) {
            return 0.0;
        }
        double base = crop.getCropType().getBaseDailyProgress();
        // 天气/装饰/事件 Rate 在 P0 固定 1.0，公式中省略（验收规范 §二十四）
        double operationRate = 1.0 + wateringService.calculateWaterGrowthBonus(crop);
        return base * elapsedGameDays * operationRate;
    }

    @Override
    public void applyGrowth(Crop crop, double elapsedGameDays) {
        double newProgress = Math.min(100.0, crop.getGrowthProgress()
                + calculateGrowthDelta(crop, elapsedGameDays));
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
