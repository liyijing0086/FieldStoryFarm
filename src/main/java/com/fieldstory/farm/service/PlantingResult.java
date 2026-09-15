package com.fieldstory.farm.service;

/**
 * 播种结果码（A 模块设计文档 §8.2）。
 *
 * <p>纯枚举值，不挂文案字段；文案由 Controller 层按结果码映射
 * （设计文档 D13：结果枚举随 Service 契约放 service 包根）。
 */
public enum PlantingResult {

    /**
     * 播种成功：经 EconomyService 消耗 1 颗种子、创建 Crop 并记录
     * plantWorldTime，土地 TILLED → PLANTED
     * （验收规范 §十八 播种消耗种子、§二十 plantWorldTime 字段）。
     */
    SUCCESS,

    /**
     * 土地非 TILLED（EMPTY 直接播种、PLANTED 再次播种均非法），禁止播种
     * （验收规范 §十六 禁止错误土地行为）。
     */
    NOT_TILLED,

    /**
     * 对应作物种子库存不足 1：不消耗种子、土地与作物不变
     * （验收规范 §十八 SeedInventory）。
     */
    NO_SEED
}
