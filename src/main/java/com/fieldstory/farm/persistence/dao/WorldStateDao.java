package com.fieldstory.farm.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

/**
 * 世界状态数据访问对象（E 模块 P1 DAO；验收规范 §七十三/§七十五 WorldStateDao）。
 *
 * <p>负责 {@code world_state} 表，固定承载验收规范 §七十三 要求的五列：
 * {@code current_world_time}、{@code last_real_time}、{@code current_weather}、
 * {@code current_day_index}、{@code random_seed}。
 * 这样 P2 的离线模拟、天气与随机事件无需推翻存档结构（P1 先占位，未启用的列写 null/0）。
 *
 * <p>单人存档约定 {@code world_state} 表恒为 0 行或 1 行（id=1）。
 */
public class WorldStateDao {

    private final Connection connection;

    public WorldStateDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection 不能为空");
    }

    /**
     * 世界状态行。
     *
     * @param currentWorldTime 当前世界时间（ISO-8601 字符串，可为 null）
     * @param lastRealTime     上次真实时间（P2 离线计算用，可为 null）
     * @param currentWeather   当前天气枚举名（P1 天气接入后写入，可为 null）
     * @param currentDayIndex  当前游戏日索引
     * @param randomSeed       随机种子（可为 null）
     */
    public record WorldStateRow(
            String currentWorldTime,
            String lastRealTime,
            String currentWeather,
            long currentDayIndex,
            Long randomSeed) {
    }

    /** 插入世界状态行（id=1）。 */
    public void insert(WorldStateRow row) throws SQLException {
        Objects.requireNonNull(row, "row 不能为空");
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO world_state(id, current_world_time, last_real_time, current_weather,"
                        + " current_day_index, random_seed) VALUES(1, ?, ?, ?, ?, ?)")) {
            bind(ps, row);
            ps.executeUpdate();
        }
    }

    /** 更新世界状态行。 */
    public void update(WorldStateRow row) throws SQLException {
        Objects.requireNonNull(row, "row 不能为空");
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE world_state SET current_world_time = ?, last_real_time = ?,"
                        + " current_weather = ?, current_day_index = ?, random_seed = ? WHERE id = 1")) {
            bind(ps, row);
            ps.executeUpdate();
        }
    }

    /**
     * 读取世界状态。
     *
     * @return 世界状态行；无记录返回 {@code null}
     */
    public WorldStateRow find() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT current_world_time, last_real_time, current_weather,"
                        + " current_day_index, random_seed FROM world_state WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            long seed = rs.getLong("random_seed");
            Long randomSeed = rs.wasNull() ? null : seed;
            return new WorldStateRow(
                    rs.getString("current_world_time"),
                    rs.getString("last_real_time"),
                    rs.getString("current_weather"),
                    rs.getLong("current_day_index"),
                    randomSeed);
        }
    }

    /** 删除世界状态行。 */
    public void deleteAll() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM world_state")) {
            ps.executeUpdate();
        }
    }

    private static void bind(PreparedStatement ps, WorldStateRow row) throws SQLException {
        ps.setString(1, row.currentWorldTime());
        ps.setString(2, row.lastRealTime());
        ps.setString(3, row.currentWeather());
        ps.setLong(4, row.currentDayIndex());
        if (row.randomSeed() == null) {
            ps.setNull(5, Types.INTEGER);
        } else {
            ps.setLong(5, row.randomSeed());
        }
    }
}
