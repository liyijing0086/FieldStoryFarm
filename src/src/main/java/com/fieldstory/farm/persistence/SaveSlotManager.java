package com.fieldstory.farm.persistence;

import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.service.SaveService;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 存档位管理（E 模块 P2）：把「磁盘上有哪些存档」与 {@link SaveService} 组装起来。
 *
 * <p>职责：
 * <ul>
 *   <li><b>按位取服务</b>：每个存档位各自一个 {@link SaveService}（P1 的
 *       {@link SqliteSaveService}），惰性创建并缓存。</li>
 *   <li><b>发现存档</b>：扫描存档目录 {@link SaveSlot#directory()}，找出所有符合
 *       命名约定的存档位（{@code farm.db}、{@code save-N.db}），因此存档数量
 *       不受限制——保存了几个就发现几个。</li>
 *   <li><b>下一个空位</b>：{@link #nextSlot()} 返回现有最大序号 + 1，用于「新建存档」。</li>
 * </ul>
 *
 * <p>两种装配方式：
 * <ul>
 *   <li>{@link #defaultManager()}：真实 SQLite 多存档，存档 1 兼容 P1 的
 *       {@code data/save.json} 迁移。</li>
 *   <li>{@link #single(SaveService)}：共享同一个 {@link SaveService}（测试/单档场景），
 *       此时新档固定落在存档 1。</li>
 * </ul>
 */
public final class SaveSlotManager {

    /** 存档摘要中的时间格式。 */
    private static final DateTimeFormatter SAVED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 存档位 → 服务 的工厂。 */
    private final Function<SaveSlot, SaveService> factory;

    /** 存档位发现策略：返回磁盘上当前存在的存档位。 */
    private final Supplier<List<SaveSlot>> slotDiscovery;

    /** 仅共享一个 SaveService（single 模式）时为 true。 */
    private final boolean sharedService;

    /** 已创建的存档位服务缓存。 */
    private final Map<SaveSlot, SaveService> services = new HashMap<>();

    /**
     * 共享单档模式：所有存档位都复用同一个服务，发现结果固定为「存档 1」。
     *
     * @param factory 存档位 → 服务 的工厂，不能为空
     */
    public SaveSlotManager(Function<SaveSlot, SaveService> factory) {
        this(factory, () -> List.of(SaveSlot.first()), true);
    }

    /**
     * 通用模式：由 {@code slotDiscovery} 决定磁盘上存在哪些存档位。
     *
     * @param factory       存档位 → 服务 的工厂，不能为空
     * @param slotDiscovery 存档位发现策略，不能为空
     */
    public SaveSlotManager(Function<SaveSlot, SaveService> factory,
                           Supplier<List<SaveSlot>> slotDiscovery) {
        this(factory, slotDiscovery, false);
    }

    private SaveSlotManager(Function<SaveSlot, SaveService> factory,
                            Supplier<List<SaveSlot>> slotDiscovery,
                            boolean sharedService) {
        this.factory = Objects.requireNonNull(factory, "factory 不能为空");
        this.slotDiscovery = Objects.requireNonNull(slotDiscovery, "slotDiscovery 不能为空");
        this.sharedService = sharedService;
    }

    /**
     * 生产环境默认：真实 SQLite 无限存档，存档 1 兼容 P1 {@code data/save.json} 迁移。
     */
    public static SaveSlotManager defaultManager() {
        Path legacyJson = Path.of(JsonSaveService.DEFAULT_SAVE_FILE);
        Path directory = SaveSlot.directory();
        return new SaveSlotManager(
                slot -> new SqliteSaveService(
                        new DatabaseService(slot.databaseFile()),
                        slot.index() == SaveSlot.FIRST_INDEX ? legacyJson : null),
                () -> discoverSlots(directory));
    }

    /**
     * 共享单档：所有存档位复用同一服务（测试/单档场景）。
     */
    public static SaveSlotManager single(SaveService saveService) {
        Objects.requireNonNull(saveService, "saveService 不能为空");
        return new SaveSlotManager(slot -> saveService);
    }

    /** 取某个存档位的服务（惰性创建并缓存）。 */
    public SaveService service(SaveSlot slot) {
        Objects.requireNonNull(slot, "slot 不能为空");
        synchronized (services) {
            return services.computeIfAbsent(slot, factory);
        }
    }

    /** 该存档位是否已有存档。 */
    public boolean hasSave(SaveSlot slot) {
        return service(slot).hasSave();
    }

    /**
     * 读取存档位摘要（序号 / N 天 / 金币 / 存档时间）。
     *
     * <p>读取失败（损坏、版本不符）时降级为「空档」，仅向 {@code System.err} 打印一行告警，
     * 不影响菜单其余存档位的展示。
     */
    public SaveSlotInfo describe(SaveSlot slot) {
        SaveService saveService = service(slot);
        try {
            if (!saveService.hasSave()) {
                return SaveSlotInfo.empty(slot);
            }
            GameState state = saveService.load();
            if (state == null) {
                return SaveSlotInfo.empty(slot);
            }
            return SaveSlotInfo.of(slot, state, lastModified(slot));
        } catch (RuntimeException e) {
            System.err.println("[SaveSlotManager] 存档 " + slot.displayName()
                    + " 读取失败，按空档处理: " + e.getMessage());
            return SaveSlotInfo.empty(slot);
        }
    }

    /** 磁盘上当前存在的所有存档位（按序号升序，无则返回空列表）。 */
    public List<SaveSlot> existingSlots() {
        List<SaveSlot> slots = new ArrayList<>();
        for (SaveSlot slot : slotDiscovery.get()) {
            if (slot != null && !slots.contains(slot)) {
                slots.add(slot);
            }
        }
        slots.sort(Comparator.naturalOrder());
        return slots;
    }

    /** 磁盘上当前存在的所有存档位摘要（列表顺序即显示顺序）。 */
    public List<SaveSlotInfo> describeAll() {
        List<SaveSlotInfo> infos = new ArrayList<>();
        for (SaveSlot slot : existingSlots()) {
            infos.add(describe(slot));
        }
        return infos;
    }

    /**
     * 下一个可用的新存档位：现有最大序号 + 1（无存档时为存档 1）。
     *
     * <p>single 模式固定返回存档 1。
     */
    public SaveSlot nextSlot() {
        if (sharedService) {
            return SaveSlot.first();
        }
        int max = 0;
        for (SaveSlot slot : existingSlots()) {
            max = Math.max(max, slot.index());
        }
        return SaveSlot.of(max + 1);
    }

    /** 存档文件最后修改时间（格式化为 {@code yyyy-MM-dd HH:mm}）；不存在则返回 null。 */
    private static String lastModified(SaveSlot slot) {
        try {
            Path file = slot.databaseFile();
            if (!Files.isRegularFile(file)) {
                return null;
            }
            Instant instant = Files.getLastModifiedTime(file).toInstant();
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(SAVED_AT_FORMAT);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 扫描目录，返回其中符合命名约定的存档位（按序号升序）。
     *
     * <p>识别 {@code farm.db}（存档 1）与 {@code save-N.db}（存档 N）；SQLite 的
     * {@code -wal}/{@code -shm} 附属文件因后缀不符而自动忽略。
     *
     * @param directory 存档目录（可能不存在）
     * @return 发现的存档位；目录不存在或读取失败时返回空列表
     */
    public static List<SaveSlot> discoverSlots(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        List<SaveSlot> slots = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                SaveSlot slot = SaveSlot.fromFileName(file.getFileName().toString());
                if (slot != null && !slots.contains(slot)) {
                    slots.add(slot);
                }
            }
        } catch (IOException e) {
            System.err.println("[SaveSlotManager] 扫描存档目录失败: "
                    + directory + "（" + e.getMessage() + "）");
            return List.of();
        }
        slots.sort(Comparator.naturalOrder());
        return slots;
    }
}
