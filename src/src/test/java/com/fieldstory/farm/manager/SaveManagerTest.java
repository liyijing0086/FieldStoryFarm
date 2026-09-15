package com.fieldstory.farm.manager;

import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventState;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.model.impl.BasicEventState;
import com.fieldstory.farm.persistence.DatabaseService;
import com.fieldstory.farm.persistence.SqliteSaveService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E 模块「事件 / 记忆持久化」单元测试。
 *
 * <p>被测对象是项目中已有的存档管理类 {@link GameManager}：它负责
 * {@code start()}（读档 / 建档）与 {@code saveNow()}（存盘）的编排，并把事件
 * （{@code active_event}）与记忆（{@code crop_memory}）的落盘/读回委托给已有的
 * {@link SqliteSaveService}。这里<b>不新增任何业务类、不改动业务代码</b>，
 * 仅对这套已有逻辑做黑盒验证。
 *
 * <p>三条用例对应需求：
 * <ol>
 *   <li>{@link #testSaveLoadEvent_ShouldRestoreAllEventInfo()}：写入事件记忆后读回，
 *       事件 ID（{@code cropUuid}）、触发状态、游戏天数、玩家选择等字段完全一致；</li>
 *   <li>{@link #testLoadCorruptedEventFile_NoCrash()}：读取损坏存档文件时程序不崩溃，
 *       返回空的事件记忆集合；</li>
 *   <li>{@link #testEmptyEventList_SaveAndLoad()}：空事件记忆列表的存档读写正常。</li>
 * </ol>
 *
 * <p><b>约束</b>：不引入任何 JavaFX / UI 类；使用 JUnit 的 {@link TempDir} 临时目录，
 * 每个测试独享目录、执行完自动清理，测试之间互不干扰。
 */
class SaveManagerTest {

    /** JUnit 提供的临时目录，每个测试方法一份，测试结束自动删除。 */
    @TempDir
    Path tempDir;

    /**
     * 在临时目录内构造一个指向指定 SQLite 文件的存档管理器。
     *
     * <p>第二个参数 {@code null} 表示不启用 P0 JSON 兼容迁移，避免误读写
     * 工作区里的 {@code data/save.json}。
     *
     * @param dbFileName 临时目录内的数据库文件名
     * @return 使用独立临时存档的管理器实例
     */
    private GameManager saveManagerOn(String dbFileName) {
        DatabaseService database = new DatabaseService(tempDir.resolve(dbFileName));
        return new GameManager(new SqliteSaveService(database, null));
    }

    /**
     * 用例 ①：保存事件记忆列表，读取后全部字段一致。
     *
     * <p>字段映射：事件 ID = 记忆的 {@code cropUuid}；触发状态 = 该作物记录的事件类型
     * 列表与枯萎风险；游戏天数 = {@link GameState#getGameDay()}；玩家选择 = 干旱当日
     * 是否施救（{@code waterRescueOnDroughtDay}）与最终故事（{@code finalStory}）。
     */
    @Test
    void testSaveLoadEvent_ShouldRestoreAllEventInfo() {
        String db = "event-memory.db";
        long gameDay = 7L;

        // ---------- 写入：一条当前事件 + 一条作物记忆 ----------
        GameManager manager = saveManagerOn(db);
        GameState state = manager.start(); // 空档 -> 建档
        assertNotNull(state.getPlayer(), "建档后玩家不应为空");
        state.setGameDay(gameDay);

        EventState event = new BasicEventState(
                EventType.MYSTERY_MERCHANT, gameDay * 24 + 6, gameDay * 24 + 18);
        event.setTargetCropType(CropType.WHEAT);
        event.setPayload("mystery-merchant:WHEAT");
        state.setActiveEvent(event);

        UUID memoryId = UUID.randomUUID();
        CropMemory memory = new CropMemory(memoryId, CropType.WHEAT, gameDay * 24 + 6);
        memory.setMatureWorldTime(gameDay * 24 + 20);
        memory.setHarvestWorldTime(gameDay * 24 + 22);
        memory.setManualWaterCount(3);
        memory.setRainCount(1);
        memory.setDroughtCount(1);
        memory.setGreenRainCount(2);
        memory.setFertilizerCount(1);
        memory.setLastDroughtGameDay(6L);
        memory.setWaterRescueOnDroughtDay(true); // 玩家选择
        memory.getEvents().add(EventType.METEOR_SHOWER); // 触发状态
        memory.getEvents().add(EventType.RAINBOW_DAY);
        memory.setWitherRisk(true);
        memory.setQuality(Quality.RARE);
        memory.setLegendary(true);
        memory.setFinalStory("a wheat that survived the drought");
        state.getMemories().add(memory);

        manager.saveNow(); // 落盘

        // ---------- 读回：换一个指向同一文件的新管理器，模拟重开程序 ----------
        GameManager restarted = saveManagerOn(db);
        assertTrue(restarted.hasSavedGame(), "保存后应能检测到存档");
        GameState reloaded = restarted.start();

        // 游戏天数一致
        assertEquals(gameDay, reloaded.getGameDay(), "游戏天数应一致");

        // 当前事件字段完全一致
        EventState loadedEvent = reloaded.getActiveEvent();
        assertNotNull(loadedEvent, "读回后当前事件不应为空");
        assertEquals(event.getEventType(), loadedEvent.getEventType(), "事件类型应一致");
        assertEquals(event.getStartWorldTime(), loadedEvent.getStartWorldTime(), "开始时间应一致");
        assertEquals(event.getEndWorldTime(), loadedEvent.getEndWorldTime(), "结束时间应一致");
        assertEquals(event.getTargetCropType(), loadedEvent.getTargetCropType(), "目标作物应一致");
        assertEquals(event.getPayload(), loadedEvent.getPayload(), "事件载荷应一致");

        // 事件记忆列表字段完全一致
        assertEquals(1, reloaded.getMemories().size(), "记忆列表大小应一致");
        CropMemory loadedMemory = reloaded.getMemories().get(0);
        assertEquals(memoryId, loadedMemory.getCropUuid(), "事件 ID 应一致");
        assertEquals(CropType.WHEAT, loadedMemory.getCropType(), "作物类型应一致");
        assertEquals(memory.getPlantWorldTime(), loadedMemory.getPlantWorldTime());
        assertEquals(memory.getMatureWorldTime(), loadedMemory.getMatureWorldTime());
        assertEquals(memory.getHarvestWorldTime(), loadedMemory.getHarvestWorldTime());
        assertEquals(memory.getManualWaterCount(), loadedMemory.getManualWaterCount());
        assertEquals(memory.getRainCount(), loadedMemory.getRainCount());
        assertEquals(memory.getDroughtCount(), loadedMemory.getDroughtCount());
        assertEquals(memory.getGreenRainCount(), loadedMemory.getGreenRainCount());
        assertEquals(memory.getFertilizerCount(), loadedMemory.getFertilizerCount());
        assertEquals(memory.getLastDroughtGameDay(), loadedMemory.getLastDroughtGameDay(), "游戏天数应一致");
        assertEquals(memory.isWaterRescueOnDroughtDay(),
                loadedMemory.isWaterRescueOnDroughtDay(), "玩家选择应一致");
        assertEquals(memory.getEvents(), loadedMemory.getEvents(), "触发状态应一致");
        assertEquals(memory.isWitherRisk(), loadedMemory.isWitherRisk());
        assertEquals(memory.getQuality(), loadedMemory.getQuality());
        assertEquals(memory.isLegendary(), loadedMemory.isLegendary());
        assertEquals(memory.getFinalStory(), loadedMemory.getFinalStory(), "玩家选择应一致");
    }

    /**
     * 用例 ②：读取损坏存档文件，程序不崩溃，返回空的事件记忆集合。
     *
     * <p>向数据库文件写入非 SQLite 内容，随后读取：存档层捕获异常并降级为全新档，
     * 因此不抛出异常，事件记忆列表为空。
     */
    @Test
    void testLoadCorruptedEventFile_NoCrash() throws Exception {
        Path corruptFile = tempDir.resolve("corrupted.db");
        // 写入一段完全不是 SQLite 格式的字节
        Files.write(corruptFile, "this is definitely not a sqlite database"
                .getBytes(StandardCharsets.UTF_8));

        GameManager manager = new GameManager(
                new SqliteSaveService(new DatabaseService(corruptFile), null));

        // 读取损坏存档不应导致程序崩溃。
        // GameManager 在降级为新建档时会向 System.err 打印一行告警；这里临时捕获它，
        // 既避免污染测试控制台，又能顺带断言「确实走了降级分支」。
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        GameState state;
        try {
            System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
            state = assertDoesNotThrow(
                    (ThrowingSupplier<GameState>) manager::start, "损坏存档不应使程序崩溃");
        } finally {
            System.setErr(originalErr);
        }

        assertNotNull(state, "损坏存档后仍应有可用的游戏状态");
        assertNotNull(state.getPlayer(), "损坏存档后应降级为可玩的新档");
        assertTrue(capturedErr.toString(StandardCharsets.UTF_8).contains("存档不可用"),
                "损坏存档时应打印降级告警");

        // 事件记忆集合应为空
        assertTrue(state.getMemories().isEmpty(), "损坏存档读回的记忆列表应为空");
        assertNull(state.getActiveEvent(), "损坏存档读回的事件应为空");
    }

    /**
     * 用例 ③：空事件记忆列表的存档读写正常。
     *
     * <p>不写入任何事件/记忆，只做存盘与读回，验证不会凭空产生数据。
     */
    @Test
    void testEmptyEventList_SaveAndLoad() {
        String db = "empty-event.db";

        GameManager manager = saveManagerOn(db);
        GameState state = manager.start();
        state.setGameDay(3L);
        assertTrue(state.getMemories().isEmpty(), "初始记忆列表应为空");
        assertNull(state.getActiveEvent(), "初始事件应为空");

        manager.saveNow(); // 保存空列表

        GameManager restarted = saveManagerOn(db);
        GameState reloaded = restarted.start();

        assertEquals(3L, reloaded.getGameDay(), "游戏天数应一致");
        assertTrue(reloaded.getMemories().isEmpty(), "读回的记忆列表仍应为空");
        assertNull(reloaded.getActiveEvent(), "读回的事件仍应为空");
    }
}
