package com.fieldstory.farm.service.economy.impl;

import com.fieldstory.farm.model.DailyOfflineLog;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.HarvestLog;
import com.fieldstory.farm.model.OfflineDaySummary;
import com.fieldstory.farm.model.OfflineLog;
import com.fieldstory.farm.model.OfflineOccurrence;
import com.fieldstory.farm.model.OfflineSimulationResult;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.service.LogService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * P2 统一日志服务默认实现。
 *
 * <p>B 模块职责：把 {@link OfflineOccurrence} 结构化离线事实转换为玩家可读叙事；
 * 不重新推导天气、成长、枯萎、事件或奖励规则。
 *
 * <p>C 模块职责协作：按追加顺序保存 {@link HarvestLog} 内存日志快照，供完整收获事务
 * 使用。最终 SQLite 持久化仍由 E 模块负责。
 */
public class BasicLogService implements LogService {

    /** 收获日志注册表（按追加顺序）。 */
    private final List<HarvestLog> harvestLogs = new ArrayList<>();

    @Override
    public Optional<OfflineLog> buildOfflineLog(OfflineSimulationResult result) {
        Objects.requireNonNull(result, "result");
        if (!result.hasOfflineProgress()) {
            return Optional.empty();
        }

        List<DailyOfflineLog> days = new ArrayList<>();
        for (OfflineDaySummary summary : result.dailySummaries()) {
            List<String> lines = new ArrayList<>();
            for (OfflineOccurrence occurrence : summary.occurrences()) {
                String line = lineFor(occurrence);
                if (line != null && !line.isBlank()) {
                    lines.add(line);
                }
            }
            if (!lines.isEmpty()) {
                days.add(new DailyOfflineLog(summary.gameDay(), lines));
            }
        }

        return Optional.of(new OfflineLog(result.effectiveOfflineMinutes(), days));
    }

    @Override
    public void append(HarvestLog log) {
        harvestLogs.add(Objects.requireNonNull(log, "日志不能为空"));
    }

    @Override
    public List<HarvestLog> listAll() {
        return Collections.unmodifiableList(new ArrayList<>(harvestLogs));
    }

    private static String lineFor(OfflineOccurrence occurrence) {
        if (occurrence instanceof OfflineOccurrence.Weather value) {
            return weatherLine(value.weather());
        }
        if (occurrence instanceof OfflineOccurrence.AutoWater value) {
            return value.count() + "株" + value.cropType().getDisplayName() + "获得了自动补水";
        }
        if (occurrence instanceof OfflineOccurrence.Growth value) {
            return value.count() + "株" + value.cropType().getDisplayName()
                    + growthStageText(value.stage());
        }
        if (occurrence instanceof OfflineOccurrence.Mature value) {
            return value.count() + "株" + value.cropType().getDisplayName() + "成熟";
        }
        if (occurrence instanceof OfflineOccurrence.Withered value) {
            return value.count() + "株" + value.cropType().getDisplayName() + "枯萎";
        }
        if (occurrence instanceof OfflineOccurrence.Event value) {
            return eventLine(value.eventType());
        }
        if (occurrence instanceof OfflineOccurrence.LegendaryOpportunity value) {
            return value.count() + "株" + value.cropType().getDisplayName() + "获得传奇潜力";
        }
        if (occurrence instanceof OfflineOccurrence.Reward value) {
            return "🎁 " + value.description();
        }
        return null;
    }

    private static String weatherLine(WeatherType weather) {
        return switch (weather) {
            case SUNNY -> "☀ 天气晴朗";
            case RAIN -> "🌧 下了一场雨";
            case DROUGHT -> "☀ 遭遇干旱天气";
            case GREEN_RAIN -> "🌿 出现绿雨";
        };
    }

    private static String growthStageText(GrowthStage stage) {
        return switch (stage) {
            case SEED -> "处于种子阶段";
            case SPROUT -> "进入幼苗阶段";
            case GROWING -> "进入成长阶段";
            case MATURE -> "成熟";
            case WITHERED -> "枯萎";
        };
    }

    private static String eventLine(EventType event) {
        return switch (event) {
            case METEOR_SHOWER -> "🌠 流星夜降临农场";
            case MYSTERY_MERCHANT -> "🧙 神秘商人来到农场";
            case ANIMAL_VISIT -> "🐿 小动物来到农场拜访";
            case RAINBOW_DAY -> "🌈 彩虹日降临农场";
            case NONE -> null;
        };
    }
}
