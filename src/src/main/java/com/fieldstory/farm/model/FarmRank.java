package com.fieldstory.farm.model;

/**
 * 农场评价（E 模块 P3；验收规范 §一百二十一、规则文档 §七十四~§七十六）。
 *
 * <p>共 8 级，由 FarmScore 映射（阈值见验收规范 §一百二十一）：
 * <pre>
 * 新手农场   0～9
 * 田园小筑   ≥10
 * 花园农场   ≥30
 * 美丽庄园   ≥55
 * 繁花似锦   ≥80
 * 自然天堂   ≥105
 * 传奇庄园   ≥125
 * 永恒花园   147（唯一毕业评价）
 * </pre>
 *
 * <p>枚举只负责「分数 → 评价」的纯映射，不含计分（计分在 {@code FarmScoreService}）。
 */
public enum FarmRank {

    /** 新手农场（0～9）。 */
    NOVICE("新手农场", 0),

    /** 田园小筑（≥10）。 */
    COTTAGE("田园小筑", 10),

    /** 花园农场（≥30）。 */
    GARDEN("花园农场", 30),

    /** 美丽庄园（≥55）。 */
    MANOR("美丽庄园", 55),

    /** 繁花似锦（≥80）。 */
    BLOOMING("繁花似锦", 80),

    /** 自然天堂（≥105）。 */
    NATURE_HEAVEN("自然天堂", 105),

    /** 传奇庄园（≥125）。 */
    LEGEND_MANOR("传奇庄园", 125),

    /** 永恒花园（147，唯一毕业评价）。 */
    ETERNAL_GARDEN("永恒花园", 147);

    /** 达到本评价所需的最低 FarmScore。 */
    private final int minScore;

    /** 评价显示名。 */
    private final String displayName;

    FarmRank(String displayName, int minScore) {
        this.displayName = displayName;
        this.minScore = minScore;
    }

    public int getMinScore() {
        return minScore;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 是否为毕业评价（永恒花园）。 */
    public boolean isGraduationRank() {
        return this == ETERNAL_GARDEN;
    }

    /**
     * 由 FarmScore 映射评价：取「最低分不超过 score」的最高一级。
     *
     * <p>负数按 0 处理（新手农场）；超过满分的分数按最高级处理（与规则「上限 147」一致）。
     *
     * @param farmScore FarmScore
     * @return 对应评价
     */
    public static FarmRank of(int farmScore) {
        FarmRank result = NOVICE;
        for (FarmRank rank : values()) {
            if (farmScore >= rank.minScore) {
                result = rank;
            }
        }
        return result;
    }
}
