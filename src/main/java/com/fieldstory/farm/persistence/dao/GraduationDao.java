package com.fieldstory.farm.persistence.dao;

import com.fieldstory.farm.model.GraduationState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * 毕业状态数据访问对象（E 模块 P3 DAO；验收规范 §一百二十九）。
 *
 * <p>负责 {@code graduation} 表（单行表，id = 1）。概要设计 §756 曾把
 * 「GraduationState 的持久化位置」列为待定项；E 在 P3 定为一{@code graduation} 单行表：
 * 语义单一、与其它会话状态同事务写入，且不污染 {@code meta}（E 内部 KV，只放迁移标记等）。
 *
 * <p>毕业只发生一次，本表因此只承载「是否已毕业 + 首次毕业时刻」。
 */
public class GraduationDao {

    /** 单行表固定主键。 */
    private static final int SINGLETON_ID = 1;

    private final Connection connection;

    public GraduationDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection 不能为空");
    }

    /** 删除毕业行（新档 / 全量重写）。 */
    public void deleteAll() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM graduation")) {
            ps.executeUpdate();
        }
    }

    /** 写入/覆盖毕业状态。 */
    public void upsert(GraduationState state) throws SQLException {
        Objects.requireNonNull(state, "state 不能为空");
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO graduation(id, graduated, graduation_world_time, graduation_game_day)"
                        + " VALUES(?, ?, ?, ?)"
                        + " ON CONFLICT(id) DO UPDATE SET"
                        + " graduated = excluded.graduated,"
                        + " graduation_world_time = excluded.graduation_world_time,"
                        + " graduation_game_day = excluded.graduation_game_day")) {
            ps.setInt(1, SINGLETON_ID);
            ps.setInt(2, state.isGraduated() ? 1 : 0);
            ps.setLong(3, state.getGraduationWorldTime());
            ps.setLong(4, state.getGraduationGameDay());
            ps.executeUpdate();
        }
    }

    /**
     * 读取毕业状态。
     *
     * @return 毕业状态；库中无记录返回 {@code null}
     */
    public GraduationState find() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT graduated, graduation_world_time, graduation_game_day"
                        + " FROM graduation WHERE id = ?")) {
            ps.setInt(1, SINGLETON_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                GraduationState state = new GraduationState();
                state.setGraduated(rs.getInt("graduated") != 0);
                state.setGraduationWorldTime(rs.getLong("graduation_world_time"));
                state.setGraduationGameDay(rs.getLong("graduation_game_day"));
                return state;
            }
        }
    }
}
