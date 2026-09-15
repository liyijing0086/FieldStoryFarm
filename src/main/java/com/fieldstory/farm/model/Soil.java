package com.fieldstory.farm.model;

/**
 * 土地模型接口（验收规范 §十三；规则文档 §十一 仅 FARM_PLOT 持有 Soil）。
 *
 * <p>只保存状态，不含任何计算与业务规则。
 * 坐标一律为全局地图 0-based 坐标。
 */
public interface Soil {

    /** 土地唯一 id：由构造按 行*12+列 生成（行列为全局坐标） */
    long getId();

    /** 行坐标（全局地图坐标，0-based） */
    int getRow();

    /** 列坐标（全局地图坐标，0-based） */
    int getColumn();

    SoilState getState();

    void setState(SoilState state);

    /** 该格作物：未播种时为 null（验收规范 §十三） */
    Crop getCrop();

    void setCrop(Crop crop);
}
