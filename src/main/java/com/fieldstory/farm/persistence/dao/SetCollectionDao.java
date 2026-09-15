package com.fieldstory.farm.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 套装收集数据访问对象（E 模块 P3 DAO；验收规范 §一百一十八、§一百五十一条）。
 *
 * <p>负责 {@code set_collection} 表。必须分开保存两个独立状态（验收规范 §一百一十八）：
 * <ul>
 *   <li>{@code collected}：曾经完整完成过（永久，FarmScore 保留套装分）；</li>
 *   <li>{@code active}：当前全部成员仍放置（决定套装 Buff 是否生效）。</li>
 * </ul>
 *
 * <p>套装成员判定与 Buff 计算属 B 模块 {@code SetService}；本 DAO 只做持久化读写。
 */
public class SetCollectionDao {

    private final Connection connection;

    public SetCollectionDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection 不能为空");
    }

    /** 套装行快照。 */
    public record SetRow(String setId, boolean collected, boolean active) {
    }

    /** 清空套装表（全量重写前调用）。 */
    public void deleteAll() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM set_collection")) {
            ps.executeUpdate();
        }
    }

    /** 写入/覆盖一套套装的收集与激活状态。 */
    public void upsert(String setId, boolean collected, boolean active) throws SQLException {
        if (setId == null || setId.isBlank()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO set_collection(set_id, collected, active) VALUES(?, ?, ?)"
                        + " ON CONFLICT(set_id) DO UPDATE SET"
                        + " collected = excluded.collected, active = excluded.active")) {
            ps.setString(1, setId.trim());
            ps.setInt(2, collected ? 1 : 0);
            ps.setInt(3, active ? 1 : 0);
            ps.executeUpdate();
        }
    }

    /** 全部套装行（按 id 升序）。 */
    public List<SetRow> findAll() throws SQLException {
        List<SetRow> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_id, collected, active FROM set_collection ORDER BY set_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new SetRow(rs.getString("set_id"),
                        rs.getInt("collected") != 0,
                        rs.getInt("active") != 0));
            }
        }
        return rows;
    }
}
