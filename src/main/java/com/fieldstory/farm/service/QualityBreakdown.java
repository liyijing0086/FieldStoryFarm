package com.fieldstory.farm.service;

/**
 * 品质评分分项明细（C 模块 品质与传说域，P1 品质解释 UI 数据源，
 * 验收规范 §七十七）。
 *
 * <p>把一次品质评分的确定性分项拆开：基础分、雨/旱/绿雨、主动浇水、
 * 施肥、装饰、事件。随机分不包含在本类中（规则文档 §三十九：收获时
 * 才经 RandomProvider 掷出，验收规范 §七十七：收获前不显示），
 * 查看作物时展示的应是确定性分项。
 *
 * <p>纯值对象：只保存分项结论，不含计算逻辑；
 * 由 {@link QualityService#explainScore} 生成。
 */
public class QualityBreakdown {

    /** 基础品质分（规则文档 §三十四，CropType.getBaseScore()） */
    private final int baseScore;

    /** 雨天品质分（+5/次、上限 20，规则文档 §三十五） */
    private final int rainScore;

    /** 干旱品质分（+8/次、上限 24，规则文档 §三十五） */
    private final int droughtScore;

    /** 绿雨品质分（+15/次、上限 45，规则文档 §三十五） */
    private final int greenRainScore;

    /** 主动浇水品质分（+3/次、上限 15，规则文档 §三十六） */
    private final int waterScore;

    /** 施肥品质分（+8/次、上限 24，规则文档 §三十六） */
    private final int fertilizerScore;

    /** 装饰品质分（B 模块汇总，规则文档 §三十七） */
    private final int decorationScore;

    /** 事件品质分（D 模块汇总，规则文档 §三十八） */
    private final int eventScore;

    /**
     * 全分项构造（由 {@code BasicQualityService} 生成，外部不直接调用）。
     */
    public QualityBreakdown(int baseScore, int rainScore, int droughtScore,
                            int greenRainScore, int waterScore,
                            int fertilizerScore, int decorationScore, int eventScore) {
        this.baseScore = baseScore;
        this.rainScore = rainScore;
        this.droughtScore = droughtScore;
        this.greenRainScore = greenRainScore;
        this.waterScore = waterScore;
        this.fertilizerScore = fertilizerScore;
        this.decorationScore = decorationScore;
        this.eventScore = eventScore;
    }

    public int getBaseScore() {
        return baseScore;
    }

    public int getRainScore() {
        return rainScore;
    }

    public int getDroughtScore() {
        return droughtScore;
    }

    public int getGreenRainScore() {
        return greenRainScore;
    }

    public int getWaterScore() {
        return waterScore;
    }

    public int getFertilizerScore() {
        return fertilizerScore;
    }

    public int getDecorationScore() {
        return decorationScore;
    }

    public int getEventScore() {
        return eventScore;
    }

    /** 天气累计分 = 雨 + 旱 + 绿雨（规则文档 §三十五 三项之和）。 */
    public int weatherTotal() {
        return rainScore + droughtScore + greenRainScore;
    }

    /** 操作累计分 = 主动浇水 + 施肥（规则文档 §三十六 两项之和）。 */
    public int operationTotal() {
        return waterScore + fertilizerScore;
    }

    /**
     * 确定性总分（不含随机分）= 基础 + 天气 + 操作 + 装饰 + 事件。
     * 收获时再加随机 0~9（规则文档 §三十九）才是最终 QualityScore。
     */
    public int deterministicTotal() {
        return baseScore + weatherTotal() + operationTotal()
                + decorationScore + eventScore;
    }
}
