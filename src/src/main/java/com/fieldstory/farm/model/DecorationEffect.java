package com.fieldstory.farm.model;

/** 装饰静态效果描述；Model 只保存配置状态。 */
public record DecorationEffect(
        BuffType buffType,
        double value,
        double cap,
        CropType targetCropType
) {
}
