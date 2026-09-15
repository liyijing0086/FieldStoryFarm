package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.FarmGameModel;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.GraduationState;
import com.fieldstory.farm.service.GraduationService;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * E 模块 P3 毕业触发控制器。
 *
 * <p>在图鉴/套装分发生真实变化后立即同步当前时间并评估 147 分；
 * 146 分永远不会触发。首次成功只向 UI 发出一次事实通知。
 */
public final class GraduationController {

    private final GameState gameState;
    private final FarmGameModel model;
    private final GraduationService graduationService;
    private Consumer<GraduationState> onGraduated = state -> { };

    public GraduationController(GameState gameState,
                                FarmGameModel model,
                                GraduationService graduationService) {
        this.gameState = Objects.requireNonNull(gameState, "gameState");
        this.model = Objects.requireNonNull(model, "model");
        this.graduationService = Objects.requireNonNull(graduationService, "graduationService");
    }

    public void setOnGraduated(Consumer<GraduationState> callback) {
        this.onGraduated = callback == null ? state -> { } : callback;
    }

    /**
     * 立即评估当前分数。首次达到 147 返回 true 并通知 UI；重复调用幂等。
     */
    public boolean evaluateNow() {
        gameState.setGameDay(model.getGameClock().getGameDay());
        gameState.setWorldTotalMinutes(model.getWorldTimeTotalMinutes());
        boolean first = graduationService.evaluateAndGraduate();
        if (first) {
            onGraduated.accept(graduationService.getState());
        }
        return first;
    }

    public boolean isGraduated() {
        return graduationService.isGraduated();
    }
}
