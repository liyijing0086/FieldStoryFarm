package com.fieldstory.farm.manager;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测试B模块Player与E模块GameManager的新游戏创建契约。
 */
class MatchGameManagerPlayerTest {

    @Test
    void shouldMatchGameManagerNewGamePlayerContract() {

        // GameManager创建全新的游戏状态
        GameState state =
                GameManager.newGame();

        // 获取GameManager创建的Player
        Player player =
                state.getPlayer();

        // 验证默认名称
        assertEquals(
                "农夫",
                player.getName()
        );

        // 验证初始金币
        assertEquals(
                500,
                player.getGold()
        );

        // 验证三种种子初始库存
        assertEquals(
                0,
                player.getSeedInventory()
                        .get(CropType.WHEAT)
        );

        assertEquals(
                0,
                player.getSeedInventory()
                        .get(CropType.CORN)
        );

        assertEquals(
                0,
                player.getSeedInventory()
                        .get(CropType.CARROT)
        );
    }
}