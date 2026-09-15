package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Decoration;
import com.fieldstory.farm.model.DecorationSet;
import com.fieldstory.farm.model.DecorationType;
import com.fieldstory.farm.model.FarmScoreBreakdown;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.economy.DecorationPlacementResult;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.service.impl.BasicDecorationService;
import com.fieldstory.farm.service.impl.BasicFarmScoreService;
import com.fieldstory.farm.service.impl.BasicSetService;
import com.fieldstory.farm.util.GameConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SetService} 单元测试（验收规范 §一百一十五~§一百二十、§一百三十二 ③④）。
 *
 * <p>覆盖：拥有+放置才完成、collected 与 active 相互独立、拆成员后 Buff 停止、
 * 反复拆装不重复计分，以及三套装合计 45 分的 FarmScore 贡献。
 */
class SetServiceTest {

    @Test
    void owningWithoutPlacingDoesNotCompleteSet() {
        GameState state = newState();
        DecorationService decorations = decorationsFor(state);
        SetService sets = new BasicSetService(decorations, state);

        grantAll(decorations, DecorationSet.NATURE_BREATH);
        sets.refresh();

        assertFalse(sets.isCollected(DecorationSet.NATURE_BREATH),
                "仅拥有、未全部放置不能算完成（验收 §一百一十五）");
        assertFalse(sets.isActive(DecorationSet.NATURE_BREATH));
        assertEquals(0, sets.collectedCount());
        assertTrue(sets.getActiveBuffDescriptions().isEmpty());
    }

    @Test
    void allMembersOwnedAndPlacedCollectsAndActivatesSet() {
        GameState state = newState();
        DecorationService decorations = decorationsFor(state);
        SetService sets = new BasicSetService(decorations, state);

        grantAndPlaceAll(decorations, DecorationSet.NATURE_BREATH);
        sets.refresh();

        assertTrue(sets.isCollected(DecorationSet.NATURE_BREATH));
        assertTrue(sets.isActive(DecorationSet.NATURE_BREATH));
        assertEquals(1, sets.collectedCount());
        assertEquals(1, sets.activeCount());
        assertTrue(sets.getActiveBuffDescriptions().stream()
                        .anyMatch(text -> text.startsWith(DecorationSet.NATURE_BREATH.getDisplayName())),
                "激活后应出现该套装 Buff");
    }

    @Test
    void removingMemberKeepsCollectedButStopsActiveAndBuff() {
        GameState state = newState();
        DecorationService decorations = decorationsFor(state);
        SetService sets = new BasicSetService(decorations, state);
        grantAndPlaceAll(decorations, DecorationSet.NATURE_BREATH);
        sets.refresh();

        // 玩家收起套装中的一个装饰：collected 保留，active 停止（验收 §一百三十二 ③④）。
        Decoration member = owned(decorations, DecorationType.SUNFLOWER);
        assertEquals(DecorationPlacementResult.SUCCESS, decorations.removeFromFarm(member));
        sets.refresh();

        assertTrue(sets.isCollected(DecorationSet.NATURE_BREATH),
                "曾完整完成 → collected 永久保留（验收 §一百一十八）");
        assertFalse(sets.isActive(DecorationSet.NATURE_BREATH),
                "拆走成员后 active 停止");
        assertEquals(1, sets.collectedCount(), "collected 不回退");
        assertTrue(sets.getActiveBuffDescriptions().isEmpty(),
                "拆走成员后套装 Buff 立即停止（验收 §一百三十二 ④）");
    }

    @Test
    void repeatedPlaceAndRemoveNeverDoubleCountsSetScore() {
        GameState state = newState();
        DecorationService decorations = decorationsFor(state);
        SetService sets = new BasicSetService(decorations, state);
        grantAndPlaceAll(decorations, DecorationSet.NATURE_BREATH);
        sets.refresh();

        Decoration member = owned(decorations, DecorationType.SUNFLOWER);
        for (int i = 0; i < 3; i++) {
            assertEquals(DecorationPlacementResult.SUCCESS, decorations.removeFromFarm(member));
            sets.refresh();
            placeSomewhere(decorations, member);
            sets.refresh();
        }

        assertEquals(1, sets.collectedCount(), "反复拆装 collected 恒为 1（验收 §一百二十）");
        assertEquals(FarmScoreService.SET_POINTS,
                new BasicFarmScoreService(state).breakdown().setScore(),
                "反复拆装套装分仍只有 15");
    }

    @Test
    void threeCompleteSetsGiveFullFortyFiveSetPoints() {
        GameState state = newState();
        DecorationService decorations = decorationsFor(state);
        SetService sets = new BasicSetService(decorations, state);

        for (DecorationSet set : DecorationSet.values()) {
            grantAndPlaceAll(decorations, set);
        }
        sets.refresh();

        assertEquals(3, sets.collectedCount());
        assertEquals(3, sets.activeCount());
        assertEquals(3, sets.getActiveBuffDescriptions().size());

        FarmScoreBreakdown breakdown = new BasicFarmScoreService(state).breakdown();
        assertEquals(45, breakdown.setScore(),
                "三套装 3 × 15 = 45（验收 §一百一十九）");
    }

    @Test
    void refreshAfterServiceRebuildMatchesPersistedPlacement() {
        GameState state = newState();
        DecorationService first = decorationsFor(state);
        SetService firstSets = new BasicSetService(first, state);
        grantAndPlaceAll(first, DecorationSet.NATURE_BREATH);
        firstSets.refresh();
        assertTrue(firstSets.isActive(DecorationSet.NATURE_BREATH));

        // 模拟退出重进：同一个 GameState（装饰快照在其中）重建服务后重算。
        DecorationService restored = decorationsFor(state);
        SetService restoredSets = new BasicSetService(restored, state);
        restoredSets.refresh();

        assertTrue(restoredSets.isCollected(DecorationSet.NATURE_BREATH));
        assertTrue(restoredSets.isActive(DecorationSet.NATURE_BREATH),
                "放置状态随存档恢复时 active 应保持");
    }

    // ------------------------------------------------------------------
    // 测试脚手架
    // ------------------------------------------------------------------

    private static GameState newState() {
        return new GameState(new Player("测试农夫", 500), 1);
    }

    private static DecorationService decorationsFor(GameState state) {
        return new BasicDecorationService(new BasicFarm(), state);
    }

    private static void grantAll(DecorationService decorations, DecorationSet set) {
        for (DecorationType member : set.getMembers()) {
            decorations.addPurchasedDecoration(member, 1);
        }
    }

    private static void grantAndPlaceAll(DecorationService decorations, DecorationSet set) {
        grantAll(decorations, set);
        for (DecorationType member : set.getMembers()) {
            placeSomewhere(decorations, owned(decorations, member));
        }
    }

    private static Decoration owned(DecorationService decorations, DecorationType type) {
        return decorations.getOwnedDecorations().stream()
                .filter(decoration -> decoration.getDecorationType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未拥有装饰：" + type));
    }

    /** 在装饰区里为该装饰找个空位放下；找不到直接失败，避免用例悄悄变成“未放置”。 */
    private static void placeSomewhere(DecorationService decorations, Decoration decoration) {
        for (int row = 0; row < GameConstants.MAP_ROWS; row++) {
            for (int column = 0; column < GameConstants.MAP_COLS; column++) {
                if (decorations.canPlace(decoration, row, column)) {
                    assertEquals(DecorationPlacementResult.SUCCESS,
                            decorations.place(decoration, row, column));
                    return;
                }
            }
        }
        throw new AssertionError("装饰区已无空位可放置：" + decoration.getDecorationType());
    }
}
