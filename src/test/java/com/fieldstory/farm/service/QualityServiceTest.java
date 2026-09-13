package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.service.impl.BasicQualityService;
import com.fieldstory.farm.util.RandomProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link QualityService} 评分与档位测试（规则文档 §三十三~四十）。
 *
 * <p>随机分固定 seed 后计算（统一随机源，规则文档 §三十九），
 * 断言区间而非具体值，避免与 JDK 随机序列耦合。
 */
class QualityServiceTest {

    private QualityService qualityService;

    @BeforeEach
    void setUp() {
        qualityService = new BasicQualityService();
        RandomProvider.setSeed(20260911L);
    }

    /** 全零经历输入（作物类型任意，仅作物类型有贡献）。 */
    private QualityScoreInput input(CropType cropType) {
        return new QualityScoreInput(cropType, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * 无经历 WHEAT：score = 基础 50 + 随机 0~9 ∈ [50, 59]（规则文档 §三十四、§三十九）。
     */
    @Test
    void scoreWithoutExperienceIsBasePlusRandom() {
        int score = qualityService.calculateScore(input(CropType.WHEAT));
        assertTrue(score >= 50 && score <= 59, "score = " + score);
    }

    /**
     * 天气分：雨 +5/上限20、旱 +8/上限24、绿雨 +15/上限45（规则文档 §三十五）。
     * 经历取超上限值（雨6次、旱4次、绿雨4次）验证封顶：
     * 封顶分 20+24+45=89，加基础 50 后 ∈ [139, 148]。
     */
    @Test
    void weatherScoreIsCappedPerRuleSection35() {
        QualityScoreInput input = new QualityScoreInput(CropType.WHEAT, 0, 6, 4, 4, 0, 0, 0);
        int score = qualityService.calculateScore(input);
        int capped = 20 + 24 + 45 + 50;
        assertTrue(score >= capped && score <= capped + 9, "score = " + score);
    }

    /**
     * 操作分：浇水 +3/上限15、施肥 +8/上限24（规则文档 §三十六）。
     * 浇水7次、施肥4次 → 15+24=39，加基础 50 后 ∈ [89, 98]。
     */
    @Test
    void operationScoreIsCappedPerRuleSection36() {
        QualityScoreInput input = new QualityScoreInput(CropType.WHEAT, 7, 0, 0, 0, 4, 0, 0);
        int score = qualityService.calculateScore(input);
        int capped = 15 + 24 + 50;
        assertTrue(score >= capped && score <= capped + 9, "score = " + score);
    }

    /**
     * 装饰与事件分由输入直接携带（规则文档 §三十七：最大固定贡献 +15；
     * §三十八：流星夜 +20、彩虹日 +15）。
     */
    @Test
    void decorationAndEventScoresArePassedThrough() {
        QualityScoreInput input = new QualityScoreInput(CropType.WHEAT, 0, 0, 0, 0, 0, 15, 20);
        int score = qualityService.calculateScore(input);
        assertTrue(score >= 85 && score <= 94, "score = " + score);
    }

    /**
     * CARROT 基础品质分 55（规则文档 §三十四）：无经历时 ∈ [55, 64]。
     */
    @Test
    void carrotBaseScoreIs55() {
        int score = qualityService.calculateScore(input(CropType.CARROT));
        assertTrue(score >= 55 && score <= 64, "score = " + score);
    }

    /**
     * 档位边界（规则文档 §四十）：&lt;60 COMMON、[60,80) EXCELLENT、
     * [80,100) RARE、≥100 EPIC；任何分数都不返回 LEGENDARY
     * （传说不通过普通分数直接获得，验收规范 §九十六）。
     */
    @Test
    void qualityBandsPerRuleSection40() {
        assertEquals(Quality.COMMON, qualityService.determineQuality(0));
        assertEquals(Quality.COMMON, qualityService.determineQuality(59));
        assertEquals(Quality.EXCELLENT, qualityService.determineQuality(60));
        assertEquals(Quality.EXCELLENT, qualityService.determineQuality(79));
        assertEquals(Quality.RARE, qualityService.determineQuality(80));
        assertEquals(Quality.RARE, qualityService.determineQuality(99));
        assertEquals(Quality.EPIC, qualityService.determineQuality(100));
        assertEquals(Quality.EPIC, qualityService.determineQuality(1000));
        assertTrue(qualityService.determineQuality(1000) != Quality.LEGENDARY);
    }

    /**
     * QualityScoreInput.of(CropType, CropMemory) 工厂：经历计数取自记忆，
     * 装饰/事件分默认 0。
     */
    @Test
    void scoreInputFactoryReadsFromCropMemory() {
        CropMemory memory = new CropMemory(UUID.randomUUID(), CropType.WHEAT, 48);
        memory.setManualWaterCount(2);
        memory.setRainCount(3);
        memory.setDroughtCount(1);
        memory.setGreenRainCount(1);
        memory.setFertilizerCount(2);

        QualityScoreInput input = QualityScoreInput.of(CropType.WHEAT, memory);

        assertEquals(CropType.WHEAT, input.getCropType());
        assertEquals(2, input.getManualWaterCount());
        assertEquals(3, input.getRainCount());
        assertEquals(1, input.getDroughtCount());
        assertEquals(1, input.getGreenRainCount());
        assertEquals(2, input.getFertilizerCount());
        assertEquals(0, input.getDecorationScore());
        assertEquals(0, input.getEventScore());
    }
}
