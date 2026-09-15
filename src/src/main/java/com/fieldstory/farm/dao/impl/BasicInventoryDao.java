package com.fieldstory.farm.dao.impl;

import com.fieldstory.farm.dao.InventoryDao;
import com.fieldstory.farm.model.item.Inventory;
import com.fieldstory.farm.model.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link InventoryDao} 内存实现（C 模块 物品·背包·商店域，P2）。
 *
 * <p>以物品列表快照保存背包数据，{@link #load()} 时重建 Inventory
 * 实例。覆盖式保存：每次 save 丢弃旧快照（与数据库 upsert 语义一致）。
 *
 * <p>P2 项目 pom 未引入数据库依赖（见 pom.xml），本类提供完整的
 * 存取语义供单元测试与离线结算对接；数据库实现在引入 JDBC 依赖后
 * 替换实现，接口与调用方不变。
 */
public class BasicInventoryDao implements InventoryDao {

    /** 背包快照：物品列表（save 时复制，load 时重建） */
    private List<Item> snapshot = new ArrayList<>();

    @Override
    public void save(Inventory inventory) {
        if (inventory == null) {
            snapshot = new ArrayList<>();
            return;
        }
        // 复制快照：load 时重建新实例，与原始背包解耦
        snapshot = new ArrayList<>(inventory.listItems());
    }

    @Override
    public Inventory load() {
        Inventory inventory = new Inventory();
        for (Item item : snapshot) {
            inventory.addItem(item);
        }
        return inventory;
    }

    @Override
    public void clear() {
        snapshot = new ArrayList<>();
    }
}
