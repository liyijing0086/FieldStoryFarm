package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.service.WitherResult;
import com.fieldstory.farm.service.WitherService;

/**
 * {@link WitherService} 基础实现（A 模块 P1 设计文档 §5；验收规范 §一百五十）。
 *
 * <p>概率计算与掷骰判定为纯函数（决策 D19）：roll 值入参，
 * 本实现禁止调用 RandomProvider，随机数由集成层经
 * {@code RandomProvider.nextDouble()} 获取后传入，保证纯函数可测。
 *
 * <p>本实现零外部依赖：只依赖 model 层共享类型（Crop/WeatherType），
 * 不依赖 D 的 Service（决策 D18）、不使用系统时间。
 */
public class BasicWitherService implements WitherService {

    // ===== P1 枯萎概率（规则文档 §三十；验收规范 §五十三）=====

    /** 普通生长期作物 streak=2 时枯萎概率 30% */
    public static final double WITHER_PROB_STREAK2_NORMAL = 0.30;

    /** 普通生长期作物 streak=3 时枯萎概率 70% */
    public static final double WITHER_PROB_STREAK3_NORMAL = 0.70;

    /** 耐性档（小麦或成熟作物）streak=3 时枯萎概率 30% */
    public static final double WITHER_PROB_STREAK3_RESISTANT = 0.30;

    /** 耐性档（小麦或成熟作物）streak=4 时枯萎概率 70% */
    public static final double WITHER_PROB_STREAK4_RESISTANT = 0.70;

    // ===== P1 枯萎抗性（规则文档 §三十一；B 石灯笼上线前恒 1.0）=====

    /** 抗性倍率：P1 集成层恒传 1.0（B 石灯笼装饰未交付，仅预留参数） */
    public static final double WITHER_MITIGATION_P1 = 1.0;

    @Override
    public void recordDailyWeather(Crop crop, WeatherType weatherType,
                                   long currentGameDay, long currentWorldTime) {
        switch (weatherType) {
            case RAIN:
                // 雨天自动补水：rainCount+1、记录补水时刻、终止 streak（规则文档 §二十一）
                crop.setRainCount(crop.getRainCount() + 1);
                crop.setLastHydratedWorldTime(currentWorldTime);
                crop.setDroughtStreak(0);
                break;
            case DROUGHT:
                // 干旱日：droughtCount+1；无有效补水 streak+1，否则归零
                // （规则文档 §二十二、§二十九）
                crop.setDroughtCount(crop.getDroughtCount() + 1);
                if (isEffectivelyHydrated(crop, weatherType, currentGameDay)) {
                    crop.setDroughtStreak(0);
                } else {
                    crop.setDroughtStreak(crop.getDroughtStreak() + 1);
                }
                break;
            case GREEN_RAIN:
                // 绿雨：greenRainCount+1、终止 streak；不属于补水、
                // 不更新 lastHydratedWorldTime（规则文档 §二十三、§二十九）
                crop.setGreenRainCount(crop.getGreenRainCount() + 1);
                crop.setDroughtStreak(0);
                break;
            case SUNNY:
            default:
                // 晴天：任何非干旱日均终止 streak（验收规范 §五十二）
                crop.setDroughtStreak(0);
                break;
        }
    }

    @Override
    public boolean isEffectivelyHydrated(Crop crop, WeatherType weatherType, long currentGameDay) {
        // 有效补水写死条款：当日主动浇水成功 或 当日天气 RAIN（设计文档 §3.2）
        return weatherType == WeatherType.RAIN
                || crop.getLastManualWaterGameDay() == currentGameDay;
    }

    @Override
    public double calculateWitherProbability(Crop crop, double witherMitigationRate) {
        GrowthStage stage = crop.getGrowthStage();
        // SEED 不参与、WITHERED 跳过（验收规范 §五十三）
        if (stage == GrowthStage.SEED || stage == GrowthStage.WITHERED) {
            return 0.0;
        }
        // 耐性档：小麦任何阶段 或 任何作物 MATURE（验收规范 §五十三"小麦和成熟作物"）
        boolean resistant = crop.getCropType() == CropType.WHEAT
                || stage == GrowthStage.MATURE;
        int streak = crop.getDroughtStreak();
        double base;
        if (streak < 2) {
            base = 0.0;                 // streak 0/1：无枯萎风险（规则文档 §三十）
        } else if (resistant) {
            base = (streak == 2) ? 0.0
                    : (streak == 3) ? WITHER_PROB_STREAK3_RESISTANT
                    : (streak == 4) ? WITHER_PROB_STREAK4_RESISTANT
                    : 1.0;              // streak ≥ 5：必枯萎
        } else {
            base = (streak == 2) ? WITHER_PROB_STREAK2_NORMAL
                    : (streak == 3) ? WITHER_PROB_STREAK3_NORMAL
                    : 1.0;              // streak ≥ 4：必枯萎
        }
        // 石灯笼抗性：最终概率 = 基础概率 × 抗性倍率（规则文档 §三十一）
        return base * witherMitigationRate;
    }

    @Override
    public boolean rollWither(Crop crop, double witherMitigationRate, double roll) {
        // roll ∈ [0,1)，roll < probability 触发（决策 D19）
        return roll < calculateWitherProbability(crop, witherMitigationRate);
    }

    @Override
    public WitherResult judgeWither(Crop crop, WeatherType weatherType, long currentGameDay,
                                    double witherMitigationRate, double roll) {
        // 判定流程顺序不可打乱（A 模块 P1 设计文档 §5.5；规则文档 §二十八 四条件）
        if (crop == null) {
            return WitherResult.NOT_PLANTED;
        }
        if (crop.getGrowthStage() == GrowthStage.WITHERED) {
            return WitherResult.ALREADY_WITHERED;
        }
        if (crop.getGrowthStage() == GrowthStage.SEED) {
            return WitherResult.SEED_EXEMPT;
        }
        if (weatherType != WeatherType.DROUGHT) {
            return WitherResult.NO_DROUGHT_RISK;
        }
        if (isEffectivelyHydrated(crop, weatherType, currentGameDay)) {
            return WitherResult.NO_DROUGHT_RISK;
        }
        double probability = calculateWitherProbability(crop, witherMitigationRate);
        if (probability <= 0.0) {
            // streak 未达风险区间（streak 0/1 普通、≤2 耐性档）
            return WitherResult.NO_DROUGHT_RISK;
        }
        if (roll < probability) {
            crop.setGrowthStage(GrowthStage.WITHERED);
            return WitherResult.WITHERED;
        }
        return WitherResult.SURVIVED;
    }
}
