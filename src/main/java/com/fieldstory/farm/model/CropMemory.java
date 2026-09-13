package com.fieldstory.farm.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 作物生命记忆模型（C 模块 品质与传说域，P2）。
 *
 * <p>每一株作物拥有唯一 {@code cropUuid}，其完整成长经历记录在本类中
 * （规则文档 §六十九；验收规范 §九十三~九十四）。
 *
 * <p>生命周期与当前 {@link Crop} 解耦（验收规范 §九十五）：收获完成后
 * 当前 Crop 从土地清除，但 CropMemory 永久保留，用于生命故事展示
 * （P3 展示台 {@code ShowcaseService} 展示的是历史记录，规则文档 §六十九）。
 *
 * <p>只保存状态，不含任何计算与业务规则（脚手架 §七.1 Model 原则）。
 * 经历记录（天气/浇水/施肥/事件计数）由 C 的 {@code service.MemoryService}
 * 统一写入，本类只提供字段读写。
 *
 * <p>时间哨兵约定（决策 D14 风格）：时间字段为 long 游戏小时/游戏日，
 * 未发生事件时使用 -1 哨兵，与第 0 小时/第 0 游戏日区分。
 */
public class CropMemory {

    /** 作物唯一标识（规则文档 §六十九） */
    private UUID cropUuid;

    /** 作物类型 */
    private CropType cropType;

    /** 播种时刻世界时间（游戏小时）；-1 哨兵 = 未记录 */
    private long plantWorldTime = -1;

    /** 成熟时刻世界时间（游戏小时）；-1 哨兵 = 未成熟 */
    private long matureWorldTime = -1;

    /** 收获时刻世界时间（游戏小时）；-1 哨兵 = 未收获 */
    private long harvestWorldTime = -1;

    /** 主动浇水次数（规则文档 §六十九） */
    private int manualWaterCount;

    /** 雨天次数 */
    private int rainCount;

    /** 干旱天数 */
    private int droughtCount;

    /** 绿雨天数 */
    private int greenRainCount;

    /** 施肥次数 */
    private int fertilizerCount;

    /** 最近一次干旱发生的游戏日；-1 哨兵 = 从未经历干旱（内部字段，
     *  用于判断"干旱当天主动浇水救援"，金色麦穗条件 2，规则文档 §四十二） */
    private long lastDroughtGameDay = -1;

    /** 是否发生过"干旱当天玩家主动浇水救援"（金色麦穗条件 2，规则文档 §四十二） */
    private boolean waterRescueOnDroughtDay;

    /** 经历过的随机事件（规则文档 §六十九"随机事件"；验收规范 §九十四） */
    private final List<EventType> events = new ArrayList<>();

    /** 是否经历过枯萎风险（验收规范 §九十四"枯萎风险"） */
    private boolean witherRisk;

    /** 最终品质结果；收获完成前为 null（验收规范 §九十四"品质"） */
    private Quality quality;

    /** 是否传说突破（验收规范 §九十四"传说突破"） */
    private boolean legendary;

    /** 最终成长故事；收获完成前为 null（规则文档 §七十） */
    private String finalStory;

    /** 无参构造：供持久化反序列化预留（时间字段保持 -1 哨兵）。 */
    public CropMemory() {
        // 状态容器，无默认业务行为
    }

    /**
     * 按作物创建记忆档案。
     *
     * @param cropUuid       作物唯一标识（规则文档 §六十九）
     * @param cropType       作物类型
     * @param plantWorldTime 播种时刻世界时间（游戏小时）
     */
    public CropMemory(UUID cropUuid, CropType cropType, long plantWorldTime) {
        this.cropUuid = Objects.requireNonNull(cropUuid, "cropUuid 不能为空");
        this.cropType = Objects.requireNonNull(cropType, "作物类型不能为空");
        this.plantWorldTime = plantWorldTime;
    }

    /** 收获是否已完成（以品质结果是否落档为准）。 */
    public boolean isCompleted() {
        return quality != null;
    }

    public UUID getCropUuid() {
        return cropUuid;
    }

    public void setCropUuid(UUID cropUuid) {
        this.cropUuid = cropUuid;
    }

    public CropType getCropType() {
        return cropType;
    }

    public void setCropType(CropType cropType) {
        this.cropType = cropType;
    }

    public long getPlantWorldTime() {
        return plantWorldTime;
    }

    public void setPlantWorldTime(long plantWorldTime) {
        this.plantWorldTime = plantWorldTime;
    }

    public long getMatureWorldTime() {
        return matureWorldTime;
    }

    public void setMatureWorldTime(long matureWorldTime) {
        this.matureWorldTime = matureWorldTime;
    }

    public long getHarvestWorldTime() {
        return harvestWorldTime;
    }

    public void setHarvestWorldTime(long harvestWorldTime) {
        this.harvestWorldTime = harvestWorldTime;
    }

    public int getManualWaterCount() {
        return manualWaterCount;
    }

    public void setManualWaterCount(int manualWaterCount) {
        this.manualWaterCount = manualWaterCount;
    }

    public int getRainCount() {
        return rainCount;
    }

    public void setRainCount(int rainCount) {
        this.rainCount = rainCount;
    }

    public int getDroughtCount() {
        return droughtCount;
    }

    public void setDroughtCount(int droughtCount) {
        this.droughtCount = droughtCount;
    }

    public int getGreenRainCount() {
        return greenRainCount;
    }

    public void setGreenRainCount(int greenRainCount) {
        this.greenRainCount = greenRainCount;
    }

    public int getFertilizerCount() {
        return fertilizerCount;
    }

    public void setFertilizerCount(int fertilizerCount) {
        this.fertilizerCount = fertilizerCount;
    }

    public long getLastDroughtGameDay() {
        return lastDroughtGameDay;
    }

    public void setLastDroughtGameDay(long lastDroughtGameDay) {
        this.lastDroughtGameDay = lastDroughtGameDay;
    }

    public boolean isWaterRescueOnDroughtDay() {
        return waterRescueOnDroughtDay;
    }

    public void setWaterRescueOnDroughtDay(boolean waterRescueOnDroughtDay) {
        this.waterRescueOnDroughtDay = waterRescueOnDroughtDay;
    }

    /** 经历过的随机事件列表（可变，由 MemoryService 维护）。 */
    public List<EventType> getEvents() {
        return events;
    }

    public boolean isWitherRisk() {
        return witherRisk;
    }

    public void setWitherRisk(boolean witherRisk) {
        this.witherRisk = witherRisk;
    }

    public Quality getQuality() {
        return quality;
    }

    public void setQuality(Quality quality) {
        this.quality = quality;
    }

    public boolean isLegendary() {
        return legendary;
    }

    public void setLegendary(boolean legendary) {
        this.legendary = legendary;
    }

    public String getFinalStory() {
        return finalStory;
    }

    public void setFinalStory(String finalStory) {
        this.finalStory = finalStory;
    }
}
