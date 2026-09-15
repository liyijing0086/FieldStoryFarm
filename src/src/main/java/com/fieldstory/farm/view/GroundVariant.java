package com.fieldstory.farm.view;

/**
 * 地面贴图变体（A 模块 P1 地面 Tile 美化；决策 D-G2/D-G3）。
 *
 * <p>由 {@link FarmView#groundVariantFor} 判定，加载器按变体从
 * {@link GroundSpriteSheet} 取对应帧：GRASS 草地帧、TILLED 耕地干帧、
 * WET 湿地深色帧；NONE 为「不铺贴图」哨兵，格子保持纯色语义。
 */
public enum GroundVariant {

    /** 不铺贴图：MATURE/WITHERED/EMPTY/LOCKED 保持纯色语义（决策 D-G3） */
    NONE,

    /** 草地帧（col=3, row=6） */
    GRASS,

    /** 耕地干帧（col=3, row=1） */
    TILLED,

    /** 湿地深色帧（col=8, row=10）：今日已浇或今日湿天（决策 D-G2） */
    WET
}
