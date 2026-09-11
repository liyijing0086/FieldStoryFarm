package com.fieldstory.farm.controller;

import com.fieldstory.farm.manager.GameManager;
import com.fieldstory.farm.manager.SceneManager;
import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.FarmGameModel;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.persistence.FarmStateAdapter;
import com.fieldstory.farm.service.GrowthService;
import com.fieldstory.farm.service.LandService;
import com.fieldstory.farm.service.PlantingService;
import com.fieldstory.farm.service.WateringService;
import com.fieldstory.farm.service.economy.EconomyService;
import com.fieldstory.farm.service.economy.impl.EconomyServiceImpl;
import com.fieldstory.farm.service.impl.BasicGrowthService;
import com.fieldstory.farm.service.impl.BasicLandService;
import com.fieldstory.farm.service.impl.BasicPlantingService;
import com.fieldstory.farm.service.impl.BasicWateringService;
import com.fieldstory.farm.util.GameConstants;
import com.fieldstory.farm.view.StatusView;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * 主界面控制器（E 场景组装：开始按钮装配 A/B/C/D 各模块，构成可玩最小闭环）。
 *
 * <p>装配职责：点击“开始游戏”后创建农场模型与各模块 Service，
 * 把农场视图挂到 CENTER、状态栏挂到 TOP，并启动主循环；
 * 跨天时由 {@link FarmController} 回调本类推进作物成长。
 *
 * <p>装配过程中的异常一律向上抛出（不吞掉），以便启动日志可见。
 */
public class MainController {

    @FXML
    private Label welcomeText;

    /** 全局唯一游戏管理器（单例） */
    private final GameManager gameManager = GameManager.getInstance();

    /** 本次会话是否已完成装配（防止重复点击“开始游戏”重复装配） */
    private boolean assembled = false;

    /** 上次记录的游戏日（跨天成长推进基准；-1 表示尚未初始化） */
    private int lastGrowthDay = -1;

    @FXML
    private void initialize() {
        welcomeText.setText("欢迎来到田野故事农场！");
    }

    /** 开始 / 继续游戏：装配游戏最小闭环（农场可玩）。 */
    @FXML
    protected void onStartButtonClick() {
        if (assembled) {
            welcomeText.setText("游戏已在运行中。");
            return;
        }

        // b. 开始游戏：有存档恢复退出瞬间状态，无存档则新建（金币 500）
        GameState state = gameManager.start();
        Player player = state.getPlayer();

        // c. 农场模型（12×12，中心 8×8 为可种植区）
        Farm farm = new BasicFarm();
        FarmGameModel model = new FarmGameModel();
        model.setFarm(farm);

        // c1. 读档还原：把数据库中的地块/作物快照覆盖到新农场；无存档时 plots 为空，
        //     农场保持初始 EMPTY（不再出现"作物/地块读不回来"的空档）。
        FarmStateAdapter.restore(state, farm);
        restoreGameDay(state, model);

        // c2. 注册存档前回填：手动保存与退出自动保存落盘前，把运行中农场（地块/作物）
        //     与当前游戏天数同步回 GameState，保证下次进入能恢复到退出瞬间。
        gameManager.setBeforeSaveHook(() -> {
            FarmStateAdapter.capture(state, farm);
            state.setGameDay(model.getGameClock().getGameDay());
        });

        // d. 经济服务 + 新档/空库存赠送起始种子（联调临时方案，商店视图接入后取消）
        EconomyService economy = new EconomyServiceImpl(player);
        int total = economy.getSeedCount(CropType.WHEAT)
                + economy.getSeedCount(CropType.CORN)
                + economy.getSeedCount(CropType.CARROT);
        if (total == 0) {
            economy.buySeed(CropType.WHEAT, 3);
            economy.buySeed(CropType.CORN, 3);
            economy.buySeed(CropType.CARROT, 3);
        }

        // e. A/B 各模块服务：开垦 / 播种 / 浇水 / 成长
        LandService land = new BasicLandService(economy);
        PlantingService planting = new BasicPlantingService(economy, model.getGameClock());
        WateringService watering = new BasicWateringService();
        GrowthService growth = new BasicGrowthService(watering);

        // f. 农场视图挂到场景中央（CENTER）
        FarmViewController farmViewController = new FarmViewController(
                farm, land, planting, watering, model.getGameClock());
        farmViewController.mountToScene();

        // g. 状态栏挂到场景顶部（TOP）
        StatusView statusView = new StatusView(model, player);
        SceneManager.getInstance().mount(SceneManager.Slot.TOP, statusView);

        // h. 主循环：每秒推进 10 分钟；跨天回调协调作物成长
        lastGrowthDay = model.getGameClock().getGameDay();
        FarmController farmLoop = new FarmController(model, statusView);
        farmLoop.setOnDayChanged(() -> applyDailyGrowth(
                farm, growth, farmViewController, model.getGameClock().getGameDay()));
        farmLoop.startGameLoop();

        // i. 装配完成
        assembled = true;
        welcomeText.setText("点击农田开始：开垦 → 播种 → 浇水 → 等待成长。");
    }

    /**
     * 读档还原游戏天数到时钟（验收规范 §41「setTotalMinutes 存档恢复」）。
     *
     * <p>无有效天数（新档 gameDay = 0）时保持时钟初值（第 1 天 06:00）；
     * P1 不持久化当天时刻，恢复后按当日 06:00 起算（离线模拟与时刻恢复属 P2）。
     */
    private static void restoreGameDay(GameState state, FarmGameModel model) {
        long savedDay = state.getGameDay();
        if (savedDay > 0) {
            int totalMinutes = (int) ((savedDay - 1) * GameConstants.MINUTES_PER_DAY
                    + GameConstants.DAY_START);
            model.restoreWorldTime(totalMinutes);
        }
    }

    /** 跨天回调：按经过天数推进所有未成熟作物成长，并刷新农场视图。 */
    private void applyDailyGrowth(Farm farm, GrowthService growth,
                                  FarmViewController farmViewController, int currentDay) {
        double elapsedDays = currentDay - lastGrowthDay;
        if (elapsedDays > 0) {
            for (Soil soil : farm.getSoils()) {
                Crop crop = soil.getCrop();
                if (crop != null
                        && crop.getGrowthStage() != GrowthStage.MATURE
                        && crop.getGrowthStage() != GrowthStage.WITHERED) {
                    growth.applyGrowth(crop, elapsedDays);
                }
            }
        }
        lastGrowthDay = currentDay;
        farmViewController.getView().setCurrentGameDay(currentDay);
        farmViewController.getView().refreshAll();
    }

    /** 手动存档入口：保存当前进度（退出/刷新时另有自动存档兜底）。 */
    @FXML
    protected void onSaveButtonClick() {
        try {
            gameManager.saveNow();
            welcomeText.setText("进度已保存！");
        } catch (IllegalStateException e) {
            welcomeText.setText("尚无进行中的游戏，请先点击“开始游戏”。");
        }
    }
}
