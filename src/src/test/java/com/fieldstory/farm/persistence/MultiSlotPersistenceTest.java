package com.fieldstory.farm.persistence;

import com.fieldstory.farm.manager.GameManager;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多存档持久化（E 模块 P2）：<b>任意多个存档位之间互不干扰</b>。
 *
 * <p>每个存档位一个 {@code .db} 文件，进程重启（新建 {@link GameManager}）后按
 * 目录扫描重新发现，各档进度彼此隔离。存档数量不再限于 3 个。
 */
class MultiSlotPersistenceTest {

    @TempDir
    Path tempDir;

    /** 以 {@code dir/data} 为存档目录的多存档管理器（真实 SQLite + 目录扫描）。 */
    private GameManager managerOn(Path dir) {
        Path dataDir = dir.resolve("data");
        return new GameManager(new SaveSlotManager(
                slot -> new SqliteSaveService(
                        new DatabaseService(dataDir.resolve(slot.databaseFile().getFileName())), null),
                () -> SaveSlotManager.discoverSlots(dataDir)));
    }

    /** 多个存档位的进度在重启后都各自保留。 */
    @Test
    void manySlotsKeepIndependentProgressAcrossRestart() {
        // ---------- 第一次「运行」写入三个互不相同的存档位 ----------
        GameManager session = managerOn(tempDir);
        writeProgress(session, SaveSlot.of(1), 500, 0L, 0);
        writeProgress(session, SaveSlot.of(2), 1234, 7L, 3);
        writeProgress(session, SaveSlot.of(5), 880, 2L, 1);

        // ---------- 重启 ----------
        GameManager restarted = managerOn(tempDir);

        assertEquals(3, restarted.allSlotInfos().size(), "重启后应发现 3 个存档");
        for (int index : new int[] {1, 2, 5}) {
            assertTrue(restarted.hasSavedGame(SaveSlot.of(index)),
                    "存档 " + index + " 应仍然存在");
        }
        assertProgress(restarted.start(SaveSlot.of(1)), 500, 0L, 0);
        assertProgress(restarted.start(SaveSlot.of(2)), 1234, 7L, 3);
        assertProgress(restarted.start(SaveSlot.of(5)), 880, 2L, 1);

        assertEquals("第 8 天 · 金币 1234", restarted.slotInfo(SaveSlot.of(2)).describe());
        assertEquals(SaveSlot.of(6), restarted.nextSlot(), "新档取现有最大序号 + 1");
    }

    /** 只保存过的存档位才是「有档」，其它序号视为不存在（无限扩展）。 */
    @Test
    void onlySavedSlotsAreVisible() {
        GameManager session = managerOn(tempDir);
        writeProgress(session, SaveSlot.of(3), 640, 1L, 2);

        GameManager restarted = managerOn(tempDir);
        assertEquals(1, restarted.allSlotInfos().size(), "只应发现存档 3");
        assertFalse(restarted.hasSavedGame(SaveSlot.of(1)));
        assertFalse(restarted.hasSavedGame(SaveSlot.of(2)));
        assertTrue(restarted.hasSavedGame(SaveSlot.of(3)));
        assertEquals(SaveSlot.of(4), restarted.nextSlot());
    }

    /** 切换存档位会加载对应存档的状态，互不串档。 */
    @Test
    void switchingSlotLoadsThatSlotState() {
        GameManager session = managerOn(tempDir);
        writeProgress(session, SaveSlot.of(1), 111, 1L, 1);
        writeProgress(session, SaveSlot.of(2), 222, 2L, 2);

        GameManager restarted = managerOn(tempDir);
        assertEquals(111, restarted.start(SaveSlot.of(1)).getPlayer().getGold());
        assertEquals(222, restarted.start(SaveSlot.of(2)).getPlayer().getGold(),
                "存档 2 不应覆盖存档 1 的进度");
        assertEquals(SaveSlot.of(2), restarted.currentSlot());
    }

    /** 在某档保存不会触碰其它档。 */
    @Test
    void savingInOneSlotDoesNotTouchOtherSlots() {
        GameManager session = managerOn(tempDir);
        writeProgress(session, SaveSlot.of(1), 777, 3L, 3);

        GameManager second = managerOn(tempDir);
        second.startNewGame(SaveSlot.of(2));
        second.currentState().getPlayer().setGold(999);
        second.currentState().setGameDay(9L);
        second.saveNow();

        GameManager restarted = managerOn(tempDir);
        assertProgress(restarted.start(SaveSlot.of(1)), 777, 3L, 3);
        assertProgress(restarted.start(SaveSlot.of(2)), 999, 9L, 0);
    }

    /** 在指定存档位写入指定进度。 */
    private void writeProgress(GameManager manager, SaveSlot slot,
                               int gold, long gameDay, int seeds) {
        GameState state = manager.startNewGame(slot);
        state.getPlayer().setGold(gold);
        state.setGameDay(gameDay);
        state.getPlayer().getSeedInventory().put(CropType.WHEAT, seeds);
        manager.saveNow();
    }

    /** 断言存档状态中的金币 / 天数 / 种子数量。 */
    private void assertProgress(GameState state, int gold, long gameDay, int seeds) {
        assertEquals(gold, state.getPlayer().getGold(), "金币不匹配");
        assertEquals(gameDay, state.getGameDay(), "游戏天数不匹配");
        assertEquals(seeds, state.getPlayer().getSeedInventory().get(CropType.WHEAT),
                "种子数量不匹配");
    }
}
