package com.fieldstory.farm.model;

import java.util.Set;

/**
 * P3 三种装饰套装定义（验收规范 §一百一十四~§一百一十七）。
 *
 * <p>每个套装列出固定成员；是否「完成」不由此枚举判定，而由 {@code service.SetService}
 * 依据「全部成员拥有 <b>且</b> 全部成员放置」实时判定（验收规范 §一百一十五~§一百一十八）。
 *
 * <p>{@link #getId()} 使用稳定的 ASCII 常量并直接用作 SQLite {@code set_collection.set_id}，
 * 与 {@link SetCollectionState} 的 collected / active 两个集合一一对应。
 */
public enum DecorationSet {

    /** 自然之息：D01 向日葵 / D02 玫瑰花坛 / D05 大树 / D06 石灯笼。 */
    NATURE_BREATH("自然之息", "成长 +8%",
            DecorationType.SUNFLOWER,
            DecorationType.ROSE_BED,
            DecorationType.BIG_TREE,
            DecorationType.STONE_LANTERN),

    /** 丰收之魂：D07 小喷泉 / D08 麦田守望者 / D09 玉米丰收 / D10 胡萝卜地。 */
    HARVEST_SOUL("丰收之魂", "售价 +10%",
            DecorationType.SMALL_FOUNTAIN,
            DecorationType.WHEAT_WATCHER,
            DecorationType.CORN_HARVEST,
            DecorationType.CARROT_FIELD),

    /** 传奇之光：D11 金色喷泉 / D12 彩虹喷泉 / D13 金色王座 / D14 丰收女神像。 */
    LEGEND_LIGHT("传奇之光", "传奇突破概率 +10%",
            DecorationType.GOLDEN_FOUNTAIN,
            DecorationType.RAINBOW_FOUNTAIN,
            DecorationType.GOLDEN_THRONE,
            DecorationType.HARVEST_GODDESS);

    private final String displayName;
    private final String buffDescription;
    private final Set<DecorationType> members;

    DecorationSet(String displayName, String buffDescription, DecorationType... members) {
        this.displayName = displayName;
        this.buffDescription = buffDescription;
        this.members = Set.of(members);
    }

    /** 稳定 id，用作 SQLite {@code set_collection.set_id}。 */
    public String getId() {
        return name();
    }

    /** 中文显示名（图鉴 / 目标提示用）。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 套装生效时的 Buff 描述（验收规范 §一百一十五~§一百一十七）。 */
    public String getBuffDescription() {
        return buffDescription;
    }

    /** 套装成员（不可变）。 */
    public Set<DecorationType> getMembers() {
        return members;
    }
}
