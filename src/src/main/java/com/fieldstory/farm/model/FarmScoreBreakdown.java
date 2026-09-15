package com.fieldstory.farm.model;

/**
 * FarmScore 明细（E 模块 P3；验收规范 §一百一十九、规则文档 §七十五）。
 *
 * <p>FarmScore 统一由四项相加（规则文档 §七十五）：
 * <pre>
 * 装饰分    14 × 3 = 42
 * 作物图鉴分 15 × 2 = 30
 * 传说分     3 × 10 = 30
 * 套装分     3 × 15 = 45
 * 满分              147
 * </pre>
 *
 * <p>只承载数值，不含计算逻辑（计算在 {@code service.FarmScoreService}）。
 *
 * @param decorationScore 装饰分（0~42）
 * @param collectionScore 作物图鉴分（0~30）
 * @param legendaryScore  传说分（0~30）
 * @param setScore        套装分（0~45）
 */
public record FarmScoreBreakdown(
        int decorationScore,
        int collectionScore,
        int legendaryScore,
        int setScore) {

    /** FarmScore 满分（全收集，验收规范 §一百一十九）。 */
    public static final int MAX_SCORE = 147;

    /** 四项合计。 */
    public int total() {
        return decorationScore + collectionScore + legendaryScore + setScore;
    }

    /** 是否满收集（147 分，唯一毕业条件，验收规范 §一百二十二）。 */
    public boolean isMaxScore() {
        return total() == MAX_SCORE;
    }
}
