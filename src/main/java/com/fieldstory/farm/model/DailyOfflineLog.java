package com.fieldstory.farm.model;

import java.util.List;
import java.util.Objects;

/**
 * B 模块 P2 单个游戏日的玩家可读离线日志。
 *
 * <p>只保存已经格式化好的叙事文本，不承载世界计算。
 *
 * @param gameDay 游戏日
 * @param lines   该日玩家可读日志行
 */
public record DailyOfflineLog(long gameDay, List<String> lines) {

    public DailyOfflineLog {
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);
    }
}
