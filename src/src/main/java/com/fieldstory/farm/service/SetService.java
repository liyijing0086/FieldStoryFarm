package com.fieldstory.farm.service;

import com.fieldstory.farm.model.DecorationSet;

import java.util.List;
import java.util.Set;

/**
 * B 模块 P3 套装服务。
 *
 * <p>负责三套装的「已收集 / 已激活」状态以及套装 Buff 的强类型输出。
 * 两个状态必须彼此独立：
 * <ul>
 *   <li><b>setCollected</b>：曾经完整完成过某套装，永久保留，用于图鉴/FarmScore；</li>
 *   <li><b>setActive</b>：当前全部成员仍然放置，决定套装 Buff 是否生效。</li>
 * </ul>
 *
 * <p>正式规则：
 * <ul>
 *   <li>自然之息：所有作物成长 +8%；</li>
 *   <li>丰收之魂：最终售价倍率 ×1.10；</li>
 *   <li>传奇之光：传奇突破概率 +10%。</li>
 * </ul>
 *
 * <p>业务代码必须使用本接口的强类型查询，不得解析
 * {@link #getActiveBuffDescriptions()} 返回的 UI 文案。
 */
public interface SetService {

    /** 自然之息成长加成：+8%。 */
    double NATURE_BREATH_GROWTH_BONUS = 0.08;

    /** 丰收之魂售价倍率：×1.10。 */
    double HARVEST_SOUL_PRICE_RATE = 1.10;

    /** 传奇之光传奇突破概率加成：+10%。 */
    int LEGEND_LIGHT_LEGENDARY_BONUS_PERCENT = 10;

    /** 三种套装（固定顺序，与 {@link DecorationSet} 枚举一致）。 */
    List<DecorationSet> allSets();

    /** 是否曾经完整完成过该套装（永久）。 */
    boolean isCollected(DecorationSet set);

    /** 该套装当前是否全部成员仍放置、Buff 是否生效。 */
    boolean isActive(DecorationSet set);

    /** 已收集套装数（0~3），用于 FarmScore 套装分与图鉴进度。 */
    int collectedCount();

    /** 当前生效套装数（0~3）。 */
    int activeCount();

    /** 已收集套装 id 集合（只读快照）。 */
    Set<String> getCollectedSetIds();

    /** 当前生效套装 id 集合（只读快照）。 */
    Set<String> getActiveSetIds();

    /**
     * 当前生效的套装 Buff 文案，仅供 UI 展示。
     *
     * <p>业务公式不得解析该字符串，应使用下方三个强类型查询。
     */
    List<String> getActiveBuffDescriptions();

    /**
     * 自然之息当前提供的成长加成。
     *
     * @return 激活时 0.08，否则 0.0
     */
    default double getGrowthSetBonus() {
        return isActive(DecorationSet.NATURE_BREATH)
                ? NATURE_BREATH_GROWTH_BONUS
                : 0.0;
    }

    /**
     * 丰收之魂当前提供的独立售价倍率。
     *
     * <p>该倍率属于最终售价公式中的 SetPriceRate，不能并入
     * DecorationPriceRate。
     *
     * @return 激活时 1.10，否则 1.0
     */
    default double getPriceSetRate() {
        return isActive(DecorationSet.HARVEST_SOUL)
                ? HARVEST_SOUL_PRICE_RATE
                : 1.0;
    }

    /**
     * 传奇之光当前提供的传奇突破概率加成（百分点）。
     *
     * @return 激活时 10，否则 0
     */
    default int getLegendarySetBonusPercent() {
        return isActive(DecorationSet.LEGEND_LIGHT)
                ? LEGEND_LIGHT_LEGENDARY_BONUS_PERCENT
                : 0;
    }

    /**
     * 依据当前装饰「拥有 / 放置」状态重算两个状态：
     * 全部成员拥有且放置 → 加入 collected（永久）与 active；
     * 否则仅移除 active，collected 不回退。
     */
    void refresh();
}
