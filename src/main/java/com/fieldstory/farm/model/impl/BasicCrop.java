package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GrowthStage;

import java.util.UUID;

/**
 * {@link Crop} 基础实现：纯状态容器，只提供字段读写，不含任何计算与业务规则。
 */
public class BasicCrop implements Crop {

    /** 作物唯一标识 */
    private UUID cropUuid;

    /** 作物类型 */
    private CropType cropType;

    /** 成长阶段 */
    private GrowthStage growthStage;

    /** 成长进度，内部口径 0~100 */
    private double growthProgress;

    /** 播种时刻世界时间（游戏小时，来自 GameClock.getWorldTime） */
    private long plantWorldTime;

    /** 主动浇水累计次数 */
    private int manualWaterCount;

    /** 最近一次主动浇水的游戏日（游戏日，来自 GameClock.getGameDay）；
     *  默认 -1 哨兵表示"从未浇水"，与游戏日 0 区分（决策 D14） */
    private long lastManualWaterGameDay = -1;

    /** 累计干旱日数（规则文档 §二十二；验收规范 §五十） */
    private int droughtCount;

    /** 累计雨日数（规则文档 §二十一；验收规范 §五十） */
    private int rainCount;

    /** 累计绿雨日数（规则文档 §二十三；验收规范 §五十） */
    private int greenRainCount;

    /** 最近一次补水时刻（游戏小时；默认 -1 哨兵表示"无记录"，决策 D16） */
    private long lastHydratedWorldTime = -1;

    /** 连续干旱计数（规则文档 §二十九；验收规范 §五十） */
    private int droughtStreak;

    @Override
    public UUID getCropUuid() {
        return cropUuid;
    }

    @Override
    public void setCropUuid(UUID cropUuid) {
        this.cropUuid = cropUuid;
    }

    @Override
    public CropType getCropType() {
        return cropType;
    }

    @Override
    public void setCropType(CropType cropType) {
        this.cropType = cropType;
    }

    @Override
    public GrowthStage getGrowthStage() {
        return growthStage;
    }

    @Override
    public void setGrowthStage(GrowthStage growthStage) {
        this.growthStage = growthStage;
    }

    @Override
    public double getGrowthProgress() {
        return growthProgress;
    }

    @Override
    public void setGrowthProgress(double growthProgress) {
        this.growthProgress = growthProgress;
    }

    @Override
    public long getPlantWorldTime() {
        return plantWorldTime;
    }

    @Override
    public void setPlantWorldTime(long plantWorldTime) {
        this.plantWorldTime = plantWorldTime;
    }

    @Override
    public int getManualWaterCount() {
        return manualWaterCount;
    }

    @Override
    public void setManualWaterCount(int manualWaterCount) {
        this.manualWaterCount = manualWaterCount;
    }

    @Override
    public long getLastManualWaterGameDay() {
        return lastManualWaterGameDay;
    }

    @Override
    public void setLastManualWaterGameDay(long lastManualWaterGameDay) {
        this.lastManualWaterGameDay = lastManualWaterGameDay;
    }

    @Override
    public int getDroughtCount() {
        return droughtCount;
    }

    @Override
    public void setDroughtCount(int droughtCount) {
        this.droughtCount = droughtCount;
    }

    @Override
    public int getRainCount() {
        return rainCount;
    }

    @Override
    public void setRainCount(int rainCount) {
        this.rainCount = rainCount;
    }

    @Override
    public int getGreenRainCount() {
        return greenRainCount;
    }

    @Override
    public void setGreenRainCount(int greenRainCount) {
        this.greenRainCount = greenRainCount;
    }

    @Override
    public long getLastHydratedWorldTime() {
        return lastHydratedWorldTime;
    }

    @Override
    public void setLastHydratedWorldTime(long lastHydratedWorldTime) {
        this.lastHydratedWorldTime = lastHydratedWorldTime;
    }

    @Override
    public int getDroughtStreak() {
        return droughtStreak;
    }

    @Override
    public void setDroughtStreak(int droughtStreak) {
        this.droughtStreak = droughtStreak;
    }
}
