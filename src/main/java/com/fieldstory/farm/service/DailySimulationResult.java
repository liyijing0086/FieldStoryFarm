package com.fieldstory.farm.service;

import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.WeatherType;

import java.util.List;
import java.util.UUID;

/**
 * 每日结算摘要（在线/离线共用）。
 *
 * <p>第三轮开始，摘要除数量外还携带两类“事实”：事件真实起止窗口，以及真正进入
 * 枯萎概率区间的 cropUuid。这样 Memory 层只记录 A 世界引擎已经判定出的事实，
 * 不复制枯萎概率公式，也不会让在线/离线各自猜一遍。
 */
public record DailySimulationResult(long gameDay, WeatherType weather,
                                    EventType event, int maturedCount,
                                    int witheredCount, int rainHydratedCount,
                                    long eventStartWorldTime,
                                    long eventEndWorldTime,
                                    List<UUID> witherRiskCropUuids) {

    /** 兼容第一/二轮及既有测试的 6 参数构造。 */
    public DailySimulationResult(long gameDay, WeatherType weather,
                                 EventType event, int maturedCount,
                                 int witheredCount, int rainHydratedCount) {
        this(gameDay, weather, event, maturedCount, witheredCount,
                rainHydratedCount, -1L, -1L, List.of());
    }

    public DailySimulationResult {
        witherRiskCropUuids = witherRiskCropUuids == null
                ? List.of()
                : List.copyOf(witherRiskCropUuids);
    }
}
