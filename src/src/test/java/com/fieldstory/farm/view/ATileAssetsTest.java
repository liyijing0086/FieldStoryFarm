package com.fieldstory.farm.view;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.ImageView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A 模块地面贴图切片加载器测试（P1 地面 Tile 美化渲染接入卡）。
 *
 * <p>ImageView 创建仅允许在 FX 线程发生（任务约束），故沿用
 * {@link AImageAssetsTest} 的 Platform.startup + onFxThread 模式。
 * 测试 classpath 含 main/resources，地面图集真实存在
 * （ground_01_16x16.png，256×256、16×16 帧网格）。
 *
 * <p>viewport 期望值 = 帧坐标 × 16（决策记录帧坐标表：GRASS col3,row6；
 * TILLED col3,row1；WET col8,row10），fit 期望值 = 16 × SCALE = 32（决策 D-G1）。
 */
class ATileAssetsTest {

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

    /** GRASS/TILLED/WET 三种变体均能切出非 null 贴图（图集真实存在）。 */
    @Test
    void viewForGroundVariantsIsNotNull() throws InterruptedException {
        onFxThread(() -> {
            assertNotNull(ATileAssets.viewFor(GroundVariant.GRASS));
            assertNotNull(ATileAssets.viewFor(GroundVariant.TILLED));
            assertNotNull(ATileAssets.viewFor(GroundVariant.WET));
            return null;
        });
    }

    /** variant 为 null 与 NONE：均为「不铺贴图」哨兵，返回 null 不抛异常。 */
    @Test
    void viewForNullAndNoneReturnsNull() throws InterruptedException {
        onFxThread(() -> {
            assertNull(ATileAssets.viewFor(null));
            assertNull(ATileAssets.viewFor(GroundVariant.NONE));
            return null;
        });
    }

    /** GRASS：viewport=(48,96,16,16)（col3×16, row6×16），fitWidth/fitHeight 均为 32。 */
    @Test
    void grassViewportAndFitSizeMatchFrame() throws InterruptedException {
        onFxThread(() -> {
            ImageView view = ATileAssets.viewFor(GroundVariant.GRASS);
            assertNotNull(view);

            Rectangle2D viewport = view.getViewport();
            assertEquals(48, viewport.getMinX(), 0.0);
            assertEquals(96, viewport.getMinY(), 0.0);
            assertEquals(16, viewport.getWidth(), 0.0);
            assertEquals(16, viewport.getHeight(), 0.0);
            assertEquals(32, view.getFitWidth(), 0.0);
            assertEquals(32, view.getFitHeight(), 0.0);
            return null;
        });
    }

    /** TILLED：viewport=(48,16,16,16)（col3×16, row1×16），fit 均为 32。 */
    @Test
    void tilledViewportAndFitSizeMatchFrame() throws InterruptedException {
        onFxThread(() -> {
            ImageView view = ATileAssets.viewFor(GroundVariant.TILLED);
            assertNotNull(view);

            Rectangle2D viewport = view.getViewport();
            assertEquals(48, viewport.getMinX(), 0.0);
            assertEquals(16, viewport.getMinY(), 0.0);
            assertEquals(16, viewport.getWidth(), 0.0);
            assertEquals(16, viewport.getHeight(), 0.0);
            assertEquals(32, view.getFitWidth(), 0.0);
            assertEquals(32, view.getFitHeight(), 0.0);
            return null;
        });
    }

    /** WET：viewport=(128,160,16,16)（col8×16, row10×16），fit 均为 32。 */
    @Test
    void wetViewportAndFitSizeMatchFrame() throws InterruptedException {
        onFxThread(() -> {
            ImageView view = ATileAssets.viewFor(GroundVariant.WET);
            assertNotNull(view);

            Rectangle2D viewport = view.getViewport();
            assertEquals(128, viewport.getMinX(), 0.0);
            assertEquals(160, viewport.getMinY(), 0.0);
            assertEquals(16, viewport.getWidth(), 0.0);
            assertEquals(16, viewport.getHeight(), 0.0);
            assertEquals(32, view.getFitWidth(), 0.0);
            assertEquals(32, view.getFitHeight(), 0.0);
            return null;
        });
    }
}
