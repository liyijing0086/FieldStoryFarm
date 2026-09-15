package com.fieldstory.farm.persistence.dao;

import com.fieldstory.farm.model.CollectionStatus;
import com.fieldstory.farm.model.CropQualityKey;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Quality;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 收集图鉴数据访问对象（E 模块 P3 DAO；验收规范 §一百一十~§一百一十八、§一百五十一）。
 *
 * <p>负责三张图鉴表：
 * <ul>
 *   <li>{@code crop_collection}（{@code crop_type} + {@code quality} 联合主键，{@code status} 三态）；</li>
 *   <li>{@code decoration_collection}（{@code decoration_type} 主键，存在即已收集）；</li>
 *   <li>{@code legendary_collection}（{@code crop_type} 主键，存在即已获得该传说）。</li>
 * </ul>
 *
 * <p>三张表都是<b>永久收集记录</b>，随会话状态全量覆盖写入（由 {@code SqliteSaveService} 清表—重写）。
 * 枚举以 {@code name()} 保存，读回时无法识别则跳过该行，坏数据不让读档崩溃。
 */
public class CollectionDao {

    private final Connection connection;

    public CollectionDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection 不能为空");
    }

    /** 清空三张图鉴表（全量重写前调用）。 */
    public void deleteAll() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM crop_collection")) {
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM decoration_collection")) {
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM legendary_collection")) {
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // 作物图鉴
    // ------------------------------------------------------------------

    /** 写入/覆盖一条作物图鉴项。 */
    public void upsertCrop(CropQualityKey key, CollectionStatus status) throws SQLException {
        Objects.requireNonNull(key, "key 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO crop_collection(crop_type, quality, status) VALUES(?, ?, ?)"
                        + " ON CONFLICT(crop_type, quality) DO UPDATE SET status = excluded.status")) {
            ps.setString(1, key.cropType().name());
            ps.setString(2, key.quality().name());
            ps.setString(3, status.name());
            ps.executeUpdate();
        }
    }

    /** 全部作物图鉴项（保持插入顺序稳定）。 */
    public Map<CropQualityKey, CollectionStatus> findAllCrops() throws SQLException {
        Map<CropQualityKey, CollectionStatus> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT crop_type, quality, status FROM crop_collection ORDER BY crop_type, quality");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CropType cropType = parseEnum(CropType.class, rs.getString("crop_type"));
                Quality quality = parseEnum(Quality.class, rs.getString("quality"));
                CollectionStatus status = parseEnum(CollectionStatus.class, rs.getString("status"));
                if (cropType == null || quality == null || status == null) {
                    continue;
                }
                result.put(new CropQualityKey(cropType, quality), status);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 装饰图鉴
    // ------------------------------------------------------------------

    /** 写入一条装饰图鉴项（存在即已收集）。 */
    public void upsertDecoration(String decorationType) throws SQLException {
        if (decorationType == null || decorationType.isBlank()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO decoration_collection(decoration_type) VALUES(?)"
                        + " ON CONFLICT(decoration_type) DO NOTHING")) {
            ps.setString(1, decorationType.trim());
            ps.executeUpdate();
        }
    }

    /** 全部已收集装饰类型。 */
    public Set<String> findAllDecorations() throws SQLException {
        Set<String> result = new LinkedHashSet<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT decoration_type FROM decoration_collection ORDER BY decoration_type");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("decoration_type"));
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 传说图鉴
    // ------------------------------------------------------------------

    /** 写入一条传说图鉴项（存在即已获得）。 */
    public void upsertLegendary(CropType cropType) throws SQLException {
        if (cropType == null) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO legendary_collection(crop_type) VALUES(?)"
                        + " ON CONFLICT(crop_type) DO NOTHING")) {
            ps.setString(1, cropType.name());
            ps.executeUpdate();
        }
    }

    /** 全部已获得传说作物类型。 */
    public Set<CropType> findAllLegendaries() throws SQLException {
        Set<CropType> result = new LinkedHashSet<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT crop_type FROM legendary_collection ORDER BY crop_type");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CropType cropType = parseEnum(CropType.class, rs.getString("crop_type"));
                if (cropType != null) {
                    result.add(cropType);
                }
            }
        }
        return result;
    }

    /** 宽容解析枚举名；null/空/非法一律返回 null（跳过坏行）。 */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, name.trim());
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
