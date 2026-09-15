package com.fieldstory.farm.service.economy.impl;

import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.service.MemoryService;
import com.fieldstory.farm.service.ShowcaseEntry;
import com.fieldstory.farm.service.ShowcaseService;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ShowcaseService} 基础实现（C 模块 品质与传说域，P3）。
 *
 * <p>数据源为 C 的 {@link MemoryService}（P2 内存注册表，保持档案创建顺序）；
 * 筛选条件对应验收规范 §一百二十三：只陈列「已经获得过的传说作物历史记录」，
 * 即同时满足：
 * <ul>
 *   <li>{@link CropMemory#isCompleted()}——收获已落档（验收规范 §九十五
 *       档案生命周期：收获完成后档案永久保留）；</li>
 *   <li>{@link CropMemory#isLegendary()} 且品质为 {@link Quality#LEGENDARY}
 *       ——传说突破成功（规则文档 §四十：传说只能经突破获得，与品质落档
 *       同步写入）。</li>
 * </ul>
 *
 * <p>本类不新增任何状态、不写任何数据库：档案唯一权威来源仍是
 * MemoryService，持久化由 E 存档模块统一处理。
 */
public class BasicShowcaseService implements ShowcaseService {

    /** 记忆服务（C P2：档案注册表与经历记录） */
    private final MemoryService memoryService;

    /**
     * 注入记忆服务。
     *
     * @param memoryService 生命记忆服务（档案唯一权威来源）
     */
    public BasicShowcaseService(MemoryService memoryService) {
        this.memoryService = Objects.requireNonNull(memoryService, "记忆服务不能为空");
    }

    @Override
    public List<ShowcaseEntry> listLegendaryEntries() {
        return memoryService.listAll().stream()
                .filter(this::isShowableLegendary)
                .map(ShowcaseEntry::of)
                .toList();
    }

    @Override
    public Optional<ShowcaseEntry> findEntry(UUID cropUuid) {
        Objects.requireNonNull(cropUuid, "cropUuid 不能为空");
        return memoryService.findMemory(cropUuid)
                .filter(this::isShowableLegendary)
                .map(ShowcaseEntry::of);
    }

    @Override
    public ShowcaseEntry buildEntry(CropMemory memory) {
        return ShowcaseEntry.of(memory);
    }

    /**
     * 可展示判定（与 {@link ShowcaseEntry#of} 校验口径完全一致，保证脏数据
     * 被过滤而不是在构建时抛异常）：已收获落档 + 传说突破成功 + 品质为
     * LEGENDARY（规则文档 §四十：LEGENDARY 不通过普通分数直接获得，只能
     * 经突破，与品质落档同步写入）+ 作物类型完整（反序列化防丢失）。
     */
    private boolean isShowableLegendary(CropMemory memory) {
        return memory.isCompleted()
                && memory.isLegendary()
                && memory.getQuality() == Quality.LEGENDARY
                && memory.getCropType() != null;
    }
}
