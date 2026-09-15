package com.fieldstory.farm.model;

import com.fieldstory.farm.model.item.Inventory;

/**
 * 存档聚合（E 存档模块持有）。
 *
 * <p>P0 JSON 存档（验收规范 §四十~§四十二）以本类为保存/加载单元，至少覆盖：
 * Player 经济（金币与种子库存，见 {@link Player}）、
 * 游戏天数（GameClock.getGameDay()）、每块土地完整状态
 * （空闲/已播种/成长中…+生长进度，见 {@link PlotState}）、已解锁内容。
 *
 * <p>P2 起（验收规范 §九十一/§九十三/§九十五/§一百零三）在同一聚合上补齐
 * "关掉再打开也不丢"的其余运行态：世界时钟总分钟与天气、作物生命记忆
 * {@link CropMemory}、当前随机事件 {@link EventState}、玩家背包 {@link Inventory}
 * （种子库存仍只在 {@link Player} 中，不重复存）。
 *
 * <p>本类只保存“现在是什么状态”，不含任何游戏计算（统一 Model 原则）。
 * 种子库存的<b>唯一</b>归属是 {@link Player#getSeedInventory()}（B 模块 §6.2），
 * 本类不再另存一份，避免出现两份库存状态。
 */
public class GameState {

    /** 玩家：姓名、金币与种子库存（B 模块 Player 模型） */
    private Player player;

    /** 游戏天数，对应 GameClock.getGameDay()（D 模块时钟接入后写入） */
    private long gameDay;

    /** 存档时刻 GameClock 世界时间（ISO-8601 字符串；验收 §四十一；
     *  D 模块时钟接入前新档可为 null） */
    private String currentWorldTime;

    /**
     * 上次真实存档/退出时间（ISO-8601），对应 SQLite {@code world_state.last_real_time}。
     * 新档或从未记录该字段的旧档为 null；离线时长由 GameClock 使用它计算。
     */
    private String lastRealTime;

    /** 已解锁内容标识集合（P3 土地解锁/商店接入后使用，P0 默认为空） */
    private final java.util.Set<String> unlocked = new java.util.LinkedHashSet<>();

    /** 全部土地格子状态快照（A Farm 接入后由适配层从 Farm/Soil/Crop 生成） */
    private final java.util.List<PlotState> plots = new java.util.ArrayList<>();

    /** 装饰快照（P1 装饰系统接入后由 B 侧写入；P0 为空，对应 SQLite decoration 表） */
    private final java.util.List<DecorationState> decorations = new java.util.ArrayList<>();

    /**
     * 世界时钟总分钟（P2 新增，对应 {@code world_state.world_total_minutes}）。
     *
     * <p>只存 {@link #gameDay} 会把"退出瞬间"抹成当天 06:00：玩家在第 2 天 20:00 退出、
     * 重开若回到第 2 天 06:00，就等于凭空退回 14 个游戏小时。总分钟让读档精确回到退出时刻。
     * {@code -1} 为哨兵：旧档没有这个值，此时退回"按天恢复"的 P1 口径。
     */
    private long worldTotalMinutes = -1L;

    /** 当前天气（P2 新增，对应 {@code world_state.current_weather}；未记录为 null） */
    private WeatherType currentWeather;

    /** 当前天气所属游戏日索引（对应 {@code world_state.current_day_index} 的天气口径） */
    private int weatherDayIndex;

    /**
     * 作物生命记忆档案快照（P2 新增，对应 SQLite {@code crop_memory} 表，验收规范 §九十三）。
     * 收获后当前作物会从土地清除，但档案永久保留（§九十五），因此它<b>不</b>跟着 plots 走。
     */
    private final java.util.List<CropMemory> memories = new java.util.ArrayList<>();

    /** 当前随机事件快照（P2 新增，对应 SQLite {@code active_event} 表，验收规范 §九十一） */
    private EventState activeEvent;

    /**
     * 玩家背包（P2 新增，对应 SQLite {@code player_item} 表；C 模块 {@code Inventory}）。
     *
     * <p>只放收获产物 / 肥料等物品；<b>种子库存不在其中</b>，它的唯一归属是
     * {@link Player#getSeedInventory()}（B 模块 §6.2 禁止第二份库存）。
     */
    private final Inventory inventory = new Inventory();

    /**
     * 收集图鉴状态（P3 新增，对应 SQLite {@code crop_collection} / {@code decoration_collection}
     * / {@code legendary_collection} 三表，验收规范 §一百一十~§一百一十八）。
     * 永久保存——退出重进后 FarmScore 与毕业判定不丢失（验收规范 §一百三十二 ⑤）。
     */
    private final CollectionState collection = new CollectionState();

    /** 套装收集状态（P3 新增，对应 SQLite {@code set_collection} 表，验收规范 §一百一十八）。 */
    private final SetCollectionState setCollection = new SetCollectionState();

    /** 毕业状态（P3 新增，对应 SQLite {@code graduation} 表，验收规范 §一百二十九）。 */
    private final GraduationState graduation = new GraduationState();


    public GameState() {
        this(null, 0L);
    }

    public GameState(Player player, long gameDay) {
        this.player = player;
        this.gameDay = gameDay;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    /** 当前游戏天数（新建存档为 0，由 D 模块 GameClock 在每日结算时写入）。 */
    public long getGameDay() {
        return gameDay;
    }

    public void setGameDay(long gameDay) {
        this.gameDay = gameDay;
    }

    /** 存档时刻 GameClock 世界时间（ISO-8601）；无时钟记录时返回 null。 */
    public String getCurrentWorldTime() {
        return currentWorldTime;
    }

    public void setCurrentWorldTime(String currentWorldTime) {
        this.currentWorldTime = currentWorldTime;
    }

    /** 上次真实存档/退出时间（ISO-8601）；无记录返回 null。 */
    public String getLastRealTime() {
        return lastRealTime;
    }

    public void setLastRealTime(String lastRealTime) {
        this.lastRealTime = lastRealTime;
    }

    /** 已解锁内容标识集合。 */
    public java.util.Set<String> getUnlocked() {
        return unlocked;
    }

    /** 全部土地格子状态快照。 */
    public java.util.List<PlotState> getPlots() {
        return plots;
    }

    /** 装饰快照（P1 起持久化到 SQLite decoration 表；P0 为空）。 */
    public java.util.List<DecorationState> getDecorations() {
        return decorations;
    }

    /** 世界时钟总分钟；{@code -1} 表示旧档未记录（读档时按 {@link #getGameDay()} 粗恢复）。 */
    public long getWorldTotalMinutes() {
        return worldTotalMinutes;
    }

    public void setWorldTotalMinutes(long worldTotalMinutes) {
        this.worldTotalMinutes = worldTotalMinutes;
    }

    /** 当前天气；未记录为 null。 */
    public WeatherType getCurrentWeather() {
        return currentWeather;
    }

    public void setCurrentWeather(WeatherType currentWeather) {
        this.currentWeather = currentWeather;
    }

    /** 当前天气所属游戏日索引。 */
    public int getWeatherDayIndex() {
        return weatherDayIndex;
    }

    public void setWeatherDayIndex(int weatherDayIndex) {
        this.weatherDayIndex = weatherDayIndex;
    }

    /** 作物生命记忆档案快照（可写列表；读档装配时按 cropUuid 覆盖式写入）。 */
    public java.util.List<CropMemory> getMemories() {
        return memories;
    }

    /** 当前随机事件快照；无事件为 null。 */
    public EventState getActiveEvent() {
        return activeEvent;
    }

    public void setActiveEvent(EventState activeEvent) {
        this.activeEvent = activeEvent;
    }

    /** 玩家背包（永不为 null；空背包即空实例）。 */
    public Inventory getInventory() {
        return inventory;
    }

    /** 收集图鉴状态（永不为 null）。P3 起随存档持久化。 */
    public CollectionState getCollection() {
        return collection;
    }

    /** 套装收集状态（永不为 null）。P3 起随存档持久化。 */
    public SetCollectionState getSetCollection() {
        return setCollection;
    }

    /** 毕业状态（永不为 null）。P3 起随存档持久化。 */
    public GraduationState getGraduation() {
        return graduation;
    }
}
