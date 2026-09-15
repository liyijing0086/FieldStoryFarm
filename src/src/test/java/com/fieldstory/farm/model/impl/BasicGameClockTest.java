package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.GameClock;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 BasicGameClock 测试（D 模块 P0 文档 §10.1）。
 *
 * <p>覆盖：初始 360（第 1 天 06:00）、连续 tick、跨日、setTotalMinutes 边界。
 */
class BasicGameClockTest {

    @Test
    void defaultConstructorStartsAtDay1SixAm() {
        GameClock clock = new BasicGameClock();
        assertEquals(360, clock.getTotalMinutes());
        assertEquals(1, clock.getGameDay());
        assertEquals(6, clock.getGameHour());
        assertEquals(0, clock.getGameMinute());
        assertEquals("06:00", clock.getTimeString());
        assertTrue(clock.isDaytime());
    }

    @Test
    void tickSixtyTimesAdvancesOneGameHour() {
        GameClock clock = new BasicGameClock();
        for (int i = 0; i < 60; i++) {
            clock.tick();
        }
        assertEquals(420, clock.getTotalMinutes());
        assertEquals("07:00", clock.getTimeString());
        assertEquals(1, clock.getGameDay());
    }

    @Test
    void tick1440TimesCrossesOneDay() {
        GameClock clock = new BasicGameClock();
        for (int i = 0; i < 1440; i++) {
            clock.tick();
        }
        assertEquals(1800, clock.getTotalMinutes());
        assertEquals(2, clock.getGameDay());
        assertEquals("06:00", clock.getTimeString());
    }

    @Test
    void setTotalMinutesZeroIsMidnightNotDaytime() {
        GameClock clock = new BasicGameClock();
        clock.setTotalMinutes(0);
        assertEquals("00:00", clock.getTimeString());
        assertFalse(clock.isDaytime());
        assertEquals(1, clock.getGameDay());
    }

    @Test
    void daytimeBoundariesAreSixToEighteen() {
        GameClock clock = new BasicGameClock();
        clock.setTotalMinutes(360);
        assertTrue(clock.isDaytime(), "06:00 应为白天");
        clock.setTotalMinutes(1079);
        assertTrue(clock.isDaytime(), "17:59 应为白天");
        clock.setTotalMinutes(1080);
        assertFalse(clock.isDaytime(), "18:00 应为夜晚");
        clock.setTotalMinutes(359);
        assertFalse(clock.isDaytime(), "05:59 应为夜晚");
    }

    @Test
    void constructorWithTotalMinutesRestoresSave() {
        GameClock clock = new BasicGameClock(1800);
        assertEquals(1800, clock.getTotalMinutes());
        assertEquals(2, clock.getGameDay());
        assertEquals("06:00", clock.getTimeString());
    }

    @Test
    void advanceIsEquivalentToTick() {
        GameClock clock = new BasicGameClock();
        clock.advance();
        assertEquals(361, clock.getTotalMinutes());
        assertEquals("06:01", clock.getTimeString());
    }

    @Test
    void plantWorldTimeAdaptedFromDayAndHour() {
        // 决策 D14：A 侧 plantWorldTime = getGameDay() * 24 + getGameHour()（游戏小时）
        GameClock clock = new BasicGameClock();
        assertEquals(1 * 24 + 6, clock.getGameDay() * 24 + clock.getGameHour());
        clock.setTotalMinutes(1800); // 第 2 天 06:00
        assertEquals(2 * 24 + 6, clock.getGameDay() * 24 + clock.getGameHour());
        clock.setTotalMinutes(0); // 第 1 天 00:00
        assertEquals(1 * 24 + 0, clock.getGameDay() * 24 + clock.getGameHour());
    }

    @Test
    void realTimeIsCurrentSystemTime() {
        GameClock clock = new BasicGameClock();
        assertNotNull(clock.getRealTime());
    }

    @Test
    void offlineDurationWithoutSavedRealTimeIsZero() {
        GameClock clock = new BasicGameClock();
        assertEquals(0L, clock.calculateOfflineDuration(),
                "没有可靠退出时间基准时不得猜测离线时长");
    }

    @Test
    void offlineDurationUsesSavedRealTimeInWholeMinutes() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 14, 12, 0);
        GameClock clock = new FixedNowClock(now);
        clock.setLastRealTime(now.minusMinutes(30));

        assertEquals(30L, clock.calculateOfflineDuration());
    }

    @Test
    void offlineDurationNeverBecomesNegativeWhenSystemClockMovesBackwards() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 14, 12, 0);
        GameClock clock = new FixedNowClock(now);
        clock.setLastRealTime(now.plusMinutes(5));

        assertEquals(0L, clock.calculateOfflineDuration());
    }

    @Test
    void setTotalMinutesRejectsNegative() {
        GameClock clock = new BasicGameClock();
        assertThrows(IllegalArgumentException.class, () -> clock.setTotalMinutes(-1),
                "totalMinutes < 0 应抛 IllegalArgumentException");
    }

    @Test
    void constructorRejectsNegativeTotalMinutes() {
        assertThrows(IllegalArgumentException.class, () -> new BasicGameClock(-1),
                "构造器 totalMinutes < 0 应抛 IllegalArgumentException");
    }

    @Test
    void setTotalMinutesZeroIsAllowed() {
        GameClock clock = new BasicGameClock();
        clock.setTotalMinutes(0);
        assertEquals(0, clock.getTotalMinutes());
    }
    /** 固定现实时间，保证离线时长测试完全可重复。 */
    private static final class FixedNowClock extends BasicGameClock {
        private final LocalDateTime now;

        private FixedNowClock(LocalDateTime now) {
            this.now = now;
        }

        @Override
        public LocalDateTime getRealTime() {
            return now;
        }
    }
}
