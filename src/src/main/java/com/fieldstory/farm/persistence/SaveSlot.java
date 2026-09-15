package com.fieldstory.farm.persistence;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 存档位（E 模块 P2）：<b>无限存档位</b>。
 *
 * <p>P2 允许玩家同时拥有任意多个存档，因此「存档位」不再是固定枚举，而是由序号
 * 动态表示的值对象：保存了几个存档，菜单里就出现几个。每个存档位对应
 * <b>一个独立的 SQLite 数据库文件</b>，沿用 P1 定稿的表结构与 DAO，实现按位隔离：
 *
 * <pre>
 * 存档 1 → data/farm.db      （沿用 P1 正式存档路径，旧档自动成为「存档 1」）
 * 存档 2 → data/save-2.db
 * 存档 3 → data/save-3.db
 * ...
 * 存档 N → data/save-N.db
 * </pre>
 *
 * <p>该类只回答「存档位是什么」；「磁盘上现有哪些存档位」由
 * {@link SaveSlotManager#existingSlots()} 负责扫描发现。
 */
public final class SaveSlot implements Comparable<SaveSlot> {

    /** 最小（也是默认）存档位序号。 */
    public static final int FIRST_INDEX = 1;

    /** 存档目录：按规范 §一·七十四，数据放 {@code data/}，纳入 .gitignore。 */
    private static final String DATA_DIRECTORY = "data";

    /** 存档 1 沿用 P1 正式存档文件名。 */
    private static final String LEGACY_FILE_NAME = "farm.db";

    /** 存档 N（N≥2）文件名前缀。 */
    private static final String SLOT_FILE_PREFIX = "save-";

    /** 存档文件名后缀。 */
    private static final String SLOT_FILE_SUFFIX = ".db";

    /** 存档位序号，从 1 开始，玩家可见。 */
    private final int index;

    private SaveSlot(int index) {
        if (index < FIRST_INDEX) {
            throw new IllegalArgumentException(
                    "非法存档位序号: " + index + "（须 >= " + FIRST_INDEX + "）");
        }
        this.index = index;
    }

    /**
     * 按序号获取存档位（任意 >= 1 的序号都合法，序号无上限）。
     *
     * @param index 序号（>= 1）
     * @return 对应存档位
     * @throws IllegalArgumentException 序号小于 1
     */
    public static SaveSlot of(int index) {
        return new SaveSlot(index);
    }

    /** 默认存档位（存档 1），首次进入菜单或旧档场景使用。 */
    public static SaveSlot first() {
        return new SaveSlot(FIRST_INDEX);
    }

    /** 存档目录（相对工作目录）。 */
    public static Path directory() {
        return Paths.get(DATA_DIRECTORY);
    }

    /** 存档位序号（1 起）。 */
    public int index() {
        return index;
    }

    /** 存档位显示名，例如「存档 1」。 */
    public String displayName() {
        return "存档 " + index;
    }

    /** 该存档位的数据库文件路径（相对工作目录）。 */
    public Path databaseFile() {
        if (index == FIRST_INDEX) {
            return Paths.get(DATA_DIRECTORY, LEGACY_FILE_NAME);
        }
        return Paths.get(DATA_DIRECTORY, SLOT_FILE_PREFIX + index + SLOT_FILE_SUFFIX);
    }

    /**
     * 由数据库文件名解析存档位（用于扫描存档目录）。
     *
     * @param fileName 文件名（不含目录），例如 {@code farm.db} 或 {@code save-3.db}
     * @return 对应存档位；文件名不符合约定时返回 {@code null}
     */
    public static SaveSlot fromFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        if (LEGACY_FILE_NAME.equals(fileName)) {
            return of(FIRST_INDEX);
        }
        if (!fileName.startsWith(SLOT_FILE_PREFIX) || !fileName.endsWith(SLOT_FILE_SUFFIX)) {
            return null;
        }
        String middle = fileName.substring(
                SLOT_FILE_PREFIX.length(), fileName.length() - SLOT_FILE_SUFFIX.length());
        try {
            int parsed = Integer.parseInt(middle);
            return parsed >= FIRST_INDEX ? of(parsed) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public int compareTo(SaveSlot other) {
        return Integer.compare(index, other.index);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SaveSlot other && other.index == index;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(index);
    }

    @Override
    public String toString() {
        return displayName();
    }
}
