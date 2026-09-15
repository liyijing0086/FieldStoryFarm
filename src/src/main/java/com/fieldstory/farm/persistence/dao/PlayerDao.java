package com.fieldstory.farm.persistence.dao;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 玩家数据访问对象（E 模块 P1 DAO；验收规范 §七十五 PlayerDao）。
 *
 * <p>负责 {@code player}（姓名/金币）、{@code player_seed}（唯一种子库存，B §6.2）与
 * {@code unlocked}（玩家已解锁内容）三张表的增删改查。
 * <b>不承载任何业务规则</b>：金币增减、种子买卖由 {@code EconomyService} 完成，
 * 本类只做持久化映射（脚手架 §3.1 DAO 职责）。
 *
 * <p>单人存档约定：{@code player} 表恒为 0 行（新档未落盘）或 1 行（id=1）。
 * 连接由外部注入，事务边界由调用方（{@code SqliteSaveService}）控制。
 */
public class PlayerDao {

    private final Connection connection;

    public PlayerDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection 不能为空");
    }

    // ------------------------------------------------------------------
    // player：姓名与金币
    // ------------------------------------------------------------------

    /** 插入玩家行（单人存档 id 恒为 1）。 */
    public void insert(Player player) throws SQLException {
        Objects.requireNonNull(player, "player 不能为空");
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player(id, name, gold) VALUES(1, ?, ?)")) {
            ps.setString(1, player.getName());
            ps.setInt(2, player.getGold());
            ps.executeUpdate();
        }
    }

    /** 更新玩家行（id=1）。 */
    public void update(Player player) throws SQLException {
        Objects.requireNonNull(player, "player 不能为空");
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE player SET name = ?, gold = ? WHERE id = 1")) {
            ps.setString(1, player.getName());
            ps.setInt(2, player.getGold());
            ps.executeUpdate();
        }
    }

    /**
     * 读取玩家。
     *
     * @return 玩家；库中无玩家行时返回 {@code null}
     */
    public Player find() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name, gold FROM player WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return new Player(rs.getString("name"), rs.getInt("gold"));
        }
    }

    /** 是否存在玩家行（判定“数据库是否已有正式玩家数据”，供 JSON 迁移判断使用）。 */
    public boolean exists() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM player WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    /** 删除玩家行。 */
    public void deleteAll() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player")) {
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // player_seed：唯一种子库存（枚举名 → 数量）
    // ------------------------------------------------------------------

    /** 全量替换种子库存（先清空再写入；应处于同一事务）。 */
    public void replaceSeeds(Map<CropType, Integer> inventory) throws SQLException {
        deleteAllSeeds();
        if (inventory == null || inventory.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player_seed(crop_type, quantity) VALUES(?, ?)")) {
            for (CropType type : CropType.values()) {
                Integer quantity = inventory.get(type);
                if (quantity == null) {
                    continue;
                }
                ps.setString(1, type.name());
                ps.setInt(2, Math.max(0, quantity));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** 读取种子库存；未记录某作物时补 0（保持 D15「空库存=全 0 键」语义）。 */
    public Map<CropType, Integer> findSeeds() throws SQLException {
        Map<CropType, Integer> inventory = new EnumMap<>(CropType.class);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT crop_type, quantity FROM player_seed");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CropType type = parseCropType(rs.getString("crop_type"));
                if (type != null) {
                    inventory.put(type, rs.getInt("quantity"));
                }
            }
        }
        for (CropType type : CropType.values()) {
            inventory.putIfAbsent(type, 0);
        }
        return inventory;
    }

    /** 删除全部种子库存行。 */
    public void deleteAllSeeds() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_seed")) {
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // unlocked：玩家已解锁内容
    // ------------------------------------------------------------------

    /** 全量替换已解锁内容。 */
    public void replaceUnlocked(Collection<String> keys) throws SQLException {
        deleteAllUnlocked();
        if (keys == null || keys.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO unlocked(unlocked_key) VALUES(?)")) {
            for (String key : keys) {
                if (key == null) {
                    continue;
                }
                ps.setString(1, key);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** 读取已解锁内容（按写入顺序去重）。 */
    public Set<String> findUnlocked() throws SQLException {
        Set<String> keys = new LinkedHashSet<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT unlocked_key FROM unlocked ORDER BY unlocked_key");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                keys.add(rs.getString(1));
            }
        }
        return keys;
    }

    /** 删除全部已解锁行。 */
    public void deleteAllUnlocked() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM unlocked")) {
            ps.executeUpdate();
        }
    }

    /** 枚举名 → {@link CropType}；未知名返回 null（前向兼容：旧程序跳过新作物）。 */
    private static CropType parseCropType(String name) {
        try {
            return name == null ? null : CropType.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
