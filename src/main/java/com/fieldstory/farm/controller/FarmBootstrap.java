package com.fieldstory.farm.controller;

import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.manager.GameManager;
import com.fieldstory.farm.manager.SceneManager;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.FarmGameModel;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.SoilState;
import com.fieldstory.farm.model.economy.PurchaseResult;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.model.impl.BasicGameClock;
import com.fieldstory.farm.service.GrowthService;
import com.fieldstory.farm.service.HarvestService;
import com.fieldstory.farm.service.LandService;
import com.fieldstory.farm.service.PlantingService;
import com.fieldstory.farm.service.WateringService;
import com.fieldstory.farm.service.economy.EconomyService;
import com.fieldstory.farm.service.economy.impl.EconomyServiceImpl;
import com.fieldstory.farm.service.impl.BasicGrowthService;
import com.fieldstory.farm.service.impl.BasicHarvestService;
import com.fieldstory.farm.service.impl.BasicLandService;
import com.fieldstory.farm.service.impl.BasicPlantingService;
import com.fieldstory.farm.service.impl.BasicWateringService;
import com.fieldstory.farm.view.FarmView;
import com.fieldstory.farm.view.StatusView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * 农场雏形装配器（C 模块 P0 可运行原型）。
 *
 * <p>一键装配 P0 闭环：农场（A 的 FarmView/FarmViewController）挂 CENTER、
 * 状态栏（D 的 StatusView）挂 TOP、临时商店面板挂 RIGHT（UI规范 §7 槽位布局），
 * 时间与作物成长由 D 的 FarmController 主循环驱动（每秒推进 10 游戏分钟）。
 * 全部服务按模块契约注入：
 * 经济（B 的 EconomyServiceImpl）→ 土地/播种/浇水/成长（A 的服务）
 * → 收获（C 的 BasicHarvestService）。
 *
 * <p>雏形性质（往后正式交付后逐项替换）：
 * <ul>
 *   <li>临时商店面板：雏形种子购买入口（B 的商店视图交付后替换）。
 *       TODO B 商店接入后移除。</li>
 *   <li>渲染同步 Timeline：主循环推进成长后每秒回写 FarmView 画面；
 *       正式视图绑定/观察者交付后移除。</li>
 *   <li>金币/种子标签每秒随主循环 tick 刷新，收获入账后 1 秒内可见。</li>
 * </ul>
 *
 * <p>本类只装配不实现业务；颜色只用 UI规范 §14 主色表与 §13 按钮三态色。
 */
public final class FarmBootstrap {

    /** 商店面板按钮宽（UI规范 §13） */
    private static final double BUTTON_WIDTH = 120;

    /** 商店面板按钮高（UI规范 §13） */
    private static final double BUTTON_HEIGHT = 36;

    /** 商店面板样式：UI背景 #FFF3DD + 木色 2px 边框 + 圆角 12（UI规范 §14、§16） */
    private static final String PANEL_STYLE = "-fx-background-color: #FFF3DD;"
            + "-fx-background-radius: 12;"
            + "-fx-border-color: #8B5E3C;"
            + "-fx-border-width: 2;"
            + "-fx-border-radius: 12;";

    /** 按钮三态样式（UI规范 §13）：Normal #A97850 / Hover #C28B5A / Disabled #CCCCCC */
    private static final String STYLE_BTN_NORMAL = "-fx-background-color: #A97850;"
            + "-fx-background-radius: 10;"
            + "-fx-text-fill: #FFF3DD;"
            + "-fx-font-size: 14;";

    private static final String STYLE_BTN_HOVER = "-fx-background-color: #C28B5A;"
            + "-fx-background-radius: 10;"
            + "-fx-text-fill: #FFF3DD;"
            + "-fx-font-size: 14;";

    /** 是否已装配（"开始游戏"可重复点击，只装配一次） */
    private static boolean mounted = false;

    /** 渲染同步 Timeline 强引用（防 GC 停止） */
    private static Timeline renderTimeline;

    private FarmBootstrap() {
        // 工具类
    }

    /**
     * 挂载农场雏形场景（由 MainController 开始游戏后调用一次）。
     *
     * <p>前置：{@link GameManager#start()} 已执行（currentState 可用）。
     * 组装顺序：农场模型 → 经济服务 → D 正式时钟 → A 的服务 → C 的收获服务
     * → A 的 FarmViewController（挂 CENTER）→ D 的状态栏（挂 TOP）
     * → 临时商店面板（挂 RIGHT）→ D 的 FarmController 主循环
     * → 渲染同步 Timeline。
     */
    public static void mountFarmScene() {
        if (mounted) {
            return;
        }
        mounted = true;

        GameState state = GameManager.getInstance().currentState();

        // 模型与 B 的经济服务（金币/种子唯一入口）
        Farm farm = new BasicFarm();
        EconomyService economy = new EconomyServiceImpl(state.getPlayer());

        // D 的正式时钟（第 1 天 06:00 起，每 tick 推进 10 游戏分钟）
        GameClock gameClock = new BasicGameClock();

        // A 的服务（开垦/播种/浇水/成长）
        LandService landService = new BasicLandService(economy);
        PlantingService plantingService = new BasicPlantingService(economy, gameClock);
        WateringService wateringService = new BasicWateringService();
        GrowthService growthService = new BasicGrowthService(wateringService);

        // C 的收获服务（经济 + A 的土地回退）
        HarvestService harvestService = new BasicHarvestService(economy, landService);

        // A 的视图控制器（挂 CENTER）
        FarmViewController farmViewController = new FarmViewController(
                farm, landService, plantingService, wateringService, harvestService, gameClock);
        farmViewController.mountToScene();

        // D 的模型聚合 + 状态栏（挂 TOP）：显示游戏日/时间/金币/天气（P0 固定晴天）
        FarmGameModel gameModel = new FarmGameModel(gameClock);
        gameModel.setFarm(farm);
        StatusView statusView = new StatusView(gameModel, state.getPlayer());
        SceneManager.getInstance().mount(SceneManager.Slot.TOP, statusView);

        // 临时商店面板（挂 RIGHT；TODO B 的商店视图交付后替换）
        VBox shopPanel = buildShopPanel(economy);
        SceneManager.getInstance().mount(SceneManager.Slot.RIGHT, shopPanel);

        // D 的主循环：每秒推进 10 游戏分钟 + 协调作物成长 + 刷新状态栏
        FarmController farmController = new FarmController(gameModel, statusView, growthService);
        farmController.startGameLoop();

        // 跨天时同步 FarmView 的游戏日（浇水 Tooltip 判定基准）并全量刷新
        FarmView farmView = farmViewController.getView();
        farmController.setOnDayChanged(() -> {
            farmView.setCurrentGameDay(gameClock.getGameDay());
            for (Soil soil : farm.getSoils()) {
                farmView.refreshTile(soil);
            }
        });

        // 渲染同步 Timeline（纯视图刷新，不推进时间）：
        // 主循环推进成长后，作物阶段与金币/种子变化每秒回写画面
        renderTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            for (Soil soil : farm.getSoils()) {
                if (soil.getState() == SoilState.PLANTED) {
                    farmView.refreshTile(soil);
                }
            }
            // 收获/购买都会改变金币与种子，统一随 tick 刷新面板标签
            refreshShopPanel(economy, shopPanel);
        }));
        renderTimeline.setCycleCount(Timeline.INDEFINITE);
        renderTimeline.play();
    }

    /**
     * 构建临时商店面板：金币/种子/状态标签 + 三种种子购买按钮
     * （价格取自 CropType.getSeedPrice，单一数据源 D12，不硬编码）。
     *
     * <p>面板布局符合 UI规范 §16 Panel 风格；标题与标签文字用 §14 主色表文字色。
     */
    private static VBox buildShopPanel(EconomyService economy) {
        VBox panel = new VBox(8);
        panel.setStyle(PANEL_STYLE);
        panel.setPadding(new Insets(12));
        panel.setPrefWidth(160);

        Label title = new Label("种子商店（P0 临时）");
        title.setStyle("-fx-text-fill: #493526; -fx-font-size: 15; -fx-font-weight: bold;");
        panel.getChildren().add(title);

        refreshShopPanel(economy, panel);

        Label status = new Label("雏形模式：作物每秒成长 2 游戏小时");
        status.setWrapText(true);
        status.setStyle("-fx-text-fill: #493526; -fx-font-size: 12;");
        panel.getChildren().add(status);

        for (CropType type : CropType.values()) {
            Button button = createShopButton(
                    type.getDisplayName() + "种子 " + type.getSeedPrice() + "金");
            button.setOnAction(event -> {
                PurchaseResult result = economy.buySeed(type, 1);
                switch (result) {
                    case SUCCESS -> status.setText(
                            "已购买" + type.getDisplayName() + "种子 ×1");
                    case INSUFFICIENT_GOLD -> status.setText("金币不足，无法购买");
                    default -> status.setText("购买数量无效");
                }
                refreshShopPanel(economy, panel);
            });
            panel.getChildren().add(button);
        }
        return panel;
    }

    /**
     * 刷新面板内金币与三种种子库存标签。
     * 面板首行是标题，其后固定为金币/种子标签，随状态变化更新文字。
     */
    private static void refreshShopPanel(EconomyService economy, VBox panel) {
        int childCount = panel.getChildren().size();
        int labelBase = 1; // 面板 [0]=标题，[1..3]=金币+三种种子
        for (int i = 0; i < 1 + CropType.values().length; i++) {
            String text;
            if (i == 0) {
                text = "金币：" + economy.getGold();
            } else {
                CropType type = CropType.values()[i - 1];
                text = type.getDisplayName() + "种子：" + economy.getSeedCount(type);
            }
            Label label;
            if (labelBase + i < childCount && panel.getChildren().get(labelBase + i) instanceof Label existing) {
                label = existing;
            } else {
                label = new Label();
                label.setStyle("-fx-text-fill: #493526; -fx-font-size: 13;");
                panel.getChildren().add(labelBase + i, label);
            }
            label.setText(text);
        }
    }

    /** 创建 120×36 圆角按钮（UI规范 §13），三态色同 §13。 */
    private static Button createShopButton(String text) {
        Button button = new Button(text);
        button.setPrefSize(BUTTON_WIDTH, BUTTON_HEIGHT);
        button.setStyle(STYLE_BTN_NORMAL);
        button.setOnMouseEntered(event -> button.setStyle(STYLE_BTN_HOVER));
        button.setOnMouseExited(event -> button.setStyle(STYLE_BTN_NORMAL));
        return button;
    }

}
