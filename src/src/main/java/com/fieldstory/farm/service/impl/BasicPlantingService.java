package com.fieldstory.farm.service.impl;

import com.fieldstory.farm.factory.CropFactory;
import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.service.PlantingResult;
import com.fieldstory.farm.service.PlantingService;
import com.fieldstory.farm.service.economy.EconomyService;

/**
 * {@link PlantingService} 基础实现（A 模块设计文档 §8.2）。
 *
 * <p>播种流程（验收规范 §十八、§十九、§二十）：state=TILLED 且种子充足 →
 * 经 {@link EconomyService#consumeSeed(CropType, int)} 消耗 1 颗种子 →
 * 经 {@link CropFactory} 创建 Crop 并记录播种时刻世界时间
 * plantWorldTime → 置 PLANTED。播种不扣金币；种子不足时土地与作物不变。
 *
 * <p>plantWorldTime 口径（决策 D14 适配，long 游戏小时）：
 * {@code gameClock.getGameDay() × 24 + gameClock.getGameHour()}，
 * 与 {@link Crop#getPlantWorldTime()} 的 long 字段一致
 * （验收规范 §二十 plantWorldTime 字段）。
 *
 * <p>种子库存变化统一走 EconomyService、不直接操作 Player 模型（跨模块契约）；
 * 播种时刻来自构造器注入的 {@link GameClock}（使用 D 正式接口），
 * 本实现不使用系统时间、不使用随机数。
 */
public class BasicPlantingService implements PlantingService {

    /** 一个游戏日的小时数：plantWorldTime 口径为 long 游戏小时（决策 D14） */
    public static final long HOURS_PER_GAME_DAY = 24;

    /** 经济服务：播种经统一经济入口消耗种子（B 文档 §17.2、§23 冻结表；决策 D08） */
    private final EconomyService economyService;

    /** 游戏时钟（使用 D 正式接口） */
    private final GameClock gameClock;

    /**
     * 构造器注入经济服务与游戏时钟（A 模块设计文档 §8.2）。
     *
     * @param economyService 经济服务（B 模块交付，负责种子库存校验与消耗）
     * @param gameClock      游戏时钟（D 模块交付，提供播种时刻游戏时间）
     */
    public BasicPlantingService(EconomyService economyService, GameClock gameClock) {
        this.economyService = economyService;
        this.gameClock = gameClock;
    }

    @Override
    public boolean canPlant(Soil soil, CropType type) {
        return soil.getState() == SoilState.TILLED
                && economyService.hasSeed(type, 1);
    }

    @Override
    public PlantingResult plant(Soil soil, CropType type) {
        if (soil.getState() != SoilState.TILLED) {
            return PlantingResult.NOT_TILLED;
        }
        // 先耗种后改状态：消耗失败土地与作物不变（A 模块设计文档 §8.2）
        if (!economyService.consumeSeed(type, 1)) {
            return PlantingResult.NO_SEED;
        }
        long plantWorldTime = gameClock.getGameDay() * HOURS_PER_GAME_DAY
                + gameClock.getGameHour();
        Crop crop = CropFactory.create(type, plantWorldTime);
        soil.setCrop(crop);
        soil.setState(SoilState.PLANTED);
        return PlantingResult.SUCCESS;
    }
}
