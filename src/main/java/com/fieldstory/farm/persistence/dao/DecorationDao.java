package com.fieldstory.farm.persistence.dao;

import com.fieldstory.farm.model.DecorationState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 装饰数据访问对象（E 模块 P1 DAO；验收规范 §七十五 DecorationDao）。
 *
 * <p>负责 {@code decoration} 表：配置放置的装饰类型与坐标（P1 共 14 种装饰，B 模块提供）。
 * 本 DAO 只做持久化，不含装饰 Buff/成长倍率等业务规则（那些归 B 侧 Service）。
 * 插入时若不指定 id，由 SQLite 自增主键分配并回填到 {@link DecorationState#setId(long)}。
 */
public class DecorationDao {

    private final Connection connection;

    public DecorationDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection 不能为空");
    }

    /** 插入一条装饰；未指定 id 时由数据库分配并回填。 */
    public void insert(DecorationState decoration) throws SQLException {
        Objects.requireNonNull(decoration, "decoration 不能为空");
        boolean withExplicitId = decoration.getId() > 0;
        String sql = withExplicitId
                ? "INSERT INTO decoration(id, decoration_type, row_index, col_index) VALUES(?, ?, ?, ?)"
                : "INSERT INTO decoration(decoration_type, row_index, col_index) VALUES(?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (withExplicitId) {
                ps.setLong(1, decoration.getId());
                ps.setString(2, decoration.getDecorationType());
                ps.setInt(3, decoration.getRow());
                ps.setInt(4, decoration.getColumn());
            } else {
                ps.setString(1, decoration.getDecorationType());
                ps.setInt(2, decoration.getRow());
                ps.setInt(3, decoration.getColumn());
            }
            ps.executeUpdate();
            if (!withExplicitId) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        decoration.setId(keys.getLong(1));
                    }
                }
            }
        }
    }

    /** 更新指定装饰的坐标与类型（按 id）。 */
    public void update(DecorationState decoration) throws SQLException {
        Objects.requireNonNull(decoration, "decoration 不能为空");
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE decoration SET decoration_type = ?, row_index = ?, col_index = ? WHERE id = ?")) {
            ps.setString(1, decoration.getDecorationType());
            ps.setInt(2, decoration.getRow());
            ps.setInt(3, decoration.getColumn());
            ps.setLong(4, decoration.getId());
            ps.executeUpdate();
        }
    }

    /**
     * 按 id 查询装饰。
     *
     * @return 装饰；不存在返回 {@code null}
     */
    public DecorationState findById(long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, decoration_type, row_index, col_index FROM decoration WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** 查询全部装饰（按 id 升序）。 */
    public List<DecorationState> findAll() throws SQLException {
        List<DecorationState> decorations = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, decoration_type, row_index, col_index FROM decoration ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                decorations.add(map(rs));
            }
        }
        return decorations;
    }

    /** 装饰条数。 */
    public int count() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM decoration");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** 删除全部装饰。 */
    public void deleteAll() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM decoration")) {
            ps.executeUpdate();
        }
    }

    private static DecorationState map(ResultSet rs) throws SQLException {
        DecorationState decoration = new DecorationState(
                rs.getString("decoration_type"),
                rs.getInt("row_index"),
                rs.getInt("col_index"));
        decoration.setId(rs.getLong("id"));
        return decoration;
    }
}
