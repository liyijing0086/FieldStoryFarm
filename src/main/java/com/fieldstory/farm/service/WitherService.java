package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.WeatherType;

/**
 * 枯萎服务接口（A 模块 P1；验收规范 §一百五十）。
 *
 * <p>只依赖 model 层共享类型（Crop/WeatherType），不依赖 D 的 Service（决策 D18）；
 * 概率计算与掷骰判定为纯函数（决策 D19）：roll 值入参，
 * 服务内禁止调用 RandomProvider，随机数由集成层获取后传入。
 *
 * <p>枯萎判定四条件（规则文档 §二十八）：
 * ① 非 SEED ② 当日存在干旱风险 ③ 没有有效补水 ④ streak 达风险区间。
 */
public interface WitherService {

    /**
     * 当日天气记录（跨天回调对每株 PLANTED 作物调用一次）：
     * RAIN → rainCount+1、lastHydratedWorldTime=currentWorldTime、streak=0（规则 §二十一）；
     * DROUGHT → droughtCount+1，无有效补水 streak+1 否则 0（规则 §二十二、§二十九）；
     * GREEN_RAIN → greenRainCount+1、streak=0（规则 §二十三）；
     * SUNNY → streak=0（任何非干旱日，验收 §五十二）。
     * 均不修改 manualWaterCount（验收 §五十一）。
     *
     * @param crop            目标作物
     * @param weatherType     当日天气
     * @param currentGameDay  当前游戏日
     * @param currentWorldTime 当前世界时间（游戏小时，来自 GameClock.getWorldTime）
     */
    void recordDailyWeather(Crop crop, WeatherType weatherType,
                            long currentGameDay, long currentWorldTime);

    /**
     * 有效补水判定（A 模块 P1 设计文档 §3.2 写死条款）：
     * 当日主动浇水成功（lastManualWaterGameDay == currentGameDay）
     * 或当日天气为 RAIN；绿雨/晴天终止 streak 但不属于补水（规则 §二十九）。
     *
     * @param crop           目标作物
     * @param weatherType    当日天气
     * @param currentGameDay 当前游戏日
     * @return true 表示当日存在有效补水
     */
    boolean isEffectivelyHydrated(Crop crop, WeatherType weatherType, long currentGameDay);

    /**
     * 纯函数：枯萎概率（规则文档 §三十；验收规范 §五十三）：
     * SEED/WITHERED 返回 0；普通生长期 streak&lt;2→0、2→30%、3→70%、≥4→100%；
     * 耐性档（小麦或 MATURE）streak≤2→0、3→30%、4→70%、≥5→100%；
     * 最终概率 = 基础概率 × 抗性倍率（规则 §三十一）。
     *
     * @param crop                  目标作物
     * @param witherMitigationRate  抗性倍率（P1 恒传 1.0；B 石灯笼上线后传 0.7）
     * @return 枯萎概率 [0, 1]
     */
    double calculateWitherProbability(Crop crop, double witherMitigationRate);

    /**
     * 纯函数：掷骰判定，roll ∈ [0,1)，roll &lt; probability 触发（决策 D19）。
     *
     * @param crop                  目标作物
     * @param witherMitigationRate  抗性倍率
     * @param roll                  随机掷骰值（集成层经 RandomProvider.nextDouble() 获取）
     * @return true 表示本次掷骰触发枯萎
     */
    boolean rollWither(Crop crop, double witherMitigationRate, double roll);

    /**
     * 枯萎判定（四条件，规则文档 §二十八；流程顺序见 A 模块 P1 设计文档 §5.5）：
     * ① crop == null → NOT_PLANTED
     * ② stage == WITHERED → ALREADY_WITHERED
     * ③ stage == SEED → SEED_EXEMPT（规则 §16.1）
     * ④ 当日非 DROUGHT → NO_DROUGHT_RISK
     * ⑤ 已有效补水 → NO_DROUGHT_RISK
     * ⑥ 概率 ≤ 0（streak 未达风险区间）→ NO_DROUGHT_RISK
     * ⑦ roll &lt; probability → stage=WITHERED，返回 WITHERED（验收 §五十四）
     * ⑧ 否则 → SURVIVED。
     *
     * @param crop                  目标作物
     * @param weatherType           当日天气
     * @param currentGameDay        当前游戏日
     * @param witherMitigationRate  抗性倍率
     * @param roll                  随机掷骰值
     * @return 枯萎判定结果码
     */
    WitherResult judgeWither(Crop crop, WeatherType weatherType, long currentGameDay,
                             double witherMitigationRate, double roll);
}
