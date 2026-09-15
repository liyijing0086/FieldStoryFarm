package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.service.LandService;
import com.fieldstory.farm.service.ReclaimResult;
import com.fieldstory.farm.service.economy.EconomyService;

/**
 * {@link LandService} 基础实现（A 模块设计文档 §8.1）。
 *
 * <p>开垦流程（验收规范 §十五）：state=EMPTY 且金币充足（经 B 的
 * {@link EconomyService#canAfford(int)} 校验）→ 先经
 * {@link EconomyService#spendGold(int)} 扣 {@value #RECLAIM_COST} 金币
 * → 置 TILLED。先扣钱后改状态；任一失败均不扣钱、不改地。
 *
 * <p>金币变化统一走 EconomyService、不直接操作 Player 模型（跨模块契约）；
 * 收获/枯萎铲除后的土地回退 {@link #removeCropAndSetTilled(Soil)} 由
 * C 的 BasicHarvestService 调用（决策 D09），土地状态机唯一入口保持在本模块。
 *
 * <p>本实现不使用系统时间、不使用随机数。
 */
public class BasicLandService implements LandService {

    /** 开垦一格 EMPTY 土地消耗的金币（规则文档 §12.1；验收规范 §十五） */
    public static final int RECLAIM_COST = 5;

    /** 经济服务：开垦经统一经济入口扣金币（B 文档 §17.1、§23 冻结表） */
    private final EconomyService economyService;

    /**
     * 构造器注入经济服务（A 模块设计文档 §8.1）。
     *
     * @param economyService 经济服务（B 模块交付，负责金币校验与扣除）
     */
    public BasicLandService(EconomyService economyService) {
        this.economyService = economyService;
    }

    @Override
    public boolean canReclaim(Soil soil) {
        return soil.getState() == SoilState.EMPTY
                && economyService.canAfford(RECLAIM_COST);
    }

    @Override
    public ReclaimResult reclaim(Soil soil) {
        if (soil.getState() != SoilState.EMPTY) {
            return ReclaimResult.NOT_EMPTY;
        }
        if (!economyService.canAfford(RECLAIM_COST)) {
            return ReclaimResult.NO_GOLD;
        }
        // 先扣钱后改状态：扣款成功才 EMPTY→TILLED（A 模块设计文档 §8.1）
        economyService.spendGold(RECLAIM_COST);
        soil.setState(SoilState.TILLED);
        return ReclaimResult.SUCCESS;
    }

    @Override
    public void removeCropAndSetTilled(Soil soil) {
        soil.setCrop(null);
        soil.setState(SoilState.TILLED);
    }
}
