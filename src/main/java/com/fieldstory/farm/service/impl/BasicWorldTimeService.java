package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.service.WorldTimeService;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * {@link WorldTimeService} 基础实现（A 模块 P2）。
 *
 * <p>全部为纯函数：不读系统时间（规则文档 §八 禁止 System.currentTimeMillis）、
 * 不依赖 GameClock、不 new Random，仅依据入参计算，保证确定性可测。
 *
 * <p>时间换算（规则文档 §5.1）：1 现实分钟 = 1 游戏小时；
 * capOfflineRealMinutes 只做封顶，不换算。
 */
public class BasicWorldTimeService implements WorldTimeService {

    /** 单次离线结算上限：72 现实分钟 = 3 游戏日（规则文档 §七；验收规范 §八十三）。 */
    public static final long MAX_OFFLINE_REAL_MINUTES = 72L;

    /** 1 游戏日 = 24 游戏小时（规则文档 §5.1 时间比例）。 */
    public static final long GAME_HOURS_PER_DAY = 24L;

    @Override
    public long toWorldHour(int gameDay, int gameHour) {
        // 世界小时口径（决策 D14）：gameDay×24+gameHour，long
        return (long) gameDay * GAME_HOURS_PER_DAY + (long) gameHour;
    }

    @Override
    public long capOfflineRealMinutes(long rawOfflineRealMinutes) {
        // min(raw, 72)（验收规范 §八十三）；负数/0 返回 0
        if (rawOfflineRealMinutes <= 0L) {
            return 0L;
        }
        return Math.min(rawOfflineRealMinutes, MAX_OFFLINE_REAL_MINUTES);
    }

    @Override
    public List<Long> segmentCutPoints(long startWorldHour, long endWorldHour,
                                       Long eventEndWorldHour,
                                       List<Long> cropMatureWorldHours) {
        // TreeSet 天然去重 + 升序
        TreeSet<Long> cutPoints = new TreeSet<>();
        cutPoints.add(startWorldHour);
        cutPoints.add(endWorldHour);

        // 游戏日 00:00 边界（验收规范 §八十八）：start 之后的第一个 24 倍数起，
        // 每 24 游戏小时一个；超出 [start, end] 的丢弃
        long firstDayBoundary = Math.floorDiv(startWorldHour, GAME_HOURS_PER_DAY)
                * GAME_HOURS_PER_DAY + GAME_HOURS_PER_DAY;
        for (long boundary = firstDayBoundary;
             boundary <= endWorldHour;
             boundary += GAME_HOURS_PER_DAY) {
            cutPoints.add(boundary);
        }

        // 事件结束时刻（验收规范 §八十八）：null 忽略，越界丢弃
        if (eventEndWorldHour != null
                && eventEndWorldHour >= startWorldHour
                && eventEndWorldHour <= endWorldHour) {
            cutPoints.add(eventEndWorldHour);
        }

        // 作物成熟时刻（验收规范 §八十八）：越界丢弃，重复由 TreeSet 去重
        if (cropMatureWorldHours != null) {
            for (Long matureWorldHour : cropMatureWorldHours) {
                if (matureWorldHour != null
                        && matureWorldHour >= startWorldHour
                        && matureWorldHour <= endWorldHour) {
                    cutPoints.add(matureWorldHour);
                }
            }
        }

        return new ArrayList<>(cutPoints);
    }
}
