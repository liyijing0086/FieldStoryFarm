package com.fieldstory.farm.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 RandomProvider 测试（D 模块 P0 文档 §10.2）。
 *
 * <p>覆盖：固定种子可复现、nextInt 范围。
 */
class RandomProviderTest {

    @Test
    void fixedSeedProducesReproducibleSequence() {
        RandomProvider.setSeed(12345L);
        int first = RandomProvider.nextInt(100);
        int second = RandomProvider.nextInt(100);

        RandomProvider.setSeed(12345L);
        assertEquals(first, RandomProvider.nextInt(100));
        assertEquals(second, RandomProvider.nextInt(100));
    }

    @Test
    void nextIntStaysWithinBound() {
        RandomProvider.setSeed(42L);
        for (int i = 0; i < 1000; i++) {
            int value = RandomProvider.nextInt(10);
            assertTrue(value >= 0 && value < 10, "nextInt 应在 [0, bound) 内");
        }
    }

    @Test
    void nextDoubleStaysWithinUnitInterval() {
        RandomProvider.setSeed(7L);
        for (int i = 0; i < 1000; i++) {
            double value = RandomProvider.nextDouble();
            assertTrue(value >= 0.0 && value < 1.0, "nextDouble 应在 [0.0, 1.0) 内");
        }
    }
}
