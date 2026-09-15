package com.fieldstory.farm.model;

import java.util.Objects;

/**
 * 作物图鉴项键：作物类型 × 品质（E 模块 P3；验收规范 §一百一十、规则文档 §七十一）。
 *
 * <p>作物图鉴目标为 {@code 3 作物 × 5 品质 = 15} 项。每一项由「作物 + 品质」唯一确定，
 * 例如 {@code WHEAT + RARE = 稀有小麦}；收集 {@code 稀有小麦} 不会自动解锁
 * {@code 普通小麦}（验收规范 §一百一十一）。因此图鉴状态以本键为粒度保存。
 *
 * @param cropType 作物类型
 * @param quality  品质
 */
public record CropQualityKey(CropType cropType, Quality quality) {

    public CropQualityKey {
        Objects.requireNonNull(cropType, "cropType 不能为空");
        Objects.requireNonNull(quality, "quality 不能为空");
    }
}
