package com.fieldstory.farm.service.economy;

import com.fieldstory.farm.manager.GameManager;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.economy.PurchaseResult;
import com.fieldstory.farm.service.economy.impl.EconomyServiceImpl;
import com.fieldstory.farm.util.GameConstants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class P0EconomyFlowTest {

    @Test
    void shouldCompleteP0EconomyLoop() {

        /*
         * =============================
         * 1. 开始新游戏
         * =============================
         */

        GameState state =
                GameManager.newGame();

        EconomyService economy =
                new EconomyServiceImpl(
                        state.getPlayer()
                );

        assertEquals(
                500,
                economy.getGold()
        );


        /*
         * =============================
         * 2. 模拟A模块开垦
         * =============================
         */

        assertTrue(
                economy.canAfford(
                        GameConstants.TILL_COST
                )
        );

        economy.spendGold(
                GameConstants.TILL_COST
        );

        // 500 - 5 = 495
        assertEquals(
                495,
                economy.getGold()
        );


        /*
         * =============================
         * 3. B模块购买小麦种子
         * =============================
         */

        PurchaseResult result =
                economy.buySeed(
                        CropType.WHEAT,
                        1
                );

        assertEquals(
                PurchaseResult.SUCCESS,
                result
        );

        // 495 - 10 = 485
        assertEquals(
                485,
                economy.getGold()
        );

        assertEquals(
                1,
                economy.getSeedCount(
                        CropType.WHEAT
                )
        );


        /*
         * =============================
         * 4. 模拟A模块播种
         * =============================
         */

        boolean consumed =
                economy.consumeSeed(
                        CropType.WHEAT,
                        1
                );

        assertTrue(consumed);

        assertEquals(
                0,
                economy.getSeedCount(
                        CropType.WHEAT
                )
        );


        /*
         * =============================
         * 5. 模拟C模块收获
         * =============================
         */

        int sellPrice =
                economy.calculateBaseSellPrice(
                        CropType.WHEAT
                );

        assertEquals(
                50,
                sellPrice
        );

        economy.addGold(
                sellPrice
        );


        /*
         * =============================
         * 6. 最终经济结果
         *
         * 500
         * -5 开垦
         * -10 小麦种子
         * +50 小麦出售
         *
         * = 535
         * =============================
         */

        assertEquals(
                535,
                economy.getGold()
        );
    }
}