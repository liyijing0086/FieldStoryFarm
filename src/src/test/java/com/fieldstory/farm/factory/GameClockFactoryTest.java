package com.fieldstory.farm.factory;

import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.model.impl.DemoGameClock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameClockFactoryTest {

    @Test
    void formalIsTheSafeDefault() {
        assertInstanceOf(BasicGameClock.class, GameClockFactory.create(null));
        assertInstanceOf(BasicGameClock.class, GameClockFactory.create(""));
        assertInstanceOf(BasicGameClock.class, GameClockFactory.create("formal"));
        assertInstanceOf(BasicGameClock.class, GameClockFactory.create("unknown"));
    }

    @Test
    void demoMustBeExplicit() {
        GameClock demo = GameClockFactory.create("demo");
        assertInstanceOf(DemoGameClock.class, demo);
        assertInstanceOf(DemoGameClock.class, GameClockFactory.create("x12"));
        assertInstanceOf(DemoGameClock.class, GameClockFactory.create("12"));
    }

    @Test
    void demoModeParserIsCaseInsensitiveAndTrimmed() {
        assertTrue(GameClockFactory.isDemoMode(" DEMO "));
        assertTrue(GameClockFactory.isDemoMode("X12"));
        assertFalse(GameClockFactory.isDemoMode("formal"));
        assertFalse(GameClockFactory.isDemoMode(null));
    }
}
