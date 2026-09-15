package com.fieldstory.farm.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link CropQualityPanelView} 纯函数测试（验收规范 §七十七）。
 *
 * <p>面板本体是 JavaFX 控件，测试只覆盖可静态调用的文案纯函数
 * （与 ShowcaseView 的 selectorLabelFor 同模式）。
 */
class CropQualityPanelViewTest {

    /** 普通分项行：「基础分 +50」。 */
    @Test
    void rowLabelFormatsNameAndScore() {
        assertEquals("基础分 +50", CropQualityPanelView.rowLabel("基础分", 50));
    }

    /** 天气累计行含雨/旱/绿雨明细。 */
    @Test
    void weatherRowLabelShowsDetail() {
        assertEquals("天气累计 +31（雨 +15 · 旱 +16 · 绿雨 +0）",
                CropQualityPanelView.weatherRowLabel(15, 16, 0));
    }

    /** 带次数明细的分项行：「施肥 +8（3 次）」。 */
    @Test
    void detailRowLabelShowsCount() {
        assertEquals("施肥 +8（3 次）",
                CropQualityPanelView.detailRowLabel("施肥", "3 次", 8));
    }

    /** 随机行：收获前不显示具体值。 */
    @Test
    void randomRowLabelHidesValueBeforeHarvest() {
        assertEquals("随机 0~9（收获时确定）", CropQualityPanelView.randomRowLabel());
    }

    /** 确定性合计行。 */
    @Test
    void totalRowLabelShowsDeterministicTotal() {
        assertEquals("确定性合计 120（收获时再加随机分）",
                CropQualityPanelView.totalRowLabel(120));
    }

    /** 负分防御：负值按 0 展示（脏数据不产生「+-5」文案）。 */
    @Test
    void labelsDefendAgainstNegativeScore() {
        assertEquals("装饰 +0", CropQualityPanelView.rowLabel("装饰", -3));
        assertEquals("天气累计 +0（雨 +0 · 旱 +0 · 绿雨 +0）",
                CropQualityPanelView.weatherRowLabel(-1, -2, -3));
    }
}
