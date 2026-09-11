package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.Quality;

import java.util.Objects;

/**
 * 收获事务结果（C 模块 品质与传说域，P2；验收规范 §一百零三）。
 *
 * <p>比 P0 的 {@link HarvestResult} 多携带事务产物：品质、评分、售价、
 * 肥料奖励、传说标志、生命故事与记忆档案。失败时仅结果码有效，
 * 其余字段为默认值。
 *
 * <p>纯值对象：只保存结论，不含判定逻辑。
 */
public class HarvestOutcome {

    /** 事务结果码（复用 P0 HarvestResult） */
    private final HarvestResult result;

    /** 最终品质（成功时有效） */
    private final Quality quality;

    /** 品质评分（成功时有效） */
    private final int qualityScore;

    /** 最终售价（金币；成功时有效） */
    private final int sellPrice;

    /** 肥料奖励数量（规则文档 §六十六；成功时有效） */
    private final int fertilizerReward;

    /** 是否传说突破成功 */
    private final boolean legendary;

    /** 最终生命故事（规则文档 §七十；成功时有效） */
    private final String finalStory;

    /** 生命记忆档案（成功时已落档保存） */
    private final CropMemory memory;

    private HarvestOutcome(HarvestResult result, Quality quality, int qualityScore,
                           int sellPrice, int fertilizerReward, boolean legendary,
                           String finalStory, CropMemory memory) {
        this.result = Objects.requireNonNull(result, "结果码不能为空");
        this.quality = quality;
        this.qualityScore = qualityScore;
        this.sellPrice = sellPrice;
        this.fertilizerReward = fertilizerReward;
        this.legendary = legendary;
        this.finalStory = finalStory;
        this.memory = memory;
    }

    /** 成功结果工厂。 */
    public static HarvestOutcome success(Quality quality, int qualityScore, int sellPrice,
                                         int fertilizerReward, boolean legendary,
                                         String finalStory, CropMemory memory) {
        return new HarvestOutcome(HarvestResult.SUCCESS, quality, qualityScore,
                sellPrice, fertilizerReward, legendary, finalStory, memory);
    }

    /** 失败结果工厂（未成熟/未种植/无作物）。 */
    public static HarvestOutcome failure(HarvestResult result) {
        if (result == HarvestResult.SUCCESS) {
            throw new IllegalArgumentException("成功结果请使用 success 工厂");
        }
        return new HarvestOutcome(result, null, 0, 0, 0, false, null, null);
    }

    /** 是否收获成功。 */
    public boolean isSuccess() {
        return result == HarvestResult.SUCCESS;
    }

    public HarvestResult getResult() {
        return result;
    }

    /** 最终品质；失败时为 null。 */
    public Quality getQuality() {
        return quality;
    }

    public int getQualityScore() {
        return qualityScore;
    }

    public int getSellPrice() {
        return sellPrice;
    }

    public int getFertilizerReward() {
        return fertilizerReward;
    }

    public boolean isLegendary() {
        return legendary;
    }

    /** 最终生命故事；失败时为 null。 */
    public String getFinalStory() {
        return finalStory;
    }

    /** 生命记忆档案；失败时为 null。 */
    public CropMemory getMemory() {
        return memory;
    }
}
