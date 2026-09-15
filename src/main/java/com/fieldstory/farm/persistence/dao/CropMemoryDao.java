package com.fieldstory.farm.persistence.dao;

import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.Quality;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 作物生命记忆数据访问对象（E 模块 P2 DAO；验收规范 §九十三~§九十五）。
 *
 * <p>负责 {@code crop_memory} 表：每株作物一条档案，键为 {@code crop_uuid}
 * （生命周期唯一，§九十三）。本表与 {@code crop} 表<b>没有外键</b>——
 * 收获后当前作物行被删除，档案必须永久保留（§九十五），因此两张表的生命周期不同。
 *
 * <p>字段口径：
 * <ul>
 *   <li>三个世界时间字段为 {@code long} 游戏小时（决策 D14），未发生用 -1 哨兵；</li>
 *   <li>{@code events} 以逗号分隔的 {@link EventType#name()} 保存经历过的随机事件；</li>
 *   <li>布尔字段以 0/1 保存；</li>
 *   <li>枚举以 {@code name()} 保存，读回时无法识别的值按"未知即缺省"处理
 *       （枚举置 null），坏数据不让读档崩溃。</li>
 * </ul>
 *
 * <p>本 DAO 不含任何业务计算（统一 Model 原则）；事务边界由
 * {@code SqliteSaveService} 控制。
 */
public class CropMemoryDao {

    /** 事件列表分隔符（{@link EventType#name()} 只含 A~Z_，不会与之冲突）。 */
    private static final String EVENT_SEPARATOR = ",";

    private final Connection connection;

    public CropMemoryDao(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection 不能为空");
    }

    /** 插入/覆盖一条生命记忆档案。 */
    public void upsert(CropMemory memory) throws SQLException {
        Objects.requireNonNull(memory, "memory 不能为空");
        Objects.requireNonNull(memory.getCropUuid(), "档案 cropUuid 不能为空");
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO crop_memory(crop_uuid, crop_type, plant_world_time, mature_world_time,"
                        + " harvest_world_time, manual_water_count, rain_count, drought_count,"
                        + " green_rain_count, fertilizer_count, last_fertilize_game_day,"
                        + " last_drought_game_day, water_rescue, events, wither_risk, quality, legendary, final_story)"
                        + " VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT(crop_uuid) DO UPDATE SET"
                        + " crop_type = excluded.crop_type,"
                        + " plant_world_time = excluded.plant_world_time,"
                        + " mature_world_time = excluded.mature_world_time,"
                        + " harvest_world_time = excluded.harvest_world_time,"
                        + " manual_water_count = excluded.manual_water_count,"
                        + " rain_count = excluded.rain_count,"
                        + " drought_count = excluded.drought_count,"
                        + " green_rain_count = excluded.green_rain_count,"
                        + " fertilizer_count = excluded.fertilizer_count,"
                        + " last_fertilize_game_day = excluded.last_fertilize_game_day,"
                        + " last_drought_game_day = excluded.last_drought_game_day,"
                        + " water_rescue = excluded.water_rescue,"
                        + " events = excluded.events,"
                        + " wither_risk = excluded.wither_risk,"
                        + " quality = excluded.quality,"
                        + " legendary = excluded.legendary,"
                        + " final_story = excluded.final_story")) {
            ps.setString(1, memory.getCropUuid().toString());
            ps.setString(2, nameOf(memory.getCropType()));
            ps.setLong(3, memory.getPlantWorldTime());
            ps.setLong(4, memory.getMatureWorldTime());
            ps.setLong(5, memory.getHarvestWorldTime());
            ps.setInt(6, memory.getManualWaterCount());
            ps.setInt(7, memory.getRainCount());
            ps.setInt(8, memory.getDroughtCount());
            ps.setInt(9, memory.getGreenRainCount());
            ps.setInt(10, memory.getFertilizerCount());
            ps.setLong(11, memory.getLastFertilizeGameDay());
            ps.setLong(12, memory.getLastDroughtGameDay());
            ps.setInt(13, memory.isWaterRescueOnDroughtDay() ? 1 : 0);
            ps.setString(14, joinEvents(memory.getEvents()));
            ps.setInt(15, memory.isWitherRisk() ? 1 : 0);
            ps.setString(16, nameOf(memory.getQuality()));
            ps.setInt(17, memory.isLegendary() ? 1 : 0);
            ps.setString(18, memory.getFinalStory());
            ps.executeUpdate();
        }
    }

    /**
     * 按作物 id 查询档案。
     *
     * @return 档案；不存在或 id 非法返回 {@code null}
     */
    public CropMemory findById(String cropUuid) throws SQLException {
        if (cropUuid == null || cropUuid.isBlank()) {
            return null;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM crop_memory WHERE crop_uuid = ?")) {
            ps.setString(1, cropUuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** 全部档案（按种植世界时间升序，保证读回顺序稳定）。 */
    public List<CropMemory> findAll() throws SQLException {
        List<CropMemory> memories = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM crop_memory ORDER BY plant_world_time, crop_uuid");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CropMemory memory = map(rs);
                if (memory != null) {
                    memories.add(memory);
                }
            }
        }
        return memories;
    }

    /** 档案行数。 */
    public int count() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM crop_memory");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** 删除全部档案（仅在"重开新档"全量重写时使用）。 */
    public void deleteAll() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM crop_memory")) {
            ps.executeUpdate();
        }
    }

    private static final String COLUMNS =
            "crop_uuid, crop_type, plant_world_time, mature_world_time, harvest_world_time,"
                    + " manual_water_count, rain_count, drought_count, green_rain_count,"
                    + " fertilizer_count, last_fertilize_game_day, last_drought_game_day, water_rescue, events,"
                    + " wither_risk, quality, legendary, final_story";

    /** 行 → 档案；crop_uuid 非法时返回 null（跳过坏行，不阻断整次读档）。 */
    private static CropMemory map(ResultSet rs) throws SQLException {
        UUID cropUuid;
        try {
            cropUuid = UUID.fromString(rs.getString("crop_uuid"));
        } catch (IllegalArgumentException | NullPointerException bad) {
            System.err.println("[CropMemoryDao] 跳过非法 cropUuid 的记忆行: "
                    + rs.getString("crop_uuid"));
            return null;
        }
        CropType cropType = parseEnum(CropType.class, rs.getString("crop_type"));
        // 用无参构造 + setter 装配：坏行（crop_type 无法识别）也能读回，绝不因一格坏数据抛异常
        CropMemory memory = new CropMemory();
        memory.setCropUuid(cropUuid);
        memory.setCropType(cropType);
        memory.setPlantWorldTime(rs.getLong("plant_world_time"));
        memory.setMatureWorldTime(rs.getLong("mature_world_time"));
        memory.setHarvestWorldTime(rs.getLong("harvest_world_time"));
        memory.setManualWaterCount(rs.getInt("manual_water_count"));
        memory.setRainCount(rs.getInt("rain_count"));
        memory.setDroughtCount(rs.getInt("drought_count"));
        memory.setGreenRainCount(rs.getInt("green_rain_count"));
        memory.setFertilizerCount(rs.getInt("fertilizer_count"));
        memory.setLastFertilizeGameDay(rs.getLong("last_fertilize_game_day"));
        memory.setLastDroughtGameDay(rs.getLong("last_drought_game_day"));
        memory.setWaterRescueOnDroughtDay(rs.getInt("water_rescue") != 0);
        memory.getEvents().addAll(splitEvents(rs.getString("events")));
        memory.setWitherRisk(rs.getInt("wither_risk") != 0);
        memory.setQuality(parseEnum(Quality.class, rs.getString("quality")));
        memory.setLegendary(rs.getInt("legendary") != 0);
        memory.setFinalStory(rs.getString("final_story"));
        return memory;
    }

    private static String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String joinEvents(List<EventType> events) {
        if (events == null || events.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (EventType event : events) {
            if (event == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(EVENT_SEPARATOR);
            }
            sb.append(event.name());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static List<EventType> splitEvents(String raw) {
        List<EventType> events = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return events;
        }
        for (String token : raw.split(EVENT_SEPARATOR)) {
            EventType event = parseEnum(EventType.class, token);
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    /** 宽容解析枚举名；null/空/非法一律返回 null。 */
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
