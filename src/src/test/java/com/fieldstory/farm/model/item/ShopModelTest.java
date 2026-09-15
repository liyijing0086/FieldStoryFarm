package com.fieldstory.farm.model.item;

import com.fieldstory.farm.model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 ShopModel 商店测试：购买扣款、金币不足拒绝、出售结算、总价计算。
 *
 * <p>数值依据：《完整游戏规则设计文档》§六十三 初始金币 500；§六十四 种子价格
 * 小麦 10 / 玉米 15 / 胡萝卜 20。
 */
class ShopModelTest {

    private ShopModel shop;
    private Player player;

    @BeforeEach
    void setUp() {
        shop = new ShopModel();
        player = new Player("测试农夫", 500);
    }

    @Test
    void buySeedDeductsGold() {
        Item wheat = new Item(ItemType.WHEAT_SEED, 3);   // 单价 10

        assertTrue(shop.buyItem(wheat, player));
        assertEquals(500 - 30, player.getGold());
    }

    @Test
    void buyInsufficientGoldReturnsFalseAndKeepsGold() {
        Item carrot = new Item(ItemType.CARROT_SEED, 50); // 单价 20，总价 1000 > 500

        assertFalse(shop.buyItem(carrot, player));
        assertEquals(500, player.getGold());
    }

    @Test
    void buyWithExactGoldSucceeds() {
        Item carrot = new Item(ItemType.CARROT_SEED, 25); // 20 × 25 = 500

        assertTrue(shop.buyItem(carrot, player));
        assertEquals(0, player.getGold());
    }

    @Test
    void buyInvalidArgumentsReturnsFalse() {
        assertFalse(shop.buyItem(null, player));
        assertFalse(shop.buyItem(new Item(ItemType.WHEAT_SEED, 1), null));
        // 单价为 0 的物品不可购买（如产物/肥料默认单价 0）
        assertFalse(shop.buyItem(new Item(ItemType.HARVEST_PRODUCE, 1), player));
        assertEquals(500, player.getGold());
    }

    @Test
    void sellItemAddsGoldByUnitPrice() {
        // 收获产物：小麦基础售价 50（验收规范 §三十二）
        Item produce = new Item(ItemType.HARVEST_PRODUCE, 2, 50);

        assertTrue(shop.sellItem(produce, player));
        assertEquals(600, player.getGold());
    }

    @Test
    void sellInvalidArgumentsReturnsFalse() {
        assertFalse(shop.sellItem(null, player));
        assertFalse(shop.sellItem(new Item(ItemType.WHEAT_SEED, 1), null));
        assertEquals(500, player.getGold());
    }

    @Test
    void canAffordChecksGold() {
        assertTrue(shop.canAfford(player, 500));
        assertFalse(shop.canAfford(player, 501));
    }

    @Test
    void priceForComputesTotal() {
        assertEquals(10, shop.priceFor(ItemType.WHEAT_SEED, 1));
        assertEquals(30, shop.priceFor(ItemType.CORN_SEED, 2));     // 15 × 2
        assertEquals(60, shop.priceFor(ItemType.CARROT_SEED, 3));   // 20 × 3
    }

    @Test
    void priceForInvalidArgumentsThrows() {
        assertThrows(IllegalArgumentException.class, () -> shop.priceFor(ItemType.WHEAT_SEED, 0));
        assertThrows(IllegalArgumentException.class, () -> shop.priceFor(ItemType.WHEAT_SEED, -1));
        assertThrows(NullPointerException.class, () -> shop.priceFor(null, 1));
    }
}
