package com.fieldstory.farm.factory;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.impl.BasicCrop;

import java.util.UUID;

/**
 * <p>创建一株全新作物的初始状态（验收规范 §二十 字段清单）：
 * 随机 cropUuid、growthStage=SEED、growthProgress=0、
 * manualWaterCount=0、lastManualWaterGameDay=-1（哨兵：从未浇水）、
 * 天气记录 5 字段 0/0/0/-1/0（P1 验收规范 §五十，D16 哨兵口径）。
 */
public final class CropFactory {

    private CropFactory() {
        // 工厂不拆分、只提供静态创建，禁止实例化
    }

    /**
     * 创建初始作物。
     *
     * @param type            作物类型
     * @param plantWorldTime  播种时刻世界时间（游戏小时，来自 GameClock.getWorldTime）
     * @return 初始状态为 SEED 的新作物
     */
    public static Crop create(CropType type, long plantWorldTime) {
        BasicCrop crop = new BasicCrop();
        crop.setCropUuid(UUID.randomUUID());
        crop.setCropType(type);
        crop.setGrowthStage(GrowthStage.SEED);
        crop.setGrowthProgress(0.0);
        crop.setPlantWorldTime(plantWorldTime);
        crop.setManualWaterCount(0);
        crop.setLastManualWaterGameDay(-1);// -1 哨兵：从未浇水（游戏日从 0 起，不能用 0 表示"未浇"）
        crop.setDroughtCount(0);
        crop.setRainCount(0);
        crop.setGreenRainCount(0);
        crop.setLastHydratedWorldTime(-1);// -1 哨兵：无补水记录（D16，与 lastManualWaterGameDay 对称）
        crop.setDroughtStreak(0);
        return crop;
    }
}
