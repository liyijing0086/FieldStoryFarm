package com.fieldstory.farm.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 土地数据访问对象（E 模块 P1 DAO；验收规范 §七十五 SoilDao）。
 *
 * <p>负责 {@code soil} 表：每格农场土地的坐标与状态（EMPTY/TILLED/PLANTED…）。
 * 状态以字符串保存，E 不直接依赖 A 模块枚举（与 {@code PlotState} 一致的边界策略）。
 * 作物本体在 {@code crop} 表，经 {@code soil_id} 关联。
 *
 * <p>{@code soil_id} 口径与 A 模块 {@code BasicSoil} 一致：{@code 行 × 地图列数 + 列}。
 */
public class SoilDao {

    private final Connection connection;

    public SoilDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection 不能为空");
    }

    /** 单块土地行（不含作物）。{@code plotId} 保留 E 地块标识，便于无损回读。 */
    public record SoilRow(long soilId, String plotId, int row, int column, String state) {
    }

    /** 插入一块土地行。 */
    public void insert(SoilRow row) throws SQLException {
        Objects.requireNonNull(row, "row 不能为空");
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO soil(soil_id, plot_id, row_index, col_index, state)"
                        + " VALUES(?, ?, ?, ?, ?)")) {
            ps.setLong(1, row.soilId());
            ps.setString(2, row.plotId());
            ps.setInt(3, row.row());
            ps.setInt(4, row.column());
            ps.setString(5, row.state());
            ps.executeUpdate();
        }
    }

    /** 更新土地状态。 */
    public void updateState(long soilId, String state) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE soil SET state = ? WHERE soil_id = ?")) {
            ps.setString(1, state);
            ps.setLong(2, soilId);
            ps.executeUpdate();
        }
    }

    /**
     * 按 id 查询土地行。
     *
     * @return 土地行；不存在返回 {@code null}
     */
    public SoilRow findById(long soilId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT soil_id, plot_id, row_index, col_index, state FROM soil WHERE soil_id = ?")) {
            ps.setLong(1, soilId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** 查询全部土地行（按 soil_id 升序，保证读档顺序稳定）。 */
    public List<SoilRow> findAll() throws SQLException {
        List<SoilRow> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT soil_id, plot_id, row_index, col_index, state FROM soil ORDER BY soil_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(map(rs));
            }
        }
        return rows;
    }

    /** 土地行数。 */
    public int count() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM soil");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** 删除全部土地行（crop 表经外键 ON DELETE CASCADE 一并清理）。 */
    public void deleteAll() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM soil")) {
            ps.executeUpdate();
        }
    }

    private static SoilRow map(ResultSet rs) throws SQLException {
        return new SoilRow(
                rs.getLong("soil_id"),
                rs.getString("plot_id"),
                rs.getInt("row_index"),
                rs.getInt("col_index"),
                rs.getString("state"));
    }
}
