package com.fieldstory.farm.model;

/**
 * 单块土地在存档中的状态快照（E 存档聚合的一部分）。
 *
 * <p>字段与验收规范 §四十一（P0 JSON 必须保存）一一对应：
 * 土地状态、每株 Crop 的 cropUuid/cropType/growthStage/growthProgress/
 * plantWorldTime/manualWaterCount/lastManualWaterGameDay。
 *
 * <p>{@code state}/{@code cropType}/{@code growthStage} 以字符串保存，E 不持有
 * A 的 FarmPlot/SoilState/CropType/GrowthStage 枚举，避免在 A 模型交付前形成
 * 跨模块类型依赖；A 模块接入后由适配层在“枚举 ↔ 字符串”之间映射
 * （实现见 {@link com.fieldstory.farm.persistence.FarmStateAdapter}）。
 */
public class PlotState {

    /** 格子唯一标识；为空时序列化按 “row,column” 生成 */
    private String plotId;

    /** 行坐标（0-based） */
    private int row;

    /** 列坐标（0-based） */
    private int column;

    /** 土地状态名（空闲/已开垦/已播种…，对应未来 A 枚举名如 EMPTY/TILLED/…） */
    private String state;

    // ---------------- 作物部分（无作物时 JSON 中 crop 为 null） ----------------

    /** 作物唯一标识 */
    private String cropUuid;

    /** 作物类型名（WHEAT/CORN/CARROT…） */
    private String cropType;

    /** 成长阶段名（未来对应 GrowthStage 枚举名） */
    private String growthStage;

    /** 成长进度（A 模块内部口径 0~100，与 {@code CropDao} 一致；由成长模块写入） */
    private double growthProgress;

    /** 播种时刻世界时间（A 侧 long 游戏小时，以十进制字符串保存；E 不做时间换算） */
    private String plantWorldTime;

    /** 主动浇水次数 */
    private int manualWaterCount;

    /** 最近一次主动浇水的游戏日（A 侧 long 游戏日，以十进制字符串保存；-1 表示从未浇水） */
    private String lastManualWaterGameDay;

    public PlotState() {
        // 空构造供反序列化使用
    }

    /** 该格子当前是否存在作物。 */
    public boolean hasCrop() {
        return cropUuid != null;
    }

    public String getPlotId() {
        return plotId;
    }

    public void setPlotId(String plotId) {
        this.plotId = plotId;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCropUuid() {
        return cropUuid;
    }

    public void setCropUuid(String cropUuid) {
        this.cropUuid = cropUuid;
    }

    public String getCropType() {
        return cropType;
    }

    public void setCropType(String cropType) {
        this.cropType = cropType;
    }

    public String getGrowthStage() {
        return growthStage;
    }

    public void setGrowthStage(String growthStage) {
        this.growthStage = growthStage;
    }

    public double getGrowthProgress() {
        return growthProgress;
    }

    public void setGrowthProgress(double growthProgress) {
        this.growthProgress = growthProgress;
    }

    public String getPlantWorldTime() {
        return plantWorldTime;
    }

    public void setPlantWorldTime(String plantWorldTime) {
        this.plantWorldTime = plantWorldTime;
    }

    public int getManualWaterCount() {
        return manualWaterCount;
    }

    public void setManualWaterCount(int manualWaterCount) {
        this.manualWaterCount = manualWaterCount;
    }

    public String getLastManualWaterGameDay() {
        return lastManualWaterGameDay;
    }

    public void setLastManualWaterGameDay(String lastManualWaterGameDay) {
        this.lastManualWaterGameDay = lastManualWaterGameDay;
    }
}
