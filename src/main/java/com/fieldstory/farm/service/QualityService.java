package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Quality;

/**
 * 品质服务接口（C 模块 品质与传说域；P1 品质系统，P2 收获事务的前置依赖）。
 *
 * <p>职责（验收规范 §一百零二 品质最终顺序的第一步）：
 * 计算 {@code QualityScore} 并映射普通四档品质（COMMON/EXCELLENT/RARE/EPIC）。
 * <b>LEGENDARY 不通过普通分数直接获得</b>（规则文档 §四十），传说判定由
 * {@code service.LegendaryService} 独立负责（验收规范 §九十六：传说判断必须
 * 从 QualityService 拆出）。
 *
 * <p>评分公式（规则文档 §三十三）：
 * QualityScore = BaseScore + WeatherScore + OperationScore
 *              + DecorationScore + EventScore + RandomScore。
 */
public interface QualityService {

    /**
     * 计算品质评分（规则文档 §三十三~三十九）。
     *
     * <p>分项规则（全部取规则文档数值）：
     * <ul>
     *   <li>BaseScore：作物基础品质分（§三十四，CropType.getBaseScore()）；</li>
     *   <li>WeatherScore：雨 +5/次上限 20，干旱 +8/次上限 24，
     *       绿雨 +15/次上限 45（§三十五）；</li>
     *   <li>OperationScore：主动浇水 +3/次上限 15，施肥 +8/次上限 24（§三十六）；</li>
     *   <li>DecorationScore / EventScore：由输入携带（B/D 模块汇总，§三十七/§三十八）；</li>
     *   <li>RandomScore：收获时 RandomProvider.nextInt(10)，0~9（§三十九）。</li>
     * </ul>
     *
     * @param input 评分输入（作物类型 + 经历计数 + 装饰/事件分）
     * @return 品质评分（非负整数）
     */
    int calculateScore(QualityScoreInput input);

    /**
     * 普通四档品质判定（规则文档 §四十）。
     *
     * <p>档位：Score &lt; 60 → COMMON；60 ≤ Score &lt; 80 → EXCELLENT；
     * 80 ≤ Score &lt; 100 → RARE；Score ≥ 100 → EPIC。
     * 任何分数都不会返回 LEGENDARY（规则文档 §四十：传说不通过分数直接获得）。
     *
     * @param score 品质评分
     * @return 普通四档品质之一（COMMON/EXCELLENT/RARE/EPIC）
     */
    Quality determineQuality(int score);
}
