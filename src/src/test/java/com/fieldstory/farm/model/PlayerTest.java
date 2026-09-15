package com.fieldstory.farm.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {

    @Test
    void shouldCreateNewPlayerWithNameAndGold() {

        Player player =
                new Player("农夫", 500);

        assertEquals(
                "农夫",
                player.getName()
        );

        assertEquals(
                500,
                player.getGold()
        );
    }

    @Test
    void shouldInitializeSeedInventoryWithZero() {

        Player player =
                new Player("农夫", 500);

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