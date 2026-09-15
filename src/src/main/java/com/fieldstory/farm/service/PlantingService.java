package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Soil;

/**
 * 播种服务接口（A 模块设计文档 §8.2；概要设计说明书 §4.1 PlantingService）。
 *
 * <p>职责：校验土地与种子并创建作物（TILLED → PLANTED）。
 * P0 由 {@code impl.BasicPlantingService} 实现，依赖 EconomyService
 * （种子库存）、CropFactory（创建 Crop）、GameClock（播种时刻世界时间），
 * 依赖均在实现层注入，本接口不引入。
 *
 * <p>播种消耗 1 颗种子，不得直接扣金币（验收规范 §十八、§十九）。
 */
public interface PlantingService {

    /**
     * 播种前置校验。
     *
     * <p>前置状态不限；接口层只校验土地状态：state=TILLED。
     * 种子库存是否 ≥1 经 EconomyService 校验（设计文档 D08），
     * 由实现层完成（本接口不引入 EconomyService 依赖）。
     *
     * @param soil 待播种土地
     * @param type 作物类型
     * @return true 仅表示土地状态可播种（TILLED）；种子是否足够由实现层综合判定
     */
    boolean canPlant(Soil soil, CropType type);

    /**
     * 播种：TILLED → PLANTED。
     *
     * <p>前置状态：state=TILLED（否则返回 {@link PlantingResult#NOT_TILLED}）。
     *
     * <p>成功路径：经 EconomyService 消耗 1 颗对应种子（验收规范 §十八）→
     * 经 CropFactory.create(type, gameClock.getWorldTime()) 创建 Crop 并记录
     * 播种时刻世界时间 plantWorldTime（验收规范 §二十）→ soil.setCrop →
     * state=PLANTED。播种不直接扣金币（验收规范 §十九）。
     *
     * <p>失败路径：种子不足返回 {@link PlantingResult#NO_SEED}。
     * 两种失败均不消耗种子、土地与作物不变（验收规范 §十六 非法行为拦截）。
     *
     * @param soil 待播种土地
     * @param type 作物类型
     * @return 播种结果码
     */
    PlantingResult plant(Soil soil, CropType type);
}
