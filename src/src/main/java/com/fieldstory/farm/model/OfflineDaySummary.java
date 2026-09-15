package com.fieldstory.farm.model;

import java.util.List;
import java.util.Objects;

/**
 * B 模块 P2 单个游戏日的离线结构化事实汇总。
 *
 * <p>该类型是 WorldSimulationService 与 B 离线日志层之间的数据边界之一。
 * 它只保存状态结果，不实现任何世界规则。
 *
 * @param gameDay     游戏日
 * @param occurrences 当日发生的结构化事实
 */
public record OfflineDaySummary(
        long gameDay,
        List<OfflineOccurrence> occurrences
) {

    public OfflineDaySummary {
        Objects.requireNonNull(occurrences, "occurrences");
        occurrences = List.copyOf(occurrences);
    }
}
