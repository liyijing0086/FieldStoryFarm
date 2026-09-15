package com.fieldstory.farm.service;

import com.fieldstory.farm.model.FarmRank;

/**
 * 农场评价服务（E 模块 P3；验收规范 §一百二十一，规则文档 §七十四/§七十六）。
 *
 * <p>只把 FarmScore 映射为 8 级评价（{@link FarmRank}），不含计分与毕业判定。
 * 永恒花园（147）是唯一毕业评价，但「达到某等级即可毕业」是<b>被禁止</b>的错误逻辑
 * （验收规范 §一百二十二）——毕业只由 {@link GraduationService} 依据 {@code FarmScore == 147} 判定。
 */
public interface FarmRankService {

    /** 由 FarmScore 映射评价。 */
    FarmRank rankOf(int farmScore);

    /** 评价显示名（等价于 {@code rankOf(farmScore).getDisplayName()}）。 */
    default String displayNameOf(int farmScore) {
        return rankOf(farmScore).getDisplayName();
    }
}
