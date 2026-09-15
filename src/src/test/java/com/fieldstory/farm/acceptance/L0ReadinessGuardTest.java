package com.fieldstory.farm.acceptance;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventState;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.impl.BasicEventState;
import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.persistence.DatabaseService;
import com.fieldstory.farm.persistence.SchemaMigrator;
import com.fieldstory.farm.persistence.dao.ActiveEventDao;
import com.fieldstory.farm.service.EventService;
import com.fieldstory.farm.service.impl.BasicEventService;
import com.fieldstory.farm.util.RandomProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;

import static com.fieldstory.farm.util.GameConstants.EVENT_DURATION_METEOR_SHOWER;
import static com.fieldstory.farm.util.GameConstants.EVENT_DURATION_MYSTERY_MERCHANT;
import static com.fieldstory.farm.util.GameConstants.EVENT_DURATION_RAINBOW_DAY;
import static com.fieldstory.farm.util.GameConstants.EVENT_PROB_ANIMAL_VISIT;
import static com.fieldstory.farm.util.GameConstants.EVENT_PROB_METEOR_SHOWER;
import static com.fieldstory.farm.util.GameConstants.EVENT_PROB_MYSTERY_MERCHANT;
import static com.fieldstory.farm.util.GameConstants.EVENT_PROB_NONE;
import static com.fieldstory.farm.util.GameConstants.EVENT_PROB_RAINBOW_DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L0 前置就绪守卫测试（P3 开工前锁定 P2 验收契约）。
 *
 * <p>本测试不新增玩法，只把 L0.1/L0.3 已验收通过的 P2 随机事件契约固化为回归红线，
 * 防止 P3 开发过程中被无意改动：
 * <ul>
 *   <li><b>L0.1</b>：概率 74/5/8/10/3、持续时间 24/12/24h、固定种子可复现
 *       （规则文档 §四十七~§五十一；验收规范 §九十）；</li>
 *   <li><b>L0.3</b>：{@code active_event} 五列与 {@code EventState} 一一映射、
 *       退出重进事件不丢失（验收规范 §九十一）。</li>
 * </ul>
 *
 * <p>溯源：概率/时长常量来自 {@code GameConstants}（规则文档 §四十七~§五十一）；
 * 表结构来自 {@code SchemaMigrator} v2（验收规范 §九十一）。
 */
class L0ReadinessGuardTest {

    /** 概率抽样次数（与 EventServiceTest 一致）。 */
    private static final int SAMPLE_SIZE = 10000;

    /** 概率容差 ±3%（D 模块 P2 文档 §6.1）。 */
    private static final double TOLERANCE = 0.03;

    @TempDir
    Path tempDir;

    // =========================================================
    // L0.1 概率 74/5/8/10/3
    // =========================================================

    @Test
    void probabilityConstantsMatchRules() {
        // 规则文档 §四十七：无事件 74%、流星夜 5%、神秘商人 8%、小动物来访 10%、彩虹日 3%
        assertEquals(74, EVENT_PROB_NONE);
        assertEquals(5, EVENT_PROB_METEOR_SHOWER);
        assertEquals(8, EVENT_PROB_MYSTERY_MERCHANT);
        assertEquals(10, EVENT_PROB_ANIMAL_VISIT);
        assertEquals(3, EVENT_PROB_RAINBOW_DAY);
        assertEquals(100, EVENT_PROB_NONE + EVENT_PROB_METEOR_SHOWER
                + EVENT_PROB_MYSTERY_MERCHANT + EVENT_PROB_ANIMAL_VISIT + EVENT_PROB_RAINBOW_DAY,
                "五档概率之和必须为 100%");
    }

    @Test
    void probabilityDistributionWithinTolerance() {
        RandomProvider.setSeed(20240601L);
        EventService service = new BasicEventService(new BasicEventState(), new BasicGameClock());
        Map<EventType, Integer> counts = new EnumMap<>(EventType.class);
        for (EventType type : EventType.values()) {
            counts.put(type, 0);
        }
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            EventType type = service.rollDailyEvent(i + 1);
            counts.put(type, counts.get(type) + 1);
        }
        assertProbability(counts, EventType.NONE, 0.74);
        assertProbability(counts, EventType.METEOR_SHOWER, 0.05);
        assertProbability(counts, EventType.MYSTERY_MERCHANT, 0.08);
        assertProbability(counts, EventType.ANIMAL_VISIT, 0.10);
        assertProbability(counts, EventType.RAINBOW_DAY, 0.03);
    }

    private void assertProbability(Map<EventType, Integer> counts, EventType type, double expected) {
        double actual = counts.get(type) / (double) SAMPLE_SIZE;
        assertTrue(Math.abs(actual - expected) <= TOLERANCE,
                type + " 概率应约 " + expected + "，实际 " + actual);
    }

    // =========================================================
    // L0.1 持续时间 24/12/24h + 即时
    // =========================================================

    @Test
    void durationConstantsMatchRules() {
        // 规则文档 §四十八/§四十九/§五十一
        assertEquals(24, EVENT_DURATION_METEOR_SHOWER);
        assertEquals(12, EVENT_DURATION_MYSTERY_MERCHANT);
        assertEquals(24, EVENT_DURATION_RAINBOW_DAY);
        assertEquals(24, EventType.METEOR_SHOWER.getDurationHours());
        assertEquals(12, EventType.MYSTERY_MERCHANT.getDurationHours());
        assertEquals(24, EventType.RAINBOW_DAY.getDurationHours());
        assertTrue(EventType.ANIMAL_VISIT.isInstant(), "小动物来访为即时事件（规则文档 §五十）");
    }

    @Test
    void eventActiveWindowMatchesDuration() {
        EventState state = new BasicEventState();
        EventService service = new BasicEventService(state, new BasicGameClock());

        state.setEventType(EventType.METEOR_SHOWER);
        state.setStartWorldTime(100L);
        state.setEndWorldTime(100L + EVENT_DURATION_METEOR_SHOWER);
        assertTrue(service.isEventActive(100L), "起始时刻应生效");
        assertTrue(service.isEventActive(123L), "持续期内应生效");
        assertFalse(service.isEventActive(124L), "到期时刻应失效（左闭右开）");

        state.setEventType(EventType.MYSTERY_MERCHANT);
        state.setStartWorldTime(50L);
        state.setEndWorldTime(50L + EVENT_DURATION_MYSTERY_MERCHANT);
        assertTrue(service.isEventActive(61L));
        assertFalse(service.isEventActive(62L));
    }

    // =========================================================
    // L0.1 固定种子可复现
    // =========================================================

    @Test
    void fixedSeedReproducesEventSequence() {
        RandomProvider.setSeed(12345L);
        EventService first = new BasicEventService(new BasicEventState(), new BasicGameClock());
        EventType[] seq = new EventType[50];
        for (int i = 0; i < seq.length; i++) {
            seq[i] = first.rollDailyEvent(i + 1);
        }

        RandomProvider.setSeed(12345L);
        EventService second = new BasicEventService(new BasicEventState(), new BasicGameClock());
        for (int i = 0; i < seq.length; i++) {
            assertEquals(seq[i], second.rollDailyEvent(i + 1),
                    "相同种子应产生相同事件序列（规则文档 §九十）");
        }
    }

    // =========================================================
    // L0.3 active_event 五列契约
    // =========================================================

    @Test
    void activeEventTableHasExactlyFiveSpecifiedColumns() throws Exception {
        DatabaseService db = new DatabaseService(tempDir.resolve("l0-schema.db"));
        try (Connection connection = db.openConnection()) {
            assertEquals(5, SchemaMigrator.SCHEMA_VERSION,
                    "结构版本应为 5（v5 增加作物运行态/施肥持久化字段，且保留 P3 收集/套装/毕业表）");
            assertTrue(columnExists(connection, "active_event", "event_type"));
            assertTrue(columnExists(connection, "active_event", "start_world_time"));
            assertTrue(columnExists(connection, "active_event", "end_world_time"));
            assertTrue(columnExists(connection, "active_event", "target_crop_type"));
            assertTrue(columnExists(connection, "active_event", "payload"));
        }
    }

    @Test
    void activeEventDaoRoundTripsEventStateFields() throws Exception {
        DatabaseService db = new DatabaseService(tempDir.resolve("l0-dao.db"));
        try (Connection connection = db.openConnection()) {
            ActiveEventDao dao = new ActiveEventDao(connection);
            assertNull(dao.find(), "空表应返回 null（无事件）");

            BasicEventState merchant = new BasicEventState(EventType.MYSTERY_MERCHANT, 96L, 108L);
            merchant.setTargetCropType(CropType.CARROT);
            merchant.setPayload("double_price");
            dao.upsert(merchant);
            EventState row = dao.find();
            assertNotNull(row);
            assertEquals("MYSTERY_MERCHANT", row.getEventType().name());
            assertEquals(96L, row.getStartWorldTime());
            assertEquals(108L, row.getEndWorldTime());
            assertEquals("CARROT", row.getTargetCropType().name());
            assertEquals("double_price", row.getPayload());
        }
    }

    @Test
    void eventStateMapsOneToOneWithActiveEventColumns() {
        // EventState ↔ active_event 五列一一映射（验收规范 §九十一）
        EventState state = new BasicEventState(EventType.METEOR_SHOWER, 72L, 96L);
        assertEquals(EventType.METEOR_SHOWER.name(), state.getEventType().name());
        assertEquals(72L, state.getStartWorldTime());
        assertEquals(96L, state.getEndWorldTime());
        assertNull(state.getTargetCropType(), "非神秘商人 target_crop_type 为 null");
        assertNull(state.getPayload(), "无附加数据 payload 为 null");
    }

    private static boolean columnExists(Connection connection, String table, String column)
            throws SQLException {
        try (var ps = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
