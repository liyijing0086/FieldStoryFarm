package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.service.WorldTimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link BasicWorldTimeService} 测试（A 模块 P2；验收规范 §一百零七 P2 必须测试的关键边界）。
 *
 * <p>三个方法全部为纯函数：不读系统时间、不依赖 GameClock、不使用 RandomProvider，
 * 世界小时直接以 long 入参断言（决策 D14 口径）。
 */
class BasicWorldTimeServiceTest {

    private WorldTimeService worldTimeService;

    @BeforeEach
    void setUp() {
        worldTimeService = new BasicWorldTimeService();
    }

    // ===== 1. 世界小时口径（决策 D14：gameDay×24+gameHour，long）=====

    /** 第 3 天 14 点 → 3×24+14 = 86。 */
    @Test
    void toWorldHourDay3Hour14Is86() {
        assertEquals(86L, worldTimeService.toWorldHour(3, 14));
    }

    // ===== 2. 离线结算上限（验收规范 §八十三：min(raw, 72)，负数/0 返回 0）=====

    /** 未超上限：30 → 30。 */
    @Test
    void cap30Keeps30() {
        assertEquals(30L, worldTimeService.capOfflineRealMinutes(30L));
    }

    /** 离开 8 小时（验收 §一百零七）：480 → 封顶 72。 */
    @Test
    void cap480ClampsTo72() {
        assertEquals(72L, worldTimeService.capOfflineRealMinutes(480L));
    }

    /** 恰好等于上限：72 → 72。 */
    @Test
    void cap72Keeps72() {
        assertEquals(72L, worldTimeService.capOfflineRealMinutes(72L));
    }

    /** 0 → 0。 */
    @Test
    void capZeroReturnsZero() {
        assertEquals(0L, worldTimeService.capOfflineRealMinutes(0L));
    }

    /** 负数 → 0。 */
    @Test
    void capNegativeReturnsZero() {
        assertEquals(0L, worldTimeService.capOfflineRealMinutes(-5L));
    }

    // ===== 3. 分段切点（验收规范 §八十八：日边界/事件结束/作物成熟 三切点）=====

    /** 跨 3 个游戏日（验收 §一百零七）：10→70 含日边界 24、48。 */
    @Test
    void cutPointsAcross3DaysIncludeDayBoundaries() {
        assertEquals(List.of(10L, 24L, 48L, 70L),
                worldTimeService.segmentCutPoints(10L, 70L, null, Collections.emptyList()));
    }

    /** 事件结束时刻落在区间中 → 插入切点。 */
    @Test
    void cutPointsIncludeEventEndInsideRange() {
        assertEquals(List.of(10L, 24L, 30L, 48L, 70L),
                worldTimeService.segmentCutPoints(10L, 70L, 30L, Collections.emptyList()));
    }

    /** 事件结束为 null → 忽略（契约条款）。 */
    @Test
    void cutPointsIgnoreNullEventEnd() {
        assertEquals(List.of(10L, 24L, 48L, 70L),
                worldTimeService.segmentCutPoints(10L, 70L, null, Collections.emptyList()));
    }

    /** 作物成熟时刻多点 → 全部插入。 */
    @Test
    void cutPointsIncludeMultipleCropMatureTimes() {
        assertEquals(List.of(10L, 24L, 25L, 48L, 50L, 70L),
                worldTimeService.segmentCutPoints(10L, 70L, null, List.of(25L, 50L)));
    }

    /** 成熟时刻在区间外（5、90）→ 丢弃。 */
    @Test
    void cutPointsDropOutOfRangeCropMatureTimes() {
        assertEquals(List.of(10L, 24L, 48L, 70L),
                worldTimeService.segmentCutPoints(10L, 70L, null, List.of(5L, 90L)));
    }

    /** 重复切点（事件结束 48 撞日边界、成熟 24 撞日边界）去重，乱序输入升序输出。 */
    @Test
    void cutPointsDedupeAndSortAscending() {
        assertEquals(List.of(10L, 24L, 25L, 48L, 50L, 70L),
                worldTimeService.segmentCutPoints(10L, 70L, 48L, List.of(50L, 24L, 25L)));
    }
}
