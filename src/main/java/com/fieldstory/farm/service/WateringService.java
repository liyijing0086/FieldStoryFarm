package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Crop;

/**
 * 浇水服务接口（A 模块设计文档 §8.4，时间类型按决策 D14 修正）。
 *
 * <p>三重校验（验收规范 §二十六、§二十七、§二十八）：
 * ① 阶段 ∈ {SPROUT, GROWING, MATURE}（SEED 不可主动浇水）；
 * ② 当日未浇（每个游戏日最多 1 次有效主动浇水）；
 * ③ manualWaterCount &lt; 5（单株最多记录 5 次）。
 *
 * <p>D11 行为约定：第 5 次浇水有效，第 6 次起 canWater 直接返回 false，
 * 加成维持 +20% 封顶；UI 建议提示"这株作物已经不需要浇水了"。
 *
 * <p>时间类型按 D14 修正：currentGameDay 使用 long（游戏日），
 * 与已交付 Crop 模型（getLastManualWaterGameDay）及 E 的 GameState 一致；
 * "当日已浇"判断为 ==；初始哨兵 -1 表示从未浇水（与游戏日 0 区分）。
 * 本接口不依赖 GameClock（接口层禁止 import GameClock），
 * 当前游戏日由调用方传入。
 */
public interface WateringService {

    /**
     * 浇水前置校验（三重校验，A 模块设计文档 §8.4）。
     *
     * <p>校验条件：阶段 ∈ {SPROUT, GROWING, MATURE}
     * 且 lastManualWaterGameDay != currentGameDay
     * 且 manualWaterCount &lt; 5（决策 D11：第 5 次仍有效，
     * 第 6 次起返回 false）。
     *
     * @param crop           目标作物
     * @param currentGameDay 当前游戏日（游戏日，来自 GameClock.getGameDay）
     * @return true 仅表示满足三重校验，可以浇水
     */
    boolean canWater(Crop crop, long currentGameDay);

    /**
     * 执行浇水：count+1（≤5）、记录当日；返回具体拒绝原因。
     *
     * <p>校验顺序（A 模块设计文档 §8.4）：
     * SEED → {@link WateringResult#SEED_STAGE}；
     * count ≥ 5 → {@link WateringResult#WATER_LIMIT_REACHED}；
     * 当日已浇（==）→ {@link WateringResult#ALREADY_WATERED_TODAY}；
     * 通过 → count+1、lastManualWaterGameDay=currentGameDay，
     * 返回 {@link WateringResult#SUCCESS}。
     *
     * @param crop           目标作物
     * @param currentGameDay 当前游戏日
     * @return 浇水结果码
     */
    WateringResult water(Crop crop, long currentGameDay);

    /**
     * 浇水成长加成：min(manualWaterCount × 0.05, 0.20)
     * （规则文档 §二十七；验收规范 §二十八 每次 +5%、最多 +20%）。
     *
     * @param crop 目标作物
     * @return 浇水成长加成比例（0.00~0.20）
     */
    double calculateWaterGrowthBonus(Crop crop);
}
