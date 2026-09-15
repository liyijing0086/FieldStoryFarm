package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.service.HarvestResult;
import com.fieldstory.farm.service.HarvestService;
import com.fieldstory.farm.service.LandService;
import com.fieldstory.farm.service.economy.EconomyService;

/**
 * {@link HarvestService} 基础实现（C 模块 P0 基础收获；概要设计说明书 §4.1）。
 *
 * <p>收获流程（验收规范 §三十一）：
 * 检查成熟 → 读取 CropType → 经 {@link EconomyService#calculateBaseSellPrice}
 * 读取基础售价 → {@link EconomyService#addGold} 入账 → 经
 * {@link LandService#removeCropAndSetTilled} 移除作物并置 TILLED。
 * 先入账后移除：作物移除后无法再读取作物类型（验收规范 §三十一顺序）。
 *
 * <p>P0 售价 = 基础售价（验收规范 §三十二 FinalPrice = BasePrice：
 * 小麦 50 / 玉米 70 / 胡萝卜 60，规则文档 §十三；品质倍率 P1 启用）；
 * 金币变化统一走 EconomyService、不直接操作 Player 模型（跨模块契约，
 * B-P0-DESIGH v1.1-aligned 接口说明 "C 收获"）；土地状态机唯一入口
 * 保持在 A 的 LandService（决策 D09）。
 *
 * <p>失败路径（概要设计说明书 §11.1 无效操作必须失败但不改状态）：
 * 非 PLANTED → {@link HarvestResult#NOT_PLANTED}；
 * PLANTED 但无作物 → {@link HarvestResult#NO_CROP}；
 * 未成熟 → {@link HarvestResult#NOT_MATURE}。
 * 收获是玩家行为，不存在 HARVESTED 成长阶段（规则文档 §十五、
 * 验收规范 §三十三：作物对象直接离开土地）。
 *
 * <p>本实现不使用系统时间、不使用随机数。
 */
public class BasicHarvestService implements HarvestService {

    /** 经济服务：基础售价读取与金币入账（B 模块交付，B-P0-DESIGH "C 收获"） */
    private final EconomyService economyService;

    /** 土地服务：收获后的土地回退 removeCropAndSetTilled（A 模块交付，决策 D09） */
    private final LandService landService;

    /**
     * 构造器注入经济服务与土地服务。
     *
     * @param economyService 经济服务（B 模块，负责基础售价与金币入账）
     * @param landService    土地服务（A 模块，负责作物移除与土地回退 TILLED）
     */
    public BasicHarvestService(EconomyService economyService, LandService landService) {
        this.economyService = economyService;
        this.landService = landService;
    }

    @Override
    public boolean canHarvest(Soil soil) {
        if (soil == null || soil.getState() != SoilState.PLANTED) {
            return false;
        }
        Crop crop = soil.getCrop();
        return crop != null && crop.getGrowthStage() == GrowthStage.MATURE;
    }

    @Override
    public HarvestResult harvest(Soil soil) {
        if (soil.getState() != SoilState.PLANTED) {
            return HarvestResult.NOT_PLANTED;
        }
        Crop crop = soil.getCrop();
        if (crop == null) {
            return HarvestResult.NO_CROP;
        }
        if (crop.getGrowthStage() != GrowthStage.MATURE) {
            return HarvestResult.NOT_MATURE;
        }
        // 先读价后入账再移除（验收规范 §三十一顺序）：移除作物后无法再读取作物类型
        int price = economyService.calculateBaseSellPrice(crop.getCropType());
        economyService.addGold(price);
        landService.removeCropAndSetTilled(soil);
        return HarvestResult.SUCCESS;
    }
}
