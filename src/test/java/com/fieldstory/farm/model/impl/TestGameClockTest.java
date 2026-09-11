package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.GameClock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P1 TestGameClock 测试（D 模块 P1 文档 §6、规则文档 §九）。
 *
 * <p>覆盖：advance(int) 手动推进、setGameDay/setGameHour 辅助、构造器边界。
 */
class TestGameClockTest {

    @Test
    void defaultConstructorStartsAtDay1SixAm() {
        GameClock clock = new TestGameClock();
        assertEquals(360, clock.getTotalMinutes());
        assertEquals(1, clock.getGameDay());
    }

    @Test
    void advanceAddsGivenMinutes() {
        TestGameClock clock = new TestGameClock();
        clock.advance(90);
        assertEquals(450, clock.getTotalMinutes());
        assertEquals("07:30", clock.getTimeString());
    }

    @Test
    void advanceCrossesDay() {
        TestGameClock clock = new TestGameClock();
        clock.advance(1440);
        assertEquals(1800, clock.getTotalMinutes());
        assertEquals(2, clock.getGameDay());
    }

    @Test
    void advanceZeroIsNoOp() {
        TestGameClock clock = new TestGameClock();
        clock.advance(0);
        assertEquals(360, clock.getTotalMinutes());
    }

    @Test
    void advanceRejectsNegative() {
        TestGameClock clock = new TestGameClock();
        assertThrows(IllegalArgumentException.class, () -> clock.advance(-1));
    }

    @Test
    void setGameDayKeepsTimeOfDay() {
        TestGameClock clock = new TestGameClock();
        clock.setGameDay(3);
        assertEquals(3, clock.getGameDay());
        assertEquals("06:00", clock.getTimeString());
    }

    @Test
    void setGameHourKeepsDay() {
        TestGameClock clock = new TestGameClock();
        clock.setGameHour(7);
        assertEquals(1, clock.getGameDay());
        assertEquals(7, clock.getGameHour());
    }

    @Test
    void constructorWithTotalMinutesRestoresSave() {
        GameClock clock = new TestGameClock(1800);
        assertEquals(1800, clock.getTotalMinutes());
        assertEquals(2, clock.getGameDay());
    }
}
