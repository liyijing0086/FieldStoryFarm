package com.fieldstory.farm.model;

/**
 * 作物品质枚举（C 模块 品质与传说域）。
 *
 * <p>品质共五档（规则文档 §三十二）：COMMON 普通 / EXCELLENT 优秀 /
 * RARE 稀有 / EPIC 史诗 / LEGENDARY 传说。
 *
 * <p>品质影响出售价格（售价倍率，规则文档 §六十五）、肥料奖励
 * （规则文档 §六十六）、作物图鉴与成长故事稀有度（规则文档 §三十二）。
 *
 * <p>关键规则（规则文档 §四十）：LEGENDARY 不通过普通分数直接获得，
 * 无论 Score 多高，只要未满足传说突破条件，最高只能得到 EPIC；
 * 传说判定流程见 {@code service.LegendaryService}（规则文档 §四十六）。
 */
public enum Quality {

    /** 普通：售价 ×1，肥料奖励 0（规则文档 §六十五、§六十六） */
    COMMON("普通", 1.0, 0),

    /** 优秀：售价 ×1.5，肥料奖励 1 */
    EXCELLENT("优秀", 1.5, 1),

    /** 稀有：售价 ×2，肥料奖励 2 */
    RARE("稀有", 2.0, 2),

    /** 史诗：售价 ×3，肥料奖励 3 */
    EPIC("史诗", 3.0, 3),

    /** 传说：售价 ×5，肥料奖励 5；只能经传说突破获得（规则文档 §四十） */
    LEGENDARY("传说", 5.0, 5);

    /** 显示名称（UI 展示用） */
    private final String displayName;

    /** 售价倍率（规则文档 §六十五） */
    private final double priceMultiplier;

    /** 收获肥料奖励（规则文档 §六十六） */
    private final int fertilizerReward;

    Quality(String displayName, double priceMultiplier, int fertilizerReward) {
        this.displayName = displayName;
        this.priceMultiplier = priceMultiplier;
        this.fertilizerReward = fertilizerReward;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 售价倍率（规则文档 §六十五）。 */
    public double getPriceMultiplier() {
        return priceMultiplier;
    }

    /** 收获肥料奖励（规则文档 §六十六）。 */
    public int getFertilizerReward() {
        return fertilizerReward;
    }

    /** 是否为传说品质。 */
    public boolean isLegendary() {
        return this == LEGENDARY;
    }
}
