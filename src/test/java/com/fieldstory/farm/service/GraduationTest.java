package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.FarmScoreBreakdown;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.GraduationState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.service.impl.BasicCollectionService;
import com.fieldstory.farm.service.impl.BasicFarmScoreService;
import com.fieldstory.farm.service.impl.BasicGraduationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 毕业测试（E 模块 P3；验收规范 §一百二十二/§一百二十九/§一百三十二）。
 *
 * <p>核心红线：毕业唯一条件是 {@code FarmScore == 147}；146 分绝对不能毕业（§一百三十二 ⑨），
 * 「完成 3 套装 / 获得 3 传说 / Rank 达到某级」都不能毕业（§一百二十二、P3 禁止项 §一百五十二）。
 * 首次毕业只触发一次，之后保持永恒花园状态（§一百二十九）。
 */
class GraduationTest {

    /** 可控分数的桩，用于精确落在 146 / 147 边界。 */
    private static final class FixedScoreService implements FarmScoreService {
        private int score;

        FixedScoreService(int score) {
            this.score = score;
        }

        void setScore(int score) {
            this.score = score;
        }

        @Override
        public FarmScoreBreakdown breakdown() {
            return new FarmScoreBreakdown(score, 0, 0, 0);
        }
    }

    private static GameState stateWithDay(long day, long worldMinutes) {
        GameState state = new GameState(new Player("农夫", 500), day);
        state.setWorldTotalMinutes(worldMinutes);
        return state;
    }

    @Test
    void score146NeverGraduates() {
        GameState state = stateWithDay(12, 600);
        GraduationService service =
                new BasicGraduationService(state, new FixedScoreService(146));
        assertFalse(service.evaluateAndGraduate(), "146 分不能毕业（§一百三十二 ⑨）");
        assertFalse(service.isGraduated());
    }

    @Test
    void score147GraduatesFirstTimeOnly() {
        GameState state = stateWithDay(20, 480);
        FixedScoreService score = new FixedScoreService(147);
        GraduationService service = new BasicGraduationService(state, score);

        assertTrue(service.evaluateAndGraduate(), "首次达到 147 应触发毕业（§一百二十九）");
        assertTrue(service.isGraduated());
        GraduationState recorded = service.getState();
        assertEquals(480L, recorded.getGraduationWorldTime(), "记录毕业时刻世界时间");
        assertEquals(20L, recorded.getGraduationGameDay(), "记录毕业所在游戏日");

        // 再次评估：已毕业，不再重复触发首次动画（§一百二十九）
        assertFalse(service.evaluateAndGraduate(), "首次毕业只触发一次");
    }

    @Test
    void crossingFrom146To147Graduates() {
        GameState state = stateWithDay(1, 24);
        FixedScoreService score = new FixedScoreService(146);
        GraduationService service = new BasicGraduationService(state, score);

        assertFalse(service.evaluateAndGraduate());
        score.setScore(147);
        assertTrue(service.evaluateAndGraduate(), "补齐最后一项后应毕业");
    }

    @Test
    void threeSetsAloneDoNotGraduate() {
        // 只有 3 套装（45 分），远不到 147 → 不能毕业（§一百二十二 禁止项）
        GameState state = stateWithDay(5, 120);
        state.getSetCollection().getCollected().add("NATURAL_BREATH");
        state.getSetCollection().getCollected().add("HARVEST_SOUL");
        state.getSetCollection().getCollected().add("LEGEND_LIGHT");
        GraduationService service = new BasicGraduationService(
                state, new BasicFarmScoreService(state));
        assertFalse(service.evaluateAndGraduate(), "完成 3 套装不等于毕业");
    }

    @Test
    void fullCollectionGraduatesThroughRealScoreService() {
        GameState state = stateWithDay(30, 720);
        CollectionService collection = new BasicCollectionService(state);
        for (int i = 1; i <= 14; i++) {
            collection.collectDecoration(String.format("D%02d", i));
        }
        for (CropType cropType : CropType.values()) {
            for (Quality quality : Quality.values()) {
                collection.collectCrop(cropType, quality);
            }
            collection.collectLegendary(cropType);
        }
        for (String setId : new String[]{"NATURAL_BREATH", "HARVEST_SOUL", "LEGEND_LIGHT"}) {
            state.getSetCollection().getCollected().add(setId);
        }

        FarmScoreService scoreService = new BasicFarmScoreService(state);
        assertEquals(147, scoreService.totalScore());
        GraduationService graduation = new BasicGraduationService(state, scoreService);
        assertTrue(graduation.evaluateAndGraduate(), "满收集 147 → 毕业");
    }
}
