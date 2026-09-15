package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.service.impl.BasicQualityService;
import com.fieldstory.farm.util.RandomProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QualityService#explainScore} 分项明细测试
 * （验收规范 §七十七 品质解释 UI 数据源）。
 *
 * <p>验证：分项明细与评分公式同源（规则文档 §三十三~三十六）、
 * 各分项封顶正确（§三十五/§三十六）、explainScore 不消耗随机源
 * （§三十九：随机只在收获时掷出）。
 */
class QualityBreakdownTest {

    private QualityService qualityService;

    @BeforeEach
    void setUp() {
        qualityService = new BasicQualityService();
    }

    /** 小麦档案：雨 3、旱 2、绿雨 1、浇水 5、施肥 3。 */
    private QualityScoreInput inputWithExperience(CropType cropType) {
        CropMemory memory = new CropMemory(UUID.randomUUID(), cropType, 0);
        memory.setRainCount(3);
        memory.setDroughtCount(2);
        memory.setGreenRainCount(1);
        memory.setManualWaterCount(5);
        memory.setFertilizerCount(3);
        return new QualityScoreInput(cropType,
                memory.getManualWaterCount(), memory.getRainCount(),
                memory.getDroughtCount(), memory.getGreenRainCount(),
                memory.getFertilizerCount(), 10, 20);
    }

    /** 分项明细与公式同源：基础 50、雨 +15、旱 +16、绿雨 +15、
     *  浇水 +15（5 次封顶）、施肥 +24（3 次封顶）、装饰 +10、事件 +20。 */
    @Test
    void explainScoreBreaksDownEveryComponent() {
        QualityBreakdown breakdown = qualityService.explainScore(
                inputWithExperience(CropType.WHEAT));

        assertEquals(50, breakdown.getBaseScore());
        assertEquals(15, breakdown.getRainScore());
        assertEquals(16, breakdown.getDroughtScore());
        assertEquals(15, breakdown.getGreenRainScore());
        assertEquals(15, breakdown.getWaterScore());
        assertEquals(24, breakdown.getFertilizerScore());
        assertEquals(10, breakdown.getDecorationScore());
        assertEquals(20, breakdown.getEventScore());
        assertEquals(15 + 16 + 15, breakdown.weatherTotal());
        assertEquals(15 + 24, breakdown.operationTotal());
        assertEquals(165, breakdown.deterministicTotal());
    }

    /** 各分项封顶：雨 20、旱 24、绿雨 45、浇水 15、施肥 24。 */
    @Test
    void explainScoreCapsEveryComponent() {
        CropMemory memory = new CropMemory(UUID.randomUUID(), CropType.CORN, 0);
        memory.setRainCount(10);
        memory.setDroughtCount(10);
        memory.setGreenRainCount(10);
        memory.setManualWaterCount(10);
        memory.setFertilizerCount(10);
        QualityScoreInput input = new QualityScoreInput(CropType.CORN,
                memory.getManualWaterCount(), memory.getRainCount(),
                memory.getDroughtCount(), memory.getGreenRainCount(),
                memory.getFertilizerCount(), 0, 0);

        QualityBreakdown breakdown = qualityService.explainScore(input);

        assertEquals(20, breakdown.getRainScore());
        assertEquals(24, breakdown.getDroughtScore());
        assertEquals(45, breakdown.getGreenRainScore());
        assertEquals(15, breakdown.getWaterScore());
        assertEquals(24, breakdown.getFertilizerScore());
        assertEquals(50 + 20 + 24 + 45 + 15 + 24, breakdown.deterministicTotal());
    }

    /** 玉米基础分 50 / 胡萝卜 55（规则文档 §三十四）。 */
    @Test
    void explainScoreUsesCropBaseScore() {
        QualityBreakdown corn = qualityService.explainScore(
                inputWithExperience(CropType.CORN));
        QualityBreakdown carrot = qualityService.explainScore(
                inputWithExperience(CropType.CARROT));

        assertEquals(50, corn.getBaseScore());
        assertEquals(55, carrot.getBaseScore());
    }

    /** calculateScore = 确定性总分 + 随机 0~9（同源且不额外消耗随机）。 */
    @Test
    void calculateScoreIsDeterministicTotalPlusRandom() {
        QualityScoreInput input = inputWithExperience(CropType.WHEAT);

        RandomProvider.setSeed(42L);
        int score = qualityService.calculateScore(input);

        QualityBreakdown breakdown = qualityService.explainScore(input);
        int random = score - breakdown.deterministicTotal();
        assertTrue(random >= 0 && random <= 9,
                "随机分应在 0~9，实际 " + random);
    }

    /** explainScore 不消耗随机源：查看多次后收获随机序列不变。 */
    @Test
    void explainScoreDoesNotConsumeRandomSource() {
        QualityScoreInput input = inputWithExperience(CropType.WHEAT);

        RandomProvider.setSeed(42L);
        int scoreWithoutExplain = qualityService.calculateScore(input);

        // 重置同一种子：先解释两次（模拟玩家反复查看），再收获
        RandomProvider.setSeed(42L);
        qualityService.explainScore(input);
        qualityService.explainScore(input);
        int scoreAfterExplain = qualityService.calculateScore(input);

        assertEquals(scoreWithoutExplain, scoreAfterExplain,
                "explainScore 不应消耗随机源，否则收获评分会被查看行为改变");
    }
}
