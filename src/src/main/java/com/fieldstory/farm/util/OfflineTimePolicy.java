package com.fieldstory.farm.util;

/**
 * B 模块 P2 离线时间规则。
 *
 * <p>正式规则：1 现实分钟 = 1 游戏小时；单次离线最多结算
 * 72 现实分钟，即最多推进 72 游戏小时（3 游戏日）。
 * 超出部分不模拟、不补偿。
 *
 * <p>本类只负责纯时间规则，不读取系统时钟。RawOfflineDuration
 * 由统一 GameClock / 启动装配层计算后传入。
 */
public final class OfflineTimePolicy {

    /** 单次离线模拟最大现实分钟数：72 分钟 = 72 游戏小时 = 3 游戏日。 */
    public static final long MAX_OFFLINE_REAL_MINUTES = 72L;

    private OfflineTimePolicy() {
        // 规则工具类，不允许实例化。
    }

    /**
     * 将实际离线分钟裁剪为本次允许模拟的有效离线分钟。
     *
     * @param rawOfflineMinutes 实际离线现实分钟数，必须 >= 0
     * @return min(rawOfflineMinutes, 72)
     * @throws IllegalArgumentException 当 rawOfflineMinutes < 0 时
     */
    public static long effectiveMinutes(long rawOfflineMinutes) {
        if (rawOfflineMinutes < 0) {
            throw new IllegalArgumentException(
                    "rawOfflineMinutes must not be negative: " + rawOfflineMinutes
            );
        }

        return Math.min(
                rawOfflineMinutes,
                MAX_OFFLINE_REAL_MINUTES
        );
    }

    /**
     * 将有效离线现实分钟换算为要推进的游戏小时。
     *
     * <p>正式比例固定为 1 现实分钟 = 1 游戏小时，因此数值保持不变。
     * 本方法不再次执行 72 分钟 cap；调用方应先通过
     * {@link #effectiveMinutes(long)} 得到有效分钟。
     *
     * @param effectiveRealMinutes 有效离线现实分钟数，必须 >= 0
     * @return 对应游戏小时数
     * @throws IllegalArgumentException 当 effectiveRealMinutes < 0 时
     */
    public static long toGameHours(long effectiveRealMinutes) {
        if (effectiveRealMinutes < 0) {
            throw new IllegalArgumentException(
                    "effectiveRealMinutes must not be negative: " + effectiveRealMinutes
            );
        }

        return effectiveRealMinutes;
    }
}
