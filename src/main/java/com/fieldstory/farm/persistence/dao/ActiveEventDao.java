package com.fieldstory.farm.persistence.dao;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventState;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.impl.BasicEventState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * 当前随机事件数据访问对象（E 模块 P2 DAO；验收规范 §九十一）。
 *
 * <p>负责 {@code active_event} 表（单行表，id = 1）。存在这张表的原因很直接：
 * <b>游戏在事件持续期间退出，回来时事件不能凭空消失</b>——必须把事件类型、
 * 起止世界时间、神秘商人的目标作物与 payload 一起存下来。
 *
 * <p>字段与 {@link EventState} 一一对应：{@code event_type}（枚举 {@code name()}）、
 * {@code start_world_time}、{@code end_world_time}、{@code target_crop_type}、{@code payload}。
 * 枚举读回时无法识别则置 null/默认 {@link EventType#NONE}，坏数据不让读档崩溃。
 *
 * <p>D 模块只负责事件规则，落库由本 DAO 负责（验收规范 §七十五：Service 不直接写 SQL）。
 */
public class ActiveEventDao {

    /** 单行表固定主键。 */
    private static final int SINGLETON_ID = 1;

    private final Connection connection;

    public ActiveEventDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection 不能为空");
    }

    /** 写入/覆盖当前事件快照。 */
    public void upsert(EventState state) throws SQLException {
        Objects.requireNonNull(state, "state 不能为空");
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO active_event(id, event_type, start_world_time, end_world_time,"
                        + " target_crop_type, payload) VALUES(?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT(id) DO UPDATE SET"
                        + " event_type = excluded.event_type,"
                        + " start_world_time = excluded.start_world_time,"
                        + " end_world_time = excluded.end_world_time,"
                        + " target_crop_type = excluded.target_crop_type,"
                        + " payload = excluded.payload")) {
            EventType type = state.getEventType() == null ? EventType.NONE : state.getEventType();
            ps.setInt(1, SINGLETON_ID);
            ps.setString(2, type.name());
            ps.setLong(3, state.getStartWorldTime());
            ps.setLong(4, state.getEndWorldTime());
            ps.setString(5, state.getTargetCropType() == null
                    ? null : state.getTargetCropType().name());
            ps.setString(6, state.getPayload());
            ps.executeUpdate();
        }
    }

    /**
     * 读取当前事件快照。
     *
     * @return 事件状态；库中无记录返回 {@code null}
     */
    public EventState find() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT event_type, start_world_time, end_world_time, target_crop_type, payload"
                        + " FROM active_event WHERE id = ?")) {
            ps.setInt(1, SINGLETON_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                BasicEventState state = new BasicEventState(
                        parseEnum(EventType.class, rs.getString("event_type"), EventType.NONE),
                        rs.getLong("start_world_time"),
                        rs.getLong("end_world_time"));
                state.setTargetCropType(parseEnum(CropType.class, rs.getString("target_crop_type")));
                state.setPayload(rs.getString("payload"));
                return state;
            }
        }
    }

    /** 删除事件快照（无事件 / 新档）。 */
    public void deleteAll() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM active_event")) {
            ps.executeUpdate();
        }
    }

    /** 宽容解析枚举名；null/空/非法时返回 {@code fallback}。 */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name, E fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name.trim());
        } catch (IllegalArgumentException unknown) {
            return fallback;
        }
    }

    /** 宽容解析枚举名；非法返回 null。 */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name) {
        return parseEnum(type, name, null);
    }
}
