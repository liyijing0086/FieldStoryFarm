package com.fieldstory.farm.service;

import com.fieldstory.farm.model.GraduationState;

/**
 * 毕业服务（E 模块 P3；验收规范 §一百二十二/§一百二十九，规则文档 §七十七）。
 *
 * <p>毕业唯一条件：<b>满收集 FarmScore == 147</b>（14 装饰 + 15 作物图鉴 + 3 传说 + 3 套装）。
 * 明确不是「完成 3 套装即可」，也不是「获得 3 传说即可」，更不是「Rank 达到某级即可」
 * （验收规范 §一百二十二；P3 禁止项 §一百五十二）。
 *
 * <p>触发流程（验收规范 §一百二十九）：第一次达到 147 → 确认 → 写 {@link GraduationState}
 * → 播放毕业 UI → 解锁完整统计。首次动画<b>只触发一次</b>，之后进入游戏保持永恒花园状态。
 */
public interface GraduationService {

    /** 是否已毕业（首次达到 147 后永久为 true）。 */
    boolean isGraduated();

    /**
     * 评估当前 FarmScore：若满 147 且尚未毕业，则写入毕业状态，本次调用返回 {@code true}
     * （表示「首次毕业，应该播放毕业 UI」）；否则返回 {@code false}。
     *
     * <p>可安全重复调用：毕业只记录一次，之后始终返回 {@code false}。
     *
     * @return true = 本次调用完成了首次毕业
     */
    boolean evaluateAndGraduate();

    /** 毕业状态（永不为 null）。 */
    GraduationState getState();
}
