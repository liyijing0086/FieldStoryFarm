package com.fieldstory.farm.model.item;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ShopModel} P2 事件倍率出售测试（规则文档 §四十九 神秘商人 ×2；
 * 《C任务跨模块开发约束文档》§四.1：倍率变量存 D 的 EventService，
 * C 只读取公开事件倍率接口）。
 */
class ShopEventPriceRateTest {

    private ShopModel shop;
    private Player player;

    @BeforeEach
    void setUp() {
        shop = new ShopModel();
        player = new Player();
        player.setGold(0);
    }

    /**
     * 默认无事件倍率：出售结算价 = 总价 × 1.0，与 P0 行为一致
     * （约束文档 §四.1；EventPriceRateProvider.NONE 恒为 1.0）。
     */
    @Test
    void defaultProviderKeepsP0Price() {
        Item produce = new Item(ItemType.HARVEST_PRODUCE, 2, 50);

        assertEquals(100, shop.sellPriceFor(produce));
        assertEquals(100, shop.sellPriceFor(produce, CropType.WHEAT));
    }

    /**
     * 注入神秘商人倍率（模拟 D 模块 EventService）：目标作物 ×2，
     * 其他作物 1.0（规则文档 §四十九）。
     */
    @Test
    void mysteryMerchantDoublesTargetCropPrice() {
        shop.setEventPriceRateProvider(cropType ->
                cropType == CropType.WHEAT ? 2.0 : 1.0);
        Item produce = new Item(ItemType.HARVEST_PRODUCE, 1, 50);

        assertEquals(100, shop.sellPriceFor(produce, CropType.WHEAT));
        assertEquals(70, shop.sellPriceFor(new Item(ItemType.HARVEST_PRODUCE, 1, 70),
                CropType.CORN));
        // 锁定签名 sellItem(Item, Player) 委托 null 类型 → 倍率 1.0
        assertEquals(50, shop.sellPriceFor(produce));
    }

    /**
     * 三参 sellItem 结算：金币按事件倍率后的价格入账（金币唯一
     * 数据源 Player，接口要求清单 §D4）。
     */
    @Test
    void sellItemWithEventRateCreditsGold() {
        shop.setEventPriceRateProvider(cropType ->
                cropType == CropType.CARROT ? 2.0 : 1.0);
        Item produce = new Item(ItemType.HARVEST_PRODUCE, 1, 60);

        assertTrue(shop.sellItem(produce, player, CropType.CARROT));
        assertEquals(120, player.getGold());
    }

    /** 倍率结果四舍五入为整数金币（如 ×1.5）。 */
    @Test
    void fractionalRateRoundsToWholeGold() {
        shop.setEventPriceRateProvider(cropType -> 1.5);
        Item produce = new Item(ItemType.HARVEST_PRODUCE, 1, 15);

        assertEquals(23, shop.sellPriceFor(produce, CropType.WHEAT));
    }

    /** 注入 null 恢复默认 NONE 倍率（约束文档 §四.1 未接入 D 实现时的安全回退）。 */
    @Test
    void nullProviderFallsBackToNoRate() {
        shop.setEventPriceRateProvider(cropType -> 2.0);
        assertEquals(100, shop.sellPriceFor(new Item(ItemType.HARVEST_PRODUCE, 1, 50),
                CropType.WHEAT));

        shop.setEventPriceRateProvider(null);
        assertEquals(50, shop.sellPriceFor(new Item(ItemType.HARVEST_PRODUCE, 1, 50),
                CropType.WHEAT));
    }

    /** EventPriceRateProvider.NONE 单例恒为 1.0（任意类型）。 */
    @Test
    void noneProviderIsAlwaysOne() {
        assertEquals(1.0, EventPriceRateProvider.NONE.priceRateFor(CropType.WHEAT), 0.0001);
        assertEquals(1.0, EventPriceRateProvider.NONE.priceRateFor(null), 0.0001);
    }
}
