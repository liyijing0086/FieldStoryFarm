package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.FarmGameModel;
import com.fieldstory.farm.model.FarmScoreBreakdown;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.impl.TestGameClock;
import com.fieldstory.farm.service.FarmScoreService;
import com.fieldstory.farm.service.GraduationService;
import com.fieldstory.farm.service.impl.BasicGraduationService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P3 生产毕业触发器：146/147 与首次回调边界。 */
class GraduationControllerTest {

    @Test
    void score146NeverTriggersUiButCrossing147TriggersExactlyOnce() {
        GameState state = new GameState(new Player("农夫", 500), 0);
        TestGameClock clock = new TestGameClock(3 * 1440 + 9 * 60); // 第4日09:00
        FarmGameModel model = new FarmGameModel(clock);
        FixedScore score = new FixedScore(146);
        GraduationService service = new BasicGraduationService(state, score);
        GraduationController controller = new GraduationController(state, model, service);
        AtomicInteger uiEvents = new AtomicInteger();
        controller.setOnGraduated(graduation -> uiEvents.incrementAndGet());

        assertFalse(controller.evaluateNow());
        assertEquals(0, uiEvents.get());
        assertFalse(controller.isGraduated());

        score.value = 147;
        assertTrue(controller.evaluateNow());
        assertTrue(controller.isGraduated());
        assertEquals(1, uiEvents.get());
        assertEquals(clock.getGameDay(), state.getGraduation().getGraduationGameDay());
        assertEquals(model.getWorldTimeTotalMinutes(),
                state.getGraduation().getGraduationWorldTime());

        assertFalse(controller.evaluateNow(), "毕业动画事实只触发第一次");
        assertEquals(1, uiEvents.get());
    }

    private static final class FixedScore implements FarmScoreService {
        private int value;
        private FixedScore(int value) { this.value = value; }
        @Override
        public FarmScoreBreakdown breakdown() {
            return new FarmScoreBreakdown(value, 0, 0, 0);
        }
    }
}
