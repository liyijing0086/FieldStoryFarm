package com.fieldstory.farm.testutil;

import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.model.impl.BasicGameClock;

import static com.fieldstory.farm.util.GameConstants.MINUTES_PER_DAY;

/**
 * {@link GameClock} 测试桩：基于 D 模块正式实现 {@link BasicGameClock}，
 * 仅补充播种时刻计算单测所需的 set 辅助方法。
 *
 * <p>gameDay / gameHour 均可 set，供播种时刻计算单测使用
 * （plantWorldTime = gameDay × 24 + gameHour，决策 D14）；
 * 正式接口 11 个方法由 {@link BasicGameClock} 天然满足，本类无需覆写。
 */
public class TestGameClock extends BasicGameClock {

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
