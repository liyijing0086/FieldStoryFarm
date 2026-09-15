package com.fieldstory.farm.view;

import com.fieldstory.farm.controller.CollectionController;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.service.CollectionService;
import com.fieldstory.farm.service.impl.BasicCollectionService;
import com.fieldstory.farm.service.impl.BasicDecorationService;
import com.fieldstory.farm.service.impl.BasicFarmRankService;
import com.fieldstory.farm.service.impl.BasicFarmScoreService;
import com.fieldstory.farm.service.impl.BasicSetService;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CollectionView} 界面测试（验收规范 §一百二十七/§一百二十八）。
 *
 * <p>在 JavaFX 线程构造控件（同 {@code MainControllerTopBarTest} 约定），断言六项必备文本
 * 与「刷新后反映最新进度」，不落盘、不触碰真实存档。
 */
class CollectionViewTest {

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

    @Test
    void showsAllSixRequiredLinesOnFreshFarm() throws InterruptedException {
        String[] lines = onFxThread(() -> {
            GameState state = newState();
            CollectionService collection = new BasicCollectionService(state);
            CollectionView view = new CollectionView(controller(state, collection));
            return new String[]{
                    view.cropText(), view.decorationText(), view.legendaryText(),
                    view.setText(), view.farmScoreText(), view.rankText()};
        });

        assertEquals("作物图鉴  0/15", lines[0]);
        assertEquals("装饰图鉴  0/14", lines[1]);
        assertEquals("传说  0/3", lines[2]);
        assertEquals("套装  0/3", lines[3]);
        assertEquals("FarmScore  0/147", lines[4]);
        assertEquals("当前评价  新手农场", lines[5]);
    }

    @Test
    void refreshReflectsNewCollections() throws InterruptedException {
        String[] lines = onFxThread(() -> {
            GameState state = newState();
            CollectionService collection = new BasicCollectionService(state);
            CollectionView view = new CollectionView(controller(state, collection));

            collection.collectCrop(CropType.WHEAT, Quality.COMMON);
            collection.collectDecoration("D01");
            collection.collectLegendary(CropType.CORN);
            view.refresh();

            return new String[]{view.cropText(), view.decorationText(), view.legendaryText(),
                    view.farmScoreText()};
        });

        assertEquals("作物图鉴  1/15", lines[0]);
        assertEquals("装饰图鉴  1/14", lines[1]);
        assertEquals("传说  1/3", lines[2]);
        // 2 + 3 + 10 = 15
        assertEquals("FarmScore  15/147", lines[3]);
    }

    @Test
    void goalHintsAreListed() throws InterruptedException {
        int hints = onFxThread(() -> {
            GameState state = newState();
            CollectionView view = new CollectionView(
                    controller(state, new BasicCollectionService(state)));
            return view.goalTexts().size();
        });
        // FarmScore/作物/装饰/套装 4 条 + 3 种传说 = 7 条（验收规范 §一百二十八）。
        assertEquals(7, hints);
    }

    // ------------------------------------------------------------------
    // 脚手架
    // ------------------------------------------------------------------

    private static GameState newState() {
        return new GameState(new Player("测试农夫", 500), 1);
    }

    private static CollectionController controller(GameState state, CollectionService collection) {
        return new CollectionController(
                collection,
                new BasicFarmScoreService(state),
                new BasicFarmRankService(),
                new BasicSetService(new BasicDecorationService(new BasicFarm(), state), state));
    }
}
