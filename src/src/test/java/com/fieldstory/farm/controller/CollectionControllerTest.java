package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Decoration;
import com.fieldstory.farm.model.DecorationSet;
import com.fieldstory.farm.model.DecorationType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.model.economy.DecorationPlacementResult;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.service.CollectionService;
import com.fieldstory.farm.service.DecorationService;
import com.fieldstory.farm.service.FarmRankService;
import com.fieldstory.farm.service.FarmScoreService;
import com.fieldstory.farm.service.SetService;
import com.fieldstory.farm.service.impl.BasicCollectionService;
import com.fieldstory.farm.service.impl.BasicDecorationService;
import com.fieldstory.farm.service.impl.BasicFarmRankService;
import com.fieldstory.farm.service.impl.BasicFarmScoreService;
import com.fieldstory.farm.service.impl.BasicSetService;
import com.fieldstory.farm.util.GameConstants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CollectionController} 单元测试（验收规范 §一百二十七/§一百二十八）。
 *
 * <p>覆盖六项必备展示数据（作物 x/15、装饰 x/14、传说 x/3、套装 x/3、FarmScore x/147、当前评价）
 * 与目标提示，全部只读、不写状态。
 */
class CollectionControllerTest {

    @Test
    void freshFarmShowsAllZeroAndNoviceRank() {
        GameState state = newState();
        CollectionService collection = new BasicCollectionService(state);
        CollectionController controller = controller(state, collection);

        assertEquals(0, controller.cropCollected());
        assertEquals(15, controller.cropTarget());
        assertEquals(0, controller.decorationCollected());
        assertEquals(14, controller.decorationTarget());
        assertEquals(0, controller.legendaryCollected());
        assertEquals(3, controller.legendaryTarget());
        assertEquals(0, controller.setCollected());
        assertEquals(3, controller.setTarget());
        assertEquals(0, controller.farmScore());
        assertEquals(147, controller.maxFarmScore());
        assertEquals("新手农场", controller.currentRankName());
    }

    @Test
    void progressTracksCollectedContent() {
        GameState state = newState();
        CollectionService collection = new BasicCollectionService(state);
        CollectionController controller = controller(state, collection);

        collection.collectCrop(CropType.WHEAT, Quality.COMMON);
        collection.collectCrop(CropType.CORN, Quality.RARE);
        collection.collectDecoration("D01");
        collection.collectLegendary(CropType.WHEAT);

        assertEquals(2, controller.cropCollected());
        assertEquals(1, controller.decorationCollected());
        assertEquals(1, controller.legendaryCollected());
        // 作物图鉴 2×2=4、装饰 1×3=3、传说 1×10=10 → 17
        assertEquals(17, controller.farmScore());
    }

    @Test
    void rankRisesAsFarmScoreGrows() {
        GameState state = newState();
        CollectionService collection = new BasicCollectionService(state);
        CollectionController controller = controller(state, collection);

        for (DecorationType type : DecorationType.values()) {
            collection.collectDecoration(type.getId());
        }

        assertEquals(14, controller.decorationCollected());
        assertEquals(42, controller.farmScore()); // 14 × 3
        assertEquals("花园农场", controller.currentRankName()); // ≥30
    }

    @Test
    void completingSetAddsScoreAndThreeGoalHintsAlwaysPresent() {
        GameState state = newState();
        CollectionService collection = new BasicCollectionService(state);
        DecorationService decorations = new BasicDecorationService(new BasicFarm(), state);
        SetService sets = new BasicSetService(decorations, state);
        FarmScoreService score = new BasicFarmScoreService(state);
        FarmRankService rank = new BasicFarmRankService();
        CollectionController controller = new CollectionController(collection, score, rank, sets);

        grantAndPlaceAll(decorations, DecorationSet.NATURE_BREATH);
        sets.refresh();

        assertEquals(1, controller.setCollected());
        assertEquals(FarmScoreService.SET_POINTS, controller.farmScore());

        // 目标提示：FarmScore/作物/装饰/套装 4 条 + 3 种传说 = 7 条。
        List<String> hints = controller.goalHints();
        assertEquals(7, hints.size());
    }

    @Test
    void legendaryGoalHintReflectsCollectedState() {
        GameState state = newState();
        CollectionService collection = new BasicCollectionService(state);
        CollectionController controller = controller(state, collection);

        assertTrue(hintFor(controller, "金色麦穗").startsWith("[未完成]"),
                "未获得时应提示未完成");
        assertFalse(controller.isLegendaryCollected(CropType.WHEAT));

        collection.collectLegendary(CropType.WHEAT);

        assertTrue(controller.isLegendaryCollected(CropType.WHEAT));
        assertTrue(hintFor(controller, "金色麦穗").startsWith("[已收集]"),
                "获得后图鉴项应标记为已收集");
    }

    // ------------------------------------------------------------------
    // 脚手架
    // ------------------------------------------------------------------

    private static GameState newState() {
        return new GameState(new Player("测试农夫", 500), 1);
    }

    private static CollectionController controller(GameState state, CollectionService collection) {
        FarmScoreService score = new BasicFarmScoreService(state);
        FarmRankService rank = new BasicFarmRankService();
        SetService sets = new BasicSetService(new BasicDecorationService(new BasicFarm(), state), state);
        return new CollectionController(collection, score, rank, sets);
    }

    private static String hintFor(CollectionController controller, String legendaryName) {
        return controller.goalHints().stream()
                .filter(text -> text.contains(legendaryName))
                .findFirst()
                .orElseThrow();
    }

    private static void grantAndPlaceAll(DecorationService decorations, DecorationSet set) {
        for (DecorationType member : set.getMembers()) {
            decorations.addPurchasedDecoration(member, 1);
            Decoration decoration = decorations.getOwnedDecorations().stream()
                    .filter(d -> d.getDecorationType() == member)
                    .findFirst()
                    .orElseThrow();
            placeSomewhere(decorations, decoration);
        }
    }

    /** 在装饰区里为该装饰找个空位放下；找不到直接失败。 */
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
