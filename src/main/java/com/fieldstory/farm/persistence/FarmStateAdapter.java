package com.fieldstory.farm.persistence;

import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.PlotState;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.impl.BasicCrop;
import com.fieldstory.farm.util.GameConstants;

import java.util.Objects;
import java.util.UUID;

/**
 * 运行态农场与存档聚合之间的适配层（E 模块 P1；设计文档 D3）。
 *
 * <p>存档聚合 {@link GameState} 只保存 {@link PlotState} 快照（字符串 + 无业务），
 * 而运行中的游戏持有 A 模块的 {@link Farm}/{@link Soil}/{@link Crop}（枚举 + long 时间）。
 * 本类负责两者之间的双向映射，让「退出自动保存」「进入从库读档」对农场格仔与作物同样生效：
 * <ul>
 *   <li>{@link #capture(GameState, Farm)}：把运行中农场的每块土地与作物写入
 *       {@link GameState#getPlots()}（存档前调用）；</li>
 *   <li>{@link #restore(GameState, Farm)}：把 {@code GameState.plots} 还原到农场
 *       （读档后调用，先整体重置再按快照覆盖，保证幂等）。</li>
 * </ul>
 *
 * <p><b>口径约定：</b>
 * <ul>
 *   <li>{@code growthProgress} 沿用 A 侧内部口径 0~100，不做缩放；</li>
 *   <li>{@code plantWorldTime}/{@code lastManualWaterGameDay} 在 A 侧是 long（游戏小时 /
 *       游戏日），快照以十进制字符串无损保存（E 不做时间换算，与 {@code CropDao} 注释一致）；</li>
 *   <li>枚举以 {@code name()} 存字符串；读回时遇到无法识别的值按「未知即缺省」处理
 *       （枚举置 null / 跳过），绝不让坏数据导致读档崩溃；</li>
 *   <li>{@code cropUuid} 是「该格是否存在作物」的判定依据（{@link PlotState#hasCrop()}），
 *       故两侧都保证其存在：缺失或非法时补生成新 id，避免作物被静默丢弃。</li>
 * </ul>
 *
 * <p>本类只做状态搬运，不含任何游戏计算（统一 Model 原则）。
 */
public final class FarmStateAdapter {

    /** 从 0 开始的合法行/列下界。 */
    private static final int MIN_COORDINATE = 0;

    private FarmStateAdapter() {
        // 工具类，禁止实例化
    }

    /**
     * 存档前回填：把运行中农场的全部土地/作物写入 {@code state.plots}。
     *
     * <p>每次调用先清空旧快照再整体重建，避免残留上一次的快照造成“幽灵地块”。
     * 全部 FARM_PLOT 格都会写入（含 EMPTY 空格），符合「每块土地完整状态」的验收口径。
     *
     * @param state 存档聚合（写入目标）
     * @param farm  运行中的农场
     */
    public static void capture(GameState state, Farm farm) {
        Objects.requireNonNull(state, "state 不能为空");
        Objects.requireNonNull(farm, "farm 不能为空");

        state.getPlots().clear();
        for (Soil soil : farm.getSoils()) {
            if (soil != null) {
                state.getPlots().add(toPlotState(soil));
            }
        }
    }

    /**
     * 读档后还原：把 {@code state.plots} 覆盖到农场。
     *
     * <p>先把农场所有土地重置为 EMPTY / 无作物，再按快照逐格覆盖，因此对同一农场
     * 重复调用结果一致（幂等）。快照中坐标越界或落在装饰区（无 Soil）的条目会被跳过，
     * 不会抛异常。
     *
     * @param state 存档聚合（读取来源）
     * @param farm  目标农场
     */
    public static void restore(GameState state, Farm farm) {
        Objects.requireNonNull(state, "state 不能为空");
        Objects.requireNonNull(farm, "farm 不能为空");

        for (Soil soil : farm.getSoils()) {
            soil.setState(SoilState.EMPTY);
            soil.setCrop(null);
        }

        for (PlotState plot : state.getPlots()) {
            if (plot == null) {
                continue;
            }
            Soil soil = soilAt(farm, plot.getRow(), plot.getColumn());
            if (soil == null) {
                continue;
            }
            SoilState soilState = parseEnum(SoilState.class, plot.getState());
            if (soilState != null) {
                soil.setState(soilState);
            }
            soil.setCrop(plot.hasCrop() ? toCrop(plot) : null);
        }
    }

    /** 取指定坐标的 Soil；越界或装饰区（无 Soil）返回 null。 */
    private static Soil soilAt(Farm farm, int row, int column) {
        if (row < MIN_COORDINATE || row >= GameConstants.MAP_ROWS
                || column < MIN_COORDINATE || column >= GameConstants.MAP_COLS) {
            return null;
        }
        return farm.getSoil(row, column);
    }

    /** A 侧 Soil → 存档快照（含作物）。 */
    private static PlotState toPlotState(Soil soil) {
        PlotState plot = new PlotState();
        plot.setPlotId(soil.getRow() + "," + soil.getColumn());
        plot.setRow(soil.getRow());
        plot.setColumn(soil.getColumn());

        SoilState soilState = soil.getState();
        plot.setState(soilState == null ? null : soilState.name());

        Crop crop = soil.getCrop();
        if (crop != null) {
            // cropUuid 非空是 PlotState.hasCrop() 的判定依据：缺失时补一个随机 id，
            // 保证"内存里有作物"就一定会被持久化，不因 id 缺失而静默丢作物。
            plot.setCropUuid(crop.getCropUuid() == null
                    ? UUID.randomUUID().toString()
                    : crop.getCropUuid().toString());
            plot.setCropType(crop.getCropType() == null ? null : crop.getCropType().name());
            plot.setGrowthStage(crop.getGrowthStage() == null ? null : crop.getGrowthStage().name());
            plot.setGrowthProgress(crop.getGrowthProgress());
            plot.setPlantWorldTime(Long.toString(crop.getPlantWorldTime()));
            plot.setManualWaterCount(crop.getManualWaterCount());
            plot.setLastManualWaterGameDay(Long.toString(crop.getLastManualWaterGameDay()));
        }
        return plot;
    }

    /** 存档快照 → A 侧 Crop（调用前须已确认 {@link PlotState#hasCrop()}）。 */
    private static Crop toCrop(PlotState plot) {
        BasicCrop crop = new BasicCrop();
        crop.setCropUuid(uuidOrGenerate(plot.getCropUuid()));
        crop.setCropType(parseEnum(CropType.class, plot.getCropType()));
        crop.setGrowthStage(parseEnum(GrowthStage.class, plot.getGrowthStage()));
        crop.setGrowthProgress(plot.getGrowthProgress());
        crop.setPlantWorldTime(parseLong(plot.getPlantWorldTime(), 0L));
        crop.setManualWaterCount(plot.getManualWaterCount());
        crop.setLastManualWaterGameDay(parseLong(plot.getLastManualWaterGameDay(), -1L));
        return crop;
    }

    /** 枚举名 → 枚举；空值或无法识别返回 null（坏数据不阻断读档）。 */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, name.trim());
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** 字符串 → UUID；空值或格式非法时生成新 id（保证作物不因坏 id 丢失）。 */
    private static UUID uuidOrGenerate(String value) {
        if (value != null && !value.isBlank()) {
            try {
                return UUID.fromString(value.trim());
            } catch (IllegalArgumentException malformed) {
                // 坏 id 不足以丢弃作物：降级为新建 id
            }
        }
        return UUID.randomUUID();
    }

    /** 十进制字符串 → long；空值或非法返回 fallback（时间字段的缺省哨兵）。 */
    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException malformed) {
            return fallback;
        }
    }
}
