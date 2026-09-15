package com.fieldstory.farm.service;

import com.fieldstory.farm.model.OfflineSimulationResult;

/**
 * B 模块 P2 离线模拟正式入口。
 *
 * <p>本 Service 的职责是离线时间窗口编排：应用离线上限，并将有效游戏时间窗口
 * 委托给 A 模块统一 WorldSimulationService。
 *
 * <p>它不得拥有独立的成长、天气、枯萎、事件或生命记忆算法。
 *
 * <p>当前 B-P2-1A 先冻结此 B 侧接口。默认实现 BasicOfflineSimulationService
 * 必须在 A 模块 WorldSimulationService 的正式 Java 签名冻结后接入，避免 B 私自定义 A 模块接口。
 */
public interface OfflineSimulationService {

    /**
     * 执行一次离线模拟。
     *
     * @param rawOfflineMinutes 实际离线现实分钟数
     * @return 本次离线模拟的结构化结果
     */
    OfflineSimulationResult simulate(long rawOfflineMinutes);
}
