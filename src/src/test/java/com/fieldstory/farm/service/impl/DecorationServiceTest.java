package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Decoration;
import com.fieldstory.farm.model.DecorationType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.economy.DecorationPlacementResult;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.service.DecorationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecorationServiceTest {

    @Test
    void purchaseStateSurvivesServiceReconstruction() {
        GameState state = new GameState(new Player("T", 500), 1);
        DecorationService first = new BasicDecorationService(new BasicFarm(), state);
        first.addPurchasedDecoration(DecorationType.SUNFLOWER, 1);

        DecorationService restored = new BasicDecorationService(new BasicFarm(), state);
        assertEquals(1, restored.getOwnedCount(DecorationType.SUNFLOWER));
        assertFalse(restored.getOwnedDecorations().get(0).isPlaced());
    }

    @Test
    void canOnlyPlaceOnDecorationArea() {
        GameState state = new GameState(new Player("T", 500), 1);
        DecorationService service = new BasicDecorationService(new BasicFarm(), state);
        Decoration decoration = service.addPurchasedDecoration(DecorationType.SUNFLOWER, 1).get(0);

        assertEquals(DecorationPlacementResult.NOT_DECORATION_AREA,
                service.place(decoration, 2, 2));
        assertEquals(DecorationPlacementResult.SUCCESS,
                service.place(decoration, 1, 1));
        assertTrue(decoration.isPlaced());
    }

    @Test
    void failedMoveKeepsOriginalPosition() {
        GameState state = new GameState(new Player("T", 500), 1);
        DecorationService service = new BasicDecorationService(new BasicFarm(), state);
        Decoration decoration = service.addPurchasedDecoration(DecorationType.SUNFLOWER, 1).get(0);
        assertEquals(DecorationPlacementResult.SUCCESS, service.place(decoration, 1, 1));

        assertEquals(DecorationPlacementResult.NOT_DECORATION_AREA,
                service.move(decoration, 2, 2));
        assertEquals(1, decoration.getRow());
        assertEquals(1, decoration.getColumn());
    }

    @Test
    void p2FootprintDefinitionIsFrozen() {
        DecorationType[] small = {
                DecorationType.SUNFLOWER,
                DecorationType.ROSE_BED,
                DecorationType.WOODEN_FENCE,
                DecorationType.STREET_LAMP,
                DecorationType.BIG_TREE,
                DecorationType.STONE_LANTERN,
                DecorationType.SMALL_FOUNTAIN
        };

        for (DecorationType type : small) {
            assertEquals(
                    1,
                    type.getWidth(),
                    type + " width"
            );

            assertEquals(
                    1,
                    type.getHeight(),
                    type + " height"
            );
        }

        DecorationType[] large = {
                DecorationType.WHEAT_WATCHER,
                DecorationType.CORN_HARVEST,
                DecorationType.CARROT_FIELD,
                DecorationType.GOLDEN_FOUNTAIN,
                DecorationType.RAINBOW_FOUNTAIN,
                DecorationType.GOLDEN_THRONE,
                DecorationType.HARVEST_GODDESS
        };

        for (DecorationType type : large) {
            assertEquals(
                    2,
                    type.getWidth(),
                    type + " width"
            );

            assertEquals(
                    2,
                    type.getHeight(),
                    type + " height"
            );
        }
    }

    @Test
    void largeDecorationRequiresWholeTwoByTwoDecorationArea() {
        GameState state =
                new GameState(
                        new Player("T", 500),
                        1
                );

        DecorationService service =
                new BasicDecorationService(
                        new BasicFarm(),
                        state
                );

        Decoration large =
                service.addPurchasedDecoration(
                        DecorationType.WHEAT_WATCHER,
                        1
                ).get(0);

        /*
         * BasicFarm 中心农田：
         *
         * row    2~9
         * column 2~9
         *
         * 如果 2x2 装饰锚定在 (1,1)，
         * footprint =
         * (1,1) (1,2)
         * (2,1) (2,2)
         *
         * 其中 (2,2) 已进入 FARM_PLOT。
         */
        assertEquals(
                DecorationPlacementResult.NOT_DECORATION_AREA,
                service.place(
                        large,
                        1,
                        1
                )
        );

        assertFalse(
                large.isPlaced()
        );

        /*
         * (0,0) footprint:
         *
         * (0,0) (0,1)
         * (1,0) (1,1)
         *
         * 四格均为外围 DECORATION_AREA。
         */
        assertEquals(
                DecorationPlacementResult.SUCCESS,
                service.place(
                        large,
                        0,
                        0
                )
        );

        assertEquals(
                0,
                large.getRow()
        );

        assertEquals(
                0,
                large.getColumn()
        );
    }

    @Test
    void largeDecorationRejectsOutOfBoundsFootprint() {
        GameState state =
                new GameState(
                        new Player("T", 500),
                        1
                );

        DecorationService service =
                new BasicDecorationService(
                        new BasicFarm(),
                        state
                );

        Decoration large =
                service.addPurchasedDecoration(
                        DecorationType.GOLDEN_FOUNTAIN,
                        1
                ).get(0);

        /*
         * 地图下标 0~11。
         *
         * 2x2 如果锚定 (11,11)，
         * 必然访问 row=12 / column=12。
         */
        assertEquals(
                DecorationPlacementResult.OUT_OF_BOUNDS,
                service.place(
                        large,
                        11,
                        11
                )
        );

        assertFalse(
                large.isPlaced()
        );
    }

    @Test
    void largeDecorationOccupiesAllFourFootprintCells() {
        GameState state =
                new GameState(
                        new Player("T", 500),
                        1
                );

        DecorationService service =
                new BasicDecorationService(
                        new BasicFarm(),
                        state
                );

        Decoration large =
                service.addPurchasedDecoration(
                        DecorationType.HARVEST_GODDESS,
                        1
                ).get(0);

        Decoration small =
                service.addPurchasedDecoration(
                        DecorationType.SUNFLOWER,
                        1
                ).get(0);

        assertEquals(
                DecorationPlacementResult.SUCCESS,
                service.place(
                        large,
                        0,
                        0
                )
        );

        /*
         * 女神像位于 (0,0)，2x2 footprint：
         *
         * (0,0) (0,1)
         * (1,0) (1,1)
         *
         * 因此向日葵尝试放 (0,1) 必须失败。
         */
        assertEquals(
                DecorationPlacementResult.CELL_OCCUPIED,
                service.place(
                        small,
                        0,
                        1
                )
        );

        assertFalse(
                small.isPlaced()
        );
    }

    @Test
    void failedLargeDecorationMoveKeepsOriginalPosition() {
        GameState state =
                new GameState(
                        new Player("T", 500),
                        1
                );

        DecorationService service =
                new BasicDecorationService(
                        new BasicFarm(),
                        state
                );

        Decoration large =
                service.addPurchasedDecoration(
                        DecorationType.CARROT_FIELD,
                        1
                ).get(0);

        assertEquals(
                DecorationPlacementResult.SUCCESS,
                service.place(
                        large,
                        0,
                        0
                )
        );

        /*
         * 尝试移动到 (1,1)：
         * 2x2 footprint 会压入中心 FARM_PLOT。
         */
        assertEquals(
                DecorationPlacementResult.NOT_DECORATION_AREA,
                service.move(
                        large,
                        1,
                        1
                )
        );

        /*
         * move 失败必须保留原坐标。
         */
        assertEquals(
                0,
                large.getRow()
        );

        assertEquals(
                0,
                large.getColumn()
        );
    }

    @Test
    void restoredLargeDecorationUsesP2Footprint() {
        GameState state =
                new GameState(
                        new Player("T", 500),
                        1
                );

        DecorationService first =
                new BasicDecorationService(
                        new BasicFarm(),
                        state
                );

        Decoration large =
                first.addPurchasedDecoration(
                        DecorationType.GOLDEN_THRONE,
                        1
                ).get(0);

        assertEquals(
                DecorationPlacementResult.SUCCESS,
                first.place(
                        large,
                        0,
                        0
                )
        );

        /*
         * 模拟重新读档。
         */
        DecorationService restored =
                new BasicDecorationService(
                        new BasicFarm(),
                        state
                );

        Decoration restoredLarge =
                restored.getOwnedDecorations()
                        .get(0);

        assertEquals(
                DecorationType.GOLDEN_THRONE,
                restoredLarge.getDecorationType()
        );

        assertEquals(
                2,
                restoredLarge
                        .getDecorationType()
                        .getWidth()
        );

        assertEquals(
                2,
                restoredLarge
                        .getDecorationType()
                        .getHeight()
        );

        /*
         * 2x2 footprint 内的第二个格子也必须视为占用。
         */
        assertTrue(
                restored.isOccupied(
                        1,
                        1,
                        null
                )
        );
    }
}
