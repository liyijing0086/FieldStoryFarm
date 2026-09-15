package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.service.LandUnlockPriceProvider;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** MainController 新游戏配置接线测试：只有 balance-config 指定格才上锁。 */
class P3ProductionWiringTest {

    @Test
    void newGameLocksOnlyConfiguredEmptyPlots() {
        Farm farm = new BasicFarm();
        Soil untouched = farm.getSoil(2, 3);
        LandUnlockPriceProvider provider = (row, col) -> row == 2 && col == 2
                ? OptionalInt.of(100) : OptionalInt.empty();

        MainController.applyConfiguredLockedPlots(farm, provider);

        assertEquals(SoilState.LOCKED, farm.getSoil(2, 2).getState());
        assertEquals(SoilState.EMPTY, untouched.getState());
    }

    @Test
    void applyingConfigNeverRelocksAlreadyUnlockedOrUsedPlot() {
        Farm farm = new BasicFarm();
        Soil configured = farm.getSoil(2, 2);
        configured.setState(SoilState.TILLED);
        LandUnlockPriceProvider provider = (row, col) -> row == 2 && col == 2
                ? OptionalInt.of(100) : OptionalInt.empty();

        MainController.applyConfiguredLockedPlots(farm, provider);

        assertEquals(SoilState.TILLED, configured.getState());
    }
}
