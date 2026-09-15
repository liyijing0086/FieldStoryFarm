package com.fieldstory.farm.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 作物数据访问对象（E 模块 P1/P2 DAO）。
 *
 * <p>v5 起除基础成长/浇水字段外，也持久化离线世界推进必须连续保存的运行态：
 * 施肥次数与最近施肥日、天气累计、最近补水世界时间、连续干旱与事件计数。
 * 这些字段只做状态搬运，业务规则仍由 Service 负责。
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
            String lastManualWaterGameDay,
            int fertilizerCount,
            String lastFertilizedGameDay,
            int droughtCount,
            int rainCount,
            int greenRainCount,
            String lastHydratedWorldTime,
            int droughtStreak,
            int eventCount) {

        /** 兼容 v1~v4 旧调用：新增运行态按默认值恢复。 */
        public CropRow(String cropUuid, long soilId, String cropType, String growthStage,
                       double growthProgress, String plantWorldTime, int manualWaterCount,
                       String lastManualWaterGameDay) {
            this(cropUuid, soilId, cropType, growthStage, growthProgress, plantWorldTime,
                    manualWaterCount, lastManualWaterGameDay,
                    0, "-1", 0, 0, 0, "-1", 0, 0);
        }
    }

    /** 插入一株作物行。 */
    public void insert(CropRow row) throws SQLException {
        Objects.requireNonNull(row, "row 不能为空");
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO crop(crop_uuid, soil_id, crop_type, growth_stage, growth_progress,"
                        + " plant_world_time, manual_water_count, last_manual_water_game_day,"
                        + " fertilizer_count, last_fertilized_game_day, drought_count, rain_count,"
                        + " green_rain_count, last_hydrated_world_time, drought_streak, event_count)"
                        + " VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            bindInsert(ps, row);
            ps.executeUpdate();
        }
    }

    /** 更新指定作物的全部运行态（按 crop_uuid）。 */
    public void update(CropRow row) throws SQLException {
        Objects.requireNonNull(row, "row 不能为空");
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE crop SET soil_id = ?, crop_type = ?, growth_stage = ?, growth_progress = ?,"
                        + " plant_world_time = ?, manual_water_count = ?, last_manual_water_game_day = ?,"
                        + " fertilizer_count = ?, last_fertilized_game_day = ?, drought_count = ?,"
                        + " rain_count = ?, green_rain_count = ?, last_hydrated_world_time = ?,"
                        + " drought_streak = ?, event_count = ? WHERE crop_uuid = ?")) {
            ps.setLong(1, row.soilId());
            ps.setString(2, row.cropType());
            ps.setString(3, row.growthStage());
            ps.setDouble(4, row.growthProgress());
            ps.setString(5, row.plantWorldTime());
            ps.setInt(6, row.manualWaterCount());
            ps.setString(7, row.lastManualWaterGameDay());
            ps.setInt(8, row.fertilizerCount());
            ps.setString(9, row.lastFertilizedGameDay());
            ps.setInt(10, row.droughtCount());
            ps.setInt(11, row.rainCount());
            ps.setInt(12, row.greenRainCount());
            ps.setString(13, row.lastHydratedWorldTime());
            ps.setInt(14, row.droughtStreak());
            ps.setInt(15, row.eventCount());
            ps.setString(16, row.cropUuid());
            ps.executeUpdate();
        }
    }

    public CropRow findById(String cropUuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM crop WHERE crop_uuid = ?")) {
            ps.setString(1, cropUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public CropRow findBySoilId(long soilId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM crop WHERE soil_id = ?")) {
            ps.setLong(1, soilId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

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

    public int count() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM crop");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public void deleteAll() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM crop")) {
            ps.executeUpdate();
        }
    }

    private static final String COLUMNS =
            "crop_uuid, soil_id, crop_type, growth_stage, growth_progress,"
                    + " plant_world_time, manual_water_count, last_manual_water_game_day,"
                    + " fertilizer_count, last_fertilized_game_day, drought_count, rain_count,"
                    + " green_rain_count, last_hydrated_world_time, drought_streak, event_count";

    private static void bindInsert(PreparedStatement ps, CropRow row) throws SQLException {
        ps.setString(1, row.cropUuid());
        ps.setLong(2, row.soilId());
        ps.setString(3, row.cropType());
        ps.setString(4, row.growthStage());
        ps.setDouble(5, row.growthProgress());
        ps.setString(6, row.plantWorldTime());
        ps.setInt(7, row.manualWaterCount());
        ps.setString(8, row.lastManualWaterGameDay());
        ps.setInt(9, row.fertilizerCount());
        ps.setString(10, row.lastFertilizedGameDay());
        ps.setInt(11, row.droughtCount());
        ps.setInt(12, row.rainCount());
        ps.setInt(13, row.greenRainCount());
        ps.setString(14, row.lastHydratedWorldTime());
        ps.setInt(15, row.droughtStreak());
        ps.setInt(16, row.eventCount());
    }

    private static CropRow map(ResultSet rs) throws SQLException {
        return new CropRow(
                rs.getString("crop_uuid"),
                rs.getLong("soil_id"),
                rs.getString("crop_type"),
                rs.getString("growth_stage"),
                rs.getDouble("growth_progress"),
                rs.getString("plant_world_time"),
                rs.getInt("manual_water_count"),
                rs.getString("last_manual_water_game_day"),
                rs.getInt("fertilizer_count"),
                rs.getString("last_fertilized_game_day"),
                rs.getInt("drought_count"),
                rs.getInt("rain_count"),
                rs.getInt("green_rain_count"),
                rs.getString("last_hydrated_world_time"),
                rs.getInt("drought_streak"),
                rs.getInt("event_count"));
    }
}
