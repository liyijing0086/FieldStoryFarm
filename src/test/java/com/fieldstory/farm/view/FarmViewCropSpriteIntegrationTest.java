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
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FarmView} 作物贴图层接入集成测试（A 模块 P1 作物贴图接入渲染接入卡）。
 *
 * <p>沿用 {@link FarmViewRestoreIntegrationTest} 的 Platform.startup + onFxThread
 * 模式，在 JavaFX 线程内走「农场模型 → FarmView 构造」真实链路：
 * 贴图层显示（决策 D2：MATURE 显示末帧贴图）、2× 放大尺寸（决策 D1）
 * 与坏数据方块回退（crop_type/stage 为 null 不得抛 NPE）。
 */
class FarmViewCropSpriteIntegrationTest {

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

    /** 在指定格播种作物（全局坐标，BasicFarm 中心种植区 2..9）。 */
    private static Farm farmWithCrop(int row, int column, CropType type, GrowthStage stage) {
        Farm farm = new BasicFarm();
        Soil soil = farm.getSoil(row, column);
        soil.setState(SoilState.PLANTED);
        Crop crop = new BasicCrop();
        crop.setCropType(type);
        crop.setGrowthStage(stage);
        crop.setGrowthProgress(50);
        crop.setLastManualWaterGameDay(-1);
        soil.setCrop(crop);
        return farm;
    }

    /** 收集画布 children 中 visible=true 的 ImageView（即作物贴图层）。 */
    private static List<ImageView> visibleSprites(FarmView view) {
        List<ImageView> sprites = new ArrayList<>();
        for (Node child : view.getChildren()) {
            // 👈 加上 "cropSprite".equals(sprite.getUserData())，只认我们打过记号的贴图
            if (child instanceof ImageView sprite && sprite.isVisible()
                    && "cropSprite".equals(sprite.getUserData())) {
                sprites.add(sprite);
            }
        }
        return sprites;
    }

    /** WHEAT/GROWING：P4 生产贴图必须完整落在 40×40 内，并按格底部居中。 */
    @Test
    void growingWheatShowsScaledSprite() throws InterruptedException {
        onFxThread(() -> {
            Farm farm = farmWithCrop(2, 2, CropType.WHEAT, GrowthStage.GROWING);
            FarmView view = assertDoesNotThrow(() -> new FarmView(farm));

            List<ImageView> sprites = visibleSprites(view);
            assertEquals(1, sprites.size(), "仅目标格显示贴图");
            ImageView sprite = sprites.get(0);
            assertTrue(sprite.getFitWidth() <= 40, "作物宽度不得超过 UI 规范 40px");
            assertTrue(sprite.getFitHeight() <= 40, "作物高度不得超过 UI 规范 40px");
            // Wheat 18×32 帧按 max=40 等比缩放后为 floor(22.5)×40。
            assertEquals(22, sprite.getFitWidth(), 0.0);
            assertEquals(40, sprite.getFitHeight(), 0.0);
            assertEquals(2 * FarmView.TILE_SIZE + (FarmView.TILE_SIZE - 22) / 2.0,
                    sprite.getX(), 0.0);
            assertEquals(3 * FarmView.TILE_SIZE - 40, sprite.getY(), 0.0);
            return null;
        });
    }

    /** WHEAT/MATURE：显示末帧贴图（决策 D2），构造不抛且存在 visible 贴图。 */
    @Test
    void matureWheatShowsSprite() throws InterruptedException {
        onFxThread(() -> {
            Farm farm = farmWithCrop(2, 2, CropType.WHEAT, GrowthStage.MATURE);
            FarmView view = assertDoesNotThrow(() -> new FarmView(farm));

            assertEquals(1, visibleSprites(view).size(), "MATURE 显示贴图（决策 D2）");
            return null;
        });
    }

    /** 坏数据：crop_type=null（存档允许 NULL）→ 构造不抛，贴图隐藏走方块回退。 */
    @Test
    void nullCropTypeFallsBackToBlock() throws InterruptedException {
        onFxThread(() -> {
            Farm farm = farmWithCrop(2, 2, null, GrowthStage.GROWING);
            FarmView view = assertDoesNotThrow(() -> new FarmView(farm));

            assertEquals(0, visibleSprites(view).size(), "坏数据不显示贴图（方块回退）");
            return null;
        });
    }

    /** 坏数据：growth_stage=null → 构造不抛，贴图隐藏走方块回退。 */
    @Test
    void nullStageFallsBackToBlock() throws InterruptedException {
        onFxThread(() -> {
            Farm farm = farmWithCrop(2, 2, CropType.WHEAT, null);
            FarmView view = assertDoesNotThrow(() -> new FarmView(farm));

            assertEquals(0, visibleSprites(view).size(), "坏数据不显示贴图（方块回退）");
            return null;
        });
    }
}
