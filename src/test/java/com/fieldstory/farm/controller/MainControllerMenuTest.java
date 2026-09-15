package com.fieldstory.farm.controller;

import com.fieldstory.farm.config.AppConfig;
import com.fieldstory.farm.manager.GameManager;
import com.fieldstory.farm.manager.SceneManager;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.persistence.DatabaseService;
import com.fieldstory.farm.persistence.SaveSlotManager;
import com.fieldstory.farm.persistence.SqliteSaveService;
import com.fieldstory.farm.service.SaveService;
import com.fieldstory.farm.util.FxmlUtil;
import com.fieldstory.farm.view.MainApplication;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开局主菜单回归（E 模块）：两个按钮从「开始游戏 / 保存进度」拆分为
 * 「开始新游戏」与「读取存档」。
 *
 * <p>拆分前只有一个「开始游戏」入口，语义模糊——有档就读档、无档就新建，
 * 玩家无法主动"重开一局"，也无从知道读档是否成功。拆分后两条路径显式分流：
 * <ul>
 *   <li>开始新游戏 → {@link GameManager#startNewGame()}：<b>无视</b>历史存档，强制新档；</li>
 *   <li>读取存档 → {@link GameManager#start()}：有档还原退出瞬间状态，无档只提示不进入；</li>
 *   <li>手动存档保留在常驻 TOP 的「保存进度」（{@code MainControllerTopBarTest} 覆盖）。</li>
 * </ul>
 *
 * <p>本测试在 JavaFX 线程驱动真实装配（同 {@code MainControllerSaveRoundTripTest} 约定），
 * 存档一律落在 {@link TempDir} 的临时 SQLite，不触碰 {@code data/farm.db}。
 */
class MainControllerMenuTest {

    /** 旧档判定值：只要新游戏沿用/读档丢掉它，断言即失败。 */
    private static final int LEGACY_GOLD = 777;

    private static final long LEGACY_GAME_DAY = 5L;

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
        assertTrue(latch.await(20, TimeUnit.SECONDS), "JavaFX 任务执行超时");
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        return ref.get();
    }

    /** 手动构造控制器时 FXML 未注入 {@code welcomeText}，测试补一个空标签并返回，供断言提示语。 */
    private static Label injectWelcomeText(MainController controller) {
        try {
            Field field = MainController.class.getDeclaredField("welcomeText");
            field.setAccessible(true);
            Label label = new Label();
            field.set(controller, label);
            return label;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入 welcomeText 失败", e);
        }
    }

    /** 取 FXML 注入的存档位容器（反射字段，避免依赖 CSS lookup 的 id 语义）。 */
    private static VBox slotListOf(MainController controller) {
        try {
            Field field = MainController.class.getDeclaredField("slotList");
            field.setAccessible(true);
            return (VBox) field.get(controller);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取 slotList 失败", e);
        }
    }

    /** 取 FXML 注入的「新建存档」按钮（反射字段）。 */
    private static Button newSaveButtonOf(MainController controller) {
        try {
            Field field = MainController.class.getDeclaredField("newSaveButton");
            field.setAccessible(true);
            return (Button) field.get(controller);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取 newSaveButton 失败", e);
        }
    }

    /** 内存存档：{@code hasSave} / {@code load} 可配置，用于"有档 / 无档"两条分支。 */
    private static final class StubSaveService implements SaveService {

        private final boolean hasSave;
        private final GameState loaded;

        StubSaveService(boolean hasSave, GameState loaded) {
            this.hasSave = hasSave;
            this.loaded = loaded;
        }

        @Override
        public boolean hasSave() {
            return hasSave;
        }

        @Override
        public void save(GameState state) {
            // 测试不落盘
        }

        @Override
        public GameState load() {
            return loaded;
        }
    }

    /** 预写一份"上次退出的进度"到临时数据库：金币 777、第 5 天。 */
    private void writeLegacySave(Path db) {
        new SqliteSaveService(new DatabaseService(db), null)
                .save(new GameState(new Player("旧档", LEGACY_GOLD), LEGACY_GAME_DAY));
    }

    /** 主菜单只列出已存在的存档，每行「存档 N：摘要（存档时间）」+「读取」+「新游戏」，并另有「新建存档」入口。 */
    @Test
    void menuListsExistingSavesAndOffersNewSave() throws InterruptedException {
        Path dataDir = tempDir.resolve("data");
        new SqliteSaveService(new DatabaseService(dataDir.resolve("farm.db")), null)
                .save(new GameState(new Player("甲", LEGACY_GOLD), LEGACY_GAME_DAY));
        new SqliteSaveService(new DatabaseService(dataDir.resolve("save-2.db")), null)
                .save(new GameState(new Player("乙", 860), 4L));

        List<String[]> rows = onFxThread(() -> {
            FXMLLoader loader = loadMenuFxml(new GameManager(new SaveSlotManager(
                    slot -> new SqliteSaveService(
                            new DatabaseService(dataDir.resolve(slot.databaseFile().getFileName())), null),
                    () -> SaveSlotManager.discoverSlots(dataDir))));
            VBox slotList = slotListOf(loader.getController());
            Button newSave = newSaveButtonOf(loader.getController());
            assertNotNull(newSave, "主菜单应有「新建存档」按钮");
            assertEquals("新建存档", newSave.getText());
            assertNotNull(newSave.getOnAction(), "「新建存档」应已绑定 onAction");
            List<String[]> result = new ArrayList<>();
            for (Node node : slotList.getChildren()) {
                HBox row = (HBox) node;
                Label summary = (Label) row.getChildren().get(0);
                Button load = (Button) row.getChildren().get(1);
                Button newGame = (Button) row.getChildren().get(2);
                result.add(new String[] {
                        summary.getText(),
                        load.getText(),
                        newGame.getText(),
                        load.isDisabled() ? "disabled" : "enabled"
                });
            }
            return result;
        });

        assertEquals(2, rows.size(), "保存了几个存档就展示几行");
        assertTrue(rows.get(0)[0].startsWith("存档 1：第 6 天 · 金币 777"), rows.get(0)[0]);
        assertTrue(rows.get(1)[0].startsWith("存档 2：第 5 天 · 金币 860"), rows.get(1)[0]);
        for (String[] row : rows) {
            assertEquals("读取", row[1], "每个存档位都应有「读取」");
            assertEquals("新游戏", row[2], "每个存档位都应有「新游戏」");
            assertEquals("enabled", row[3], "有档的「读取」应可用");
        }
    }

    /** 无档时点「读取存档」：只给提示，不进入游戏（不静默开新档）。 */
    @Test
    void loadWithoutSaveShowsHintAndStartsNoGame() throws InterruptedException {
        String hint = onFxThread(() -> {
            GameManager manager = new GameManager(new StubSaveService(false, null));
            MainController controller = new MainController(manager);
            Label welcome = injectWelcomeText(controller);

            controller.onLoadButtonClick();

            assertThrows(IllegalStateException.class, manager::currentState,
                    "无档读档不应创建会话状态");
            return welcome.getText();
        });

        assertEquals("存档 1 是空档，请先点击该档的「新游戏」。", hint);
    }


    /** 有档时点「读取存档」：金币与游戏天数都应还原为存档值。 */
    @Test
    void loadRestoresSavedProgress() throws InterruptedException {
        Path db = tempDir.resolve("menu-load.db");
        writeLegacySave(db);

        GameState state = onFxThread(() -> {
            SceneManager.getInstance().assemble(new Pane(), 800, 600);
            GameManager manager = new GameManager(new SqliteSaveService(new DatabaseService(db), null));
            MainController controller = new MainController(manager);
            injectWelcomeText(controller);

            controller.onLoadButtonClick();
            return manager.currentState();
        });

        assertEquals(LEGACY_GOLD, state.getPlayer().getGold(), "读档应还原存档金币");
        assertEquals(LEGACY_GAME_DAY, state.getGameDay(), "读档应还原存档游戏天数");
    }

    /** 有档时点「开始新游戏」：无视旧档，回到金币 500、种子库存全 0 的正式新档。 */
    @Test
    void newGameIgnoresExistingSave() throws InterruptedException {
        Path db = tempDir.resolve("menu-new.db");
        writeLegacySave(db);

        GameState state = onFxThread(() -> {
            SceneManager.getInstance().assemble(new Pane(), 800, 600);
            GameManager manager = new GameManager(new SqliteSaveService(new DatabaseService(db), null));
            MainController controller = new MainController(manager);
            injectWelcomeText(controller);

            controller.onNewGameButtonClick();
            return manager.currentState();
        });

        assertNotEquals(LEGACY_GOLD, state.getPlayer().getGold(), "新游戏不应沿用旧档金币");
        assertEquals(500, state.getPlayer().getGold(), "新游戏初始金币必须保持 500");
        assertEquals(1L, state.getGameDay(), "新游戏应回到第 1 天");
        for (CropType type : CropType.values()) {
            assertEquals(0, state.getPlayer().getSeedInventory().get(type),
                    "新游戏种子库存应从 0 开始：" + type);
        }

        // MainController 在完成新游戏装配后应立即建立正式 SQLite 存档。
        GameManager restarted = new GameManager(
                new SqliteSaveService(new DatabaseService(db), null));
        assertTrue(restarted.hasSavedGame(), "开始新游戏后应立即存在可读取存档");
        GameState reloaded = restarted.start();
        assertEquals(500, reloaded.getPlayer().getGold(), "重启读档后金币仍应为 500");
        assertEquals(1L, reloaded.getGameDay(), "重启读档后仍应为第 1 天");
    }

    /** 加载主菜单 FXML，并把控制器工厂指向给定 GameManager（不碰真实 data/farm.db）。 */
    private static FXMLLoader loadMenuFxml(GameManager manager) {
        try {
            FXMLLoader loader = FxmlUtil.load(MainApplication.class, AppConfig.MAIN_VIEW_FXML);
            loader.setControllerFactory(type -> new MainController(manager));
            loader.load();
            return loader;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("加载主菜单 FXML 失败", e);
        }
    }
}
