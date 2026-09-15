package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;

/**
 * {@link Soil} 基础实现：纯状态容器，只提供字段读写，不含任何计算与业务规则。
 *
 * <p>id 由构造按 行*12+列 生成（行列为全局 0-based 坐标），
 * 与 {@link BasicFarm} 的 12×12 地图约定一致。
 */
public class BasicSoil implements Soil {

    /** 地图总边长（规则文档 §10.1），用于 id = 行*12+列 的生成口径 */
    public static final int MAP_SIZE = 12;

    /** 土地唯一 id：行*12+列（全局坐标） */
    private final long id;

    /** 行坐标（全局地图坐标，0-based） */
    private final int row;

    /** 列坐标（全局地图坐标，0-based） */
    private final int column;

    /** 土地状态 */
    private SoilState state;

    /** 该格作物：未播种时为 null */
    private Crop crop;

    /**
     * 以全局 0-based 坐标构造土地。
     *
     * @param row    全局行坐标（0-based）
     * @param column 全局列坐标（0-based）
     */
    public BasicSoil(int row, int column) {
        this.id = row * (long) MAP_SIZE + column;
        this.row = row;
        this.column = column;
        this.state = SoilState.EMPTY;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public int getRow() {
        return row;
    }

    @Override
    public int getColumn() {
        return column;
    }

    @Override
    public SoilState getState() {
        return state;
    }

    @Override
    public void setState(SoilState state) {
        this.state = state;
    }

    @Override
    public Crop getCrop() {
        return crop;
    }

    @Override
    public void setCrop(Crop crop) {
        this.crop = crop;
    }
}
