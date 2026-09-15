package com.fieldstory.farm.service;

/**
 * 开垦结果码（A 模块设计文档 §8.1）。
 *
 * <p>纯枚举值，不挂文案字段；文案由 Controller 层按结果码映射
 * （设计文档 D13：结果枚举随 Service 契约放 service 包根）。
 */
public enum ReclaimResult {

    /**
     * 开垦成功：经 EconomyService 扣除 5 金币后，土地 EMPTY → TILLED
     * （验收规范 §十五 开垦流程）。
     */
    SUCCESS,

    /**
     * 土地非 EMPTY（如已 TILLED / PLANTED / LOCKED），禁止开垦
     * （验收规范 §十六 禁止错误土地行为）。
     */
    NOT_EMPTY,

    /**
     * 金币不足 5：不扣钱、不改土地
     * （验收规范 §十五"金币不足"分支）。
     */
    NO_GOLD
}
