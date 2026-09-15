package com.fieldstory.farm.model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 收集图鉴状态（E 模块 P3；验收规范 §一百一十~§一百一十八，规则文档 §七十一~§七十三）。
 *
 * <p>这是 E 存档聚合的一部分：把「玩家收集了什么」永久记录下来，使 FarmScore 与毕业判定
 * 在退出重进后不丢失（验收规范 §一百三十二 ⑤）。只保存状态，不含计分计算
 * （统一 Model 原则）——分值由 {@code service.FarmScoreService} 计算。
 *
 * <p>三类收集：
 * <ul>
 *   <li><b>作物图鉴</b>：{@link CropQualityKey} → {@link CollectionStatus}，
 *       目标 15 项（3 作物 × 5 品质），真正收获对应作物品质才 {@code COLLECTED}；</li>
 *   <li><b>装饰图鉴</b>：首次成功购买某类型装饰即永久解锁，存装饰类型 id（如 {@code D01}），
 *       目标 14 项（验收规范 §一百一十三）；</li>
 *   <li><b>传说图鉴</b>：首次获得某种传说作物即永久记录，存作物类型，目标 3 种
 *       （规则文档 §七十五.3）。</li>
 * </ul>
 *
 * <p>为抑制重复计分（验收规范 §一百二十），集合以「去重键」保存：同一装饰买多次只留一条，
 * 同一传说收获多株只记一次。{@link #UNDISCOVERED} 项不落库以保持存档精简。
 */
public class CollectionState {

    /** 作物图鉴：仅保存至少 DISCOVERED 的项（键唯一）。 */
    private final Map<CropQualityKey, CollectionStatus> crops = new LinkedHashMap<>();

    /** 装饰图鉴：已收集装饰类型 id（去重）。 */
    private final Set<String> decorations = new LinkedHashSet<>();

    /** 传说图鉴：已获得的传说作物类型（去重）。 */
    private final Set<CropType> legendaries = new LinkedHashSet<>();

    /** 读某项作物图鉴状态；未记录返回 {@link CollectionStatus#UNDISCOVERED}。 */
    public CollectionStatus cropStatus(CropType cropType, Quality quality) {
        if (cropType == null || quality == null) {
            return CollectionStatus.UNDISCOVERED;
        }
        return crops.getOrDefault(new CropQualityKey(cropType, quality),
                CollectionStatus.UNDISCOVERED);
    }

    /**
     * 推进某项作物图鉴状态（只前进不回退；已收集保持 {@link CollectionStatus#COLLECTED}）。
     *
     * @return 推进后的状态
     */
    public CollectionStatus advanceCrop(CropType cropType, Quality quality, CollectionStatus target) {
        if (cropType == null || quality == null) {
            return CollectionStatus.UNDISCOVERED;
        }
        CollectionStatus current = cropStatus(cropType, quality);
        CollectionStatus next = current.advanceTo(target);
        if (next != current) {
            crops.put(new CropQualityKey(cropType, quality), next);
        }
        return next;
    }

    /** 作物图鉴全部已记录项（可变，供 DAO 读写）。 */
    public Map<CropQualityKey, CollectionStatus> getCrops() {
        return crops;
    }

    /** 已收集的装饰类型 id 集合（可变）。 */
    public Set<String> getDecorations() {
        return decorations;
    }

    /** 已获得的传说作物类型集合（可变）。 */
    public Set<CropType> getLegendaries() {
        return legendaries;
    }
}
