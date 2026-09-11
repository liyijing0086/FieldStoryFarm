package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.GameClock;

import java.time.LocalDateTime;

import static com.fieldstory.farm.util.GameConstants.MINUTES_PER_TICK;

/**
 * 演示用游戏时钟（D 模块 P1：世界环境 · 开发调试与答辩演示）。
 *
 * <p>依据《游戏规则设计文档》§九「开发与答辩时间模式」、《D模块 P0 接口与类设计文档》§一。
 *
 * <p><b>用途：</b>仅用于开发调试与答辩演示，通过 ×12 时间倍率加速游戏进程
 * （规则 §九：小麦约 4 分钟成熟、玉米约 6 分钟、胡萝卜约 8 分钟）。
 *
 * <p><b>数值：</b>每次 {@link #tick()} 推进 {@code MINUTES_PER_TICK × 12 = 120} 游戏分钟
 * （即 2 游戏小时）。<b>不改变正式数值</b>：正式游戏仍为 1 现实分钟 = 1 游戏小时
 * （规则 §九），本类只放大单次 tick 的推进量，供演示使用。
 *
 * <p><b>时间源统一：</b>继承 {@link BasicGameClock}，复用其全部时间换算逻辑，
 * 仅覆写 {@link #tick()} 的推进步长，保证时间源唯一（规则 §八）。
 */
public class DemoGameClock extends BasicGameClock {

    /** 演示时间倍率（规则 §九：×12）。 */
    public static final int DEMO_TIME_MULTIPLIER = 12;

    /**
     * 默认构造：初始化为 {@code DAY_START}（第 1 天 06:00，规则 §5.1）。
     */
    public DemoGameClock() {
        super();
    }

    /**
     * 指定总分钟数构造，用于存档恢复（验收规范 §41）。
     *
     * @param totalMinutes 总分钟数
     */
    public DemoGameClock(int totalMinutes) {
        super(totalMinutes);
    }

    /**
     * 推进一个演示时间单位：{@code MINUTES_PER_TICK × 12 = 120} 游戏分钟。
     *
     * <p>只负责累计总分钟数，不触发跨天结算逻辑（跨天结算由 Service 层负责，验收规范 §81）。
     */
    @Override
    public void tick() {
        setTotalMinutes(getTotalMinutes() + MINUTES_PER_TICK * DEMO_TIME_MULTIPLIER);
    }

    @Override
    public LocalDateTime getRealTime() {
        return LocalDateTime.now();
    }
}
