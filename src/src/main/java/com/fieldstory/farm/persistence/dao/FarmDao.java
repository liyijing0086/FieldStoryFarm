package com.fieldstory.farm.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * 农场元数据访问对象（E 模块 P1 DAO；验收规范 §七十五 FarmDao）。
 *
 * <p>负责 {@code farm} 表：记录地图尺寸（12×12 = 行/列）。土地格与作物状态由
 * {@link SoilDao}/{@link CropDao} 保存；本表只存“农场有多大”，P2/P3 土地扩张时用于校验。
 * 单人存档约定 {@code farm} 表恒为 0 行或 1 行（id=1）。
 */
public class FarmDao {

    private final Connection connection;

    public FarmDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection 不能为空");
    }

    /** 插入农场元数据行（id=1）。 */
    public void insert(int mapRows, int mapCols) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO farm(id, map_rows, map_cols) VALUES(1, ?, ?)")) {
            ps.setInt(1, mapRows);
            ps.setInt(2, mapCols);
            ps.executeUpdate();
        }
    }

    /** 更新农场元数据行。 */
    public void update(int mapRows, int mapCols) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE farm SET map_rows = ?, map_cols = ? WHERE id = 1")) {
            ps.setInt(1, mapRows);
            ps.setInt(2, mapCols);
            ps.executeUpdate();
        }
    }

    /**
     * 读取地图尺寸。
     *
     * @return {@code [行, 列]}；无记录时返回 {@code null}
     */
    public int[] findMapSize() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT map_rows, map_cols FROM farm WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return new int[]{rs.getInt("map_rows"), rs.getInt("map_cols")};
        }
    }

    /** 删除农场元数据行。 */
    public void deleteAll() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM farm")) {
            ps.executeUpdate();
        }
    }
}
