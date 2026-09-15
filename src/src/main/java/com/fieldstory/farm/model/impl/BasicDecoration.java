package com.fieldstory.farm.model.impl;

import com.fieldstory.farm.model.Decoration;
import com.fieldstory.farm.model.DecorationType;

import java.util.Objects;

/** B 模块 P1 装饰实例默认实现。 */
public class BasicDecoration implements Decoration {

    private long id;
    private DecorationType decorationType;
    private int row = -1;
    private int column = -1;

    public BasicDecoration() {
    }

    public BasicDecoration(long id, DecorationType decorationType) {
        this.id = id;
        this.decorationType = Objects.requireNonNull(decorationType, "decorationType");
    }

    public BasicDecoration(long id, DecorationType decorationType, int row, int column) {
        this(id, decorationType);
        this.row = row;
        this.column = column;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    @Override
    public DecorationType getDecorationType() {
        return decorationType;
    }

    @Override
    public void setDecorationType(DecorationType decorationType) {
        this.decorationType = decorationType;
    }

    @Override
    public int getRow() {
        return row;
    }

    @Override
    public void setRow(int row) {
        this.row = row;
    }

    @Override
    public int getColumn() {
        return column;
    }

    @Override
    public void setColumn(int column) {
        this.column = column;
    }
}
