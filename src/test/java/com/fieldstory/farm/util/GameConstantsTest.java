package com.fieldstory.farm.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameConstantsTest {

    @Test
    void shouldProvideCorrectEconomyConstants() {

        assertEquals(
                500,
                GameConstants.INITIAL_GOLD
        );

        assertEquals(
                5,
                GameConstants.TILL_COST
        );
    }

    @Test
    void shouldProvideCorrectMapConstants() {

        assertEquals(
                12,
                GameConstants.MAP_ROWS
        );

        assertEquals(
                12,
                GameConstants.MAP_COLS
        );

        assertEquals(
                2,
                GameConstants.CENTER_START_ROW
        );

        assertEquals(
                9,
                GameConstants.CENTER_END_ROW
        );

        assertEquals(
                2,
                GameConstants.CENTER_START_COL
        );

        assertEquals(
                9,
                GameConstants.CENTER_END_COL
        );

        assertEquals(
                44,
                GameConstants.TILE_SIZE
        );
    }

    @Test
    void shouldProvideCorrectTimeConstants() {

        assertEquals(
                1,
                GameConstants.REAL_SECONDS_PER_TICK
        );

        assertEquals(
                1,
                GameConstants.MINUTES_PER_TICK
        );

        assertEquals(
                1440,
                GameConstants.MINUTES_PER_DAY
        );

        assertEquals(
                360,
                GameConstants.DAY_START
        );

        assertEquals(
                1080,
                GameConstants.DAY_END
        );
    }

    @Test
    void shouldProvideP0DefaultRates() {

        assertEquals(
                1.0,
                GameConstants.WEATHER_RATE_P0
        );

        assertEquals(
                1.0,
                GameConstants.DECORATION_RATE_P0
        );

        assertEquals(
                1.0,
                GameConstants.EVENT_RATE_P0
        );
    }
}