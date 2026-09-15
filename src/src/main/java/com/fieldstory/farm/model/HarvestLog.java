package com.fieldstory.farm.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 收获日志模型（C 模块 品质与传说域，P2 收获事务第⑮步）。
 *
 * <p>每次收获事务成功提交时写入一条不可变记录（规则文档 §六十八 ⑮；
 * 验收规范 §一百零三"记录HarvestLog"），用于追溯每株作物的收获结果
 * 与离线日志 UI（验收规范 §一百零五）。
 *
 * <p>只保存结论，不含计算逻辑（脚手架 §七.1 Model 原则）；
 * 与 {@link CropMemory} 分工：CropMemory 记成长经历，HarvestLog 记收获结果。
 * P2 使用内存态；持久化由 E 存档统一处理（与 CropMemory 同模式）。
 */
public class HarvestLog {

    /** 作物唯一标识（与 CropMemory.cropUuid 同源） */
    private final UUID cropUuid;

    /** 作物类型 */
    private final CropType cropType;

    /** 最终品质（含 LEGENDARY） */
    private final Quality quality;

    /** 是否传说突破成功 */
    private final boolean legendary;

    /** 收获售价（金币，含品质倍率与事件倍率） */
    private final int sellPrice;

    /** 品质肥料奖励数量（规则文档 §六十六） */
    private final int fertilizerReward;

    /** 首次传说奖励金币（规则文档 §六十七；非首次为 0） */
    private final int firstRewardGold;

    /** 最终生命故事（规则文档 §七十） */
    private final String finalStory;

    /** 收获时刻世界时间（游戏小时，决策 D14 口径） */
    private final long harvestWorldTime;

    /**
     * 按收获结果构建日志。
     *
     * @param cropUuid          作物唯一标识
     * @param cropType          作物类型
     * @param quality           最终品质
     * @param legendary         是否传说突破成功
     * @param sellPrice         收获售价
     * @param fertilizerReward  肥料奖励数量
     * @param firstRewardGold   首次传说奖励金币（非首次为 0）
     * @param finalStory        最终生命故事
     * @param harvestWorldTime  收获时刻世界时间（游戏小时）
     */
    public HarvestLog(UUID cropUuid, CropType cropType, Quality quality,
                      boolean legendary, int sellPrice, int fertilizerReward,
                      int firstRewardGold, String finalStory, long harvestWorldTime) {
        this.cropUuid = Objects.requireNonNull(cropUuid, "cropUuid 不能为空");
        this.cropType = Objects.requireNonNull(cropType, "作物类型不能为空");
        this.quality = Objects.requireNonNull(quality, "品质不能为空");
        this.legendary = legendary;
        this.sellPrice = sellPrice;
        this.fertilizerReward = fertilizerReward;
        this.firstRewardGold = firstRewardGold;
        this.finalStory = finalStory;
        this.harvestWorldTime = harvestWorldTime;
    }

    public UUID getCropUuid() {
        return cropUuid;
    }

    public CropType getCropType() {
        return cropType;
    }

    public Quality getQuality() {
        return quality;
    }

    public boolean isLegendary() {
        return legendary;
    }

    public int getSellPrice() {
        return sellPrice;
    }

    public int getFertilizerReward() {
        return fertilizerReward;
    }

    public int getFirstRewardGold() {
        return firstRewardGold;
    }

    public String getFinalStory() {
        return finalStory;
    }

    public long getHarvestWorldTime() {
        return harvestWorldTime;
    }
}
