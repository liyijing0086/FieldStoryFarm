package com.fieldstory.farm;

import com.fieldstory.farm.factory.GameClockFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LauncherTest {

    @AfterEach
    void clearClockMode() {
        System.clearProperty(GameClockFactory.CLOCK_MODE_PROPERTY);
    }

    @Test
    void demoArgumentEnablesDemoClockMode() {
        Launcher.applyClockModeArgument(new String[]{"--demo"});
        assertEquals("demo", System.getProperty(GameClockFactory.CLOCK_MODE_PROPERTY));
    }

    @Test
    void formalArgumentEnablesFormalClockMode() {
        Launcher.applyClockModeArgument(new String[]{"--formal"});
        assertEquals("formal", System.getProperty(GameClockFactory.CLOCK_MODE_PROPERTY));
    }

    @Test
    void unrelatedArgumentsDoNotChangeMode() {
        Launcher.applyClockModeArgument(new String[]{"--other"});
        assertNull(System.getProperty(GameClockFactory.CLOCK_MODE_PROPERTY));
    }
}
