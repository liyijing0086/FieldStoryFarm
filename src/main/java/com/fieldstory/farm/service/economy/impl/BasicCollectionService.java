package com.fieldstory.farm.service.economy.impl;

import com.fieldstory.farm.model.CollectionState;
import com.fieldstory.farm.model.CollectionStatus;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.service.CollectionService;

import java.util.Objects;

/**
 * {@link CollectionService} 默认实现（E 模块 P3）。
 *
 * <p>直接维护 {@link GameState} 中的 {@link CollectionState}，因此图鉴进度随存档持久化。
 * 收集遵循两条铁律：
 * <ul>
 *   <li><b>只前进不回退</b>：{@link CollectionStatus#advanceTo} 保证已收集不会被降级
 *       （图鉴永久保存，验收规范 §一百一十二）；</li>
 *   <li><b>去重</b>：作物图鉴以「作物 × 品质」为键、装饰以类型 id、传说以作物类型为键，
 *       重复触发不产生新条目，从而天然抑制重复计分（验收规范 §一百二十）。</li>
 * </ul>
 */
public class BasicCollectionService implements CollectionService {

    private final CollectionState collection;

    /**
     * 绑定会话状态构造。
     *
     * @param gameState 会话状态（不得为 null），图鉴写入其 {@code getCollection()}
     */
    public BasicCollectionService(GameState gameState) {
        Objects.requireNonNull(gameState, "gameState 不能为空");
        this.collection = gameState.getCollection();
    }

    @Override
    public void discoverCrop(CropType cropType, Quality quality) {
        collection.advanceCrop(cropType, quality, CollectionStatus.DISCOVERED);
    }

    @Override
    public void collectCrop(CropType cropType, Quality quality) {
        collection.advanceCrop(cropType, quality, CollectionStatus.COLLECTED);
    }

    @Override
    public CollectionStatus cropStatus(CropType cropType, Quality quality) {
        return collection.cropStatus(cropType, quality);
    }

    @Override
    public int collectedCropCount() {
        int count = 0;
        for (CollectionStatus status : collection.getCrops().values()) {
            if (status.isCollected()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void collectDecoration(String decorationType) {
        if (decorationType == null || decorationType.isBlank()) {
            return;
        }
        collection.getDecorations().add(decorationType.trim());
    }

    @Override
    public boolean isDecorationCollected(String decorationType) {
        return decorationType != null && collection.getDecorations().contains(decorationType.trim());
    }

    @Override
    public int collectedDecorationCount() {
        return collection.getDecorations().size();
    }

    @Override
    public void collectLegendary(CropType cropType) {
        if (cropType != null) {
            collection.getLegendaries().add(cropType);
        }
    }

    @Override
    public boolean isLegendaryCollected(CropType cropType) {
        return cropType != null && collection.getLegendaries().contains(cropType);
    }

    @Override
    public int collectedLegendaryCount() {
        return collection.getLegendaries().size();
    }

    @Override
    public CollectionState getState() {
        return collection;
    }
}
