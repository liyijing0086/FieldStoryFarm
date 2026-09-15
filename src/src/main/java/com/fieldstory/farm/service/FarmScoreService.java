package com.fieldstory.farm.service;

import com.fieldstory.farm.model.FarmScoreBreakdown;

/**
 * FarmScore 服务（E 模块 P3；验收规范 §一百一十九~§一百二十，规则文档 §七十五）。
 *
 * <p>FarmScore 的唯一计算入口，由四项相加（规则文档 §七十五）：
 * <pre>
 * FarmScore = DecorationScore + CollectionScore + LegendaryScore + SetScore
 * </pre>
 *
 * <p><b>禁止重复计分</b>（验收规范 §一百二十）：分值按「不同种类」而非「数量」计算——
 * 收获 10 株金色麦穗传说分仍只有 10；购买 5 株向日葵装饰分仍只有 3；套装反复拆装仍只有 15。
 * 因此本服务只读去重后的收集集合，不做任何累加计数。
 *
 * <p>本服务不写状态，只读 {@code GameState} 的收集 / 套装状态计算分值（统一 Model 原则）；
 * 评价映射属 {@link FarmRankService}，毕业判定属 {@link GraduationService}。
 */
public interface FarmScoreService {

    /** 每种不同装饰 +3（验收规范 §一百一十九）。 */
    int DECORATION_POINTS = 3;

    /** 每个已收集作物品质图鉴 +2。 */
    int CROP_POINTS = 2;

    /** 每种传说 +10。 */
    int LEGENDARY_POINTS = 10;

    /** 每套套装 +15。 */
    int SET_POINTS = 15;

    /** FarmScore 满分：全收集 147（验收规范 §一百一十九）。 */
    int MAX_SCORE = FarmScoreBreakdown.MAX_SCORE;

    /** 各分项与总分明细（只读计算，不修改状态）。 */
    FarmScoreBreakdown breakdown();

    /** 当前 FarmScore 总分（0~147）。 */
    default int totalScore() {
        return breakdown().total();
    }

    /** 是否满收集（147 分，唯一毕业条件，验收规范 §一百二十二）。 */
    default boolean isMaxScore() {
        return totalScore() == MAX_SCORE;
    }
}
