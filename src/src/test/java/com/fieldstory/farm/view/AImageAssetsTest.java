package com.fieldstory.farm.view;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GrowthStage;
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
 * A 模块作物贴图切片加载器测试（P1 作物贴图接入）。
 *
 * <p>ImageView 创建仅允许在 FX 线程发生（任务约束），故沿用
 * {@link FarmViewRestoreIntegrationTest} 的 Platform.startup + onFxThread 模式。
 * 测试 classpath 含 main/resources，三种作物图集真实存在
 * （wheat_18x32_8frames.png 等，素材文件名自描述单帧尺寸）。
 */
class AImageAssetsTest {

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

    /** WHEAT 在 SEED/GROWING/MATURE 阶段均能切出非 null 贴图（D2：MATURE 取末帧）。 */
    @Test
    void viewForWheatInNormalStagesIsNotNull() throws InterruptedException {
        onFxThread(() -> {
            assertNotNull(AImageAssets.viewFor(CropType.WHEAT, GrowthStage.SEED));
            assertNotNull(AImageAssets.viewFor(CropType.WHEAT, GrowthStage.GROWING));
            assertNotNull(AImageAssets.viewFor(CropType.WHEAT, GrowthStage.MATURE));
            return null;
        });
    }

    /** 作物类型为 null：forCropType 降级 null，返回 null 不抛异常。 */
    @Test
    void viewForNullCropTypeReturnsNull() throws InterruptedException {
        onFxThread(() -> {
            assertNull(AImageAssets.viewFor(null, GrowthStage.SEED));
            return null;
        });
    }

    /** 成长阶段为 null：cropFrameIndexFor 返回 -1 哨兵，返回 null 不抛异常。 */
    @Test
    void viewForNullStageReturnsNull() throws InterruptedException {
        onFxThread(() -> {
            assertNull(AImageAssets.viewFor(CropType.WHEAT, null));
            return null;
        });
    }

    /** WITHERED 不显示贴图（决策 D3）：返回 null。 */
    @Test
    void viewForWitheredReturnsNull() throws InterruptedException {
        onFxThread(() -> {
            assertNull(AImageAssets.viewFor(CropType.WHEAT, GrowthStage.WITHERED));
            return null;
        });
    }

    /** viewport 宽高等于帧宽高（18×32），fitWidth/fitHeight 等于帧尺寸 × SCALE。 */
    @Test
    void viewportAndFitSizeMatchFrameTimesScale() throws InterruptedException {
        onFxThread(() -> {
            CropSpriteSheet sheet = CropSpriteSheet.WHEAT;
            ImageView view = AImageAssets.viewFor(CropType.WHEAT, GrowthStage.SEED);
            assertNotNull(view);

            Rectangle2D viewport = view.getViewport();
            assertEquals(sheet.getFrameWidth(), viewport.getWidth(), 0.0);
            assertEquals(sheet.getFrameHeight(), viewport.getHeight(), 0.0);
            assertEquals(sheet.getFrameWidth() * AImageAssets.SCALE, view.getFitWidth(), 0.0);
            assertEquals(sheet.getFrameHeight() * AImageAssets.SCALE, view.getFitHeight(), 0.0);
            return null;
        });
    }
}
