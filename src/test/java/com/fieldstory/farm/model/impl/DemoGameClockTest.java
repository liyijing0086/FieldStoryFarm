package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.GameClock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P1 DemoGameClock 测试（D 模块 P1 文档 §6、规则文档 §九）。
 *
 * <p>覆盖：每次 tick 推进 120 游戏分钟（MINUTES_PER_TICK × 12）、跨日、构造器边界。
 */
class DemoGameClockTest {

    @Test
    void defaultConstructorStartsAtDay1SixAm() {
        GameClock clock = new DemoGameClock();
        assertEquals(360, clock.getTotalMinutes());
        assertEquals(1, clock.getGameDay());
        assertEquals("06:00", clock.getTimeString());
    }

    @Test
    void tickAdvances120Minutes() {
        GameClock clock = new DemoGameClock();
        clock.tick();
        assertEquals(480, clock.getTotalMinutes(), "每次 tick 应推进 120 分钟（10 × 12）");
        assertEquals("08:00", clock.getTimeString());
    }

    @Test
    void twelveTicksCrossOneDay() {
        GameClock clock = new DemoGameClock();
        for (int i = 0; i < 12; i++) {
            clock.tick();
        }
        assertEquals(360 + 12 * 120, clock.getTotalMinutes());
        assertEquals(2, clock.getGameDay(), "12 次 tick（1440 分钟）应跨 1 天");
        assertEquals("06:00", clock.getTimeString());
    }

    @Test
    void constructorWithTotalMinutesRestoresSave() {
        GameClock clock = new DemoGameClock(1800);
        assertEquals(1800, clock.getTotalMinutes());
        assertEquals(2, clock.getGameDay());
    }

    @Test
    void constructorRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new DemoGameClock(-1));
    }
}
