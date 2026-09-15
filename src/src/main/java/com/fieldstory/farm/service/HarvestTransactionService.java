package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.item.Inventory;

/**
 * 收获事务服务接口（C 模块 品质与传说域，P2；验收规范 §一百零三）。
 *
 * <p>P2 完整收获事务正式替代 P0 的 {@link HarvestService} 基础实现
 * （验收规范 §一百零三"HarvestService 必须正式建立"）。因 P0 接口名
 * {@code HarvestService} 已被 A 模块 FarmViewController 依赖（保持
 * P0 基础行为不变），P2 事务以 {@code HarvestTransactionService} 命名，
 * 由实现类编排完整 13 步事务：
 *
 * <pre>
 * 检查 MATURE → 计算 Score → 传说判定 → 确定 Quality → 计算售价
 * → 发金币 → 发肥料 → 落档 Memory+生成故事 → 清除土地 Crop
 * → Soil=TILLED
 * </pre>
 *
 * <p>事务原子性（规则文档 §六十八）：先完成全部计算（评分/品质/售价/故事），
 * 再依次变更状态（金币→肥料→记忆→土地），任一步失败不产生半成功状态；
 * 无效收获（未种植/无作物/未成熟）不产生任何变更
 * （概要设计说明书 §11.1 无效操作必须失败但不改状态）。
 */
public interface HarvestTransactionService {

    /**
     * 收获前置校验：PLANTED + Crop 存在 + MATURE
     * （验收规范 §三十一"检查成熟"）。
     *
     * @param soil 待收获土地
     * @return true 仅表示该格作物成熟可收获
     */
    boolean canHarvest(Soil soil);

    /**
     * 收获事务（无背包注入版）：等价于 {@code harvest(soil, null)}，
     * 肥料奖励计入 {@link HarvestOutcome#getFertilizerReward()} 但不入包，
     * 由调用方按需发放。
     *
     * @param soil 待收获土地
     * @return 事务结果
     */
    HarvestOutcome harvest(Soil soil);

    /**
     * 收获事务（背包注入版）：按验收规范 §一百零三 流程执行完整事务。
     *
     * <p>成功路径：校验成熟 → QualityService 计算 Score → LegendaryService
     * 突破判定 → 确定 Quality（§一百零二 顺序）→ 售价 = 基础售价 ×
     * 品质倍率 × 事件倍率（规则文档 §六十五 P2 子集，四舍五入）
     * → 金币入账（EconomyService.addGold）→ 肥料奖励入包
     * （规则文档 §六十六，inventory 非空且奖励 &gt;0 时）→ 记忆落档
     * 与故事生成（MemoryService）→ 土地回退 TILLED（LandService，决策 D09）。
     *
     * <p>失败路径：未种植/无作物/未成熟返回对应结果码，
     * 不产生金币、肥料、记忆、土地任何变更。
     *
     * @param soil      待收获土地
     * @param inventory 玩家背包（肥料奖励入包）；null 时跳过入包
     * @return 事务结果（含品质/评分/售价/肥料奖励/故事/记忆档案）
     */
    HarvestOutcome harvest(Soil soil, Inventory inventory);
}
