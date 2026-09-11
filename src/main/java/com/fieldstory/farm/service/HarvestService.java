package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Soil;

/**
 * 收获服务接口（C 模块 P0 基础收获；概要设计说明书 §4.1 BasicHarvestService）。
 *
 * <p>职责：校验成熟并执行基础收获与出售（MATURE Crop → 金币、TILLED Soil）。
 * P0 由 {@code impl.BasicHarvestService} 实现：
 * 售价经 B 的 {@code EconomyService.calculateBaseSellPrice} 读取、
 * 金币经 {@code EconomyService.addGold} 入账（B-P0-DESIGH v1.1-aligned 接口说明
 * "C 收获"），土地回退经 A 的 {@code LandService.removeCropAndSetTilled}
 * （决策 D09：土地状态机唯一入口保持在 A）。
 *
 * <p>P0 售价 = 基础售价（验收规范 §三十二，品质系统 P1 启用）；
 * P2 起由 HarvestService 完整事务正式替代本实现
 * （验收规范 §一百五十；概要设计说明书 §10.3：必须保持
 * "成熟才能收获、收获后土地 TILLED"基础行为）。
 */
public interface HarvestService {

    /**
     * 收获前置校验。
     *
     * <p>前置状态不限；校验条件：state=PLANTED 且存在 Crop
     * 且 Crop 阶段为 MATURE（验收规范 §三十一"检查成熟"；
     * 概要设计说明书 §11.1 非 MATURE 收获拦截）。
     *
     * @param soil 待收获土地
     * @return true 仅表示该格作物成熟可收获
     */
    boolean canHarvest(Soil soil);

    /**
     * 收获：PLANTED + MATURE → 基础售价入账 + 土地回退 TILLED。
     *
     * <p>成功路径（验收规范 §三十一流程）：
     * 读取 CropType → 经 EconomyService 读取基础售价 → addGold 入账
     * → 经 LandService.removeCropAndSetTilled 移除作物并置 TILLED。
     * 先入账后移除的顺序不可颠倒：作物移除后无法再读取作物类型。
     *
     * <p>失败路径：非 PLANTED 返回 {@link HarvestResult#NOT_PLANTED}、
     * PLANTED 但无作物返回 {@link HarvestResult#NO_CROP}、
     * 未成熟返回 {@link HarvestResult#NOT_MATURE}。
     * 所有失败均不加金币、不移除作物、不改土地状态
     * （概要设计说明书 §11.1：无效操作必须失败但不改状态）。
     *
     * @param soil 待收获土地
     * @return 收获结果码
     */
    HarvestResult harvest(Soil soil);
}
