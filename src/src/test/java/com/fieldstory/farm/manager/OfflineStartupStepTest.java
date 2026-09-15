package com.fieldstory.farm.manager;

import com.fieldstory.farm.model.OfflineSimulationResult;
import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.service.OfflineSimulationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 第二轮：E 启动接线只从统一 GameClock 取得原始离线分钟，再交给 B。 */
class OfflineStartupStepTest {

    @Test
    void savedRealTimeFlowsIntoOfflineSimulationAsRawMinutes() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 14, 12, 0);
        FixedNowClock clock = new FixedNowClock(now);
        clock.setLastRealTime(now.minusMinutes(30));
        RecordingSimulation simulation = new RecordingSimulation();

        OfflineSimulationResult result = OfflineStartupStep.run(clock, simulation);

        assertNotNull(result);
        assertEquals(30L, simulation.receivedRawMinutes);
        assertEquals(30L, result.rawOfflineMinutes());
    }

    private static final class FixedNowClock extends BasicGameClock {
        private final LocalDateTime now;

        private FixedNowClock(LocalDateTime now) {
            this.now = now;
        }

        @Override
        public LocalDateTime getRealTime() {
            return now;
        }
    }

    private static final class RecordingSimulation implements OfflineSimulationService {
        private long receivedRawMinutes = -1L;

        @Override
        public OfflineSimulationResult simulate(long rawOfflineMinutes) {
            receivedRawMinutes = rawOfflineMinutes;
            return new OfflineSimulationResult(
                    rawOfflineMinutes,
                    rawOfflineMinutes,
                    rawOfflineMinutes,
                    List.of());
        }
    }
}
