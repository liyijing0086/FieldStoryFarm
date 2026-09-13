package com.fieldstory.farm.controller;

import com.fieldstory.farm.manager.GameManager;
import com.fieldstory.farm.manager.SceneManager;
import com.fieldstory.farm.model.FarmGameModel;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.service.SaveService;
import com.fieldstory.farm.view.StatusView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归：开局后「保存进度」入口仍可达。
 *
 * <p>缺口背景：点「开始游戏」后 {@code FarmViewController.mountToScene()} 用农场视图
 * 替换 CENTER 的主菜单，main-view.fxml 的「保存进度」按钮随即消失且再不可达。
 * 修复后手动存档入口改挂常驻 TOP（{@link MainController#buildTopBar}）。
 *
 * <p>本测试在 JavaFX 线程构造控件（同 {@code StatusViewTest} 约定），
 * 并注入内存 {@link SaveService} 的 {@link GameManager}，不触碰 data/farm.db。
 */
class MainControllerTopBarTest {

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

    /** 在 JavaFX 应用线程上执行并返回结果。 */
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

    /** 内存存档：测试不落盘。 */
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

    private static MainController newController() {
        return new MainController(new GameManager(new InMemorySaveService()));
    }

    /** 缺口回归：农场视图替换 CENTER 主菜单后，TOP 上的存档按钮仍在场景图内。 */
    @Test
    void saveEntryStaysReachableAfterGameStarts() throws InterruptedException {
        boolean reachable = onFxThread(() -> {
            SceneManager sceneManager = SceneManager.getInstance();
            sceneManager.assemble(new Pane(), 320, 240); // 模拟主菜单挂 CENTER

            MainController controller = newController();
            controller.buildTopBar(new StatusView(new FarmGameModel()));

            HBox topBar = asTopBar(sceneManager);
            Button save = findSaveButton(topBar);

            // 模拟点「开始游戏」：农场视图替换 CENTER
            sceneManager.mount(SceneManager.Slot.CENTER, new Pane());

            Node topAfterStart = sceneManager.root().getTop();
            return topAfterStart == topBar && topBar.getChildren().contains(save);
        });
        assertTrue(reachable, "开局后手动存档按钮应仍在常驻 TOP");
    }

    /** 存档按钮点击走 onSaveButtonClick；未开局时给出提示且不抛异常。 */
    @Test
    void saveButtonBeforeStartShowsHintWithoutThrowing() throws InterruptedException {
        String hint = onFxThread(() -> {
            SceneManager.getInstance().assemble(new Pane(), 320, 240);
            MainController controller = newController();
            controller.buildTopBar(new StatusView(new FarmGameModel()));

            HBox topBar = asTopBar(SceneManager.getInstance());
            findSaveButton(topBar).fire();

            Label hintLabel = topBar.getChildren().stream()
                    .filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .findFirst()
                    .orElseThrow();
            return hintLabel.getText();
        });
        assertEquals("尚无进行中的游戏，请先点击“开始游戏”。", hint);
    }

    private static HBox asTopBar(SceneManager sceneManager) {
        Node top = sceneManager.root().getTop();
        assertNotNull(top, "TOP 槽位应已挂载顶栏");
        assertTrue(top instanceof HBox, "TOP 应为顶栏 HBox");
        return (HBox) top;
    }

    private static Button findSaveButton(HBox topBar) {
        Button save = topBar.getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("保存进度", save.getText());
        return save;
    }
}
