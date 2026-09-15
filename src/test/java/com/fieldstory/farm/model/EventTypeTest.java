package com.fieldstory.farm.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 EventType 枚举测试（D 模块 P0 文档 §8.2）。
 *
 * <p>P0 阶段仅定义枚举常量、禁止业务引用（验收规范 §10），
 * 因此本测试只校验常量集与 {@code name()} 稳定性，防止后续阶段误改枚举名
 * 破坏存档字符串映射（E 模块以枚举 {@code name()} 存取）。
 */
class EventTypeTest {

    @Test
    void containsExactlyFiveP0ReservedConstants() {
        assertEquals(5, EventType.values().length);
    }

    @Test
    void constantNamesAreStable() {
        // 名称即存档字符串（E 模块以 name() 存取），不得随意改动
        assertEquals("METEOR_SHOWER", EventType.METEOR_SHOWER.name());
        assertEquals("MYSTERY_MERCHANT", EventType.MYSTERY_MERCHANT.name());
        assertEquals("ANIMAL_VISIT", EventType.ANIMAL_VISIT.name());
        assertEquals("RAINBOW_DAY", EventType.RAINBOW_DAY.name());
        assertEquals("NONE", EventType.NONE.name());
    }

    @Test
    void valueOfRoundTrips() {
        for (EventType type : EventType.values()) {
            assertNotNull(type);
            assertEquals(type, EventType.valueOf(type.name()));
        }
    }

    @Test
    void displayNameAndIconAreNonEmpty() {
        for (EventType type : EventType.values()) {
            assertNotNull(type.getDisplayName());
            assertNotNull(type.getIcon());
        }
        assertEquals("流星夜", EventType.METEOR_SHOWER.getDisplayName());
        assertEquals("神秘商人", EventType.MYSTERY_MERCHANT.getDisplayName());
        assertEquals("小动物来访", EventType.ANIMAL_VISIT.getDisplayName());
        assertEquals("彩虹日", EventType.RAINBOW_DAY.getDisplayName());
        assertEquals("无事件", EventType.NONE.getDisplayName());
    }

    @Test
    void durationHoursMatchRules() {
        // 规则文档 §四十八~§五十一
        assertEquals(24, EventType.METEOR_SHOWER.getDurationHours());
        assertEquals(12, EventType.MYSTERY_MERCHANT.getDurationHours());
        assertEquals(24, EventType.RAINBOW_DAY.getDurationHours());
        assertEquals(0, EventType.ANIMAL_VISIT.getDurationHours());
        assertEquals(0, EventType.NONE.getDurationHours());
    }

    @Test
    void instantFlagMatchesRules() {
        // 规则文档 §五十：小动物来访为即时事件
        assertTrue(EventType.ANIMAL_VISIT.isInstant());
        assertTrue(EventType.NONE.isInstant());
        assertFalse(EventType.METEOR_SHOWER.isInstant());
        assertFalse(EventType.MYSTERY_MERCHANT.isInstant());
        assertFalse(EventType.RAINBOW_DAY.isInstant());
    }
}
