package com.fieldstory.farm.controller;

/**
 * 农场地块操作动作枚举（A 模块 P0 视图层；UI规范 §12 操作菜单按钮对应）。
 *
 * <p>由 {@link FarmViewController#actionsFor} 按土壤状态推导出对应动作；
 * HARVEST 按钮在 P0 保持禁用（C 模块 BasicHarvestService 未交付，
 * 收获是 C 的职责，A 禁止实现收获逻辑，决策 D09）。
 */
public enum FarmAction {

    /** 开垦：EMPTY → TILLED（LandService.reclaim，验收规范 §十五） */
    RECLAIM,

    /** 播种：TILLED → PLANTED（PlantingService.plant，验收规范 §十八） */
    PLANT,

    /** 浇水：作物阶段 ∈ {SPROUT, GROWING, MATURE}（WateringService.water，验收规范 §二十六） */
    WATER,

    /** 收获：P0 禁用，C 模块职责（决策 D09） */
    HARVEST,

    /** 铲除枯萎：WITHERED → TILLED（LandService.removeCropAndSetTilled；D20、验收 §五十四） */
    CLEAR_WITHERED
}
