package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;

import java.util.Objects;

/**
 * 品质评分输入（C 模块 品质与传说域）。
 *
 * <p>聚合一次收获品质计算所需的全部经历数据（规则文档 §三十三
 * QualityScore = BaseScore + WeatherScore + OperationScore +
 * DecorationScore + EventScore + RandomScore）。
 *
 * <p>分项来源：
 * <ul>
 *   <li>作物类型与经历计数：{@link CropMemory}（C 的 MemoryService 记录）；</li>
 *   <li>decorationScore：B 模块 BuffService 按已放置装饰汇总（规则文档 §三十七），
 *       B 未接入时为 0；</li>
 *   <li>eventScore：D 模块 EventService 按进行中事件汇总（规则文档 §三十八），
 *       D 未接入时为 0；</li>
 *   <li>randomScore：由 QualityService 内部经 {@code RandomProvider}
 *       统一随机源产生（规则文档 §三十九），不在此处传入。</li>
 * </ul>
 */
public class QualityScoreInput {

    private final CropType cropType;
    private final int manualWaterCount;
    private final int rainCount;
    private final int droughtCount;
    private final int greenRainCount;
    private final int fertilizerCount;
    private final int decorationScore;
    private final int eventScore;

    /**
     * 全字段构造。
     *
     * @param cropType         作物类型（基础品质分来源，规则文档 §三十四）
     * @param manualWaterCount 主动浇水次数
     * @param rainCount        雨天次数
     * @param droughtCount     干旱天数
     * @param greenRainCount   绿雨天数
     * @param fertilizerCount  施肥次数
     * @param decorationScore  装饰品质分（B 模块提供，规则文档 §三十七）
     * @param eventScore       事件品质分（D 模块提供，规则文档 §三十八）
     */
    public QualityScoreInput(CropType cropType, int manualWaterCount, int rainCount,
                             int droughtCount, int greenRainCount, int fertilizerCount,
                             int decorationScore, int eventScore) {
        this.cropType = Objects.requireNonNull(cropType, "作物类型不能为空");
        this.manualWaterCount = manualWaterCount;
        this.rainCount = rainCount;
        this.droughtCount = droughtCount;
        this.greenRainCount = greenRainCount;
        this.fertilizerCount = fertilizerCount;
        this.decorationScore = decorationScore;
        this.eventScore = eventScore;
    }

    /**
     * 从生命记忆创建输入（装饰/事件分默认 0，待 B/D 接入后由调用方补全）。
     *
     * <p>收获事务（P2）默认使用本工厂；经历计数一律来自 CropMemory，
     * 不直接读 Crop 对象上暂缺的天气经历字段（Crop 属于 A 模块）。
     */
    public static QualityScoreInput of(CropType cropType, CropMemory memory) {
        Objects.requireNonNull(memory, "生命记忆不能为空");
        return new QualityScoreInput(
                cropType,
                memory.getManualWaterCount(),
                memory.getRainCount(),
                memory.getDroughtCount(),
                memory.getGreenRainCount(),
                memory.getFertilizerCount(),
                0,
                0);
    }

    public CropType getCropType() {
        return cropType;
    }

    public int getManualWaterCount() {
        return manualWaterCount;
    }

    public int getRainCount() {
        return rainCount;
    }

    public int getDroughtCount() {
        return droughtCount;
    }

    public int getGreenRainCount() {
        return greenRainCount;
    }

    public int getFertilizerCount() {
        return fertilizerCount;
    }

    public int getDecorationScore() {
        return decorationScore;
    }

    public int getEventScore() {
        return eventScore;
    }
}
