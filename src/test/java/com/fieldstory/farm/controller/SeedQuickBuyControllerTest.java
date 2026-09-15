package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.economy.PurchaseResult;
import com.fieldstory.farm.service.economy.EconomyService;
import com.fieldstory.farm.service.economy.impl.EconomyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SeedQuickBuyControllerTest {

    private Player player;
    private EconomyService economyService;
    private SeedQuickBuyController controller;

    @BeforeEach
    void setUp() {

        player =
                new Player(
                        "测试玩家",
                        500
                );

        economyService =
                new EconomyServiceImpl(
                        player
                );

        controller =
                new SeedQuickBuyController(
                        economyService
                );
    }

    @Test
    void shouldBuyExactlyOneWheatSeed() {

        PurchaseResult result =
                controller.buyOneSeed(
                        CropType.WHEAT
                );

        assertEquals(
                PurchaseResult.SUCCESS,
                result
        );

        assertEquals(
                490,
                controller.getGold()
        );

        assertEquals(
                1,
                controller.getSeedCount(
                        CropType.WHEAT
                )
        );
    }

    @Test
    void shouldExposeCurrentEconomyState() {

        economyService.buySeed(
                CropType.CORN,
                2
        );

        assertEquals(
                470,
                controller.getGold()
        );

        assertEquals(
                2,
                controller.getSeedCount(
                        CropType.CORN
                )
        );
    }

    @Test
    void shouldKeepStateWhenGoldIsInsufficient() {

        player.setGold(5);

        PurchaseResult result =
                controller.buyOneSeed(
                        CropType.WHEAT
                );

        assertEquals(
                PurchaseResult.INSUFFICIENT_GOLD,
                result
        );

        assertEquals(
                5,
                controller.getGold()
        );

        assertEquals(
                0,
                controller.getSeedCount(
                        CropType.WHEAT
                )
        );
    }

    @Test
    void shouldMapPurchaseResultsToMessages() {

        assertEquals(
                "小麦种子购买成功",
                SeedQuickBuyController
                        .messageFor(
                                PurchaseResult.SUCCESS,
                                CropType.WHEAT
                        )
        );

        assertEquals(
                "金币不足",
                SeedQuickBuyController
                        .messageFor(
                                PurchaseResult
                                        .INSUFFICIENT_GOLD,
                                CropType.WHEAT
                        )
        );

        assertEquals(
                "购买数量无效",
                SeedQuickBuyController
                        .messageFor(
                                PurchaseResult
                                        .INVALID_QUANTITY,
                                CropType.WHEAT
                        )
        );
    }

    @Test
    void shouldRejectNullCropType() {

        assertThrows(
                NullPointerException.class,
                () ->
                        controller
                                .buyOneSeed(null)
        );
    }
}