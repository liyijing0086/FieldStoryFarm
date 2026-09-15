package com.fieldstory.farm.service.economy;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.economy.PurchaseResult;

import com.fieldstory.farm.service.economy.impl.EconomyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EconomyServiceImplTest {

    private Player player;
    private EconomyService economyService;

    /**
     * 每个测试执行前，
     * 都重新创建一个金币500、库存为空的玩家。
     */
    @BeforeEach
    void setUp() {

        player =
                new Player("测试玩家", 500);

        economyService =
                new EconomyServiceImpl(player);
    }


    // =========================================================
    // 金币测试
    // =========================================================

    @Test
    void shouldReturnCurrentGold() {

        assertEquals(
                500,
                economyService.getGold()
        );
    }


    @Test
    void shouldCheckAffordability() {

        assertTrue(
                economyService.canAfford(500)
        );

        assertFalse(
                economyService.canAfford(501)
        );
    }


    @Test
    void shouldSpendGold() {

        economyService.spendGold(50);

        assertEquals(
                450,
                economyService.getGold()
        );
    }


    @Test
    void shouldRejectOverspending() {

        assertThrows(
                IllegalStateException.class,
                () -> economyService.spendGold(501)
        );

        assertEquals(
                500,
                economyService.getGold()
        );
    }


    @Test
    void shouldAddGold() {

        economyService.addGold(50);

        assertEquals(
                550,
                economyService.getGold()
        );
    }


    @Test
    void shouldRejectNegativeGoldOperations() {

        assertThrows(
                IllegalArgumentException.class,
                () -> economyService.addGold(-1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> economyService.spendGold(-1)
        );
    }


    // =========================================================
    // 种子购买测试
    // =========================================================

    @Test
    void shouldBuyWheatSeed() {

        PurchaseResult result =
                economyService.buySeed(
                        CropType.WHEAT,
                        1
                );

        assertEquals(
                PurchaseResult.SUCCESS,
                result
        );

        assertEquals(
                490,
                economyService.getGold()
        );

        assertEquals(
                1,
                economyService.getSeedCount(
                        CropType.WHEAT
                )
        );
    }


    @Test
    void shouldBuyMultipleCornSeeds() {

        PurchaseResult result =
                economyService.buySeed(
                        CropType.CORN,
                        2
                );

        assertEquals(
                PurchaseResult.SUCCESS,
                result
        );

        // 500 - 15 * 2
        assertEquals(
                470,
                economyService.getGold()
        );

        assertEquals(
                2,
                economyService.getSeedCount(
                        CropType.CORN
                )
        );
    }


    @Test
    void shouldRejectInvalidPurchaseQuantity() {

        PurchaseResult result =
                economyService.buySeed(
                        CropType.WHEAT,
                        0
                );

        assertEquals(
                PurchaseResult.INVALID_QUANTITY,
                result
        );

        assertEquals(
                500,
                economyService.getGold()
        );

        assertEquals(
                0,
                economyService.getSeedCount(
                        CropType.WHEAT
                )
        );
    }


    @Test
    void shouldRejectPurchaseWhenGoldIsInsufficient() {

        player.setGold(5);

        PurchaseResult result =
                economyService.buySeed(
                        CropType.WHEAT,
                        1
                );

        assertEquals(
                PurchaseResult.INSUFFICIENT_GOLD,
                result
        );

        assertEquals(
                5,
                economyService.getGold()
        );

        assertEquals(
                0,
                economyService.getSeedCount(
                        CropType.WHEAT
                )
        );
    }


    // =========================================================
    // 种子库存测试
    // =========================================================

    @Test
    void shouldConsumeSeed() {

        economyService.buySeed(
                CropType.CARROT,
                2
        );

        boolean result =
                economyService.consumeSeed(
                        CropType.CARROT,
                        1
                );

        assertTrue(result);

        assertEquals(
                1,
                economyService.getSeedCount(
                        CropType.CARROT
                )
        );
    }


    @Test
    void shouldNotConsumeSeedWhenInventoryIsInsufficient() {

        boolean result =
                economyService.consumeSeed(
                        CropType.CORN,
                        1
                );

        assertFalse(result);

        assertEquals(
                0,
                economyService.getSeedCount(
                        CropType.CORN
                )
        );
    }


    @Test
    void shouldRejectZeroSeedConsumption() {

        economyService.buySeed(
                CropType.WHEAT,
                2
        );

        boolean result =
                economyService.consumeSeed(
                        CropType.WHEAT,
                        0
                );

        assertFalse(result);

        assertEquals(
                2,
                economyService.getSeedCount(
                        CropType.WHEAT
                )
        );
    }


    @Test
    void shouldRejectNegativeSeedConsumption() {

        economyService.buySeed(
                CropType.WHEAT,
                2
        );

        boolean result =
                economyService.consumeSeed(
                        CropType.WHEAT,
                        -1
                );

        assertFalse(result);

        assertEquals(
                2,
                economyService.getSeedCount(
                        CropType.WHEAT
                )
        );
    }


    @Test
    void shouldRejectZeroHasSeedQuantity() {

        assertFalse(
                economyService.hasSeed(
                        CropType.WHEAT,
                        0
                )
        );
    }


    // =========================================================
    // 价格测试
    // =========================================================

    @Test
    void shouldReturnBaseSellPricesFromCropType() {

        assertEquals(
                50,
                economyService.calculateBaseSellPrice(
                        CropType.WHEAT
                )
        );

        assertEquals(
                70,
                economyService.calculateBaseSellPrice(
                        CropType.CORN
                )
        );

        assertEquals(
                60,
                economyService.calculateBaseSellPrice(
                        CropType.CARROT
                )
        );
    }


    // =========================================================
    // null参数测试
    // =========================================================

    @Test
    void shouldRejectNullCropType() {

        assertThrows(
                NullPointerException.class,
                () -> economyService.buySeed(
                        null,
                        1
                )
        );
    }
}
