package com.fieldstory.farm.service.economy.impl;

import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.GraduationState;
import com.fieldstory.farm.service.FarmScoreService;
import com.fieldstory.farm.service.GraduationService;

import java.util.Objects;

/**
 * {@link GraduationService} 默认实现（E 模块 P3）。
 *
 * <p>毕业唯一条件为 {@code FarmScore == 147}（验收规范 §一百二十二）。本实现只依据
 * {@link FarmScoreService#totalScore()}，<b>绝不</b>依据套装数、传说数或 FarmRank 判定
 * （P3 禁止项，验收规范 §一百五十二）。
 *
 * <p>首次达到 147 时写入 {@link GraduationState}（含毕业时刻世界时间与游戏日），
 * 并只触发一次；之后重复调用始终返回 {@code false}（验收规范 §一百二十九）。
 */
public class BasicGraduationService implements GraduationService {

    private final GameState gameState;
    private final FarmScoreService farmScoreService;

    public BasicGraduationService(GameState gameState, FarmScoreService farmScoreService) {
        this.gameState = Objects.requireNonNull(gameState, "gameState 不能为空");
        this.farmScoreService = Objects.requireNonNull(farmScoreService, "farmScoreService 不能为空");
    }

    @Override
    public boolean isGraduated() {
        return gameState.getGraduation().isGraduated();
    }

    @Override
    public boolean evaluateAndGraduate() {
        GraduationState graduation = gameState.getGraduation();
        if (graduation.isGraduated()) {
            return false;
        }
        if (farmScoreService.totalScore() != FarmScoreService.MAX_SCORE) {
            return false;
        }
        graduation.setGraduated(true);
        graduation.setGraduationWorldTime(gameState.getWorldTotalMinutes());
        graduation.setGraduationGameDay(gameState.getGameDay());
        return true;
    }

    @Override
    public GraduationState getState() {
        return gameState.getGraduation();
    }
}
