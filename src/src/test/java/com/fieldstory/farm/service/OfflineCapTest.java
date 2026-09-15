package com.fieldstory.farm.service;

import com.fieldstory.farm.service.impl.BasicWorldTimeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * B 模块 P2 离线上限集成边界测试。
 *
 * <p>B 不再维护 OfflineTimePolicy；离线上限统一复用 A 的 WorldTimeService，
 * 防止出现第二套 72 分钟规则。
 */
class OfflineCapTest {

    private final WorldTimeService worldTimeService = new BasicWorldTimeService();

    @Test
    void thirtyRealMinutesAdvanceThirtyGameHours() {
        long effective = worldTimeService.capOfflineRealMinutes(30L);
        assertEquals(30L, effective);
    }

    @Test
    void eightRealHoursAreCappedAtSeventyTwoGameHours() {
        long effective = worldTimeService.capOfflineRealMinutes(8L * 60L);
        assertEquals(72L, effective);
    }

    @Test
    void exactlySeventyTwoMinutesAreNotReduced() {
        assertEquals(72L, worldTimeService.capOfflineRealMinutes(72L));
    }

    @Test
    void zeroAndNegativeOfflineDurationBecomeZero() {
        assertEquals(0L, worldTimeService.capOfflineRealMinutes(0L));
        assertEquals(0L, worldTimeService.capOfflineRealMinutes(-1L));
    }
}
