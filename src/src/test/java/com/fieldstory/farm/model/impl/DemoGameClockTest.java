package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.GameClock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DemoGameClock 测试：正式基础步长为 1 游戏分钟，Demo 仅额外 ×12。
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
    void tickAdvancesTwelveGameMinutes() {
        GameClock clock = new DemoGameClock();
        clock.tick();
        assertEquals(372, clock.getTotalMinutes(), "Demo 每次 1 秒 tick 应推进 12 游戏分钟（1 × 12）");
        assertEquals("06:12", clock.getTimeString());
    }

    @Test
    void fiveDemoTicksAdvanceOneGameHour() {
        GameClock clock = new DemoGameClock();
        for (int i = 0; i < 5; i++) {
            clock.tick();
        }
        assertEquals(420, clock.getTotalMinutes());
        assertEquals("07:00", clock.getTimeString(), "5 次 Demo tick = 60 游戏分钟");
    }

    @Test
    void oneHundredTwentyTicksAdvanceOneGameDay() {
        GameClock clock = new DemoGameClock();
        for (int i = 0; i < 120; i++) {
            clock.tick();
        }
        assertEquals(360 + 1440, clock.getTotalMinutes());
        assertEquals(2, clock.getGameDay(), "120 次 Demo tick（1440 游戏分钟）应跨 1 天");
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
