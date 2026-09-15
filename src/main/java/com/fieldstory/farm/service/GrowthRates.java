package com.fieldstory.farm.service;

/**
 * 成长倍率三件套（P2 升级；计划书 §5 六因子中的 WeatherRate / DecorationRate / EventRate）。
 *
 * <p>P0/P1 三率恒 1.0（{@link #P0}，验收规范 §二十四/§四十九）；P2 起由调用方
 * 按实时天气（验收规范 §四十九）、装饰（B 模块）与事件（D 模块 P2 文档 §二：
 * 彩虹日 EventRate = 2.0，其余 1.0）组装后经 4 参重载传入 {@link GrowthService}。
 *
 * <p>不可变记录：一次组装、多作物复用，避免跨天循环内重复构造。
 */
public record GrowthRates(double weatherRate, double decorationRate,
        double eventRate) {

    /** P0 占位倍率：三率全 1.0（验收规范 §二十四/§四十九，P0/P1 兼容红线） */
    public static final GrowthRates P0 = new GrowthRates(1.0, 1.0, 1.0);

    /**
     * 紧凑构造：非法值（&lt;0 或 NaN）一律钳制为 0（非功能需求：
     * 异常输入不破坏状态——取 0 只让成长暂停，杜绝负增长与 NaN 污染进度）。
     */
    public GrowthRates {
        weatherRate = clamp(weatherRate);
        decorationRate = clamp(decorationRate);
        eventRate = clamp(eventRate);
    }

    /** 非法率钳制：&lt;0 或 NaN → 0。 */
    private static double clamp(double rate) {
        return (rate < 0 || Double.isNaN(rate)) ? 0.0 : rate;
    }
}
