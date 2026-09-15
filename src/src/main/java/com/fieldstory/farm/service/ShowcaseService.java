package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CropMemory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 展示台服务接口（C 模块 品质与传说域，P3；验收规范 §一百五十 最终
 * Service 归属的 P3 新增清单含 ShowcaseService）。
 *
 * <p>职责（验收规范 §一百二十三~一百二十四）：从 {@link MemoryService}
 * 维护的生命记忆注册表中筛选「已经获得过的传说作物历史记录」，构建
 * {@link ShowcaseEntry} 展示条目供视图渲染。展示的是 CropMemory 历史档案，
 * 不是当前活 Crop（验收规范 §九十五 档案生命周期）。
 *
 * <p>本接口只做筛选与组装，不新增任何状态：档案唯一权威来源仍是
 * C 的 {@link MemoryService}（P2 已交付），持久化由 E 存档模块统一处理。
 *
 * <p>对外约定（验收规范 §一百三十二 ⑥）：三种传说作物收获后，均可
 * 经 {@link #listLegendaryEntries} 获得可展示条目。
 */
public interface ShowcaseService {

    /**
     * 全部可展示的传说记忆条目（已收获落档 + 传说突破成功的档案，
     * 按档案创建顺序）。
     *
     * @return 展示条目列表；无传说收获时为空列表
     */
    List<ShowcaseEntry> listLegendaryEntries();

    /**
     * 按 cropUuid 查找展示条目。
     *
     * @param cropUuid 作物唯一标识（规则文档 §六十九）
     * @return 条目；档案不存在或非传说时为空
     */
    Optional<ShowcaseEntry> findEntry(UUID cropUuid);

    /**
     * 从档案构建展示条目（委托 {@link ShowcaseEntry#of}）。
     *
     * <p>非传说档案、未收获落档的档案抛 {@link IllegalArgumentException}。
     *
     * @param memory 已收获落档的传说生命记忆
     * @return 展示条目
     */
    ShowcaseEntry buildEntry(CropMemory memory);
}
