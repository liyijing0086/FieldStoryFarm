package com.fieldstory.farm.controller;

import com.fieldstory.farm.manager.GameManager;
import com.fieldstory.farm.manager.SceneManager;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.service.SaveService;
import com.fieldstory.farm.service.economy.EconomyService;
import com.fieldstory.farm.service.economy.impl.EconomyServiceImpl;
import com.fieldstory.farm.view.SeedQuickBuyView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归：开局后 B 模块商店面板已拼接到场景右侧（RIGHT）。
 *
 * <p>缺口背景：《接口约定-场景合并》§1 约定 RIGHT 归 B 商店，B 已交付
 * {@link SeedQuickBuyView}（P0 快捷购买面板），但此前装配层未挂载，
 * RIGHT 槽一直为空，商店入口不可达。修复后由 {@link MainController#mountShopPanel}
 * 在开局时把面板挂到 RIGHT。
 *
 * <p>本测试在 JavaFX 线程构造控件（同 {@code StatusViewTest} / {@code MainControllerTopBarTest}
 * 约定），注入内存 {@link SaveService} 的 {@link GameManager}，不触碰 data/farm.db。
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

    /** 缺口回归：装配后 B 商店面板应挂在 RIGHT 槽位。 */
    @Test
    void shopPanelMountedToRightSlot() throws InterruptedException {
        Node right = onFxThread(() -> {
            SceneManager sceneManager = SceneManager.getInstance();
            sceneManager.assemble(new Pane(), 960, 640); // 模拟 E 组装主场景

            MainController controller = new MainController(
                    new GameManager(new InMemorySaveService()));
            EconomyService economy = new EconomyServiceImpl(new Player("测试", 500));
            controller.mountShopPanel(economy);

            return sceneManager.root().getRight();
        });

        assertNotNull(right, "RIGHT 槽位应已挂载 B 商店面板");
        assertTrue(right instanceof SeedQuickBuyView,
                "RIGHT 槽位应为 B 模块的 SeedQuickBuyView");
    }
}
