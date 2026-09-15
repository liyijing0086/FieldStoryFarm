package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Soil;

/**
 * 土地服务接口（A 模块设计文档 §8.1；概要设计说明书 §4.1 LandService*，决策 D07）。
 *
 * <p>职责：开垦（EMPTY→TILLED）与收获/枯萎铲除后的土地回退（→TILLED）。
 * P0 由 {@code impl.BasicLandService} 实现（依赖 EconomyService 扣金币）。
 *
 * <p>与 P3 LandUnlockService 的职责区分：本接口只操作已可用土地的状态机；
 * LOCKED 土地的解锁/扩张（地图扩展、解锁购买）属于 P3 LandUnlockService，
 * P0 不涉及（验收规范 §十四 LOCKED 仅占位）。
 */
public interface LandService {

    /**
     * 开垦前置校验。
     *
     * <p>前置状态不限；接口层只校验土地状态：state=EMPTY。
     * 金币充足与否经 EconomyService.canAfford(5) 校验，由实现层完成
     * （本接口不引入 EconomyService 依赖）。
     *
     * @param soil 待校验土地
     * @return true 仅表示土地状态可开垦（EMPTY）；金币是否足够由实现层综合判定
     */
    boolean canReclaim(Soil soil);

    /**
     * 开垦：EMPTY → TILLED。
     *
     * <p>前置状态：state=EMPTY（否则返回 {@link ReclaimResult#NOT_EMPTY}）。
     *
     * <p>成功路径（验收规范 §十五）：检查金币 ≥ 5 → 经 EconomyService 扣除
     * 5 金币 → 置 state=TILLED。扣款成功后才改状态（A 模块设计文档 §8.1）。
     *
     * <p>失败路径：金币不足返回 {@link ReclaimResult#NO_GOLD}。
     * 两种失败均不扣钱、不改土地（验收规范 §十五"金币不足"、§十六 非法行为拦截）。
     *
     * @param soil 待开垦土地
     * @return 开垦结果码
     */
    ReclaimResult reclaim(Soil soil);

    /**
     * 收获/枯萎铲除后的土地回退：crop=null + TILLED。
     *
     * <p>前置状态：state=PLANTED（收获/铲除场景）。
     * 成功后：该格 Crop 对象离开土地（crop=null）、state 置 TILLED，
     * 可直接再次播种（验收规范 §三十三：不得存在 HARVESTED 状态）。
     *
     * <p>P0 由 C 的 BasicHarvestService 在收获后调用；P1 枯萎铲除复用同一方法，
     * 土地状态机唯一入口保持在 A（A 模块设计文档 §8.1，决策 D09）。
     *
     * @param soil 待回退土地
     */
    void removeCropAndSetTilled(Soil soil);
}
