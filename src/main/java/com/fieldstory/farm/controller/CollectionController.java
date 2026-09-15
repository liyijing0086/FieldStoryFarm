package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.service.CollectionService;
import com.fieldstory.farm.service.FarmRankService;
import com.fieldstory.farm.service.FarmScoreService;
import com.fieldstory.farm.service.SetService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 收集图鉴控制器（E 模块 P3；验收规范 §一百二十七/§一百二十八，规则文档 §七十一~§七十七）。
 *
 * <p>把 E 的收集 / FarmScore / FarmRank / 套装四个服务聚合成图鉴界面所需的只读数据：
 * <ul>
 *   <li>作物图鉴 x/15、装饰图鉴 x/14、传说 x/3、套装 x/3（验收规范 §一百二十七）；</li>
 *   <li>FarmScore x/147 与当前 8 级评价（规则文档 §七十五/§七十六）；</li>
 *   <li>未完成目标的可读提示（验收规范 §一百二十八），避免毕业系统依赖外部攻略。</li>
 * </ul>
 *
 * <p>本控制器<b>不写任何状态</b>：只读四个服务、把结果整理给 {@code view.CollectionView}；
 * 计分/套装判定仍分别属 {@code FarmScoreService} / {@code SetService}，本层不重复实现。
 */
public class CollectionController {

    private final CollectionService collection;
    private final FarmScoreService farmScore;
    private final FarmRankService farmRank;
    private final SetService sets;

    /**
     * 绑定四个收集域服务构造。
     *
     * @param collection 图鉴服务（作物/装饰/传说收集记录）
     * @param farmScore  FarmScore 计算入口
     * @param farmRank   分数 → 8 级评价映射
     * @param sets       套装收集/激活状态
     */
    public CollectionController(CollectionService collection,
                                FarmScoreService farmScore,
                                FarmRankService farmRank,
                                SetService sets) {
        this.collection = Objects.requireNonNull(collection, "collection 不能为空");
        this.farmScore = Objects.requireNonNull(farmScore, "farmScore 不能为空");
        this.farmRank = Objects.requireNonNull(farmRank, "farmRank 不能为空");
        this.sets = Objects.requireNonNull(sets, "sets 不能为空");
    }

    // ------------------------------------------------------------------
    // 图鉴进度（验收规范 §一百二十七）
    // ------------------------------------------------------------------

    /** 已收集作物图鉴项数（0~15）。 */
    public int cropCollected() {
        return collection.collectedCropCount();
    }

    /** 作物图鉴目标数（15 = 3 作物 × 5 品质）。 */
    public int cropTarget() {
        return CollectionService.CROP_TARGET;
    }

    /** 已收集装饰图鉴种类数（0~14）。 */
    public int decorationCollected() {
        return collection.collectedDecorationCount();
    }

    /** 装饰图鉴目标数（14）。 */
    public int decorationTarget() {
        return CollectionService.DECORATION_TARGET;
    }

    /** 已获得传说种类数（0~3）。 */
    public int legendaryCollected() {
        return collection.collectedLegendaryCount();
    }

    /** 传说目标数（3）。 */
    public int legendaryTarget() {
        return CollectionService.LEGENDARY_TARGET;
    }

    /** 已收集套装数（0~3）。 */
    public int setCollected() {
        return sets.collectedCount();
    }

    /** 套装目标数（3）。 */
    public int setTarget() {
        return sets.allSets().size();
    }

    // ------------------------------------------------------------------
    // FarmScore 与评价（规则文档 §七十五/§七十六）
    // ------------------------------------------------------------------

    /** 当前 FarmScore 总分（0~147）。 */
    public int farmScore() {
        return farmScore.totalScore();
    }

    /** FarmScore 满分（147，唯一毕业线）。 */
    public int maxFarmScore() {
        return FarmScoreService.MAX_SCORE;
    }

    /** 当前农场评价显示名（8 级，规则文档 §七十四）。 */
    public String currentRankName() {
        return farmRank.displayNameOf(farmScore.totalScore());
    }

    // ------------------------------------------------------------------
    // 目标提示（验收规范 §一百二十八）
    // ------------------------------------------------------------------

    /** 某种作物的专属传说是否已获得。 */
    public boolean isLegendaryCollected(CropType cropType) {
        return collection.isLegendaryCollected(cropType);
    }

    /**
     * 未完成目标的可读提示（作物 / 装饰 / 传说 / 套装 / FarmScore 五项）。
     *
     * <p>数值严格来自规则文档：传说条件与基础概率见 §四十二~§四十四，FarmScore 结构见 §七十五。
     */
    public List<String> goalHints() {
        List<String> hints = new ArrayList<>();
        hints.add(farmScore() < maxFarmScore()
                ? "FarmScore：" + farmScore() + "/" + maxFarmScore() + "，满 147 触发「永恒花园」毕业"
                : "FarmScore：147/147，已达成永恒花园毕业");
        hints.add(cropCollected() < cropTarget()
                ? "作物图鉴：还差 " + (cropTarget() - cropCollected()) + " 项（真正收获对应作物×品质才解锁）"
                : "作物图鉴：15/15 已集齐");
        hints.add(decorationCollected() < decorationTarget()
                ? "装饰图鉴：还差 " + (decorationTarget() - decorationCollected()) + " 种（首次成功购买即永久解锁）"
                : "装饰图鉴：14/14 已集齐");
        hints.add(setCollected() < setTarget()
                ? "套装：还差 " + (setTarget() - setCollected()) + " 套（四名成员须全部拥有并放置）"
                : "套装：3/3 已完成");
        for (CropType type : CropType.values()) {
            hints.add(legendaryGoalHint(type));
        }
        return hints;
    }

    /** 单个传说目标提示（规则文档 §四十二~§四十四）。 */
    private String legendaryGoalHint(CropType type) {
        String marker = collection.isLegendaryCollected(type) ? "[已收集] " : "[未完成] ";
        return marker + switch (type) {
            case WHEAT -> "金色麦穗：至少经历1次干旱 + 干旱当天主动浇水救援 + 主动浇水≥2次 "
                    + "+ 品质评分≥110，基础突破30%";
            case CORN -> "彩虹玉米：经历绿雨≥1次 + 施肥≥1次 + 品质评分≥115，基础突破40%";
            case CARROT -> "巨龙胡萝卜：经历绿雨≥1次 + 主动浇水≥2次 + 品质评分≥120，基础突破35%";
        };
    }
}
