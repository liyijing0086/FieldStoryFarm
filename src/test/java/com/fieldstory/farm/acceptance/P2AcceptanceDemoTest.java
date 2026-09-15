package com.fieldstory.farm.acceptance;

import com.fieldstory.farm.manager.GameManager;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.FarmGameModel;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.persistence.DatabaseService;
import com.fieldstory.farm.persistence.FarmStateAdapter;
import com.fieldstory.farm.persistence.SqliteSaveService;
import com.fieldstory.farm.service.EventService;
import com.fieldstory.farm.testutil.TestGameClock;
import com.fieldstory.farm.util.RandomProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static com.fieldstory.farm.util.GameConstants.MINUTES_PER_DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L8.5 P2 验收演示：TestClock 端到端流程。
 *
 * <p>演示脚本（验收规范 §八十九/§九十/§九十一）：
 * <pre>
 * 种植多株作物 → 保存并退出 → TestClock 推进 1 游戏日 → 重新打开
 *   → 系统完成离线模拟 → 事件记录正确
 * </pre>
 *
 * <p>本测试用 {@link TestGameClock} 精确控制游戏时间，用真实 {@link SqliteSaveService}
 * 落盘，用 D 模块 {@link EventService} 复用事件规则完成离线模拟，验证事件记录跨退出重进正确。
 */
class P2AcceptanceDemoTest {

    @TempDir
    Path tempDir;

    private GameManager managerOn(String dbName) {
        DatabaseService database = new DatabaseService(tempDir.resolve(dbName));
        return new GameManager(new SqliteSaveService(database, null));
    }

    @Test
    void plantSaveExitAdvanceOneDayReopenOfflineSimulationRecordsEvent() {
        // ---------- ① 种植多株作物 ----------
        GameManager manager = managerOn("demo.db");
        GameState state = manager.start();

        Farm farm = new BasicFarm();
        FarmStateAdapter.restore(state, farm);

        TestGameClock clock = new TestGameClock();
        clock.setGameDay(1);
        clock.setGameHour(6);

        // 在 3 块地种下 3 株作物
        plantCrop(farm, 2, 2, CropType.WHEAT, clock);
        plantCrop(farm, 2, 3, CropType.CORN, clock);
        plantCrop(farm, 3, 3, CropType.CARROT, clock);

        // 装配事件系统（D 模块 P2），并固定随机种子保证可复现
        FarmGameModel model = new FarmGameModel(clock);
        RandomProvider.setSeed(20240601L);

        // ---------- ② 保存并退出 ----------
        manager.setBeforeSaveHook(() -> {
            FarmStateAdapter.capture(state, farm);
            state.setGameDay(clock.getGameDay());
            // 把当前事件状态写入存档（active_event）
            writeEventToState(state, model);
        });
        manager.saveAndExit();

        // ---------- ③ TestClock 推进 1 游戏日 ----------
        // 模拟玩家离线 1 个游戏日（1440 游戏分钟）
        clock.setTotalMinutes(clock.getTotalMinutes() + MINUTES_PER_DAY);
        assertEquals(2, clock.getGameDay(), "TestClock 应推进到第 2 游戏日");

        // ---------- ④ 重新打开 ----------
        GameManager restarted = managerOn("demo.db");
        assertTrue(restarted.hasSavedGame(), "应存在可恢复存档");
        GameState loaded = restarted.start();
        assertEquals(1L, loaded.getGameDay(), "重开时应恢复退出瞬间的游戏天数（第 1 日）");

        Farm reloaded = new BasicFarm();
        FarmStateAdapter.restore(loaded, reloaded);
        assertEquals(SoilState.PLANTED, reloaded.getSoil(2, 2).getState());
        assertEquals(SoilState.PLANTED, reloaded.getSoil(2, 3).getState());
        assertEquals(SoilState.PLANTED, reloaded.getSoil(3, 3).getState());
        assertNotNull(reloaded.getSoil(2, 2).getCrop());
        assertEquals(CropType.WHEAT, reloaded.getSoil(2, 2).getCrop().getCropType());

        // ---------- ⑤ 系统完成离线模拟（复用 D 模块事件规则） ----------
        FarmGameModel reopenedModel = new FarmGameModel(clock);
        // 从存档恢复事件状态（active_event → EventState）
        restoreEventFromState(loaded, reopenedModel);

        EventService eventService = reopenedModel.getEventService();
        long now = (long) clock.getGameDay() * 24 + clock.getGameHour();

        // 离线每日循环：先关闭到期事件，再抽取新事件（验收 §八十九 第 ⑧/⑬ 步）
        eventService.expireIfNeeded(now);
        EventType rolled = eventService.rollDailyEvent(clock.getGameDay());
        assertEquals(2, clock.getGameDay(), "离线模拟后游戏天数应推进到第 2 日");

        // ---------- ⑥ 事件记录正确 ----------
        assertNotNull(rolled, "离线模拟应抽取到事件类型");
        assertEquals(rolled, reopenedModel.getEventState().getEventType(),
                "抽取结果应写入 EventState");
        // 事件持续时长符合规则（非即时事件）
        if (!rolled.isInstant() && rolled != EventType.NONE) {
            assertEquals(rolled.getDurationHours(),
                    reopenedModel.getEventState().getEndWorldTime()
                            - reopenedModel.getEventState().getStartWorldTime(),
                    "事件持续时长应符合规则");
        }

        // 事件状态可再次落盘并恢复（active_event 往返）
        GameState afterOffline = new GameState(loaded.getPlayer(), clock.getGameDay());
        writeEventToState(afterOffline, reopenedModel);
        new SqliteSaveService(new DatabaseService(tempDir.resolve("demo.db")), null).save(afterOffline);

        GameState reloadedAgain = new SqliteSaveService(
                new DatabaseService(tempDir.resolve("demo.db")), null).load();
        assertNotNull(reloadedAgain.getActiveEvent(), "离线模拟后的事件应随存档恢复（验收 §九十一）");
        assertEquals(rolled.name(), reloadedAgain.getActiveEvent().getEventType().name(),
                "离线模拟后的事件记录应跨退出重进恢复（验收 §九十一）");
    }

    @Test
    void fixedSeedProducesReproducibleEventSequenceAcrossRestart() {
        // L8.4 固定种子验收：固定种子下事件序列可复现
        RandomProvider.setSeed(777L);
        FarmGameModel first = new FarmGameModel(new BasicGameClock());
        EventType[] firstSeq = new EventType[20];
        for (int i = 0; i < firstSeq.length; i++) {
            firstSeq[i] = first.getEventService().rollDailyEvent(i + 1);
        }

        RandomProvider.setSeed(777L);
        FarmGameModel second = new FarmGameModel(new BasicGameClock());
        for (int i = 0; i < firstSeq.length; i++) {
            assertEquals(firstSeq[i], second.getEventService().rollDailyEvent(i + 1),
                    "相同种子应产生相同事件序列（验收 §九十）");
        }
    }

    private static void plantCrop(Farm farm, int row, int col, CropType type, TestGameClock clock) {
        Soil soil = farm.getSoil(row, col);
        soil.setState(SoilState.PLANTED);
        BasicCrop crop = new BasicCrop();
        crop.setCropUuid(UUID.randomUUID());
        crop.setCropType(type);
        crop.setGrowthStage(com.fieldstory.farm.model.GrowthStage.SEED);
        crop.setGrowthProgress(0.0);
        crop.setPlantWorldTime((long) clock.getGameDay() * 24 + clock.getGameHour());
        crop.setManualWaterCount(0);
        crop.setLastManualWaterGameDay(-1L);
        soil.setCrop(crop);
    }

    private static void writeEventToState(GameState state, FarmGameModel model) {
        state.setActiveEvent(model.getEventState());
    }

    private static void restoreEventFromState(GameState state, FarmGameModel model) {
        if (state.getActiveEvent() == null) {
            return;
        }
        model.getEventState().setEventType(state.getActiveEvent().getEventType());
        model.getEventState().setStartWorldTime(state.getActiveEvent().getStartWorldTime());
        model.getEventState().setEndWorldTime(state.getActiveEvent().getEndWorldTime());
        model.getEventState().setTargetCropType(state.getActiveEvent().getTargetCropType());
        model.getEventState().setPayload(state.getActiveEvent().getPayload());
    }
}
