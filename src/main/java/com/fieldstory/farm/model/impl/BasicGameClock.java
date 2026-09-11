package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.GameClock;

import java.time.LocalDateTime;

import static com.fieldstory.farm.util.GameConstants.DAY_END;
import static com.fieldstory.farm.util.GameConstants.DAY_START;
import static com.fieldstory.farm.util.GameConstants.MINUTES_PER_DAY;
import static com.fieldstory.farm.util.GameConstants.MINUTES_PER_TICK;

/**
 * {@link GameClock} 的默认实现（D 模块 P0：世界环境）。
 *
 * <p>依据《D模块 P0 接口与类设计文档》§二、《游戏规则与数值设计文档》§5.1/§8/§九。
 *
 * <p>约定：禁止提供带 {@code LocalDateTime} 等参数的构造器，保证时间源统一（规则 §8）。
 *
 * <p><b>决策 D14：</b>不提供 {@code getWorldTime()}。A 侧时间字段统一为 {@code long}，
 * 由 {@code getGameDay() * 24 + getGameHour()} 适配计算游戏小时。
 */
public class BasicGameClock implements GameClock {

    /** 累计总分钟数（从第 1 天 00:00 起）。 */
    private int totalMinutes;

    /**
     * 默认构造：初始化为 {@code DAY_START}（360 分钟，对应第 1 天 06:00，规则 §5.1）。
     */
    public BasicGameClock() {
        this.totalMinutes = DAY_START;
    }

    /**
     * 指定总分钟数构造，用于存档恢复（验收规范 §41）。
     *
     * @param totalMinutes 总分钟数（必须 ≥ 0）
     * @throws IllegalArgumentException 当 {@code totalMinutes < 0} 时
     */
    public BasicGameClock(int totalMinutes) {
        setTotalMinutes(totalMinutes);
    }

    @Override
    public LocalDateTime getRealTime() {
        return LocalDateTime.now();
    }

    @Override
    public int getGameDay() {
        return totalMinutes / MINUTES_PER_DAY + 1;
    }

    @Override
    public int getGameHour() {
        return (totalMinutes % MINUTES_PER_DAY) / 60;
    }

    @Override
    public void advance() {
        tick();
    }

    @Override
    public long calculateOfflineDuration() {
        // P0 阶段离线模拟不启用（验收规范 §10），返回 0；P1 起由 RealGameClock 实现。
        return 0L;
    }

    @Override
    public int getTotalMinutes() {
        return totalMinutes;
    }

    @Override
    public int getGameMinute() {
        return totalMinutes % 60;
    }

    @Override
    public String getTimeString() {
        return String.format("%02d:%02d", getGameHour(), getGameMinute());
    }

    @Override
    public boolean isDaytime() {
        int minuteOfDay = totalMinutes % MINUTES_PER_DAY;
        return minuteOfDay >= DAY_START && minuteOfDay < DAY_END;
    }

    @Override
    public void tick() {
        totalMinutes += MINUTES_PER_TICK;
    }

    @Override
    public void setTotalMinutes(int totalMinutes) {
        if (totalMinutes < 0) {
            throw new IllegalArgumentException("总分钟数不得为负: " + totalMinutes);
        }
        this.totalMinutes = totalMinutes;
    }
}
