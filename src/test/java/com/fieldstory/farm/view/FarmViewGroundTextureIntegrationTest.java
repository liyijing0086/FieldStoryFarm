package com.fieldstory.farm.view;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicFarm;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4 地面贴图生产接入测试。
 *
 * <p>P4 不再把 8×8 农田画成一个纯色大色块：每个 44×44 Tile 都由同一免费图集
 * 的草地/干土/湿土帧铺满。模型状态没有改变：EMPTY/LOCKED 仍是原状态，
 * MATURE/WITHERED 仍由底色表达状态，只是在上层叠半透明土壤纹理。
 */
class FarmViewGroundTextureIntegrationTest {

    @BeforeAll
    static void initToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS), "JavaFX 工具包初始化超时");
    }

    private static <T> T onFxThread(Supplier<T> supplier) throws InterruptedException {
        AtomicReference<T> ref = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                ref.set(supplier.get());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS), "JavaFX 任务执行超时");
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        return ref.get();
    }

    private static void plant(Farm farm, int row, int column, GrowthStage stage,
                              long lastManualWaterGameDay) {
        Soil soil = farm.getSoil(row, column);
        soil.setState(SoilState.PLANTED);
        Crop crop = new BasicCrop();
        crop.setCropType(CropType.WHEAT);
        crop.setGrowthStage(stage);
        crop.setGrowthProgress(50);
        crop.setLastManualWaterGameDay(lastManualWaterGameDay);
        soil.setCrop(crop);
    }

    /** P4 地面层位于对应 44×44 格内；耕地允许为表现层留 1px 内缩边缘。 */
    private static ImageView groundAt(FarmView view, int row, int column) {
        double left = column * FarmView.TILE_SIZE;
        double top = row * FarmView.TILE_SIZE;
        double right = left + FarmView.TILE_SIZE;
        double bottom = top + FarmView.TILE_SIZE;
        return view.getChildren().stream()
                .filter(ImageView.class::isInstance)
                .map(ImageView.class::cast)
                .filter(ImageView::isVisible)
                .filter(iv -> !"cropSprite".equals(iv.getUserData()))
                .filter(iv -> iv.getX() >= left && iv.getX() < right
                        && iv.getY() >= top && iv.getY() < bottom)
                .findFirst()
                .orElse(null);
    }

    private static List<ImageView> texturesIntersectingCell(FarmView view, int row, int column) {
        return view.getChildren().stream()
                .filter(ImageView.class::isInstance)
                .map(ImageView.class::cast)
                .filter(ImageView::isVisible)
                .filter(iv -> iv.getX() < (column + 1) * FarmView.TILE_SIZE
                        && iv.getX() + iv.getFitWidth() > column * FarmView.TILE_SIZE
                        && iv.getY() < (row + 1) * FarmView.TILE_SIZE
                        && iv.getY() + iv.getFitHeight() > row * FarmView.TILE_SIZE)
                .collect(Collectors.toList());
    }

    private static Rectangle tileAt(FarmView view, int row, int column) {
        return view.getChildren().stream()
                .filter(Rectangle.class::isInstance)
                .map(Rectangle.class::cast)
                .filter(r -> r.getX() == column * FarmView.TILE_SIZE
                        && r.getY() == row * FarmView.TILE_SIZE
                        && r.getWidth() == FarmView.TILE_SIZE)
                .findFirst()
                .orElse(null);
    }

    private static void assertViewport(ImageView view, double x, double y) {
        assertNotNull(view);
        Rectangle2D viewport = view.getViewport();
        assertNotNull(viewport);
        assertEquals(x, viewport.getMinX(), 0.0);
        assertEquals(y, viewport.getMinY(), 0.0);
        assertEquals(16, viewport.getWidth(), 0.0);
        assertEquals(16, viewport.getHeight(), 0.0);
    }

    @Test
    void decorationCellShowsFullTileGrassTexture() throws InterruptedException {
        onFxThread(() -> {
            FarmView view = assertDoesNotThrow(() -> new FarmView(new BasicFarm()));
            ImageView ground = groundAt(view, 0, 0);
            assertNotNull(ground);
            assertEquals(44, ground.getFitWidth(), 0.0);
            assertEquals(44, ground.getFitHeight(), 0.0);
            assertEquals(0, ground.getX(), 0.0);
            assertEquals(0, ground.getY(), 0.0);
            // P4 确定性草地帧：row0/col0 -> {2,7}。
            assertViewport(ground, 32, 112);
            return null;
        });
    }

    @Test
    void tilledDryCellShowsDeterministicFieldTexture() throws InterruptedException {
        onFxThread(() -> {
            Farm farm = new BasicFarm();
            farm.getSoil(2, 2).setState(SoilState.TILLED);
            FarmView view = new FarmView(farm);
            // P4 干净土壤内格统一使用 {5,1}，由每格轻微亮度差制造层次。
            ImageView dry = groundAt(view, 2, 2);
            assertViewport(dry, 80, 16);
            assertEquals(42, dry.getFitWidth(), 0.0);
            assertEquals(42, dry.getFitHeight(), 0.0);
            assertEquals(2 * FarmView.TILE_SIZE + 1, dry.getX(), 0.0);
            assertEquals(2 * FarmView.TILE_SIZE + 1, dry.getY(), 0.0);
            return null;
        });
    }

    @Test
    void setWetTodayTrueThenRefreshAllShowsWetFieldTexture() throws InterruptedException {
        onFxThread(() -> {
            Farm farm = new BasicFarm();
            farm.getSoil(2, 2).setState(SoilState.TILLED);
            FarmView view = new FarmView(farm);
            view.setWetToday(true);
            view.refreshAll();
            ImageView wet = groundAt(view, 2, 2);
            assertViewport(wet, 80, 16);
            assertEquals(42, wet.getFitWidth(), 0.0);
            assertEquals(42, wet.getFitHeight(), 0.0);
            assertNotNull(wet.getEffect(), "湿地应具有轻微冷/暗表现层效果");
            return null;
        });
    }

    @Test
    void emptyFarmCellUsesGrassTextureWithoutChangingModelState() throws InterruptedException {
        onFxThread(() -> {
            Farm farm = new BasicFarm();
            assertEquals(SoilState.EMPTY, farm.getSoil(2, 2).getState());
            FarmView view = new FarmView(farm);
            assertNotNull(groundAt(view, 2, 2), "P4 EMPTY 视觉上铺草地，不再显示纯棕块");
            assertEquals(SoilState.EMPTY, farm.getSoil(2, 2).getState(), "视觉层不得改状态机");
            return null;
        });
    }

    @Test
    void plantedWateredTodayShowsWetTexture() throws InterruptedException {
        onFxThread(() -> {
            Farm farm = new BasicFarm();
            plant(farm, 2, 2, GrowthStage.GROWING, 0L);
            FarmView view = new FarmView(farm);

            ImageView wet = groundAt(view, 2, 2);
            // P4 最终视觉方案：湿地与干地复用同一块干净土壤内格，
            // 湿润差异由 FarmView 的 ColorAdjust 冷暗效果表达。
            assertViewport(wet, 80, 16);
            assertEquals(42, wet.getFitWidth(), 0.0);
            assertEquals(42, wet.getFitHeight(), 0.0);
            assertNotNull(wet.getEffect(), "当天主动浇水后的 PLANTED 地块应显示湿润冷暗效果");
            return null;
        });
    }

    @Test
    void matureAndWitheredKeepStatusColorWithSubtleSoilTexture() throws InterruptedException {
        onFxThread(() -> {
            Farm matureFarm = new BasicFarm();
            plant(matureFarm, 2, 2, GrowthStage.MATURE, -1L);
            FarmView matureView = new FarmView(matureFarm);
            ImageView matureGround = groundAt(matureView, 2, 2);
            assertNotNull(matureGround);
            assertEquals(0.46, matureGround.getOpacity(), 0.0001);
            assertEquals(FarmView.COLOR_HIGHLIGHT, tileAt(matureView, 2, 2).getFill());

            Farm witheredFarm = new BasicFarm();
            plant(witheredFarm, 2, 2, GrowthStage.WITHERED, -1L);
            FarmView witheredView = new FarmView(witheredFarm);
            ImageView witheredGround = groundAt(witheredView, 2, 2);
            assertNotNull(witheredGround);
            assertEquals(0.46, witheredGround.getOpacity(), 0.0001);
            assertEquals(FarmView.COLOR_WITHERED, tileAt(witheredView, 2, 2).getFill());
            return null;
        });
    }

    @Test
    void farmPlotTilesHaveNoStrokeAndSelectionStartsHidden() throws InterruptedException {
        onFxThread(() -> {
            Farm farm = new BasicFarm();
            FarmView view = new FarmView(farm);

            for (int row = BasicFarm.FARM_AREA_ORIGIN;
                 row < BasicFarm.FARM_AREA_ORIGIN + BasicFarm.FARM_AREA_SIZE; row++) {
                for (int column = BasicFarm.FARM_AREA_ORIGIN;
                     column < BasicFarm.FARM_AREA_ORIGIN + BasicFarm.FARM_AREA_SIZE; column++) {
                    Rectangle tile = tileAt(view, row, column);
                    assertNotNull(tile);
                    assertNull(tile.getStroke(), "普通地块无测试期深色描边");
                }
            }

            List<Rectangle> stroked = view.getChildren().stream()
                    .filter(Rectangle.class::isInstance)
                    .map(Rectangle.class::cast)
                    .filter(r -> r.getStroke() != null)
                    .collect(Collectors.toList());
            assertEquals(1, stroked.size(), "只有选中高亮矩形拥有描边");
            Rectangle selection = stroked.get(0);
            assertEquals(FarmView.COLOR_HIGHLIGHT, selection.getStroke());
            assertEquals(3, selection.getStrokeWidth(), 0.0);
            assertFalse(selection.isVisible(), "未选择地块时黄色框必须隐藏，不能固定出现在左上角");
            return null;
        });
    }

    @Test
    void groundTextureRendersBelowCropSprite() throws InterruptedException {
        onFxThread(() -> {
            Farm farm = new BasicFarm();
            plant(farm, 2, 2, GrowthStage.GROWING, -1L);
            FarmView view = new FarmView(farm);

            List<ImageView> textures = texturesIntersectingCell(view, 2, 2);
            assertEquals(2, textures.size(), "该格应有地面层 + 作物层");
            ImageView ground = textures.stream()
                    .filter(iv -> !"cropSprite".equals(iv.getUserData()))
                    .findFirst().orElse(null);
            ImageView crop = textures.stream()
                    .filter(iv -> "cropSprite".equals(iv.getUserData()))
                    .findFirst().orElse(null);
            assertNotNull(ground);
            assertNotNull(crop);
            assertTrue(view.getChildren().indexOf(ground) < view.getChildren().indexOf(crop));
            return null;
        });
    }
}
