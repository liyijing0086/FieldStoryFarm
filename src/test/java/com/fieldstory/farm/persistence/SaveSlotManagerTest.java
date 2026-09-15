package com.fieldstory.farm.persistence;

import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.service.SaveService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 存档位管理（E 模块 P2）：验证「无限存档位」的命名、发现与按位隔离。
 *
 * <p>所有数据库都落在 {@link TempDir} 的临时目录，不触碰 {@code data/}。
 */
class SaveSlotManagerTest {

    @TempDir
    Path tempDir;

    /** 以 {@code tempDir/data} 为存档目录的多存档管理器（真实 SQLite + 目录扫描）。 */
    private SaveSlotManager managerFor(Path dir) {
        Path dataDir = dir.resolve("data");
        return new SaveSlotManager(
                slot -> new SqliteSaveService(
                        new DatabaseService(dataDir.resolve(slot.databaseFile().getFileName())), null),
                () -> SaveSlotManager.discoverSlots(dataDir));
    }

    /** 存档位命名：序号从 1 起、无上限，文件互不相同。 */
    @Test
    void slotsAreUnboundedAndDistinct() {
        assertEquals(1, SaveSlot.of(1).index());
        assertEquals("存档 1", SaveSlot.of(1).displayName());
        assertEquals("存档 42", SaveSlot.of(42).displayName());
        assertEquals(Path.of("data", "farm.db"), SaveSlot.of(1).databaseFile());
        assertEquals(Path.of("data", "save-2.db"), SaveSlot.of(2).databaseFile());
        assertEquals(Path.of("data", "save-42.db"), SaveSlot.of(42).databaseFile());

        // 序号 >= 1 均合法（无限）；0 与负数非法。
        assertThrows(IllegalArgumentException.class, () -> SaveSlot.of(0));
        assertThrows(IllegalArgumentException.class, () -> SaveSlot.of(-3));
    }

    /** 文件名解析：仅识别 farm.db 与 save-N.db，忽略 SQLite 附属文件。 */
    @Test
    void fileNamesMapBackToSlots() {
        assertEquals(SaveSlot.of(1), SaveSlot.fromFileName("farm.db"));
        assertEquals(SaveSlot.of(7), SaveSlot.fromFileName("save-7.db"));
        assertNull(SaveSlot.fromFileName("save-.db"));
        assertNull(SaveSlot.fromFileName("save-0.db"));
        assertNull(SaveSlot.fromFileName("other.db"));
        assertNull(SaveSlot.fromFileName("farm.db-wal"));
        assertNull(SaveSlot.fromFileName(null));
    }

    /** 每个存档位各自一个服务与文件。 */
    @Test
    void eachSlotOwnsItsOwnServiceAndFile() {
        SaveSlotManager manager = managerFor(tempDir);

        assertSame(manager.service(SaveSlot.of(1)), manager.service(SaveSlot.of(1)),
                "同一存档位应复用同一服务");
        assertNotSame(manager.service(SaveSlot.of(1)), manager.service(SaveSlot.of(2)),
                "不同存档位应是不同服务");
    }

    /** 保存后存档位才会被扫描发现；未保存的空档不出现在列表里。 */
    @Test
    void savedSlotBecomesVisibleToDiscovery() {
        SaveSlotManager manager = managerFor(tempDir);
        assertTrue(manager.describeAll().isEmpty(), "初始没有任何存档");
        assertEquals(SaveSlot.of(1), manager.nextSlot(), "无存档时下一个空位是存档 1");

        manager.service(SaveSlot.of(1)).save(new GameState(new Player("测试员", 860), 4L));
        manager.service(SaveSlot.of(3)).save(new GameState(new Player("测试员", 100), 0L));

        assertEquals(List.of(SaveSlot.of(1), SaveSlot.of(3)), manager.existingSlots(),
                "扫描按序号升序返回已保存的存档位");
        assertEquals(2, manager.describeAll().size(), "保存了几个就发现几个");
        assertEquals(SaveSlot.of(4), manager.nextSlot(), "下一个空位是现有最大序号 + 1");
    }

    /** 摘要：空档显示「空」，有档显示天数与金币。 */
    @Test
    void describeReportsEmptyAndOccupiedSlots() {
        SaveSlotManager manager = managerFor(tempDir);

        SaveSlotInfo empty = manager.describe(SaveSlot.of(2));
        assertFalse(empty.occupied());
        assertEquals("空档", empty.describe());
        assertEquals(SaveSlot.of(2), empty.slot());

        manager.service(SaveSlot.of(2)).save(new GameState(new Player("测试员", 860), 4L));

        SaveSlotInfo occupied = manager.describe(SaveSlot.of(2));
        assertTrue(occupied.occupied());
        assertEquals(4L, occupied.gameDay());
        assertEquals(860, occupied.gold());
        assertEquals("第 5 天 · 金币 860", occupied.describe(), "显示天数从 0 起 -> 第 1 天");
        assertFalse(manager.describe(SaveSlot.of(1)).occupied(), "写存档 2 不应影响存档 1");
    }

    /** 损坏的存档位降级为空档。 */
    @Test
    void describeDegradesToEmptyWhenSaveIsUnreadable() throws Exception {
        Path broken = tempDir.resolve("broken.db");
        Files.writeString(broken, "这不是一个 SQLite 文件");
        SaveSlotManager manager = new SaveSlotManager(
                slot -> new SqliteSaveService(new DatabaseService(broken), null),
                () -> List.of(SaveSlot.of(1)));

        SaveSlotInfo info = manager.describe(SaveSlot.of(1));
        assertFalse(info.occupied(), "损坏存档应按空档处理");
    }

    /** single 模式：所有存档位共享一个服务，新档固定落在存档 1。 */
    @Test
    void singleModeSharesOneServiceAcrossSlots() {
        SaveService shared = managerFor(tempDir).service(SaveSlot.of(1));
        SaveSlotManager single = SaveSlotManager.single(shared);

        assertSame(shared, single.service(SaveSlot.of(1)));
        assertSame(shared, single.service(SaveSlot.of(3)));
        assertEquals(SaveSlot.of(1), single.nextSlot());
    }
}
