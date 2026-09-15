package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.BuffSnapshot;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Decoration;
import com.fieldstory.farm.model.economy.DecorationPlacementResult;
import com.fieldstory.farm.service.BuffService;
import com.fieldstory.farm.service.DecorationService;

import java.util.List;
import java.util.Objects;

/** B 模块 P1 装饰控制器。 */
public class DecorationController {

    private final DecorationService decorationService;
    private final BuffService buffService;
    private Runnable onChanged = () -> { };

    public DecorationController(DecorationService decorationService,
                                BuffService buffService) {
        this.decorationService = Objects.requireNonNull(decorationService, "decorationService");
        this.buffService = Objects.requireNonNull(buffService, "buffService");
    }

    /** 可由 E 装配层注入“刷新 + 自动保存”回调。 */
    public void setOnChanged(Runnable callback) {
        this.onChanged = callback == null ? () -> { } : callback;
    }

    public void addOnChanged(Runnable callback) {
        if (callback == null) {
            return;
        }
        Runnable previous = this.onChanged;
        this.onChanged = () -> {
            previous.run();
            callback.run();
        };
    }

    public List<Decoration> getOwnedDecorations() {
        return decorationService.getOwnedDecorations();
    }

    public List<Decoration> getPlacedDecorations() {
        return decorationService.getPlacedDecorations();
    }

    public DecorationPlacementResult place(Decoration decoration, int row, int column) {
        DecorationPlacementResult result = decorationService.place(decoration, row, column);
        fireIfSuccess(result);
        return result;
    }

    public DecorationPlacementResult move(Decoration decoration, int row, int column) {
        DecorationPlacementResult result = decorationService.move(decoration, row, column);
        fireIfSuccess(result);
        return result;
    }

    public DecorationPlacementResult removeFromFarm(Decoration decoration) {
        DecorationPlacementResult result = decorationService.removeFromFarm(decoration);
        fireIfSuccess(result);
        return result;
    }

    public BuffSnapshot snapshot(int row, int column, CropType cropType) {
        return buffService.getSnapshot(row, column, cropType);
    }

    private void fireIfSuccess(DecorationPlacementResult result) {
        if (result == DecorationPlacementResult.SUCCESS) {
            onChanged.run();
        }
    }

    public static String messageFor(DecorationPlacementResult result) {
        if (result == null) {
            return "未选择装饰";
        }
        return switch (result) {
            case SUCCESS -> "操作成功";
            case NOT_OWNED -> "尚未拥有该装饰";
            case ALREADY_PLACED -> "该装饰已经放置";
            case NOT_PLACED -> "该装饰当前未放置";
            case OUT_OF_BOUNDS -> "超出地图范围";
            case NOT_DECORATION_AREA -> "只能放在装饰区";
            case CELL_OCCUPIED -> "该位置已被其他装饰占用";
            case INVALID_SIZE -> "装饰尺寸无效";
        };
    }
}
