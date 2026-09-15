package com.fieldstory.farm.service.economy.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.EventState;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.service.DailySimulationResult;
import com.fieldstory.farm.service.MemoryService;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 在线/离线共享的 CropMemory 事实记录器。
 *
 * <p>只消费 WorldSimulationService 已经产出的事实，不计算成长、天气、枯萎概率或事件效果。
 * 这样 MainController 与 OfflineSimulationService 不再分别维护 Memory 规则。
 */
public final class CropMemoryFactRecorder {

    private final MemoryService memoryService;

    public CropMemoryFactRecorder(MemoryService memoryService) {
        this.memoryService = Objects.requireNonNull(memoryService, "memoryService");
    }

    /** 记录本段刚成熟的作物。 */
    public void recordMatured(List<Crop> crops, long matureWorldTime) {
        if (crops == null) {
            return;
        }
        for (Crop crop : crops) {
            if (crop == null || crop.getCropUuid() == null) {
                continue;
            }
            memoryService.markMature(memoryOf(crop), matureWorldTime);
        }
    }


    /**
     * 在线/离线推进过程中及时记录当前事件，保证玩家在事件结束前收获时品质也能读取到事实。
     * 当前 CropMemory 事件列表按“是否经历过该类型”使用，因此同类型只写一次，避免每秒重复。
     */
    public void recordActiveEvent(Farm farm, EventState eventState) {
        if (farm == null || eventState == null
                || eventState.getEventType() == null
                || eventState.getEventType() == EventType.NONE) {
            return;
        }
        DailySimulationResult fact = new DailySimulationResult(
                -1L, null, eventState.getEventType(), 0, 0, 0,
                eventState.getStartWorldTime(), eventState.getEndWorldTime(), List.of());
        for (Soil soil : farm.getSoils()) {
            Crop crop = soil == null ? null : soil.getCrop();
            if (crop == null || crop.getCropUuid() == null) {
                continue;
            }
            CropMemory memory = memoryOf(crop);
            recordEventIfEligible(crop, memory, fact);
        }
    }

    /**
     * 记录一次日结事实：天气、事件、枯萎风险。
     *
     * <p>流星夜严格只记录给 plantWorldTime 位于 [start,end) 的作物；当前 CropMemory
     * 的事件字段被品质/传奇/故事按“是否经历过该类型”消费，因此同一类型只保留一次事实，
     * 避免持续事件在在线 tick 与日结两个入口重复写入。
     */
    public void recordDaily(Farm farm, DailySimulationResult result) {
        if (farm == null || result == null) {
            return;
        }
        Set<UUID> risk = new HashSet<>(result.witherRiskCropUuids());
        for (Soil soil : farm.getSoils()) {
            Crop crop = soil == null ? null : soil.getCrop();
            if (crop == null || crop.getCropUuid() == null) {
                continue;
            }
            CropMemory memory = memoryOf(crop);
            if (result.weather() != null) {
                switch (result.weather()) {
                    case RAIN -> memoryService.recordRain(memory);
                    case DROUGHT -> memoryService.recordDrought(memory, result.gameDay());
                    case GREEN_RAIN -> memoryService.recordGreenRain(memory);
                    case SUNNY -> { }
                }
            }
            if (risk.contains(crop.getCropUuid())) {
                memoryService.markWitherRisk(memory);
            }
            recordEventIfEligible(crop, memory, result);
        }
    }

    private void recordEventIfEligible(Crop crop, CropMemory memory, DailySimulationResult result) {
        EventType event = result.event();
        if (event == null || event == EventType.NONE) {
            return;
        }
        if (event == EventType.METEOR_SHOWER) {
            long start = result.eventStartWorldTime();
            long end = result.eventEndWorldTime();
            long planted = crop.getPlantWorldTime();
            if (start < 0 || end <= start || planted < start || planted >= end) {
                return;
            }
        }
        if (!memory.getEvents().contains(event)) {
            memoryService.recordEvent(memory, event);
        }
    }

    private CropMemory memoryOf(Crop crop) {
        return memoryService.findMemory(crop.getCropUuid())
                .orElseGet(() -> memoryService.createMemory(crop));
    }
}
