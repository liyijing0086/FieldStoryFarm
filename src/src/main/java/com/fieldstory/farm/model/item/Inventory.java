package com.fieldstory.farm.model.item;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 背包（C 物品模块）：物品容器，负责增删与数量查询。
 *
 * <p>对外接口（《C任务跨模块开发约束文档》§九，签名已锁定，不得随意改动）：
 * <ul>
 *   <li>{@link #addItem(Item)}：A 收获产物 / D 离线奖励入包；</li>
 *   <li>{@link #removeItem(int)}：移除指定槽位物品（A 播种消耗种子也走本方法）。</li>
 * </ul>
 *
 * <p>P0 槽位规则：每种 {@link ItemType} 对应一个固定槽位
 * （slotId = 类型的 ordinal），同类物品数量合并堆叠。
 */
public class Inventory {

    /** 槽位 → 数量（slotId = ItemType.ordinal()） */
    private final Map<ItemType, Integer> slots = new EnumMap<>(ItemType.class);

    /**
     * 向背包增加物品（同类型数量自动合并）。
     *
     * <p>对外接口，A/D 组员调用；签名已锁定。
     *
     * @param item 物品实例（数量 ≥1）
     * @return 入包成功返回 true；item 为 null 时返回 false
     */
    public boolean addItem(Item item) {
        if (item == null) {
            return false;
        }
        slots.merge(item.getType(), item.getQuantity(), Integer::sum);
        return true;
    }

    /**
     * 移除指定槽位的全部物品。
     *
     * <p>对外接口，A 组员播种时调用；签名已锁定。
     *
     * @param slotId 槽位编号（= {@link ItemType#ordinal()}）
     * @return 被移除的物品；槽位为空或编号非法时返回 null
     */
    public Item removeItem(int slotId) {
        ItemType type = itemTypeAt(slotId);
        if (type == null) {
            return null;
        }
        Integer count = slots.remove(type);
        if (count == null) {
            return null;
        }
        return new Item(type, count);
    }

    /**
     * 消耗指定类型的物品（播种等场景）。
     *
     * <p>附加接口（非锁定签名，供 A 组员播种时精确消耗 n 个种子）。
     *
     * @param type   物品类型
     * @param amount 消耗数量（≥1）
     * @return 库存充足并完成扣减返回 true；库存不足时不扣减并返回 false
     */
    public boolean consume(ItemType type, int amount) {
        if (type == null || amount <= 0) {
            return false;
        }
        int current = getQuantity(type);
        if (current < amount) {
            return false;
        }
        int left = current - amount;
        if (left == 0) {
            slots.remove(type);
        } else {
            slots.put(type, left);
        }
        return true;
    }

    /** 指定类型物品的当前数量。 */
    public int getQuantity(ItemType type) {
        return slots.getOrDefault(type, 0);
    }

    /** 指定槽位上的物品类型；槽位编号非法时返回 null。 */
    public ItemType itemTypeAt(int slotId) {
        ItemType[] values = ItemType.values();
        if (slotId < 0 || slotId >= values.length) {
            return null;
        }
        return values[slotId];
    }

    /** 物品类型对应的槽位编号（= ordinal）。 */
    public int slotIdOf(ItemType type) {
        return Objects.requireNonNull(type, "物品类型不能为空").ordinal();
    }

    /** 背包是否为空。 */
    public boolean isEmpty() {
        return slots.isEmpty();
    }

    /** 背包内所有物品快照（只读，供 View 展示）。 */
    public List<Item> listItems() {
        if (slots.isEmpty()) {
            return Collections.emptyList();
        }
        return slots.entrySet().stream()
                .map(e -> new Item(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }
}
