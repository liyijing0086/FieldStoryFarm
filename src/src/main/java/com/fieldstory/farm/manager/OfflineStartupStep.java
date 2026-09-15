package com.fieldstory.farm.manager;

import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.model.OfflineSimulationResult;
import com.fieldstory.farm.service.OfflineSimulationService;

/**
 * E 场景组装/启动集成：P2 离线启动顺序中的「离线一段」。
 *
 * <p>按《FSF_P0-P4功能实现与验收规范》§八十四固定启动顺序，离线部分为：
 * <pre>
 *   GameClock.calculateOfflineDuration()
 *     → OfflineSimulationService.simulate(rawMinutes)
 *     → 事务保存模拟后状态
 *     → LogService.buildOfflineLog(result)                         （B 模块）
 *     → EffectiveOfflineDuration &gt; 0 时显示一次 OfflineLogPopupView（B 模块）
 * </pre>
 *
 * <p>本步骤只负责 E 的接线部分：从<b>统一</b> {@link GameClock} 取原始离线真实分钟
 * （正式游戏时间统一走 GameClock，此处绝不读取系统时钟），再原样交给 B 的
 * {@link OfflineSimulationService}。是否需要落盘由调用方依据结果判定。
 * 离线日志与弹窗属 B 模块，本步骤不涉及。
 */
public final class OfflineStartupStep {

    private OfflineStartupStep() {
        // 工具类，禁止实例化
    }

    /**
     * 执行「离线一段」：算离线时长 → 调 B 的离线模拟。
     *
     * @param clock      统一游戏时钟（提供原始离线真实分钟）
     * @param simulation B 的离线模拟服务；为 {@code null} 时直接跳过
     * @return 模拟结果；未注入服务时返回 {@code null}（调用方据此跳过落盘）
     */
    public static OfflineSimulationResult run(GameClock clock, OfflineSimulationService simulation) {
        if (simulation == null) {
            return null;
        }
        long rawOfflineMinutes = clock.calculateOfflineDuration();
        return simulation.simulate(rawOfflineMinutes);
    }
}
