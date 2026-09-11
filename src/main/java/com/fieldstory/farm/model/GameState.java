package com.fieldstory.farm.model;

/**
 * 存档聚合（E 存档模块持有）。
 *
 * <p>P0 JSON 存档（验收规范 §四十~§四十二）以本类为保存/加载单元，至少覆盖：
 * Player 经济（金币与种子库存，见 {@link Player}）、
 * 游戏天数（GameClock.getGameDay()）、每块土地完整状态
 * （空闲/已播种/成长中…+生长进度，见 {@link PlotState}）、已解锁内容。
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

    /** 已解锁内容标识集合（P3 土地解锁/商店接入后使用，P0 默认为空） */
    private final java.util.Set<String> unlocked = new java.util.LinkedHashSet<>();

    /** 全部土地格子状态快照（A Farm 接入后由适配层从 Farm/Soil/Crop 生成） */
    private final java.util.List<PlotState> plots = new java.util.ArrayList<>();

    /** 装饰快照（P1 装饰系统接入后由 B 侧写入；P0 为空，对应 SQLite decoration 表） */
    private final java.util.List<DecorationState> decorations = new java.util.ArrayList<>();

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
}
