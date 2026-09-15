package com.fieldstory.farm.dao;

import com.fieldstory.farm.dao.impl.BasicInventoryDao;
import com.fieldstory.farm.model.item.Inventory;
import com.fieldstory.farm.model.item.Item;
import com.fieldstory.farm.model.item.ItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InventoryDao} 存取语义测试（《C任务跨模块开发约束文档》§五：
 * C 负责 InventoryDAO 背包数据读写；P2 内存实现，数据库实现替换后
 * 接口与语义不变）。
 */
class BasicInventoryDaoTest {

    private InventoryDao dao;

    @BeforeEach
    void setUp() {
        dao = new BasicInventoryDao();
    }

    /** save/load 往返：物品类型与数量一致。 */
    @Test
    void saveAndLoadRoundTrip() {
        Inventory inventory = new Inventory();
        inventory.addItem(new Item(ItemType.WHEAT_SEED, 3));
        inventory.addItem(new Item(ItemType.FERTILIZER, 5));
        inventory.addItem(new Item(ItemType.HARVEST_PRODUCE, 2, 50));

        dao.save(inventory);
        Inventory loaded = dao.load();

        assertEquals(3, loaded.getQuantity(ItemType.WHEAT_SEED));
        assertEquals(5, loaded.getQuantity(ItemType.FERTILIZER));
        assertEquals(2, loaded.getQuantity(ItemType.HARVEST_PRODUCE));
    }

    /** 未保存时 load 返回空背包。 */
    @Test
    void loadWithoutSaveReturnsEmptyInventory() {
        Inventory loaded = dao.load();
        assertTrue(loaded.isEmpty());
    }

    /**
     * load 返回新实例：修改 load 结果不影响已保存快照
     * （快照隔离，防止外部篡改存档数据）。
     */
    @Test
    void loadedInventoryIsDecoupledFromSnapshot() {
        Inventory inventory = new Inventory();
        inventory.addItem(new Item(ItemType.WHEAT_SEED, 3));
        dao.save(inventory);

        Inventory loaded = dao.load();
        loaded.addItem(new Item(ItemType.WHEAT_SEED, 10));

        assertEquals(3, dao.load().getQuantity(ItemType.WHEAT_SEED));
    }

    /** 覆盖式保存：新快照替换旧快照。 */
    @Test
    void saveOverwritesPreviousSnapshot() {
        Inventory first = new Inventory();
        first.addItem(new Item(ItemType.WHEAT_SEED, 3));
        dao.save(first);

        Inventory second = new Inventory();
        second.addItem(new Item(ItemType.CARROT_SEED, 1));
        dao.save(second);

        Inventory loaded = dao.load();
        assertEquals(0, loaded.getQuantity(ItemType.WHEAT_SEED));
        assertEquals(1, loaded.getQuantity(ItemType.CARROT_SEED));
    }

    /** save(null) 清空快照（空背包语义）。 */
    @Test
    void saveNullClearsSnapshot() {
        Inventory inventory = new Inventory();
        inventory.addItem(new Item(ItemType.WHEAT_SEED, 3));
        dao.save(inventory);

        dao.save(null);

        assertTrue(dao.load().isEmpty());
    }

    /** clear 清空快照。 */
    @Test
    void clearRemovesSnapshot() {
        Inventory inventory = new Inventory();
        inventory.addItem(new Item(ItemType.WHEAT_SEED, 3));
        dao.save(inventory);

        dao.clear();

        assertTrue(dao.load().isEmpty());
    }
}
