package com.fieldstory.farm.persistence.dao;

import com.fieldstory.farm.model.item.Inventory;
import com.fieldstory.farm.model.item.Item;
import com.fieldstory.farm.model.item.ItemType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * 玩家背包物品数据访问对象（E 模块 P2 DAO）。
 *
 * <p>负责 {@code player_item} 表：把 C 模块的 {@link Inventory}（收获产物、肥料等）
 * 按"物品类型 → 数量 + 单价"落库，读档时重建一个等价的 {@link Inventory}。
 *
 * <p>为什么背包单独一张表、而不是塞进玩家行：背包是<b>多行</b>结构（每种物品一行），
 * 且生命周期与 {@code player} 一致但形状不同；沿用"一种物品一行"也让 P3 的
 * 展示台/仓库统计能直接按类型聚合。
 *
 * <p>种子库存<b>不</b>在这里：它的唯一归属是 {@code player_seed} 表
 * （B 模块 §6.2 禁止第二份库存），本表只放 {@code Inventory} 里的物品。
 *
 * <p>写入为覆盖式（先清空再插入），事务边界由 {@code SqliteSaveService} 控制。
 */
public class PlayerItemDao {

    private final Connection connection;

    public PlayerItemDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection 不能为空");
    }

    /** 覆盖式保存背包快照；{@code inventory} 为 null 视为空背包。 */
    public void replaceAll(Inventory inventory) throws SQLException {
        deleteAll();
        if (inventory == null || inventory.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player_item(item_type, quantity, unit_price) VALUES(?, ?, ?)")) {
            for (Item item : inventory.listItems()) {
                if (item == null || item.getType() == null || item.getQuantity() <= 0) {
                    continue;
                }
                ps.setString(1, item.getType().name());
                ps.setInt(2, item.getQuantity());
                ps.setInt(3, item.getUnitPrice());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * 读回背包。
     *
     * <p>永远返回非 null 的新实例（无记录 = 空背包），调用方可直接使用；
     * 无法识别的物品类型会被跳过（坏数据不让读档崩溃）。
     */
    public Inventory load() throws SQLException {
        Inventory inventory = new Inventory();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT item_type, quantity, unit_price FROM player_item ORDER BY item_type");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ItemType type = parseItemType(rs.getString("item_type"));
                int quantity = rs.getInt("quantity");
                if (type == null || quantity <= 0) {
                    continue;
                }
                inventory.addItem(new Item(type, quantity, rs.getInt("unit_price")));
            }
        }
        return inventory;
    }

    /** 清空背包快照。 */
    public void deleteAll() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_item")) {
            ps.executeUpdate();
        }
    }

    private static ItemType parseItemType(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return ItemType.valueOf(name.trim());
        } catch (IllegalArgumentException unknown) {
            System.err.println("[PlayerItemDao] 跳过无法识别的物品类型: " + name);
            return null;
        }
    }
}
