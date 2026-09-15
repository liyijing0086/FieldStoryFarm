package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.BuffSnapshot;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Decoration;
import com.fieldstory.farm.model.DecorationType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.service.BuffService;
import com.fieldstory.farm.service.DecorationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuffServiceTest {

    @Test
    void threeAdjacentSunflowersCapAtFifteenPercent() {
        GameState state = new GameState(new Player("T", 500), 1);
        DecorationService decorations = new BasicDecorationService(new BasicFarm(), state);
        BuffService buffs = new BasicBuffService(decorations);

        Decoration a = decorations.addPurchasedDecoration(DecorationType.SUNFLOWER, 1).get(0);
        Decoration b = decorations.addPurchasedDecoration(DecorationType.SUNFLOWER, 1).get(0);
        Decoration c = decorations.addPurchasedDecoration(DecorationType.SUNFLOWER, 1).get(0);
        Decoration d = decorations.addPurchasedDecoration(DecorationType.SUNFLOWER, 1).get(0);
        decorations.place(a, 1, 1);
        decorations.place(b, 1, 2);
        decorations.place(c, 2, 1);
        decorations.place(d, 1, 3);

        assertEquals(1.15, buffs.getGrowthRate(2, 2, CropType.WHEAT), 1e-9);
    }

    @Test
    void d14AddsPriceAndQuality() {
        GameState state = new GameState(new Player("T", 500), 1);
        DecorationService decorations = new BasicDecorationService(new BasicFarm(), state);
        BuffService buffs = new BasicBuffService(decorations);
        Decoration goddess = decorations.addPurchasedDecoration(DecorationType.HARVEST_GODDESS, 1).get(0);
        decorations.place(goddess, 0, 0);

        BuffSnapshot snapshot = buffs.getSnapshot(2, 2, CropType.WHEAT);
        assertEquals(1.15, snapshot.priceRate(), 1e-9);
        assertEquals(5, snapshot.qualityScoreBonus());
    }
}
