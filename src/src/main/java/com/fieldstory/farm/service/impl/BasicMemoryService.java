package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.EventType;
import com.fieldstory.farm.model.Quality;
import com.fieldstory.farm.service.LegendaryService;
import com.fieldstory.farm.service.MemoryService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link MemoryService} 基础实现（C 模块 品质与传说域，P2）。
 *
 * <p>P2 使用内存注册表（LinkedHashMap，保持创建顺序）；数据库化
 * （验收规范 §九十三 crop_memory 表）由 P3 DAO 层承接，本类接口不变。
 *
 * <p>故事生成（规则文档 §七十）：基础模板 + 关键经历标签，每句话
 * 都可追溯到档案中的真实记录，不使用随机文学。
 */
public class BasicMemoryService implements MemoryService {

    /** 档案注册表：cropUuid → CropMemory（保持创建顺序） */
    private final Map<UUID, CropMemory> memories = new LinkedHashMap<>();

    /** 世界时间(游戏小时)与游戏日的换算：1 游戏日 = 24 游戏小时（规则 §5.1） */
    private static final long HOURS_PER_DAY = 24;

    @Override
    public CropMemory createMemory(Crop crop) {
        Objects.requireNonNull(crop, "作物不能为空");
        CropMemory memory = new CropMemory(crop.getCropUuid(), crop.getCropType(), crop.getPlantWorldTime());
        save(memory);
        return memory;
    }

    @Override
    public Optional<CropMemory> findMemory(UUID cropUuid) {
        Objects.requireNonNull(cropUuid, "cropUuid 不能为空");
        return Optional.ofNullable(memories.get(cropUuid));
    }

    @Override
    public void save(CropMemory memory) {
        Objects.requireNonNull(memory, "档案不能为空");
        Objects.requireNonNull(memory.getCropUuid(), "档案 cropUuid 不能为空");
        memories.put(memory.getCropUuid(), memory);
    }

    @Override
    public List<CropMemory> listAll() {
        return List.copyOf(memories.values());
    }

    @Override
    public void recordManualWater(CropMemory memory, long gameDay) {
        Objects.requireNonNull(memory, "档案不能为空");
        memory.setManualWaterCount(memory.getManualWaterCount() + 1);
        long lastDroughtDay = memory.getLastDroughtGameDay();
        if (lastDroughtDay != -1 && gameDay == lastDroughtDay) {
            // 干旱当天主动浇水救援（金色麦穗条件 2，规则文档 §四十二）
            memory.setWaterRescueOnDroughtDay(true);
        }
    }

    @Override
    public void recordRain(CropMemory memory) {
        Objects.requireNonNull(memory, "档案不能为空");
        memory.setRainCount(memory.getRainCount() + 1);
    }

    @Override
    public void recordDrought(CropMemory memory, long gameDay) {
        Objects.requireNonNull(memory, "档案不能为空");
        memory.setDroughtCount(memory.getDroughtCount() + 1);
        memory.setLastDroughtGameDay(gameDay);
    }

    @Override
    public void recordGreenRain(CropMemory memory) {
        Objects.requireNonNull(memory, "档案不能为空");
        memory.setGreenRainCount(memory.getGreenRainCount() + 1);
    }

    @Override
    public void recordFertilizer(CropMemory memory) {
        Objects.requireNonNull(memory, "档案不能为空");
        memory.setFertilizerCount(memory.getFertilizerCount() + 1);
    }

    @Override
    public void recordEvent(CropMemory memory, EventType eventType) {
        Objects.requireNonNull(memory, "档案不能为空");
        if (eventType == null || eventType == EventType.NONE) {
            return;
        }
        memory.getEvents().add(eventType);
    }

    @Override
    public void markMature(CropMemory memory, long matureWorldTime) {
        Objects.requireNonNull(memory, "档案不能为空");
        memory.setMatureWorldTime(matureWorldTime);
    }

    @Override
    public void markWitherRisk(CropMemory memory) {
        Objects.requireNonNull(memory, "档案不能为空");
        memory.setWitherRisk(true);
    }

    @Override
    public String completeHarvest(CropMemory memory, Quality quality, boolean legendary, long harvestWorldTime) {
        Objects.requireNonNull(memory, "档案不能为空");
        Objects.requireNonNull(quality, "品质不能为空");
        memory.setQuality(quality);
        memory.setLegendary(legendary);
        memory.setHarvestWorldTime(harvestWorldTime);
        String story = generateFinalStory(memory);
        memory.setFinalStory(story);
        save(memory);
        return story;
    }

    @Override
    public String generateFinalStory(CropMemory memory) {
        Objects.requireNonNull(memory, "档案不能为空");
        Quality quality = memory.getQuality();
        if (quality == null) {
            throw new IllegalStateException("品质未落档，无法生成最终故事");
        }

        // 基础模板 + 关键经历标签（规则文档 §七十），每句对应真实记录
        StringBuilder story = new StringBuilder();
        story.append("这株").append(memory.getCropType().getDisplayName())
                .append("于第").append(gameDayOf(memory.getPlantWorldTime()))
                .append("天播下种子，开始了它的一生。");

        if (memory.getRainCount() > 0) {
            story.append("它沐浴过").append(memory.getRainCount()).append("场雨。");
        }
        if (memory.getDroughtCount() > 0) {
            story.append("它经历过").append(memory.getDroughtCount()).append("次干旱的考验。");
            if (memory.isWaterRescueOnDroughtDay()) {
                story.append("在土地即将失去水分的时候，玩家及时为它浇下了水。");
            }
        }
        if (memory.getGreenRainCount() > 0) {
            story.append("它沐浴过").append(memory.getGreenRainCount()).append("场珍贵的绿雨。");
        }
        if (memory.getManualWaterCount() > 0) {
            story.append("玩家主动为它浇水").append(memory.getManualWaterCount()).append("次。");
        }
        if (memory.getFertilizerCount() > 0) {
            story.append("它被精心施肥").append(memory.getFertilizerCount()).append("次。");
        }
        if (memory.isWitherRisk()) {
            story.append("它曾面临枯萎的危险，最终顽强地挺了过来。");
        }
        for (EventType event : memory.getEvents()) {
            story.append(eventTag(event));
        }

        if (memory.isLegendary()) {
            story.append("最终在收获时化作")
                    .append(LegendaryService.legendaryName(memory.getCropType()))
                    .append("。");
        } else {
            story.append("最终以").append(quality.getDisplayName()).append("品质完成收获。");
        }
        return story.toString();
    }

    /** 事件标签文案（对应事件名，可追溯事件列表记录）。 */
    private String eventTag(EventType event) {
        return switch (event) {
            case METEOR_SHOWER -> "它在流星划过的夜晚默默积蓄力量。";
            case MYSTERY_MERCHANT -> "神秘的商贩曾从它的田垄边经过。";
            case ANIMAL_VISIT -> "有小动物来探望过它。";
            case RAINBOW_DAY -> "它见过天边升起的彩虹。";
            case NONE -> "";
        };
    }

    /** 世界时间(游戏小时) → 游戏日（plantWorldTime = gameDay×24 + hour，决策 D14）。 */
    private long gameDayOf(long worldTime) {
        return worldTime / HOURS_PER_DAY;
    }
}
