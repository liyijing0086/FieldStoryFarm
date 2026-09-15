package com.fieldstory.farm.model;

/**
 * 天气类型枚举（D 模块 P0：世界环境，P1 预留）。
 *
 * <p>依据《D模块 P0 接口与类设计文档》§8.1、《P0-P4功能实现与验收规范》§48。
 *
 * <p><b>P0 约束：</b>本阶段只允许定义枚举常量，<b>禁止</b>在 P0 业务代码中引用
 * （验收规范 §10：P0 不实现随机天气，世界环境固定 {@code WeatherRate = 1.0}）。
 * 天气系统自 P1 起启用。
 */
public enum WeatherType {

    /** 晴天（P0 固定天气，验收规范 §76）。 */
    SUNNY,

    /** 雨天（P1 启用，验收规范 §51）。 */
    RAIN,

    /** 干旱（P1 启用，验收规范 §52）。 */
    DROUGHT,

    /** 绿雨（P1 启用，规则文档天气系统）。 */
    GREEN_RAIN
}
