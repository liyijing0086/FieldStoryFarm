package com.fieldstory.farm.model.item;

import java.util.Objects;

/**
 * 物品实例：类型 + 数量（C 物品模块）。
 *
 * <p>遵循统一 Model 原则：只保存状态，不含买卖/金币等业务计算
 * （那些属于 {@link ShopModel}）。
 *
 * <p>跨模块约定：A 收获产物、D 离线奖励均创建本类实例后经
 * {@link Inventory#addItem(Item)} 存入背包（约束文档 §二/§四）。
 */
public class Item {

    /** 物品类型 */
    private final ItemType type;

    /** 数量（≥1） */
    private final int quantity;

    /** 单件价格（金币）：购买/出售共用；收获产物由创建方传入基础售价 */
    private final int unitPrice;

    /** 使用类型默认单价（种子 = 购买价；产物/肥料 = 0）。 */
    public Item(ItemType type, int quantity) {
        this(type, quantity, type.getDefaultPrice());
    }

    /**
     * 显式指定单件价格。
     *
     * @param type      物品类型
     * @param quantity  数量（必须 ≥1）
     * @param unitPrice 单件价格（金币）；如收获产物：{@code new Item(HARVEST_PRODUCE, 1, 50)}
     */
    public Item(ItemType type, int quantity, int unitPrice) {
        this.type = Objects.requireNonNull(type, "物品类型不能为空");
        if (quantity <= 0) {
            throw new IllegalArgumentException("物品数量必须为正数: " + quantity);
        }
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public ItemType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getUnitPrice() {
        return unitPrice;
    }

    /** 总价 = 单件价格 × 数量。 */
    public int totalPrice() {
        return unitPrice * quantity;
    }

    @Override
    public String toString() {
        return type.getDisplayName() + "×" + quantity;
    }
}
