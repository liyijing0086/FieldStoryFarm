package com.fieldstory.farm.model.item;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 Inventory 背包测试：入包合并、按槽位移除、精确消耗、数量查询。
 */
class InventoryTest {

    private Inventory inventory;

    @BeforeEach
    void setUp() {
        inventory = new Inventory();
    }

    @Test
    void addItemStoresQuantity() {
        assertTrue(inventory.addItem(new Item(ItemType.WHEAT_SEED, 3)));
        assertEquals(3, inventory.getQuantity(ItemType.WHEAT_SEED));
        assertEquals(0, inventory.getQuantity(ItemType.CORN_SEED));
        assertFalse(inventory.isEmpty());
    }

    @Test
    void addSameTypeMergesQuantity() {
        inventory.addItem(new Item(ItemType.WHEAT_SEED, 3));
        inventory.addItem(new Item(ItemType.WHEAT_SEED, 2));

        assertEquals(5, inventory.getQuantity(ItemType.WHEAT_SEED));
        // 同类合并后只占一个槽位
        assertEquals(1, inventory.listItems().size());
    }

    @Test
    void addNullItemReturnsFalse() {
        assertFalse(inventory.addItem(null));
        assertTrue(inventory.isEmpty());
    }

    @Test
    void removeItemReturnsWholeSlotAndClears() {
        inventory.addItem(new Item(ItemType.CARROT_SEED, 4));

        Item removed = inventory.removeItem(ItemType.CARROT_SEED.ordinal());

        assertNotNull(removed);
        assertEquals(ItemType.CARROT_SEED, removed.getType());
        assertEquals(4, removed.getQuantity());
        assertEquals(0, inventory.getQuantity(ItemType.CARROT_SEED));
        assertTrue(inventory.isEmpty());
    }

    @Test
    void removeItemOnEmptyOrInvalidSlotReturnsNull() {
        assertNull(inventory.removeItem(ItemType.WHEAT_SEED.ordinal()));
        assertNull(inventory.removeItem(-1));
        assertNull(inventory.removeItem(999));
    }

    @Test
    void consumeDeductsExactAmount() {
        inventory.addItem(new Item(ItemType.WHEAT_SEED, 3));

        assertTrue(inventory.consume(ItemType.WHEAT_SEED, 1));
        assertEquals(2, inventory.getQuantity(ItemType.WHEAT_SEED));
    }

    @Test
    void consumeAllRemovesSlot() {
        inventory.addItem(new Item(ItemType.CORN_SEED, 2));

        assertTrue(inventory.consume(ItemType.CORN_SEED, 2));
        assertEquals(0, inventory.getQuantity(ItemType.CORN_SEED));
        assertTrue(inventory.isEmpty());
    }

    @Test
    void consumeInsufficientStockReturnsFalseWithoutDeduct() {
        inventory.addItem(new Item(ItemType.WHEAT_SEED, 1));

        assertFalse(inventory.consume(ItemType.WHEAT_SEED, 2));
        assertEquals(1, inventory.getQuantity(ItemType.WHEAT_SEED));
    }

    @Test
    void consumeInvalidArgumentsReturnsFalse() {
        assertFalse(inventory.consume(null, 1));
        assertFalse(inventory.consume(ItemType.WHEAT_SEED, 0));
        assertFalse(inventory.consume(ItemType.WHEAT_SEED, -1));
    }

    @Test
    void slotIdIsStableOrdinal() {
        assertEquals(ItemType.WHEAT_SEED.ordinal(), inventory.slotIdOf(ItemType.WHEAT_SEED));
        assertEquals(ItemType.WHEAT_SEED, inventory.itemTypeAt(ItemType.WHEAT_SEED.ordinal()));
    }

    @Test
    void listItemsReflectsCurrentInventory() {
        inventory.addItem(new Item(ItemType.WHEAT_SEED, 1));
        inventory.addItem(new Item(ItemType.CARROT_SEED, 2));

        List<Item> items = inventory.listItems();
        assertEquals(2, items.size());

        long wheat = items.stream()
                .filter(i -> i.getType() == ItemType.WHEAT_SEED)
                .mapToInt(Item::getQuantity)
                .sum();
        long carrot = items.stream()
                .filter(i -> i.getType() == ItemType.CARROT_SEED)
                .mapToInt(Item::getQuantity)
                .sum();
        assertEquals(1, wheat);
        assertEquals(2, carrot);
    }
}
