package com.fieldstory.farm.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B 模块 P2 离线模拟结果模型测试。
 */
class OfflineSimulationResultTest {

    @Test
    void resultReportsProgressAndCapState() {
        OfflineSimulationResult normal =
                new OfflineSimulationResult(
                        30L,
                        30L,
                        30L,
                        List.of()
                );

        assertTrue(normal.hasOfflineProgress());
        assertFalse(normal.wasCapped());

        OfflineSimulationResult capped =
                new OfflineSimulationResult(
                        480L,
                        72L,
                        72L,
                        List.of()
                );

        assertTrue(capped.hasOfflineProgress());
        assertTrue(capped.wasCapped());
    }

    @Test
    void zeroResultHasNoOfflineProgress() {
        OfflineSimulationResult result =
                new OfflineSimulationResult(
                        0L,
                        0L,
                        0L,
                        List.of()
                );

        assertFalse(result.hasOfflineProgress());
        assertFalse(result.wasCapped());
    }

    @Test
    void dailySummariesAreDefensivelyCopied() {
        List<OfflineDaySummary> source = new ArrayList<>();

        source.add(
                new OfflineDaySummary(
                        12L,
                        List.of(
                                new OfflineOccurrence.Weather(
                                        WeatherType.RAIN
                                )
                        )
                )
        );

        OfflineSimulationResult result =
                new OfflineSimulationResult(
                        30L,
                        30L,
                        30L,
                        source
                );

        source.clear();

        assertEquals(1, result.dailySummaries().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.dailySummaries().clear()
        );
    }

    @Test
    void invalidTimeRelationshipIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OfflineSimulationResult(
                        30L,
                        31L,
                        31L,
                        List.of()
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new OfflineSimulationResult(
                        30L,
                        30L,
                        29L,
                        List.of()
                )
        );
    }
}
