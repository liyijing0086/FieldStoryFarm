package com.fieldstory.farm.service;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.Quality;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 生命记忆服务接口（C 模块 品质与传说域，P2）。
 *
 * <p>职责（验收规范 §九十三~九十五；规则文档 §六十九~七十）：
 * 维护每株作物的 {@link CropMemory} 档案，记录成长经历，并在收获时
 * 落档品质结果与生成可追溯的最终生命故事。
 *
 * <p>档案生命周期（验收规范 §九十五）：收获后当前 Crop 从土地清除，
 * CropMemory 永久保留，供 P3 展示台（ShowcaseService）读取历史记录。
 *
 * <p>经历记录 API 供跨模块调用：D 的日结算（天气/事件）、A/B 的
 * 浇水与施肥在各自流程中调用对应 record 方法，C 的 HarvestTransactionService
 * 在收获事务中调用 {@link #completeHarvest} 落档。
 */
public interface MemoryService {

    /**
     * 为作物创建生命记忆档案（播种时调用）。
     *
     * <p>档案以 cropUuid 为键（验收规范 §九十三：cropUuid 生命周期唯一），
     * 播种时刻取自 Crop.plantWorldTime（决策 D14 时间口径）。
     *
     * @param crop 已播种作物
     * @return 新建档案（已登记）
     */
    CropMemory createMemory(Crop crop);

    /**
     * 按 cropUuid 查询档案。
     *
     * @param cropUuid 作物唯一标识
     * @return 档案；从未创建时为空
     */
    Optional<CropMemory> findMemory(UUID cropUuid);

    /**
     * 登记/覆盖保存档案（幂等；P2 内存注册表，持久化由 E 存档模块统一处理）。
     *
     * @param memory 档案
     */
    void save(CropMemory memory);

    /**
     * 全部档案快照（只读，按创建顺序）。
     *
     * @return 档案列表
     */
    List<CropMemory> listAll();

    /**
     * 记录一次主动浇水（B 的 WateringService 在浇水成功后调用）。
     *
     * <p>若浇水发生在最近一次干旱的当天（gameDay == lastDroughtGameDay），
     * 同时标记"干旱当天浇水救援"（金色麦穗条件 2，规则文档 §四十二）。
     *
     * @param memory  档案
     * @param gameDay 浇水发生时的游戏日
     */
    void recordManualWater(CropMemory memory, long gameDay);

    /** 记录一个雨天（D 日结算调用）。 */
    void recordRain(CropMemory memory);

    /**
     * 记录一个干旱天（D 日结算调用）。
     *
     * @param memory  档案
     * @param gameDay 干旱发生的游戏日（用于"当天浇水救援"判定）
     */
    void recordDrought(CropMemory memory, long gameDay);

    /** 记录一场绿雨（D 日结算调用）。 */
    void recordGreenRain(CropMemory memory);

    /** 记录一次施肥（B 的施肥服务调用）。 */
    void recordFertilizer(CropMemory memory);

    /**
     * 记录一次随机事件（D 的 EventService 调用；同一事件可多次经历）。
     *
     * @param memory  档案
     * @param eventType 事件类型（NONE 忽略）
     */
    void recordEvent(CropMemory memory, EventType eventType);

    /**
     * 标记成熟时刻（A 的成长服务在作物成熟时调用）。
     *
     * @param memory          档案
     * @param matureWorldTime 成熟时刻世界时间（游戏小时，决策 D14 口径）
     */
    void markMature(CropMemory memory, long matureWorldTime);

    /** 标记经历过枯萎风险（D 日结算调用）。 */
    void markWitherRisk(CropMemory memory);

    /**
     * 收获落档：写入品质结果、传说标志与收获时刻，并生成最终生命故事
     * （验收规范 §九十四 记录项；规则文档 §七十）。
     *
     * <p>必须在收获事务确定品质后调用；落档完成后
     * {@link CropMemory#isCompleted()} 返回 true。
     *
     * @param memory           档案
     * @param quality          最终品质（含 LEGENDARY）
     * @param legendary        是否传说突破成功
     * @param harvestWorldTime 收获时刻世界时间（游戏小时）
     * @return 生成的最终生命故事
     */
    String completeHarvest(CropMemory memory, Quality quality, boolean legendary, long harvestWorldTime);

    /**
     * 生成最终生命故事（规则文档 §七十：基础模板 + 关键经历标签）。
     *
     * <p>故事必须能够追溯到真实游戏记录：每句话对应档案中的真实
     * 计数/事件/时间字段，不使用随机文学。品质未落档时抛出
     * {@link IllegalStateException}。
     *
     * @param memory 已落档档案
     * @return 最终故事文本
     */
    String generateFinalStory(CropMemory memory);
}
