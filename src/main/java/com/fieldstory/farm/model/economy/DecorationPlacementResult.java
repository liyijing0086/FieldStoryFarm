package com.fieldstory.farm.model.economy;

/** B 模块 P1 装饰放置/移动结果。失败时保持原状态。 */
public enum DecorationPlacementResult {
    SUCCESS,
    NOT_OWNED,
    ALREADY_PLACED,
    NOT_PLACED,
    OUT_OF_BOUNDS,
    NOT_DECORATION_AREA,
    CELL_OCCUPIED,
    INVALID_SIZE
}
