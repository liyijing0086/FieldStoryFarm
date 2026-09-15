package com.fieldstory.farm.controller;

import com.fieldstory.farm.manager.GameManager;
import com.fieldstory.farm.manager.SceneManager;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.persistence.DatabaseService;
import com.fieldstory.farm.persistence.SqliteSaveService;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 存档端到端回归（E 模块）：从「开始新游戏」装配一路走到「保存 → 重开读档」。
 *
 * <p>覆盖此前缺测的装配层契约——{@link MainController#onNewGameButtonClick()} 注册的
 * 存档前回填钩子（{@code setBeforeSaveHook}）在落盘前把<b>运行中的农场</b>
 * （中心 8×8 地块）与<b>当前游戏天数</b>同步进 {@link GameState}，
 * 从而使退出自动保存 / 手动保存都能恢复到退出瞬间。
 *
 * <p>现有 {@code MainControllerSceneAssemblyTest} / {@code MainControllerTopBarTest}
 * 均注入内存存档、只验证挂载，未触及该回填路径；本测试改用临时目录里的真实
 * {@link SqliteSaveService}（不触碰 {@code data/farm.db}），并在 JavaFX 线程驱动
 * 真实装配（同 {@code StatusViewTest} 约定）。
 */
class MainControllerSaveRoundTripTest {

    @TempDir
    Path tempDir;

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

    /** 手动构造控制器时 FXML 未注入 {@code welcomeText}，测试补一个空标签避免 NPE。 */
    private static void injectWelcomeText(MainController controller) {
        try {
            Field field = MainController.class.getDeclaredField("welcomeText");
            field.setAccessible(true);
            field.set(controller, new Label());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入 welcomeText 失败", e);
        }
    }

    /**
     * 开局装配 → 手动保存 → 用同一数据库文件重开：农场地块与种子库存应无损往返。
     */
    @Test
    void startThenSavePersistsFarmPlotsAndSeedsThroughProductionHook() throws InterruptedException {
        Path db = tempDir.resolve("assembly.db");

        GameState snapshot = onFxThread(() -> {
            SceneManager.getInstance().assemble(new Pane(), 800, 600);
            GameManager manager = new GameManager(new SqliteSaveService(new DatabaseService(db)));
            MainController controller = new MainController(manager);
            injectWelcomeText(controller);

            controller.onNewGameButtonClick(); // 真实装配：注册存档前回填钩子
            manager.saveNow();               // 触发回填 → 写入 SQLite
            return manager.currentState();
        });

        // 回填钩子应把运行中农场（中心 8×8）与当前游戏天数写入快照
        assertEquals(64, snapshot.getPlots().size(), "存档前回填应捕获中心 8×8 共 64 块农田");
        assertEquals(1L, snapshot.getGameDay(), "新档保存后应为第 1 天（存档天数 = GameClock.getGameDay()，从 1 起）");
        // 用同一数据库文件重开读档（模拟关掉程序再打开）
        GameState reloaded = new SqliteSaveService(new DatabaseService(db)).load();
        assertNotNull(reloaded, "重开后应能读到存档");
        assertNotNull(reloaded.getPlayer(), "玩家应随存档恢复");
        assertEquals(64, reloaded.getPlots().size(), "SQLite 应持久化全部 64 块农田");

        // P3 起新档可能根据 balance-config 将部分格子设为 LOCKED。
        // 本测试验证的是“保存 → 重开”无损往返，因此不再把所有格子写死为 EMPTY，
        // 而是逐坐标比较保存前快照与重载结果，确保 EMPTY / LOCKED / 其他状态都原样恢复。
        for (var expected : snapshot.getPlots()) {
            var actual = reloaded.getPlots().stream()
                    .filter(plot -> plot.getRow() == expected.getRow()
                            && plot.getColumn() == expected.getColumn())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "重载后缺少地块 (" + expected.getRow() + "," + expected.getColumn() + ")"));
            assertEquals(expected.getState(), actual.getState(),
                    "地块状态保存/重载后应保持一致：("
                            + expected.getRow() + "," + expected.getColumn() + ")");
        }

        // B P0 经济口径：新档不赠送起始种子，库存 0 也应随存档往返
        assertEquals(0, reloaded.getPlayer().getSeedInventory().get(CropType.WHEAT),
                "新档不赠送起始种子，库存应保持 0");
    }
}
