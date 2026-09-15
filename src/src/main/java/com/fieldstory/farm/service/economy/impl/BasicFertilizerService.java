package com.fieldstory.farm.service.economy.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.item.Inventory;
import com.fieldstory.farm.model.item.ItemType;
import com.fieldstory.farm.service.FertilizeResult;
import com.fieldstory.farm.service.FertilizerService;
import com.fieldstory.farm.service.MemoryService;

import java.util.Objects;

/**
 * {@link FertilizerService} 基础实现。
 *
 * <p>Crop 保存当前生命周期施肥运行态，CropMemory 保存永久事实。施肥成功时两者同步更新；
 * 成长公式只读 Crop，品质/传奇/故事继续读 Memory，避免成长链依赖已经“归档”的对象。
 */
public class BasicFertilizerService implements FertilizerService {

    /** 每次施肥成长加成：+15%（规则文档 §二十六） */
    private static final double GROWTH_BONUS_PER_TIME = 0.15;

    private final MemoryService memoryService;

    public BasicFertilizerService(MemoryService memoryService) {
        this.memoryService = Objects.requireNonNull(memoryService, "记忆服务不能为空");
    }

    @Override
    public FertilizeResult fertilize(Crop crop, CropMemory memory,
                                     Inventory inventory, long gameDay) {
        if (crop == null || memory == null) {
            return FertilizeResult.NO_CROP_OR_MEMORY;
        }

        GrowthStage stage = crop.getGrowthStage();
        if (stage != GrowthStage.SPROUT && stage != GrowthStage.GROWING) {
            return FertilizeResult.NOT_ALLOWED_STAGE;
        }

        // 读档迁移兜底：旧档可能只在 Memory 里有施肥次数；取两者较新事实做校验。
        int effectiveCount = Math.max(crop.getFertilizerCount(), memory.getFertilizerCount());
        long cropLastDay = crop.getLastFertilizedGameDay();
        long memoryLastDay = memory.getLastFertilizeGameDay();
        if (cropLastDay == gameDay || memoryLastDay == gameDay) {
            return FertilizeResult.ALREADY_FERTILIZED_TODAY;
        }
        if (effectiveCount >= MAX_FERTILIZE_PER_LIFE) {
            return FertilizeResult.MAX_TIMES_PER_LIFE;
        }

        if (inventory == null || !inventory.consume(ItemType.FERTILIZER, FERTILIZER_COST)) {
            return FertilizeResult.NOT_ENOUGH_FERTILIZER;
        }

        // 只有库存扣减成功后才提交状态，失败路径保持原子性。
        int nextCount = effectiveCount + 1;
        crop.setFertilizerCount(nextCount);
        crop.setLastFertilizedGameDay(gameDay);

        // Memory 可能来自旧档且次数落后：先对齐到 effectiveCount，再通过 MemoryService 记录事实。
        memory.setFertilizerCount(effectiveCount);
        memoryService.recordFertilizer(memory);
        memory.setLastFertilizeGameDay(gameDay);
        return FertilizeResult.SUCCESS;
    }

    @Override
    public double fertilizerGrowthRate(Crop crop) {
        Objects.requireNonNull(crop, "作物不能为空");
        return bonusForCount(crop.getFertilizerCount());
    }

    @Override
    public double fertilizerGrowthRate(CropMemory memory) {
        Objects.requireNonNull(memory, "档案不能为空");
        return bonusForCount(memory.getFertilizerCount());
    }

    private static double bonusForCount(int count) {
        int normalized = Math.max(0, Math.min(count, MAX_FERTILIZE_PER_LIFE));
        return normalized * GROWTH_BONUS_PER_TIME;
    }
}
