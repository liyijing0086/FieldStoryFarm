package com.fieldstory.farm.model;

/**
 * 图鉴收集状态（E 模块 P3；验收规范 §一百一十二、规则文档 §七十二）。
 *
 * <p>每个图鉴项按「未发现 → 已发现 → 已收集」三态推进：
 * <ul>
 *   <li>{@link #UNDISCOVERED}：玩家不知道具体获得条件（默认态，不落库）；</li>
 *   <li>{@link #DISCOVERED}：曾经看见过对应品质信息或满足部分条件；</li>
 *   <li>{@link #COLLECTED}：真正收获过对应作物品质，<b>永久保存</b>（规则文档 §七十二）。</li>
 * </ul>
 *
 * <p>本枚举只描述状态与推进关系，不含任何业务计算（统一 Model 原则）。
 */
public enum CollectionStatus {

    /** 未发现：默认态，玩家尚不知道获得条件。 */
    UNDISCOVERED,

    /** 已发现：看见过对应信息或满足部分条件，但尚未真正收获。 */
    DISCOVERED,

    /** 已收集：真正收获过（永久保存，不可回退）。 */
    COLLECTED;

    /** 是否已经收集（{@link #COLLECTED}）。 */
    public boolean isCollected() {
        return this == COLLECTED;
    }

    /**
     * 状态推进：只允许沿 UNDISCOVERED → DISCOVERED → COLLECTED 前进，永不回退
     * （图鉴一旦收集永久保存，验收规范 §一百一十二）。
     *
     * @param target 目标状态
     * @return 二者中更靠后的状态（同序或回退时返回 {@code this}）
     */
    public CollectionStatus advanceTo(CollectionStatus target) {
        if (target == null) {
            return this;
        }
        return target.ordinal() > this.ordinal() ? target : this;
    }
}
