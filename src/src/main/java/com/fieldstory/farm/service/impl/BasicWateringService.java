package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.service.WateringResult;
import com.fieldstory.farm.service.WateringService;

/**
 * {@link WateringService} 基础实现（A 模块设计文档 §8.4，时间类型按决策 D14）。
 *
 * <p>三重校验（验收规范 §二十六、§二十七、§二十八）：
 * ① 阶段 ∈ {SPROUT, GROWING, MATURE}（SEED 不可主动浇水）；
 * ② 当日未浇（每个游戏日最多 1 次有效主动浇水）；
 * ③ manualWaterCount &lt; 5（单株最多记录 5 次）。
 *
 * <p>时间类型按 D14：currentGameDay 为 long（游戏日），
 * 与 {@link Crop#getLastManualWaterGameDay()} 口径一致；
 * "当日已浇"判断为 {@code ==}；初始哨兵 -1 表示从未浇水
 * （-1 != 0，第 0 游戏日可正常浇水）。
 *
 * <p>决策 D11 行为约定：第 5 次浇水有效，第 6 次起 canWater 直接返回 false，
 * 加成维持 +20% 封顶。
 *
 * <p>本实现无外部依赖，不依赖 GameClock、不使用系统时间：
 * 当前游戏日由调用方传入。
 */
public class BasicWateringService implements WateringService {

    /** 单株最多记录 5 次主动浇水（验收规范 §二十八；决策 D11） */
    public static final int MAX_MANUAL_WATER_COUNT = 5;

    /** 每次主动浇水成长加成 +5%（规则文档 §二十七） */
    public static final double WATER_BONUS_PER_TIME = 0.05;

    /** 浇水成长加成封顶 +20%（规则文档 §二十七） */
    public static final double MAX_WATER_BONUS = 0.20;

    @Override
    public boolean canWater(Crop crop, long currentGameDay) {
        GrowthStage stage = crop.getGrowthStage();
        boolean stageAllowed = stage == GrowthStage.SPROUT
                || stage == GrowthStage.GROWING
                || stage == GrowthStage.MATURE;
        return stageAllowed
                && crop.getLastManualWaterGameDay() != currentGameDay
                && crop.getManualWaterCount() < MAX_MANUAL_WATER_COUNT;
    }

    @Override
    public WateringResult water(Crop crop, long currentGameDay) {
        // 校验顺序不可打乱（A 模块设计文档 §8.4）
        if (crop.getGrowthStage() == GrowthStage.SEED) {
            return WateringResult.SEED_STAGE;
        }
        if (crop.getManualWaterCount() >= MAX_MANUAL_WATER_COUNT) {
            return WateringResult.WATER_LIMIT_REACHED;
        }
        // 当日已浇：long 用 == 比较（时间类型按 D14）
        if (crop.getLastManualWaterGameDay() == currentGameDay) {
            return WateringResult.ALREADY_WATERED_TODAY;
        }
        crop.setManualWaterCount(crop.getManualWaterCount() + 1);
        crop.setLastManualWaterGameDay(currentGameDay);
        return WateringResult.SUCCESS;
    }

    @Override
    public double calculateWaterGrowthBonus(Crop crop) {
        return Math.min(crop.getManualWaterCount() * WATER_BONUS_PER_TIME, MAX_WATER_BONUS);
    }
}
