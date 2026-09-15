package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.Quality;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 展示台条目（C 模块 品质与传说域，P3）。
 *
 * <p>验收规范 §一百二十三：展示台允许展示「已经获得过的传说作物历史记录」，
 * 展示的是 {@link CropMemory} 历史档案，而不是当前活 Crop（收获后
 * 当前 Crop 已从土地清除，档案永久保留，验收规范 §九十五）。
 *
 * <p>验收规范 §一百二十四 要求至少显示九项内容：传说名称、作物类型、
 * 品质、种植时间、收获时间、关键天气、关键事件、玩家操作、完整生命故事
 * ——本类一个条目携带全部九项，文本在静态工厂 {@link #of} 中格式化完成。
 *
 * <p>纯值对象 + 纯静态函数：不含任何 JavaFX 依赖，全部文本转换可在
 * 无 GUI 线程下单元测试（任务约束：测试只测纯函数）。
 */
public class ShowcaseEntry {

    /** 作物唯一标识（档案 key，规则文档 §六十九） */
    private final UUID cropUuid;

    /** 传说名称：金色麦穗/彩虹玉米/巨龙胡萝卜（规则文档 §四十二~四十四） */
    private final String legendaryName;

    /** 作物类型（规则文档 §十三） */
    private final CropType cropType;

    /** 品质（展示台只陈列传说，恒为 LEGENDARY，验收规范 §一百二十三） */
    private final Quality quality;

    /** 种植时间文本（决策 D14 口径：worldTime = gameDay×24 + gameHour） */
    private final String plantTimeText;

    /** 收获时间文本（口径同上；-1 哨兵显示占位符） */
    private final String harvestTimeText;

    /** 关键天气摘要（雨天/干旱/绿雨经历，规则文档 §六十九） */
    private final String weatherSummary;

    /** 关键事件摘要（随机事件经历，规则文档 §六十九） */
    private final String eventSummary;

    /** 玩家操作摘要（浇水/施肥/干旱救援，规则文档 §六十九） */
    private final String actionSummary;

    /** 完整生命故事（规则文档 §七十：可追溯到真实游戏记录） */
    private final String fullStory;

    private ShowcaseEntry(UUID cropUuid, String legendaryName, CropType cropType,
                          Quality quality, String plantTimeText, String harvestTimeText,
                          String weatherSummary, String eventSummary,
                          String actionSummary, String fullStory) {
        this.cropUuid = cropUuid;
        this.legendaryName = legendaryName;
        this.cropType = cropType;
        this.quality = quality;
        this.plantTimeText = plantTimeText;
        this.harvestTimeText = harvestTimeText;
        this.weatherSummary = weatherSummary;
        this.eventSummary = eventSummary;
        this.actionSummary = actionSummary;
        this.fullStory = fullStory;
    }

    /**
     * 从已落档的传说档案构建展示条目（验收规范 §一百二十三：只展示传说历史记录）。
     *
     * <p>非传说档案、未收获落档的档案一律拒绝（抛
     * {@link IllegalArgumentException}），保证展示台内容纯净。
     *
     * @param memory 已收获落档的传说生命记忆
     * @return 展示条目
     */
    public static ShowcaseEntry of(CropMemory memory) {
        Objects.requireNonNull(memory, "档案不能为空");
        if (!memory.isCompleted()) {
            throw new IllegalArgumentException("档案未完成收获落档，不能进入展示台");
        }
        if (!memory.isLegendary() || memory.getQuality() != Quality.LEGENDARY) {
            throw new IllegalArgumentException("展示台只陈列传说作物");
        }
        if (memory.getCropType() == null) {
            // 持久化反序列化可能丢失作物类型（无参构造 + setter 不完整），
            // 视图层 getDisplayName 会 NPE，这里在 Service 层提前拒绝
            throw new IllegalArgumentException("档案作物类型缺失，不能进入展示台");
        }
        return new ShowcaseEntry(
                memory.getCropUuid(),
                LegendaryService.legendaryName(memory.getCropType()),
                memory.getCropType(),
                memory.getQuality(),
                formatWorldTime(memory.getPlantWorldTime()),
                formatWorldTime(memory.getHarvestWorldTime()),
                weatherSummaryOf(memory),
                eventSummaryOf(memory),
                actionSummaryOf(memory),
                memory.getFinalStory() == null ? "" : memory.getFinalStory());
    }

    /**
     * 纯函数：世界时间(游戏小时) → 「第N天 HH:00」文本
     * （决策 D14 口径：worldTime = gameDay×24 + gameHour）。
     *
     * <p>时间哨兵（决策 D14 风格）：worldTime 为 -1 表示未发生，返回占位符「——」。
     *
     * @param worldTime 世界时间（游戏小时）
     * @return 时间文本
     */
    public static String formatWorldTime(long worldTime) {
        if (worldTime < 0) {
            return "——";
        }
        long gameDay = worldTime / 24;
        long gameHour = worldTime % 24;
        return "第" + gameDay + "天 " + String.format("%02d:00", gameHour);
    }

    /**
     * 纯函数：关键天气摘要（规则文档 §六十九 天气经历）。
     *
     * <p>只列出非零经历：雨天/干旱/绿雨；全部为零时返回「无特殊天气经历」。
     *
     * @param memory 生命记忆档案
     * @return 天气摘要文本
     */
    public static String weatherSummaryOf(CropMemory memory) {
        Objects.requireNonNull(memory, "档案不能为空");
        List<String> parts = new ArrayList<>();
        if (memory.getRainCount() > 0) {
            parts.add("雨天 " + memory.getRainCount() + " 次");
        }
        if (memory.getDroughtCount() > 0) {
            parts.add("干旱 " + memory.getDroughtCount() + " 天");
        }
        if (memory.getGreenRainCount() > 0) {
            parts.add("绿雨 " + memory.getGreenRainCount() + " 场");
        }
        return parts.isEmpty() ? "无特殊天气经历" : String.join("、", parts);
    }

    /**
     * 纯函数：关键事件摘要（规则文档 §六十九 随机事件经历）。
     *
     * <p>同一事件可多次经历（MemoryService 约定），摘要按事件类型去重；
     * NONE 不参与展示；无事件时返回「无特殊事件」。
     *
     * @param memory 生命记忆档案
     * @return 事件摘要文本
     */
    public static String eventSummaryOf(CropMemory memory) {
        Objects.requireNonNull(memory, "档案不能为空");
        Set<String> names = new LinkedHashSet<>();
        for (EventType event : memory.getEvents()) {
            String name = eventName(event);
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names.isEmpty() ? "无特殊事件" : String.join("、", names);
    }

    /**
     * 纯函数：玩家操作摘要（规则文档 §六十九 玩家干预记录）。
     *
     * <p>只列出非零操作：浇水次数、施肥次数、干旱当天浇水救援
     * （金色麦穗条件 2，规则文档 §四十二）；全部没有时返回「无玩家干预」。
     *
     * @param memory 生命记忆档案
     * @return 操作摘要文本
     */
    public static String actionSummaryOf(CropMemory memory) {
        Objects.requireNonNull(memory, "档案不能为空");
        List<String> parts = new ArrayList<>();
        if (memory.getManualWaterCount() > 0) {
            parts.add("浇水 " + memory.getManualWaterCount() + " 次");
        }
        if (memory.getFertilizerCount() > 0) {
            parts.add("施肥 " + memory.getFertilizerCount() + " 次");
        }
        if (memory.isWaterRescueOnDroughtDay()) {
            parts.add("干旱当天及时浇水救援");
        }
        return parts.isEmpty() ? "无玩家干预" : String.join("、", parts);
    }

    /**
     * 纯函数：事件类型 → 中文名（四个事件名取自验收规范 §九十二 事件效果）。
     *
     * @param event 事件类型
     * @return 中文名；NONE 与 null 返回空串
     */
    public static String eventName(EventType event) {
        if (event == null) {
            return "";
        }
        return switch (event) {
            case METEOR_SHOWER -> "流星夜";
            case MYSTERY_MERCHANT -> "神秘商人";
            case ANIMAL_VISIT -> "小动物来访";
            case RAINBOW_DAY -> "彩虹日";
            case NONE -> "";
        };
    }

    public UUID getCropUuid() {
        return cropUuid;
    }

    public String getLegendaryName() {
        return legendaryName;
    }

    public CropType getCropType() {
        return cropType;
    }

    public Quality getQuality() {
        return quality;
    }

    public String getPlantTimeText() {
        return plantTimeText;
    }

    public String getHarvestTimeText() {
        return harvestTimeText;
    }

    public String getWeatherSummary() {
        return weatherSummary;
    }

    public String getEventSummary() {
        return eventSummary;
    }

    public String getActionSummary() {
        return actionSummary;
    }

    public String getFullStory() {
        return fullStory;
    }
}
