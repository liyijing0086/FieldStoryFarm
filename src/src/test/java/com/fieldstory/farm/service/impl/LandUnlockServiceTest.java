package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.economy.LandUnlockResult;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.service.LandUnlockPriceProvider;
import com.fieldstory.farm.service.economy.impl.EconomyServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P3 土地解锁核心业务测试。 */
class LandUnlockServiceTest {

    @Test
    void lockedPlotUsesProviderPriceSpendsGoldAndBecomesEmpty() {
        Player player = new Player("农夫", 500);
        Soil soil = new BasicFarm().getSoil(2, 2);
        soil.setState(SoilState.LOCKED);
        LandUnlockPriceProvider prices = (row, col) -> row == 2 && col == 2
                ? OptionalInt.of(100) : OptionalInt.empty();
        BasicLandUnlockService service = new BasicLandUnlockService(
                new EconomyServiceImpl(player), prices);

        assertTrue(service.canUnlock(soil));
        assertEquals(LandUnlockResult.SUCCESS, service.unlock(soil));
        assertEquals(400, player.getGold());
        assertEquals(SoilState.EMPTY, soil.getState());
    }

    @Test
    void missingPriceOrGoldNeverMutatesState() {
        Player player = new Player("农夫", 50);
        Soil soil = new BasicFarm().getSoil(2, 2);
        soil.setState(SoilState.LOCKED);

        BasicLandUnlockService missing = new BasicLandUnlockService(
                new EconomyServiceImpl(player), (row, col) -> OptionalInt.empty());
        assertEquals(LandUnlockResult.PRICE_NOT_CONFIGURED, missing.unlock(soil));
        assertEquals(50, player.getGold());
        assertEquals(SoilState.LOCKED, soil.getState());

        BasicLandUnlockService expensive = new BasicLandUnlockService(
                new EconomyServiceImpl(player), (row, col) -> OptionalInt.of(100));
        assertFalse(expensive.canUnlock(soil));
        assertEquals(LandUnlockResult.NO_GOLD, expensive.unlock(soil));
        assertEquals(50, player.getGold());
        assertEquals(SoilState.LOCKED, soil.getState());
    }
}
