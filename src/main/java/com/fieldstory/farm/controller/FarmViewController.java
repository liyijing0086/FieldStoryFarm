package com.fieldstory.farm.controller;

import com.fieldstory.farm.manager.SceneManager;
import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.service.HarvestResult;
import com.fieldstory.farm.service.HarvestService;
import com.fieldstory.farm.service.LandService;
import com.fieldstory.farm.service.PlantingResult;
import com.fieldstory.farm.service.PlantingService;
import com.fieldstory.farm.service.ReclaimResult;
import com.fieldstory.farm.service.WateringResult;
import com.fieldstory.farm.service.WateringService;
import com.fieldstory.farm.view.FarmView;
import javafx.scene.Node;
import javafx.scene.control.Button;

import java.util.ArrayList;
import java.util.List;

/**
 * 农场视图控制器（A 模块 P0 视图层；脚手架 §七.4 controller 职责：
 * 接收用户操作 → 调用 Service → 刷新界面）。
 *
 * <p>点击 FARM_PLOT 格 → 选中并弹出操作菜单（UI规范 §11、§12），
 * 按土壤状态决定按钮：EMPTY→开垦、TILLED→播种、PLANTED→浇水、MATURE→收获
 * （{@link #actionsFor}）。业务一律走 A 的 Service：
 * 开垦→LandService.reclaim、播种→PlantingService.plant、浇水→WateringService.water。
 *
 * <p>收获：C 模块 BasicHarvestService 已交付，本控制器只做接线——
 * 调用注入的 {@link HarvestService#harvest(Soil)} 并按结果码刷新界面；
 * 收获业务（读取售价、金币入账、土地回退）全部在 C 的服务内完成
 * （决策 D09：土地回退经 A 的 LandService.removeCropAndSetTilled）。
 *
 * <p>浇水 currentGameDay 不再用 P0 常量：当前游戏日来自 D 的
 * GameClock.getGameDay()，浇水时传入 WateringService，
 * 刷新视图前同步给 FarmView（{@link FarmView#setCurrentGameDay}）。
 *
 * <p>纯静态函数 {@link #actionsFor} / {@link #actionMessageFor} 只返回
 * 枚举/字符串，不依赖 JavaFX 线程，可在无 GUI 线程下单测。
 */
public class FarmViewController {


    /** 农场模型（12×12，中心 8×8 为 FARM_PLOT） */
    private final Farm farm;

    /** 开垦服务（A：EMPTY→TILLED） */
    private final LandService landService;

    /** 播种服务（A：TILLED→PLANTED，消耗 1 颗种子） */
    private final PlantingService plantingService;

    /** 浇水服务（A：三重校验 + 浇水计数） */
    private final WateringService wateringService;

    /** 收获服务（C：MATURE 收获，售价入账 + 土地回退） */
    private final HarvestService harvestService;

    /** 游戏时钟（D：当前游戏日，浇水/播种视图刷新基准） */
    private final GameClock gameClock;

    /** 农场画布视图 */
    private final FarmView farmView;

    /**
     * 装配视图、四个 Service（三个 A + 一个 C 收获）与 D 的时钟。
     *
     * @param farm            农场模型
     * @param landService     开垦服务
     * @param plantingService 播种服务
     * @param wateringService 浇水服务
     * @param harvestService  收获服务
     * @param gameClock       游戏时钟
     */
    public FarmViewController(
            Farm farm,
            LandService landService,
            PlantingService plantingService,
            WateringService wateringService,
            HarvestService harvestService,
            GameClock gameClock) {
        this.farm = farm;
        this.landService = landService;
        this.plantingService = plantingService;
        this.wateringService = wateringService;
        this.harvestService = harvestService;
        this.gameClock = gameClock;
        this.farmView = new FarmView(farm);
        this.farmView.setOnTileSelected(this::onTileSelected);
    }

    /** 农场画布视图。 */
    public FarmView getView() {
        return farmView;
    }

    /**
     * 挂载到场景中央：调用 E 的 SceneManager 完成组装
     * （不修改 E 的 SceneManager/MainController/MainApplication 文件）。
     */
    public void mountToScene() {
        SceneManager.getInstance().mount(SceneManager.Slot.CENTER, farmView);
    }

    // ==================== 纯静态函数（可无 GUI 线程单测） ====================

    /**
     * 纯函数：按土壤状态推导可执行动作。
     *
     * <p>EMPTY→开垦、TILLED→播种、PLANTED（未成熟）→浇水、
     * PLANTED 且作物 MATURE→收获；装饰区（null）与 LOCKED 无动作
     * （P0 不产生 LOCKED，验收规范 §十四）。
     *
     * @param soil 目标格土地（装饰区为 null）
     * @return 动作列表（0~1 个）
     */
    public static List<FarmAction> actionsFor(Soil soil) {
        if (soil == null) {
            return List.of();
        }
        switch (soil.getState()) {
            case EMPTY:
                return List.of(FarmAction.RECLAIM);
            case TILLED:
                return List.of(FarmAction.PLANT);
            case PLANTED:
                Crop crop = soil.getCrop();
                if (crop != null && crop.getGrowthStage() == GrowthStage.MATURE) {
                    return List.of(FarmAction.HARVEST);
                }
                return List.of(FarmAction.WATER);
            case LOCKED:
            default:
                return List.of();
        }
    }

    /**
     * 纯函数：开垦结果码 → 用户提示文案
     * （设计文档 D13：结果枚举不挂文案，文案由 Controller 映射）。
     */
    public static String actionMessageFor(ReclaimResult result) {
        switch (result) {
            case SUCCESS:
                return "开垦成功";
            case NOT_EMPTY:
                return "该格不是空地，无法开垦";
            case NO_GOLD:
                return "金币不足，开垦需要5金币";
            default:
                return "开垦失败";
        }
    }

    /**
     * 纯函数：播种结果码 → 用户提示文案
     * （设计文档 D13：结果枚举不挂文案，文案由 Controller 映射）。
     */
    public static String actionMessageFor(PlantingResult result) {
        switch (result) {
            case SUCCESS:
                return "播种成功";
            case NOT_TILLED:
                return "该格未开垦，无法播种";
            case NO_SEED:
                return "种子不足，无法播种";
            default:
                return "播种失败";
        }
    }

    /**
     * 纯函数：浇水结果码 → 用户提示文案
     * （设计文档 D13；WATER_LIMIT_REACHED 文案为决策 D11 UI 建议：
     * "这株作物已经不需要浇水了"）。
     */
    public static String actionMessageFor(WateringResult result) {
        switch (result) {
            case SUCCESS:
                return "浇水成功";
            case SEED_STAGE:
                return "种子阶段还不能浇水";
            case ALREADY_WATERED_TODAY:
                return "今天已经浇过水了";
            case WATER_LIMIT_REACHED:
                return "这株作物已经不需要浇水了";
            default:
                return "浇水失败";
        }
    }

    /**
     * 纯函数：收获结果码 → 用户提示文案
     * （C 模块 BasicHarvestService 结果码；设计文档 D13 文案由 Controller 映射）。
     */
    public static String actionMessageFor(HarvestResult result) {
        switch (result) {
            case SUCCESS:
                return "收获成功";
            case NOT_PLANTED:
            case NO_CROP:
                return "该格没有可收获的作物";
            case NOT_MATURE:
                return "作物还没成熟";
            default:
                return "收获失败";
        }
    }

    // ==================== 交互（UI规范 §11、§12） ====================

    /** 点击格回调：选中 + 按状态弹菜单；装饰区点击收起菜单。 */
    private void onTileSelected(Soil soil) {
        // 选中前同步当前游戏日（Tooltip"今日已浇"判定基准，来自 D 的 GameClock）
        farmView.setCurrentGameDay(gameClock.getGameDay());
        if (soil == null) {
            farmView.hideMenu();
            farmView.selectTile(null);
            return;
        }
        farmView.selectTile(soil);
        List<FarmAction> actions = actionsFor(soil);
        if (actions.isEmpty()) {
            farmView.hideMenu();
            return;
        }
        farmView.showMenuFor(soil, buildButtons(soil, actions));
    }

    /** 按动作生成菜单按钮（UI规范 §12；HARVEST 由 C 的 HarvestService 执行）。 */
    private List<Node> buildButtons(Soil soil, List<FarmAction> actions) {
        List<Node> buttons = new ArrayList<>();
        for (FarmAction action : actions) {
            Button button = farmView.createMenuButton(labelFor(action));
            button.setOnAction(event -> perform(soil, action));
            buttons.add(button);
        }
        return buttons;
    }

    /** 动作按钮文案（UI规范 §12 操作菜单）。 */
    private static String labelFor(FarmAction action) {
        switch (action) {
            case RECLAIM:
                return "开垦";
            case PLANT:
                return "播种";
            case WATER:
                return "浇水";
            case HARVEST:
                return "收获";
            default:
                return "";
        }
    }

    /** 执行动作；完成后刷新对应格渲染，失败经 Tooltip 提示（任务约束）。 */
    private void perform(Soil soil, FarmAction action) {
        switch (action) {
            case RECLAIM:
                reclaim(soil);
                break;
            case PLANT:
                showSeedButtons(soil);
                break;
            case WATER:
                water(soil);
                break;
            case HARVEST:
                harvest(soil);
                break;
            default:
                break;
        }
    }

    /** 开垦：EMPTY→TILLED（验收规范 §十五）。 */
    private void reclaim(Soil soil) {
        ReclaimResult result = landService.reclaim(soil);
        if (result == ReclaimResult.SUCCESS) {
            farmView.hideMenu();
            farmView.setCurrentGameDay(gameClock.getGameDay());
            farmView.refreshTile(soil);
        } else {
            farmView.showTip(soil, actionMessageFor(result));
        }
    }

    /**
     * 播种入口：弹出种子选择按钮（小麦10金/玉米15金/胡萝卜20金，
     * 种子价取自 CropType.getSeedPrice，单一数据源 D12，不硬编码）。
     */
    private void showSeedButtons(Soil soil) {
        List<Node> buttons = new ArrayList<>();
        for (CropType type : CropType.values()) {
            Button button = farmView.createMenuButton(
                    type.getDisplayName() + " " + type.getSeedPrice() + "金");
            button.setOnAction(event -> plant(soil, type));
            buttons.add(button);
        }
        farmView.showMenuFor(soil, buttons);
    }

    /** 播种：TILLED→PLANTED，消耗 1 颗种子（验收规范 §十八、§十九）。 */
    private void plant(Soil soil, CropType type) {
        PlantingResult result = plantingService.plant(soil, type);
        if (result == PlantingResult.SUCCESS) {
            farmView.hideMenu();
            farmView.setCurrentGameDay(gameClock.getGameDay());
            farmView.refreshTile(soil);
        } else {
            farmView.showTip(soil, actionMessageFor(result));
        }
    }

    /** 浇水：三重校验后计数（验收规范 §二十六、§二十七、§二十八）。 */
    private void water(Soil soil) {
        Crop crop = soil.getCrop();
        if (crop == null) {
            return;
        }
        WateringResult result = wateringService.water(crop, gameClock.getGameDay());
        if (result == WateringResult.SUCCESS) {
            farmView.hideMenu();
            farmView.setCurrentGameDay(gameClock.getGameDay());
            farmView.refreshTile(soil);
        } else {
            farmView.showTip(soil, actionMessageFor(result));
        }
    }

    /**
     * 收获：MATURE→成功（售价入账 + 土地回退 TILLED，验收规范 §三十一 由
     * C 的 BasicHarvestService 完成；本控制器只接线与刷新，决策 D09）。
     */
    private void harvest(Soil soil) {
        HarvestResult result = harvestService.harvest(soil);
        if (result == HarvestResult.SUCCESS) {
            farmView.hideMenu();
            farmView.refreshTile(soil);
        } else {
            farmView.showTip(soil, actionMessageFor(result));
        }
    }
}
