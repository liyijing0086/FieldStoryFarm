package com.fieldstory.farm.model;

import java.util.List;
import java.util.Objects;

/**
 * B 模块 P2 一次离线模拟的结构化结果。
 *
 * <p>Model 只表达本次离线模拟“处理了多久、是否触发上限、发生了什么”，
 * 不负责计算成长、天气、枯萎、事件、传奇或持久化。
 *
 * @param rawOfflineMinutes       实际离线现实分钟数
 * @param effectiveOfflineMinutes 应用 72 分钟上限后的有效离线现实分钟数
 * @param simulatedGameHours      实际推进的游戏小时数
 * @param dailySummaries          按游戏日汇总的结构化模拟事实
 */
public record OfflineSimulationResult(
        long rawOfflineMinutes,
        long effectiveOfflineMinutes,
        long simulatedGameHours,
        List<OfflineDaySummary> dailySummaries
) {

    public OfflineSimulationResult {
        if (rawOfflineMinutes < 0) {
            throw new IllegalArgumentException(
                    "rawOfflineMinutes must not be negative: " + rawOfflineMinutes
            );
        }
        if (effectiveOfflineMinutes < 0) {
            throw new IllegalArgumentException(
                    "effectiveOfflineMinutes must not be negative: "
                            + effectiveOfflineMinutes
            );
        }
        if (simulatedGameHours < 0) {
            throw new IllegalArgumentException(
                    "simulatedGameHours must not be negative: " + simulatedGameHours
            );
        }
        if (effectiveOfflineMinutes > rawOfflineMinutes) {
            throw new IllegalArgumentException(
                    "effectiveOfflineMinutes must not exceed rawOfflineMinutes"
            );
        }
        if (simulatedGameHours != effectiveOfflineMinutes) {
            throw new IllegalArgumentException(
                    "P2 rule requires 1 real offline minute = 1 simulated game hour"
            );
        }

        Objects.requireNonNull(dailySummaries, "dailySummaries");
        dailySummaries = List.copyOf(dailySummaries);
    }

    /** @return 本次是否存在有效离线推进。 */
    public boolean hasOfflineProgress() {
        return effectiveOfflineMinutes > 0;
    }

    /** @return 实际离线时间是否超过 72 分钟上限。 */
    public boolean wasCapped() {
        return rawOfflineMinutes > effectiveOfflineMinutes;
    }
}
