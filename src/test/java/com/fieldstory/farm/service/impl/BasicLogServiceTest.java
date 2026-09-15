package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.OfflineDaySummary;
import com.fieldstory.farm.model.OfflineLog;
import com.fieldstory.farm.model.OfflineOccurrence;
import com.fieldstory.farm.model.OfflineSimulationResult;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.WeatherType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicLogServiceTest {

    private final BasicLogService service = new BasicLogService();

    @Test
    void zeroOfflineDurationProducesNoPopupLog() {
        Optional<OfflineLog> log = service.buildOfflineLog(
                new OfflineSimulationResult(0L, 0L, 0L, List.of()));
        assertTrue(log.isEmpty());
    }

    @Test
    void structuredFactsBecomePlayerReadableDailyNarrative() {
        OfflineSimulationResult result = new OfflineSimulationResult(
                80L,
                72L,
                72L,
                List.of(
                        new OfflineDaySummary(12L, List.of(
                                new OfflineOccurrence.Weather(WeatherType.RAIN),
                                new OfflineOccurrence.AutoWater(CropType.CORN, 3),
                                new OfflineOccurrence.Growth(CropType.CARROT, GrowthStage.GROWING, 1))),
                        new OfflineDaySummary(13L, List.of(
                                new OfflineOccurrence.Event(EventType.MYSTERY_MERCHANT),
                                new OfflineOccurrence.LegendaryOpportunity(CropType.CARROT, 2),
                                new OfflineOccurrence.Reward("收到了一份神秘礼物"))),
                        new OfflineDaySummary(14L, List.of(
                                new OfflineOccurrence.Weather(WeatherType.SUNNY),
                                new OfflineOccurrence.Mature(CropType.WHEAT, 4),
                                new OfflineOccurrence.Withered(CropType.CORN, 1)))));

        OfflineLog log = service.buildOfflineLog(result).orElseThrow();

        assertEquals(72L, log.effectiveOfflineMinutes());
        assertEquals(3, log.days().size());
        assertEquals(12L, log.days().get(0).gameDay());
        assertEquals("🌧 下了一场雨", log.days().get(0).lines().get(0));
        assertTrue(log.days().get(0).lines().contains("3株玉米获得了自动补水"));
        assertTrue(log.days().get(0).lines().contains("1株胡萝卜进入成长阶段"));
        assertTrue(log.days().get(1).lines().contains("🧙 神秘商人来到农场"));
        assertTrue(log.days().get(1).lines().contains("2株胡萝卜获得传奇潜力"));
        assertTrue(log.days().get(1).lines().contains("🎁 收到了一份神秘礼物"));
        assertTrue(log.days().get(2).lines().contains("4株小麦成熟"));
        assertTrue(log.days().get(2).lines().contains("1株玉米枯萎"));

        assertFalse(log.days().stream()
                .flatMap(day -> day.lines().stream())
                .anyMatch(line -> line.contains("MYSTERY_MERCHANT") || line.contains("RAIN")));
    }
}
