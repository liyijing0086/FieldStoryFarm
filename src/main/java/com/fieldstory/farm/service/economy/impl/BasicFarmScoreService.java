package com.fieldstory.farm.service.economy.impl;

import com.fieldstory.farm.model.CollectionState;
import com.fieldstory.farm.model.FarmScoreBreakdown;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.SetCollectionState;
import com.fieldstory.farm.service.CollectionService;
import com.fieldstory.farm.service.FarmScoreService;

import java.util.Objects;

/**
 * {@link FarmScoreService} 默认实现（E 模块 P3）。
 *
 * <p>计分口径严格对齐验收规范 §一百一十九 / 规则文档 §七十五，且<b>禁止重复计分</b>
 * （验收规范 §一百二十）：
 * <ul>
 *   <li>装饰分 = 已收集装饰种类数 × 3，上限 14×3=42（重复购买同一装饰不计）；</li>
 *   <li>作物图鉴分 = 已收集图鉴项数 × 2，上限 15×2=30；</li>
 *   <li>传说分 = 已获得传说种类数 × 10，上限 3×10=30（收获多株同种传说不计）；</li>
 *   <li>套装分 = 已收集套装数 × 15，上限 3×15=45（反复拆装不计）。</li>
 * </ul>
 * 各项均对上限做钳制，保证总分不超过 147，即使脏数据混入也不会算出超满分。
 */
public class BasicFarmScoreService implements FarmScoreService {

    /** 套装目标数：3 套（验收规范 §一百一十九）。套装规则属 B 模块，这里只用于钳制。 */
    private static final int SET_TARGET = 3;

    private final CollectionState collection;
    private final SetCollectionState setCollection;

    /**
     * 绑定会话状态构造。
     *
     * @param gameState 会话状态（不得为 null）
     */
    public BasicFarmScoreService(GameState gameState) {
        Objects.requireNonNull(gameState, "gameState 不能为空");
        this.collection = gameState.getCollection();
        this.setCollection = gameState.getSetCollection();
    }

    @Override
    public FarmScoreBreakdown breakdown() {
        int decorations = clamp(collection.getDecorations().size(), CollectionService.DECORATION_TARGET);
        int crops = clamp(countCrops(), CollectionService.CROP_TARGET);
        int legendaries = clamp(collection.getLegendaries().size(), CollectionService.LEGENDARY_TARGET);
        int sets = clamp(setCollection.getCollected().size(), SET_TARGET);
        return new FarmScoreBreakdown(
                decorations * DECORATION_POINTS,
                crops * CROP_POINTS,
                legendaries * LEGENDARY_POINTS,
                sets * SET_POINTS);
    }

    /** 已收集作物图鉴项数（只统计 COLLECTED）。 */
    private int countCrops() {
        int count = 0;
        for (var status : collection.getCrops().values()) {
            if (status != null && status.isCollected()) {
                count++;
            }
        }
        return count;
    }

    private static int clamp(int value, int max) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, max);
    }
}
