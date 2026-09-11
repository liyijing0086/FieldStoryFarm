package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.GameClock;

import static com.fieldstory.farm.util.GameConstants.MINUTES_PER_DAY;

/**
 * 测试用游戏时钟（D 模块 P1：世界环境 · 自动化测试）。
 *
 * <p>依据《游戏规则设计文档》§九「开发与答辩时间模式」、《D模块 P0 接口与类设计文档》§一。
 *
 * <p><b>用途：</b>仅供自动化测试手动推进游戏时间，不参与正式游戏逻辑。
 *
 * <p><b>时间源统一：</b>继承 {@link BasicGameClock}，复用其全部时间换算逻辑，
 * 额外提供 {@link #advance(int)} 手动推进指定分钟数（规则 §八：时间源唯一）。
 */
public class TestGameClock extends BasicGameClock {

    /**
     * 默认构造：初始化为 {@code DAY_START}（第 1 天 06:00，规则 §5.1）。
     */
    public TestGameClock() {
        super();
    }

    /**
     * 指定总分钟数构造，用于存档恢复（验收规范 §41）。
     *
     * @param totalMinutes 总分钟数
     */
    public TestGameClock(int totalMinutes) {
        super(totalMinutes);
    }

    /**
     * 手动推进指定游戏分钟数（测试用）。
     *
     * <p>只负责累计总分钟数，不触发跨天结算逻辑（跨天结算由 Service 层负责，验收规范 §81）。
     *
     * @param minutes 推进的游戏分钟数（必须 ≥ 0）
     * @throws IllegalArgumentException 当 {@code minutes < 0} 时
     */
    public void advance(int minutes) {
        if (minutes < 0) {
            throw new IllegalArgumentException("推进分钟数不得为负: " + minutes);
        }
        setTotalMinutes(getTotalMinutes() + minutes);
    }

    /**
     * 设置当前游戏日（测试用），保留当日小时与分钟。
     *
     * @param gameDay 目标游戏日（≥1）
     */
    public void setGameDay(int gameDay) {
        setTotalMinutes((gameDay - 1) * MINUTES_PER_DAY
                + getTotalMinutes() % MINUTES_PER_DAY);
    }

    /**
     * 设置当前游戏小时（测试用），保留游戏日与分钟。
     *
     * @param gameHour 目标小时（0~23）
     */
    public void setGameHour(int gameHour) {
        setTotalMinutes((getGameDay() - 1) * MINUTES_PER_DAY
                + gameHour * 60 + getTotalMinutes() % 60);
    }
}
