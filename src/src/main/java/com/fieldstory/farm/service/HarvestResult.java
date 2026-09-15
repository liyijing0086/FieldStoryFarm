package com.fieldstory.farm.service;

/**
 * 收获结果码（C 模块 P0 基础收获；验收规范 §三十一、§三十三）。
 *
 * <p>纯枚举值，不挂文案字段；文案由 Controller 层按结果码映射
 * （与 PlantingResult、ReclaimResult、WateringResult 同一约定，设计文档 D13）。
 */
public enum HarvestResult {

    /**
     * 收获成功：基础售价入账（验收规范 §三十二 FinalPrice = BasePrice）、
     * Crop 对象离开土地、SoilState 置 TILLED（验收规范 §三十三）。
     */
    SUCCESS,

    /**
     * 土地非 PLANTED（EMPTY/TILLED/LOCKED 无作物可收），禁止收获
     * （概要设计说明书 §11.1：TILLED 收获为无效操作，必须失败但不改状态）。
     */
    NOT_PLANTED,

    /**
     * 土地为 PLANTED 但无作物对象（防御性校验，如存档恢复异常）。
     * 失败时不加金币、不改土地与作物。
     */
    NO_CROP,

    /**
     * 作物未成熟（GrowthStage != MATURE），禁止收获
     * （概要设计说明书 §11.1：非 MATURE 作物收获为无效操作；
     * 验收规范 §三十一 第一步"检查成熟"）。
     */
    NOT_MATURE
}
