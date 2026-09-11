package com.fieldstory.farm.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 作物数据访问对象（E 模块 P1 DAO；验收规范 §七十五 CropDao）。
 *
 * <p>负责 {@code crop} 表：每株作物的类型、成长阶段、成长进度、播种世界时间与浇水计数，
 * 经 {@code soil_id} 外键关联到 {@code soil}（删除土地时级联删除作物）。
 *
 * <p>字段口径对应 {@code PlotState} 的作物部分（验收规范 §四十一）：成长进度内部 0~100，
 * 播种/浇水时间以字符串保存（时间类型由 D/A 模块约定，E 不做换算）。
 */
public class CropDao {

    private final Connection connection;

    public CropDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection 不能为空");
    }

    /** 单株作物行。 */
    public record CropRow(
            String cropUuid,
            long soilId,
            String cropType,
            String growthStage,
            double growthProgress,
            String plantWorldTime,
            int manualWaterCount,
            String lastManualWaterGameDay) {
    }

    /** 插入一株作物行。 */
    public void insert(CropRow row) throws SQLException {
        Objects.requireNonNull(row, "row 不能为空");
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO crop(crop_uuid, soil_id, crop_type, growth_stage, growth_progress,"
                        + " plant_world_time, manual_water_count, last_manual_water_game_day)"
                        + " VALUES(?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, row.cropUuid());
            ps.setLong(2, row.soilId());
            ps.setString(3, row.cropType());
            ps.setString(4, row.growthStage());
            ps.setDouble(5, row.growthProgress());
            ps.setString(6, row.plantWorldTime());
            ps.setInt(7, row.manualWaterCount());
            ps.setString(8, row.lastManualWaterGameDay());
            ps.executeUpdate();
        }
    }

    /** 更新指定作物的成长数据（按 crop_uuid）。 */
    public void update(CropRow row) throws SQLException {
        Objects.requireNonNull(row, "row 不能为空");
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE crop SET soil_id = ?, crop_type = ?, growth_stage = ?, growth_progress = ?,"
                        + " plant_world_time = ?, manual_water_count = ?, last_manual_water_game_day = ?"
                        + " WHERE crop_uuid = ?")) {
            ps.setLong(1, row.soilId());
            ps.setString(2, row.cropType());
            ps.setString(3, row.growthStage());
            ps.setDouble(4, row.growthProgress());
            ps.setString(5, row.plantWorldTime());
            ps.setInt(6, row.manualWaterCount());
            ps.setString(7, row.lastManualWaterGameDay());
            ps.setString(8, row.cropUuid());
            ps.executeUpdate();
        }
    }

    /**
     * 按作物 id 查询。
     *
     * @return 作物行；不存在返回 {@code null}
     */
    public CropRow findById(String cropUuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM crop WHERE crop_uuid = ?")) {
            ps.setString(1, cropUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * 按土地 id 查询该格作物。
     *
     * @return 作物行；该格无作物返回 {@code null}
     */
    public CropRow findBySoilId(long soilId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM crop WHERE soil_id = ?")) {
            ps.setLong(1, soilId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** 查询全部作物行（按 soil_id 升序）。 */
    public List<CropRow> findAll() throws SQLException {
        List<CropRow> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM crop ORDER BY soil_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(map(rs));
            }
        }
        return rows;
    }

    /** 作物行数。 */
    public int count() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM crop");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** 删除全部作物行。 */
    public void deleteAll() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM crop")) {
            ps.executeUpdate();
        }
    }

    private static final String COLUMNS =
            "crop_uuid, soil_id, crop_type, growth_stage, growth_progress,"
                    + " plant_world_time, manual_water_count, last_manual_water_game_day";

    private static CropRow map(ResultSet rs) throws SQLException {
        return new CropRow(
                rs.getString("crop_uuid"),
                rs.getLong("soil_id"),
                rs.getString("crop_type"),
                rs.getString("growth_stage"),
                rs.getDouble("growth_progress"),
                rs.getString("plant_world_time"),
                rs.getInt("manual_water_count"),
                rs.getString("last_manual_water_game_day"));
    }
}
