package com.fieldstory.farm.service;

import com.fieldstory.farm.model.CollectionState;
import com.fieldstory.farm.model.CollectionStatus;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Quality;

/**
 * 收集图鉴服务（E 模块 P3；验收规范 §一百一十~§一百一十八，规则文档 §七十一~§七十三）。
 *
 * <p>职责：
 * <ul>
 *   <li>记录「已发现 / 已收集」事件，维护 {@link CollectionState}；</li>
 *   <li>提供图鉴进度查询（作物 x/15、装饰 x/14、传说 x/3）；</li>
 *   <li>保证「收集永久保存、重复收集不重复」——状态以去重键保存，收集只前进不回退。</li>
 * </ul>
 *
 * <p>本服务只维护图鉴，不计算 FarmScore（那是 {@link FarmScoreService} 的事），
 * 也不做套装成员判定（属 B 模块 {@code SetService}）。谁触发收集：
 * <ul>
 *   <li>作物：C 模块收获（真正获得对应作物 × 品质）→ {@link #collectCrop}；</li>
 *   <li>传说：C 模块传说突破成功 → {@link #collectLegendary}；</li>
 *   <li>装饰：B 模块首次成功购买某类型 → {@link #collectDecoration}。</li>
 * </ul>
 */
public interface CollectionService {

    /** 作物图鉴目标数：3 作物 × 5 品质 = 15（验收规范 §一百一十）。 */
    int CROP_TARGET = 15;

    /** 装饰图鉴目标数：14（验收规范 §一百一十三）。 */
    int DECORATION_TARGET = 14;

    /** 传说图鉴目标数：3（规则文档 §七十五.3）。 */
    int LEGENDARY_TARGET = 3;

    /**
     * 标记作物图鉴项为「已发现」（曾看见对应品质信息 / 满足部分条件，规则文档 §七十二）。
     * 不覆盖已收集状态。
     */
    void discoverCrop(CropType cropType, Quality quality);

    /**
     * 真正收获对应作物 × 品质：图鉴项永久置 {@link CollectionStatus#COLLECTED}
     * （验收规范 §一百一十一）。同项重复收集不产生额外效果。
     */
    void collectCrop(CropType cropType, Quality quality);

    /** 查询某项作物图鉴状态。 */
    CollectionStatus cropStatus(CropType cropType, Quality quality);

    /** 已收集作物图鉴项数（0~15）。 */
    int collectedCropCount();

    /** 首次成功购买某类型装饰 → 该装饰图鉴永久解锁（验收规范 §一百一十三）。重复购买无副作用。 */
    void collectDecoration(String decorationType);

    /** 某装饰类型是否已收集。 */
    boolean isDecorationCollected(String decorationType);

    /** 已收集装饰类型数（0~14）。 */
    int collectedDecorationCount();

    /** 首次获得某种传说作物 → 永久记录（规则文档 §七十五.3）。重复获得同种传说无副作用。 */
    void collectLegendary(CropType cropType);

    /** 某种作物的传说是否已获得。 */
    boolean isLegendaryCollected(CropType cropType);

    /** 已获得传说种类数（0~3）。 */
    int collectedLegendaryCount();

    /** 图鉴状态（存档聚合的一部分，永不为 null）。 */
    CollectionState getState();
}
