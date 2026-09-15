package com.fieldstory.farm.controller;

import com.fieldstory.farm.manager.GameManager;
import com.fieldstory.farm.manager.SceneManager;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.service.SaveService;
import com.fieldstory.farm.view.BusinessToolbarView;
import com.fieldstory.farm.view.DecorationOverlayView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B P1 场景装配回归：完整商店入口进入 TOP，装饰覆盖层进入 CENTER，
 * P0 SeedQuickBuyView 不再占用 RIGHT。
 */
class MainControllerSceneAssemblyTest {

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
        assertTrue(latch.await(20, TimeUnit.SECONDS), "JavaFX 任务执行超时");
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        return ref.get();
    }

    private static final class InMemorySaveService implements SaveService {
        @Override
        public boolean hasSave() {
            return false;
        }

        @Override
        public void save(GameState state) {
            // 测试不落盘
        }

        @Override
        public GameState load() {
            return null;
        }
    }

    private static void injectWelcomeText(MainController controller) {
        try {
            Field field = MainController.class.getDeclaredField("welcomeText");
            field.setAccessible(true);
            field.set(controller, new Label());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入 welcomeText 失败", e);
        }
    }

    @Test
    void p1BusinessUiMountedWithoutLegacyRightPanel() throws InterruptedException {
        Result result = onFxThread(() -> {
            SceneManager sceneManager = SceneManager.getInstance();
            sceneManager.assemble(new Pane(), 960, 640);

            MainController controller = new MainController(
                    new GameManager(new InMemorySaveService()));
            injectWelcomeText(controller);
            controller.onNewGameButtonClick();

            return new Result(
                    sceneManager.root().getCenter(),
                    sceneManager.root().getTop(),
                    sceneManager.root().getRight());
        });

        assertNotNull(result.center());
        assertTrue(result.center() instanceof DecorationOverlayView,
                "CENTER 应为 B DecorationOverlayView 包裹 A FarmView");

        assertNotNull(result.top());
        assertTrue(result.top() instanceof HBox, "TOP 应为组合顶栏");
        HBox top = (HBox) result.top();
        assertTrue(top.getChildren().stream().anyMatch(BusinessToolbarView.class::isInstance),
                "TOP 应包含 B 的 BusinessToolbarView");

        assertNull(result.right(), "P1 完整商店通过 TOP 弹窗进入，RIGHT 不应再挂 P0 快捷购买面板");
    }

    private record Result(Node center, Node top, Node right) {
    }
}
