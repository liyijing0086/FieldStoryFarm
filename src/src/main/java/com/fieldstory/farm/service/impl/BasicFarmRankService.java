package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.model.FarmRank;
import com.fieldstory.farm.service.FarmRankService;

/**
 * {@link FarmRankService} 默认实现（E 模块 P3）。
 *
 * <p>纯映射，无状态；委托 {@link FarmRank#of(int)}，方便按接口注入与替换。
 */
public class BasicFarmRankService implements FarmRankService {

    @Override
    public FarmRank rankOf(int farmScore) {
        return FarmRank.of(farmScore);
    }
}
