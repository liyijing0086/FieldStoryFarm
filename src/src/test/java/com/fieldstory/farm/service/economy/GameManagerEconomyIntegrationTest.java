package com.fieldstory.farm.service.economy;

import com.fieldstory.farm.manager.GameManager;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.economy.PurchaseResult;
import com.fieldstory.farm.service.economy.impl.EconomyServiceImpl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameManagerEconomyIntegrationTest {

    @Test
    void economyServiceShouldModifyGameManagerPlayer() {

        // 1. 由E模块GameManager创建真实的新游戏状态
        GameState state =
                GameManager.newGame();

        // 2. 获取GameState中唯一的Player
        Player player =
                state.getPlayer();

        // 3. B模块经济服务绑定这个Player
        EconomyService economyService =
                new EconomyServiceImpl(player);

        // 4. 购买1颗小麦种子
        PurchaseResult result =
                economyService.buySeed(
                        CropType.WHEAT,
                        1
                );

        // 5. 购买成功
        assertEquals(
                PurchaseResult.SUCCESS,
                result
        );

        // 6. 经济服务看到490金币
        assertEquals(
                490,
                economyService.getGold()
        );

        // 7. GameState中的Player也必须是490
        assertEquals(
                490,
                state.getPlayer().getGold()
        );

        // 8. GameState中的Player也必须有1颗小麦
        assertEquals(
                1,
                state.getPlayer()
                        .getSeedInventory()
                        .get(CropType.WHEAT)
        );
    }
}