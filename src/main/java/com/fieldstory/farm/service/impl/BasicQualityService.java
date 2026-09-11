package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.service.QualityScoreInput;
import com.fieldstory.farm.service.QualityService;
import com.fieldstory.farm.util.RandomProvider;

import java.util.Objects;

/**
 * {@link QualityService} 基础实现（C 模块 品质与传说域）。
 *
 * <p>评分公式（规则文档 §三十三）：
 * QualityScore = BaseScore + WeatherScore + OperationScore
 *              + DecorationScore + EventScore + RandomScore。
 *
 * <p>各项数值与上限全部取自规则文档：
 * <ul>
 *   <li>基础分：CropType.getBaseScore()（§三十四：小麦 50 / 玉米 50 / 胡萝卜 55）；</li>
 *   <li>雨天 +5/次、上限 20；干旱 +8/次、上限 24；绿雨 +15/次、上限 45（§三十五）；</li>
 *   <li>主动浇水 +3/次、上限 15；施肥 +8/次、上限 24（§三十六）；</li>
 *   <li>装饰/事件分由输入携带（§三十七：最大固定贡献 +15；§三十八：流星夜 +20、彩虹日 +15）；</li>
 *   <li>随机分：收获时经 {@link RandomProvider} 取 0~9（§三十九，
 *       统一随机源，支持固定 seed 测试与离线复现）。</li>
 * </ul>
 *
 * <p>档位判定（规则文档 §四十）：&lt;60 COMMON、[60,80) EXCELLENT、
 * [80,100) RARE、≥100 EPIC；任何分数都不返回 LEGENDARY
 * （传说不通过普通分数直接获得，验收规范 §九十六 由 LegendaryService 拆出）。
 */
public class BasicQualityService implements QualityService {

    /** 雨天品质分：+5/次（规则文档 §三十五） */
    private static final int RAIN_SCORE = 5;

    /** 雨天品质分上限：20（规则文档 §三十五） */
    private static final int RAIN_SCORE_CAP = 20;

    /** 干旱品质分：+8/次（规则文档 §三十五） */
    private static final int DROUGHT_SCORE = 8;

    /** 干旱品质分上限：24（规则文档 §三十五） */
    private static final int DROUGHT_SCORE_CAP = 24;

    /** 绿雨品质分：+15/次（规则文档 §三十五） */
    private static final int GREEN_RAIN_SCORE = 15;

    /** 绿雨品质分上限：45（规则文档 §三十五） */
    private static final int GREEN_RAIN_SCORE_CAP = 45;

    /** 主动浇水品质分：+3/次（规则文档 §三十六） */
    private static final int WATER_SCORE = 3;

    /** 主动浇水品质分上限：15（规则文档 §三十六） */
    private static final int WATER_SCORE_CAP = 15;

    /** 施肥品质分：+8/次（规则文档 §三十六） */
    private static final int FERTILIZER_SCORE = 8;

    /** 施肥品质分上限：24（规则文档 §三十六） */
    private static final int FERTILIZER_SCORE_CAP = 24;

    /** 随机品质分上界：nextInt(10) 即 0~9（规则文档 §三十九） */
    private static final int RANDOM_SCORE_BOUND = 10;

    /** 品质档位边界（规则文档 §四十） */
    private static final int EXCELLENT_MIN = 60;
    private static final int RARE_MIN = 80;
    private static final int EPIC_MIN = 100;

    @Override
    public int calculateScore(QualityScoreInput input) {
        Objects.requireNonNull(input, "评分输入不能为空");

        int baseScore = input.getCropType().getBaseScore();
        int weatherScore = rainScore(input.getRainCount())
                + droughtScore(input.getDroughtCount())
                + greenRainScore(input.getGreenRainCount());
        int operationScore = waterScore(input.getManualWaterCount())
                + fertilizerScore(input.getFertilizerCount());
        int randomScore = RandomProvider.nextInt(RANDOM_SCORE_BOUND);

        return baseScore
                + weatherScore
                + operationScore
                + input.getDecorationScore()
                + input.getEventScore()
                + randomScore;
    }

    @Override
    public Quality determineQuality(int score) {
        if (score < EXCELLENT_MIN) {
            return Quality.COMMON;
        }
        if (score < RARE_MIN) {
            return Quality.EXCELLENT;
        }
        if (score < EPIC_MIN) {
            return Quality.RARE;
        }
        return Quality.EPIC;
    }

    /** 雨天品质分：+5/次、上限 20（规则文档 §三十五）。 */
    private int rainScore(int rainCount) {
        return cappedScore(rainCount, RAIN_SCORE, RAIN_SCORE_CAP);
    }

    /** 干旱品质分：+8/次、上限 24（规则文档 §三十五）。 */
    private int droughtScore(int droughtCount) {
        return cappedScore(droughtCount, DROUGHT_SCORE, DROUGHT_SCORE_CAP);
    }

    /** 绿雨品质分：+15/次、上限 45（规则文档 §三十五）。 */
    private int greenRainScore(int greenRainCount) {
        return cappedScore(greenRainCount, GREEN_RAIN_SCORE, GREEN_RAIN_SCORE_CAP);
    }

    /** 主动浇水品质分：+3/次、上限 15（规则文档 §三十六）。 */
    private int waterScore(int manualWaterCount) {
        return cappedScore(manualWaterCount, WATER_SCORE, WATER_SCORE_CAP);
    }

    /** 施肥品质分：+8/次、上限 24（规则文档 §36）。 */
    private int fertilizerScore(int fertilizerCount) {
        return cappedScore(fertilizerCount, FERTILIZER_SCORE, FERTILIZER_SCORE_CAP);
    }

    /** 通用"每次加分、总上限封顶"计算。 */
    private int cappedScore(int count, int perTime, int cap) {
        if (count <= 0) {
            return 0;
        }
        return Math.min(count * perTime, cap);
    }
}
