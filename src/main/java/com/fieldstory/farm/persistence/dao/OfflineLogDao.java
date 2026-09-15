package com.fieldstory.farm.persistence.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 离线日志数据访问对象（E 模块 P2 DAO；验收规范 §一百零四/§一百零五、DAO 归属 §一百五十一条）。
 *
 * <p>负责 {@code offline_log} 表：按游戏日记录「离开期间农场发生了什么」，
 * 对应验收规范 §一百零四 的 {@code offline_log} 与 §一百零五 的离线日志 UI（按游戏日分组展示）。
 *
 * <p>本表是<b>追加式</b>历史记录，不参与存档的全量清表—重写；写入方（B 侧离线模拟）
 * 每模拟完一个游戏日就 {@link #insert} 一条摘要。本 DAO 不含任何业务计算。
 */
public class OfflineLogDao {

    private static final String COLUMNS = "id, day_index, summary";

    private final Connection connection;

    public OfflineLogDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection 不能为空");
    }

    /**
     * 一条离线日志。
     *
     * @param id       自增主键（新记录用 0，由数据库分配）
     * @param dayIndex 该条日志对应的游戏日
     * @param summary  该游戏日离线期间农场发生的变化的文字摘要
     */
    public record OfflineLogRow(long id, long dayIndex, String summary) {

        /** 新记录构造：主键交由数据库分配。 */
        public OfflineLogRow(long dayIndex, String summary) {
            this(0L, dayIndex, summary);
        }
    }

    /**
     * 追加一条离线日志。
     *
     * @return 数据库分配的自增主键
     */
    public long insert(OfflineLogRow row) throws SQLException {
        Objects.requireNonNull(row, "row 不能为空");
        Objects.requireNonNull(row.summary(), "summary 不能为空");
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO offline_log(day_index, summary) VALUES(?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, row.dayIndex());
            ps.setString(2, row.summary());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    /** 全部日志（按游戏日、再按 id 升序，便于按日分组）。 */
    public List<OfflineLogRow> findAll() throws SQLException {
        List<OfflineLogRow> logs = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM offline_log ORDER BY day_index, id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                logs.add(map(rs));
            }
        }
        return logs;
    }

    /** 指定游戏日的日志（按 id 升序）。 */
    public List<OfflineLogRow> findByDayIndex(long dayIndex) throws SQLException {
        List<OfflineLogRow> logs = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM offline_log WHERE day_index = ? ORDER BY id")) {
            ps.setLong(1, dayIndex);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(map(rs));
                }
            }
        }
        return logs;
    }

    /** 日志条数。 */
    public int count() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM offline_log");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** 清空日志（仅测试或重开新档时使用；日志是追加式记录，正常存档不清空）。 */
    public void deleteAll() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM offline_log")) {
            ps.executeUpdate();
        }
    }

    private static OfflineLogRow map(ResultSet rs) throws SQLException {
        return new OfflineLogRow(
                rs.getLong("id"),
                rs.getLong("day_index"),
                rs.getString("summary"));
    }
}
