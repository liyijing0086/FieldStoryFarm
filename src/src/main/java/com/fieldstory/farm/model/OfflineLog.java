package com.fieldstory.farm.model;

import java.util.List;
import java.util.Objects;

/**
 * B 模块 P2 一次离线返回摘要。
 *
 * @param effectiveOfflineMinutes 本次实际结算的现实分钟数（最多 72）
 * @param days                    按游戏日分组的玩家可读日志
 */
public record OfflineLog(long effectiveOfflineMinutes, List<DailyOfflineLog> days) {

    public OfflineLog {
        if (effectiveOfflineMinutes < 0) {
            throw new IllegalArgumentException("effectiveOfflineMinutes must not be negative");
        }
        Objects.requireNonNull(days, "days");
        days = List.copyOf(days);
    }
}
