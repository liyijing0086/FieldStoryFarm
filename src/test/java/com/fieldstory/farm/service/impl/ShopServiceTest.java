package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.DecorationType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.model.economy.DecorationPurchaseResult;
import com.fieldstory.farm.service.DecorationService;
import com.fieldstory.farm.service.ShopService;
import com.fieldstory.farm.service.economy.EconomyService;
import com.fieldstory.farm.service.economy.impl.EconomyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopServiceTest {

    private Player player;
    private DecorationService decorationService;
    private ShopService shopService;

    @BeforeEach
    void setUp() {
        player = new Player("测试玩家", 500);
        GameState state = new GameState(player, 1);
        EconomyService economyService = new EconomyServiceImpl(player);
        decorationService = new BasicDecorationService(new BasicFarm(), state);
        shopService = new BasicShopService(economyService, decorationService);
    }

    @Test
    void buyDecorationDeductsGoldAndAddsInventory() {
        assertEquals(DecorationPurchaseResult.SUCCESS,
                shopService.buyDecoration(DecorationType.SUNFLOWER, 1));
        assertEquals(420, player.getGold());
        assertEquals(1, decorationService.getOwnedCount(DecorationType.SUNFLOWER));
    }

    @Test
    void insufficientGoldDoesNotMutateState() {
        player.setGold(10);
        assertEquals(DecorationPurchaseResult.INSUFFICIENT_GOLD,
                shopService.buyDecoration(DecorationType.SUNFLOWER, 1));
        assertEquals(10, player.getGold());
        assertEquals(0, decorationService.getOwnedCount(DecorationType.SUNFLOWER));
    }

    @Test
    void invalidQuantityDoesNotMutateState() {
        assertEquals(DecorationPurchaseResult.INVALID_QUANTITY,
                shopService.buyDecoration(DecorationType.SUNFLOWER, 0));
        assertEquals(500, player.getGold());
        assertEquals(0, decorationService.getOwnedCount(DecorationType.SUNFLOWER));
    }

    @Test
    void hugeQuantityIsRejectedWithoutOverflow() {
        assertEquals(DecorationPurchaseResult.INVALID_QUANTITY,
                shopService.buyDecoration(DecorationType.HARVEST_GODDESS, Integer.MAX_VALUE));
        assertEquals(500, player.getGold());
    }
}
