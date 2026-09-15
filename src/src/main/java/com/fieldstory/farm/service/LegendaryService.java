package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;

/**
 * 传说服务接口（C 模块 品质与传说域，P2）。
 *
 * <p>职责（验收规范 §九十六）：检查特殊条件、计算突破概率、执行突破骰、
 * 返回突破结果。传说判断必须从 QualityService 拆出，QualityService 只负责
 * QualityScore 与普通品质档位。
 *
 * <p>三种传说作物（规则文档 §四十二~四十四）：
 * 金色麦穗（WHEAT，Score≥110，基础 30%）、彩虹玉米（CORN，Score≥115，基础 40%）、
 * 巨龙胡萝卜（CARROT，Score≥120，基础 35%）。
 *
 * <p>最终突破概率（规则文档 §四十五）：
 * LegendaryChance = BaseChance + GreenRainBonus + MeteorBonus + LegendarySetBonus，
 * 绿雨 +5%/次最大 +15%、流星夜 +10%、传奇之光套装 +10%（P2 默认 0，验收规范 §一百），
 * 总上限 80%（验收规范 §一百零一）。
 */
public interface LegendaryService {

    /** 总突破概率上限：80%（验收规范 §一百零一；规则文档 §四十五） */
    int MAX_CHANCE = 80;

    /**
     * 检查作物是否满足对应传说的全部突破条件（验收规范 §九十六"检查特殊条件"）。
     *
     * <p>经历数据一律取自 {@link CropMemory}（C 的 MemoryService 记录），
     * 不依赖 Crop 对象上暂缺的天气经历字段（Crop 属于 A 模块）。
     *
     * @param crop         待判定作物（提供类型与 uuid）
     * @param memory       该作物的生命记忆
     * @param qualityScore 品质评分（规则文档 §三十三）
     * @return 资格检查结果（含未满足条件描述）
     */
    LegendaryCheck checkEligibility(Crop crop, CropMemory memory, int qualityScore);

    /**
     * 对应作物的基础突破概率（%）：小麦 30 / 玉米 40 / 胡萝卜 35
     * （规则文档 §四十二~四十四）。
     */
    int baseChance(CropType cropType);

    /**
     * 计算最终突破概率（%，含加成与 80% 上限）。
     *
     * <p>加成（规则文档 §四十五；验收规范 §一百）：
     * 绿雨 +5%/次、最大 +15%；流星夜 +10%；传奇之光套装 +10%（P2 默认 0）。
     *
     * @param crop         待判定作物
     * @param memory       该作物的生命记忆
     * @param qualityScore 品质评分
     * @return 最终概率（0~80）
     */
    int legendaryChance(Crop crop, CropMemory memory, int qualityScore);

    /**
     * 执行突破骰（验收规范 §九十六"执行突破骰"）。
     *
     * <p>随机统一经 {@code RandomProvider}（规则文档 §三十九/§四十六）。
     * 条件不满足时不掷骰、直接返回 false；满足时按 {@link #legendaryChance}
     * 判定。传说失败不损失作物，只不发生突破（规则文档 §四十六）。
     *
     * @param crop         待判定作物
     * @param memory       该作物的生命记忆
     * @param qualityScore 品质评分
     * @return true = 突破成功 → LEGENDARY；false = 未突破（按分数正常返回品质）
     */
    boolean rollBreakthrough(Crop crop, CropMemory memory, int qualityScore);

    /**
     * 设置传奇之光套装加成（P3 套装系统接入，验收规范 §一百：P2 预留、默认 0）。
     *
     * @param bonusPercent 套装加成百分比（≥0，如 10）
     */
    void setLegendarySetBonus(int bonusPercent);

    /** 指定作物的目标传说名（金色麦穗/彩虹玉米/巨龙胡萝卜，规则文档 §四十二~四十四）。 */
    static String legendaryName(CropType cropType) {
        if (cropType == null) {
            return "";
        }
        return switch (cropType) {
            case WHEAT -> "金色麦穗";
            case CORN -> "彩虹玉米";
            case CARROT -> "巨龙胡萝卜";
        };
    }
}
