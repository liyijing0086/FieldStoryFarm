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
 * 事件日志数据访问对象（E 模块 P2 DAO；验收规范 §一百零四、DAO 归属 §一百五十一条）。
 *
 * <p>负责 {@code event_log} 表：把每次随机事件的发生追加成一条可追溯记录
 * （事件类型、起止世界时间、神秘商人目标作物、payload、所属游戏日），
 * 对应验收规范 §一百零四 的 {@code event_log} 与概要设计 §11.2「日志可解释、可追溯、可复现」。
 *
 * <p>本表是<b>追加式</b>历史记录，与 {@code active_event}（只存"当前事件"、可被覆盖）不同：
 * 它不参与存档的全量清表—重写，写入方（D/B 侧 Service）每发生一次事件就 {@link #insert} 一条。
 * 本 DAO 不含任何业务计算，事务边界由调用方控制。
 */
public class EventLogDao {

    private static final String COLUMNS =
            "id, day_index, event_type, start_world_time, end_world_time, target_crop_type, payload";

    private final Connection connection;

    public EventLogDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection 不能为空");
    }

    /**
     * 一条事件日志。
     *
     * @param id              自增主键（新记录用 0，由数据库分配）
     * @param dayIndex        事件发生的游戏日
     * @param eventType       事件类型名（{@code EventType.name()}）
     * @param startWorldTime  事件起始世界时间（游戏小时）
     * @param endWorldTime    事件结束世界时间（游戏小时）
     * @param targetCropType  神秘商人目标作物名，无则 {@code null}
     * @param payload         附加数据，无则 {@code null}
     */
    public record EventLogRow(
            long id,
            long dayIndex,
            String eventType,
            long startWorldTime,
            long endWorldTime,
            String targetCropType,
            String payload) {

        /** 新记录构造：主键交由数据库分配。 */
        public EventLogRow(long dayIndex, String eventType, long startWorldTime,
                           long endWorldTime, String targetCropType, String payload) {
            this(0L, dayIndex, eventType, startWorldTime, endWorldTime, targetCropType, payload);
        }
    }

    /**
     * 追加一条事件日志。
     *
     * @return 数据库分配的自增主键
     */
    public long insert(EventLogRow row) throws SQLException {
        Objects.requireNonNull(row, "row 不能为空");
        Objects.requireNonNull(row.eventType(), "eventType 不能为空");
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO event_log(day_index, event_type, start_world_time,"
                        + " end_world_time, target_crop_type, payload)"
                        + " VALUES(?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, row.dayIndex());
            ps.setString(2, row.eventType());
            ps.setLong(3, row.startWorldTime());
            ps.setLong(4, row.endWorldTime());
            ps.setString(5, row.targetCropType());
            ps.setString(6, row.payload());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    /** 全部日志（按 id 升序，即发生先后）。 */
    public List<EventLogRow> findAll() throws SQLException {
        List<EventLogRow> logs = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM event_log ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                logs.add(map(rs));
            }
        }
        return logs;
    }

    /** 指定游戏日的日志（按 id 升序）。 */
    public List<EventLogRow> findByDayIndex(long dayIndex) throws SQLException {
        List<EventLogRow> logs = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM event_log WHERE day_index = ? ORDER BY id")) {
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
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM event_log");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** 清空日志（仅测试或重开新档时使用；日志是追加式记录，正常存档不清空）。 */
    public void deleteAll() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM event_log")) {
            ps.executeUpdate();
        }
    }

    private static EventLogRow map(ResultSet rs) throws SQLException {
        return new EventLogRow(
                rs.getLong("id"),
                rs.getLong("day_index"),
                rs.getString("event_type"),
                rs.getLong("start_world_time"),
                rs.getLong("end_world_time"),
                rs.getString("target_crop_type"),
                rs.getString("payload"));
    }
}
