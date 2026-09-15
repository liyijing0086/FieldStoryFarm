package com.fieldstory.farm.service.economy.impl;

import com.fieldstory.farm.model.DecorationSet;
import com.fieldstory.farm.model.DecorationType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.SetCollectionState;
import com.fieldstory.farm.service.DecorationService;
import com.fieldstory.farm.service.SetService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * {@link SetService} 默认实现（E 模块 P3）。
 *
 * <p>判定口径取自验收规范 §一百一十五~§一百一十八：某套装完成的充要条件是
 * 「四个成员 {@link DecorationType} 全部拥有（{@code getOwnedCount > 0}）
 * <b>且</b>全部处于放置状态（{@code getPlacedCount > 0}）」。
 *
 * <ul>
 *   <li>满足 → 写入 collected（永久）与 active；</li>
 *   <li>不满足 → 只移除 active；collected 永不回退，故拆装任意次套装分都只算一次
 *       （验收规范 §一百二十）。</li>
 * </ul>
 *
 * <p>只读 {@link DecorationService}、只写 {@code GameState.setCollection}，不持有额外状态。
 */
public class BasicSetService implements SetService {

    private final DecorationService decorationService;
    private final SetCollectionState state;

    /**
     * 绑定装饰服务与会话状态构造。
     *
     * @param decorationService 装饰业务入口（不得为 null）
     * @param gameState         会话状态，其 {@code setCollection} 为读写目标（不得为 null）
     */
    public BasicSetService(DecorationService decorationService, GameState gameState) {
        this.decorationService = Objects.requireNonNull(decorationService, "decorationService");
        Objects.requireNonNull(gameState, "gameState");
        this.state = gameState.getSetCollection();
    }

    @Override
    public List<DecorationSet> allSets() {
        return List.of(DecorationSet.values());
    }

    @Override
    public boolean isCollected(DecorationSet set) {
        return set != null && state.getCollected().contains(set.getId());
    }

    @Override
    public boolean isActive(DecorationSet set) {
        return set != null && state.getActive().contains(set.getId());
    }

    @Override
    public int collectedCount() {
        return state.getCollected().size();
    }

    @Override
    public int activeCount() {
        return state.getActive().size();
    }

    @Override
    public Set<String> getCollectedSetIds() {
        return Set.copyOf(state.getCollected());
    }

    @Override
    public Set<String> getActiveSetIds() {
        return Set.copyOf(state.getActive());
    }

    @Override
    public List<String> getActiveBuffDescriptions() {
        List<String> buffs = new ArrayList<>();
        for (DecorationSet set : DecorationSet.values()) {
            if (isActive(set)) {
                buffs.add(set.getDisplayName() + "：" + set.getBuffDescription());
            }
        }
        return buffs;
    }

    @Override
    public void refresh() {
        for (DecorationSet set : DecorationSet.values()) {
            boolean allOwned = hasAllMembers(set, true);
            boolean allPlaced = hasAllMembers(set, false);
            if (allOwned && allPlaced) {
                state.getCollected().add(set.getId());
                state.getActive().add(set.getId());
            } else {
                // collected 是「曾经完成」的永久记录，绝不在此回退（验收规范 §一百一十八）。
                state.getActive().remove(set.getId());
            }
        }
    }

    private boolean hasAllMembers(DecorationSet set, boolean owned) {
        for (DecorationType member : set.getMembers()) {
            int count = owned
                    ? decorationService.getOwnedCount(member)
                    : decorationService.getPlacedCount(member);
            if (count <= 0) {
                return false;
            }
        }
        return true;
    }
}
