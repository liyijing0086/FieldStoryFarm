package com.fieldstory.farm.model;

/**
 * B 模块 P1 装饰实例状态。
 *
 * <p>Model 仅保存状态。id 与 E 模块 SQLite decoration.id 对齐；
 * row/column 为 -1 时表示装饰位于仓库中，非负时表示已放置。
 */
public interface Decoration {

    long getId();

    void setId(long id);

    DecorationType getDecorationType();

    void setDecorationType(DecorationType decorationType);

    int getRow();

    void setRow(int row);

    int getColumn();

    void setColumn(int column);

    default boolean isPlaced() {
        return getRow() >= 0 && getColumn() >= 0;
    }
}
