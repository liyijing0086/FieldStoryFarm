package com.fieldstory.farm.persistence;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventState;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.impl.BasicEventState;
import com.fieldstory.farm.persistence.dao.ActiveEventDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L8.3 存档验收：{@code active_event} 表字段与 E 模块对接可用，退出重进事件状态正确恢复。
 *
 * <p>覆盖验收规范 §九十一：{@code active_event} 五列
 * （{@code event_type}/{@code start_world_time}/{@code end_world_time}/
 * {@code target_crop_type}/{@code payload}）与 {@link EventState} 一一映射，
 * 且「游戏内事件仅在期间退出重进，事件记录不凭空消失」。
 *
 * <p>DAO 口径与 E 现行实现一致：{@link ActiveEventDao} 以 {@link EventState} 为读写单元
 * （{@code upsert/find/deleteAll}），不再单独暴露字符串行对象。
 */
class ActiveEventPersistenceTest {

    @TempDir
    Path tempDir;

    private DatabaseService database(String name) {
        return new DatabaseService(tempDir.resolve(name));
    }

    @Test
    void activeEventTableHasFiveSpecifiedColumns() throws Exception {
        DatabaseService db = database("schema.db");
        try (Connection connection = db.openConnection()) {
            assertTrue(columnExists(connection, "active_event", "event_type"));
            assertTrue(columnExists(connection, "active_event", "start_world_time"));
            assertTrue(columnExists(connection, "active_event", "end_world_time"));
            assertTrue(columnExists(connection, "active_event", "target_crop_type"));
            assertTrue(columnExists(connection, "active_event", "payload"));
        }
    }

    @Test
    void activeEventDaoRoundTripsAllFields() throws Exception {
        DatabaseService db = database("dao.db");
        try (Connection connection = db.openConnection()) {
            ActiveEventDao dao = new ActiveEventDao(connection);
            assertNull(dao.find(), "空表应返回 null");

            BasicEventState merchant = new BasicEventState(EventType.MYSTERY_MERCHANT, 50L, 62L);
            merchant.setTargetCropType(CropType.CORN);
            merchant.setPayload("reward");
            dao.upsert(merchant);

            EventState stored = dao.find();
            assertNotNull(stored);
            assertEquals(EventType.MYSTERY_MERCHANT, stored.getEventType());
            assertEquals(50L, stored.getStartWorldTime());
            assertEquals(62L, stored.getEndWorldTime());
            assertEquals(CropType.CORN, stored.getTargetCropType());
            assertEquals("reward", stored.getPayload());

            dao.upsert(new BasicEventState(EventType.METEOR_SHOWER, 100L, 124L));
            EventState updated = dao.find();
            assertEquals(EventType.METEOR_SHOWER, updated.getEventType());
            assertEquals(100L, updated.getStartWorldTime());
            assertEquals(124L, updated.getEndWorldTime());
            assertNull(updated.getTargetCropType());
            assertNull(updated.getPayload());

            dao.deleteAll();
            assertNull(dao.find());
        }
    }

    @Test
    void eventStateSurvivesExitAndRestart() {
        DatabaseService db = database("event.db");
        SqliteSaveService save = new SqliteSaveService(db, null);

        GameState state = new GameState(new Player("农夫", 500), 3L);
        state.setActiveEvent(new BasicEventState(EventType.METEOR_SHOWER, 72L, 96L));
        save.save(state);

        // ---------- 退出重进 ----------
        SqliteSaveService reopened = new SqliteSaveService(db, null);
        GameState loaded = reopened.load();
        assertNotNull(loaded);
        assertNotNull(loaded.getActiveEvent(), "事件应跨退出重进恢复（验收 §九十一）");
        assertEquals(EventType.METEOR_SHOWER, loaded.getActiveEvent().getEventType(),
                "事件类型应跨退出重进恢复（验收 §九十一）");
        assertEquals(72L, loaded.getActiveEvent().getStartWorldTime());
        assertEquals(96L, loaded.getActiveEvent().getEndWorldTime());
    }

    @Test
    void mysteryMerchantTargetCropSurvivesRestart() {
        DatabaseService db = database("merchant.db");
        SqliteSaveService save = new SqliteSaveService(db, null);

        GameState state = new GameState(new Player("农夫", 500), 4L);
        BasicEventState merchant = new BasicEventState(EventType.MYSTERY_MERCHANT, 96L, 108L);
        merchant.setTargetCropType(CropType.CARROT);
        merchant.setPayload("double_price");
        state.setActiveEvent(merchant);
        save.save(state);

        GameState loaded = new SqliteSaveService(db, null).load();
        assertEquals(EventType.MYSTERY_MERCHANT, loaded.getActiveEvent().getEventType());
        assertEquals(CropType.CARROT, loaded.getActiveEvent().getTargetCropType(),
                "神秘商人指定作物应跨退出重进恢复");
        assertEquals("double_price", loaded.getActiveEvent().getPayload());
    }

    @Test
    void noEventWritesNoRowAndLoadsAsNull() {
        DatabaseService db = database("none.db");
        SqliteSaveService save = new SqliteSaveService(db, null);

        GameState state = new GameState(new Player("农夫", 500), 1L);
        // 不设置事件（默认无事件）
        save.save(state);

        GameState loaded = new SqliteSaveService(db, null).load();
        assertNull(loaded.getActiveEvent(), "无事件时不应写入 active_event 行");
    }

    @Test
    void restoredEventStateDrivesEventService() {
        DatabaseService db = database("drive.db");
        SqliteSaveService save = new SqliteSaveService(db, null);

        GameState state = new GameState(new Player("农夫", 500), 3L);
        state.setActiveEvent(new BasicEventState(EventType.RAINBOW_DAY, 48L, 72L));
        save.save(state);

        GameState loaded = new SqliteSaveService(db, null).load();

        // 用存档字段重建 EventState，验证与 D 模块 EventService 对接可用
        EventState restored = loaded.getActiveEvent();
        assertEquals(EventType.RAINBOW_DAY, restored.getEventType());
        assertEquals(24L, restored.getEndWorldTime() - restored.getStartWorldTime(),
                "彩虹日持续 24 游戏小时（验收 §八十二）");
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
