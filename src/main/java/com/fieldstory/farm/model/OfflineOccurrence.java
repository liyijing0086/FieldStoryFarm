package com.fieldstory.farm.model;

import java.util.Objects;

/**
 * B 模块 P2 离线世界中的一条结构化事实。
 *
 * <p>本模型只表达“离线期间发生了什么”，不承担成长、天气、枯萎、
 * 事件或传奇计算。事实应由统一 WorldSimulationService 产生，B 模块
 * 后续的 LogService 只负责将这些事实转换为玩家可读叙事。
 */
public sealed interface OfflineOccurrence {

    /** 当日天气事实。 */
    record Weather(WeatherType weather) implements OfflineOccurrence {
        public Weather {
            Objects.requireNonNull(weather, "weather");
        }
    }

    /** 雨天等世界规则产生的自动补水事实。 */
    record AutoWater(CropType cropType, int count) implements OfflineOccurrence {
        public AutoWater {
            Objects.requireNonNull(cropType, "cropType");
            requireNonNegative(count, "count");
        }
    }

    /** 作物进入某成长阶段的汇总事实。 */
    record Growth(CropType cropType, GrowthStage stage, int count)
            implements OfflineOccurrence {
        public Growth {
            Objects.requireNonNull(cropType, "cropType");
            Objects.requireNonNull(stage, "stage");
            requireNonNegative(count, "count");
        }
    }

    /** 作物离线成熟事实；成熟不代表自动收获或自动出售。 */
    record Mature(CropType cropType, int count) implements OfflineOccurrence {
        public Mature {
            Objects.requireNonNull(cropType, "cropType");
            requireNonNegative(count, "count");
        }
    }

    /** 作物离线枯萎事实。 */
    record Withered(CropType cropType, int count) implements OfflineOccurrence {
        public Withered {
            Objects.requireNonNull(cropType, "cropType");
            requireNonNegative(count, "count");
        }
    }

    /** 随机事件发生事实；事件规则仍归 D 模块 EventService。 */
    record Event(EventType eventType) implements OfflineOccurrence {
        public Event {
            Objects.requireNonNull(eventType, "eventType");
        }
    }

    /** 作物获得传奇潜力/机会的汇总事实；传奇判定规则仍归 C 模块。 */
    record LegendaryOpportunity(CropType cropType, int count)
            implements OfflineOccurrence {
        public LegendaryOpportunity {
            Objects.requireNonNull(cropType, "cropType");
            requireNonNegative(count, "count");
        }
    }

    /** 即时事件等产生的玩家奖励描述。 */
    record Reward(String description) implements OfflineOccurrence {
        public Reward {
            Objects.requireNonNull(description, "description");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative: " + value);
        }
    }
}
