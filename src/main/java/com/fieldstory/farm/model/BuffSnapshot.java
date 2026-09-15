package com.fieldstory.farm.model;

/**
 * 某一作物位置当前受到的全部装饰效果快照。
 *
 * <p>P1 不启用套装，因此这里只包含 P1 真正生效的字段。
 */
public record BuffSnapshot(
        double growthRate,
        int qualityScoreBonus,
        double priceRate,
        double witherProbabilityMultiplier,
        double wateringGrowthMultiplier,
        double fertilizerGrowthMultiplier
) {
    public static BuffSnapshot neutral() {
        return new BuffSnapshot(1.0, 0, 1.0, 1.0, 1.0, 1.0);
    }
}
