package com.fieldstory.farm.model;

/** B 模块内部使用的具体装饰效果类型。 */
public enum BuffType {
    ADJACENT_GROWTH(BuffCategory.GROWTH),
    GLOBAL_GROWTH(BuffCategory.GROWTH),
    CROP_SPECIFIC_GROWTH(BuffCategory.GROWTH),
    QUALITY_SCORE(BuffCategory.QUALITY),
    PRICE_RATE(BuffCategory.PRICE),
    WITHER_RESISTANCE(BuffCategory.WITHER_RESISTANCE),
    WATER_OPERATION_MULTIPLIER(BuffCategory.OPERATION_MODIFIER),
    FERTILIZER_OPERATION_MULTIPLIER(BuffCategory.OPERATION_MODIFIER);

    private final BuffCategory category;

    BuffType(BuffCategory category) {
        this.category = category;
    }

    public BuffCategory getCategory() {
        return category;
    }
}
