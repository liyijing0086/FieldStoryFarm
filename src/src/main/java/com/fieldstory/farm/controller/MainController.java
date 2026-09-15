package com.fieldstory.farm.controller;

import com.fieldstory.farm.factory.GameClockFactory;
import com.fieldstory.farm.manager.GameManager;
import com.fieldstory.farm.manager.OfflineStartupStep;
import com.fieldstory.farm.manager.SceneManager;
import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.Decoration;
import com.fieldstory.farm.model.EventState;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.FarmGameModel;
import com.fieldstory.farm.model.FarmRank;
import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.OfflineSimulationResult;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.WeatherType;
import com.fieldstory.farm.model.impl.BasicFarm;
import com.fieldstory.farm.model.item.EventPriceRateProvider;
import com.fieldstory.farm.model.item.Inventory;
import com.fieldstory.farm.persistence.FarmStateAdapter;
import com.fieldstory.farm.persistence.SaveSlot;
import com.fieldstory.farm.persistence.SaveSlotInfo;
import com.fieldstory.farm.service.BuffService;
import com.fieldstory.farm.service.AudioService;
import com.fieldstory.farm.service.CollectionService;
import com.fieldstory.farm.service.DecorationService;
import com.fieldstory.farm.service.FarmRankService;
import com.fieldstory.farm.service.FarmScoreService;
import com.fieldstory.farm.service.FertilizerService;
import com.fieldstory.farm.service.GraduationService;
import com.fieldstory.farm.service.GrowthService;
import com.fieldstory.farm.service.HarvestOutcome;
import com.fieldstory.farm.service.HarvestResult;
import com.fieldstory.farm.service.HarvestService;
import com.fieldstory.farm.service.HarvestTransactionService;
import com.fieldstory.farm.service.LandService;
import com.fieldstory.farm.service.LandUnlockPriceProvider;
import com.fieldstory.farm.service.LandUnlockService;
import com.fieldstory.farm.service.LegendaryFirstRewardService;
import com.fieldstory.farm.service.LegendaryService;
import com.fieldstory.farm.service.LogService;
import com.fieldstory.farm.service.MemoryService;
import com.fieldstory.farm.service.OfflineSimulationService;
import com.fieldstory.farm.service.PlantingService;
import com.fieldstory.farm.service.QualityService;
import com.fieldstory.farm.service.SetService;
import com.fieldstory.farm.service.ShowcaseService;
import com.fieldstory.farm.service.ShopService;
import com.fieldstory.farm.service.WateringService;
import com.fieldstory.farm.service.WitherService;
import com.fieldstory.farm.service.WorldSimulationService;
import com.fieldstory.farm.service.economy.EconomyService;
import com.fieldstory.farm.service.economy.impl.EconomyServiceImpl;
import com.fieldstory.farm.service.impl.BasicBuffService;
import com.fieldstory.farm.service.impl.BasicAudioService;
import com.fieldstory.farm.service.impl.BasicCollectionService;
import com.fieldstory.farm.service.impl.BasicDecorationService;
import com.fieldstory.farm.service.impl.BasicFarmRankService;
import com.fieldstory.farm.service.impl.BasicFarmScoreService;
import com.fieldstory.farm.service.impl.BasicFertilizerService;
import com.fieldstory.farm.service.impl.BasicGraduationService;
import com.fieldstory.farm.service.impl.BasicGrowthService;
import com.fieldstory.farm.service.impl.BasicHarvestTransactionService;
import com.fieldstory.farm.service.impl.BasicLandService;
import com.fieldstory.farm.service.impl.BasicLandUnlockService;
import com.fieldstory.farm.service.impl.BasicLegendaryFirstRewardService;
import com.fieldstory.farm.service.impl.BasicLegendaryService;
import com.fieldstory.farm.service.impl.BasicLogService;
import com.fieldstory.farm.service.impl.BasicMemoryService;
import com.fieldstory.farm.service.impl.BasicOfflineSimulationService;
import com.fieldstory.farm.service.impl.BasicPlantingService;
import com.fieldstory.farm.service.impl.BasicQualityService;
import com.fieldstory.farm.service.impl.BasicSetService;
import com.fieldstory.farm.service.impl.BasicShowcaseService;
import com.fieldstory.farm.service.impl.BasicShopService;
import com.fieldstory.farm.service.impl.BasicWateringService;
import com.fieldstory.farm.service.impl.BasicWitherService;
import com.fieldstory.farm.service.impl.BasicWorldSimulationService;
import com.fieldstory.farm.service.impl.BasicWorldTimeService;
import com.fieldstory.farm.service.impl.CropMemoryFactRecorder;
import com.fieldstory.farm.util.GameConstants;
import com.fieldstory.farm.util.JsonLandUnlockPriceProvider;
import com.fieldstory.farm.view.BusinessToolbarView;
import com.fieldstory.farm.view.AudioSettingsPopupView;
import com.fieldstory.farm.view.CollectionPopupView;
import com.fieldstory.farm.view.DecorationOverlayView;
import com.fieldstory.farm.view.FarmView;
import com.fieldstory.farm.view.GraduationPopupView;
import com.fieldstory.farm.view.LandUnlockPopupView;
import com.fieldstory.farm.view.StatusView;
import com.fieldstory.farm.view.ShowcasePopupView;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * 主界面控制器（E 场景组装：开始按钮装配 A/B/C/D 各模块，构成可玩最小闭环）。
 *
 * <p>本类只负责装配与调度：
 * A 提供农场/成长/浇水等能力，B 提供经济/商店/装饰/Buff，
 * C 提供收获（P2 起为完整收获事务，产出品质/传说/生命记忆），
 * D 提供时间/天气/随机事件，E 负责场景与存档。
 *
 * <p><b>P2 存档（三存档位）</b>：主菜单逐行展示三个存档位（空档 / 第 N 天 · 金币 /
 * 存档时间），玩家选一档「读取」或「新游戏」；进游戏后所有保存都落在该档。
 * 存档前回填钩子除地块外，还会把<b>世界时钟总分钟、天气、作物生命记忆、当前事件、
 * 背包物品</b>一起写进 {@link GameState}，使"关掉再打开"能回到退出瞬间而非当天 06:00。
 */
public class MainController {

    @FXML
    private Label welcomeText;

    /** P2：存档位列表容器（每行由 {@link #buildSlotRow} 装配）。 */
    @FXML
    private VBox slotList;

    /** P2：主菜单「新建存档」按钮（FXML 注入）。 */
    @FXML
    private Button newSaveButton;

    /** 全局唯一游戏管理器（单例） */
    private final GameManager gameManager;

    /** 顶栏常驻提示标签（开局后主菜单被替换，承接保存/装饰操作反馈） */
    private Label topHintLabel;

    /** FXML 默认构造：使用全局唯一 {@link GameManager} 单例。 */
    public MainController() {
        this(GameManager.getInstance());
    }

    /** 允许注入 GameManager（单测用，避免触碰真实 SQLite 存档）。 */
    MainController(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    /** E P2：测试可注入离线模拟实现；正式运行未注入时由装配层创建 BasicOfflineSimulationService。 */
    private OfflineSimulationService offlineSimulationService;

    /**
     * 装配层/测试注入 B 的离线模拟实现。
     *
     * <p>E 只按《验收规范》§八十四固定顺序调用它，绝不自行实现离线成长/枯萎/事件算法
     * （那属 B 模块）；离线日志与弹窗同样属 B，不在 E 的接线范围内。
     */
    void setOfflineSimulationService(OfflineSimulationService offlineSimulationService) {
        this.offlineSimulationService = offlineSimulationService;
    }

    /** 本次会话是否已完成装配（防止重复点击重复装配）。 */
    private boolean assembled = false;

    @FXML
    private void initialize() {
        welcomeText.setText("欢迎来到田野故事农场，选一档开始你的故事。");
        styleMenuButton(newSaveButton, 180, 42);
        buildSlotList();
    }

    // ==================================================================
    // 入口界面样式（UI美术设计规范 §13 按钮 / §14 主色表 / §16 组件）
    // ==================================================================

    /**
     * 统一按钮四态（Normal / Hover / Pressed / Disabled），与 A/B 模块既有实现
     * （{@code SeedQuickBuyView}、{@code FarmView}）保持一致；禁用态不响应鼠标悬停。
     */
    private static void styleMenuButton(Button button, double width, double height) {
        if (button == null) {
            return;
        }
        button.setPrefSize(width, height);
        button.setMinSize(width, height);
        if (!button.getStyleClass().contains("primary-button")) {
            button.getStyleClass().add("primary-button");
        }
    }

    // ==================================================================
    // P2 无限存档位：主菜单
    // ==================================================================

    /** 装配存档位列表：只列出磁盘上已存在的存档（保存了几个就显示几个）。 */
    private void buildSlotList() {
        if (slotList == null) {
            return;
        }
        slotList.getChildren().clear();
        List<SaveSlotInfo> infos = gameManager.allSlotInfos();
        if (infos.isEmpty()) {
            Label empty = new Label("还没有存档，点击「新建存档」开启第一段田野故事。");
            empty.setWrapText(true);
            empty.getStyleClass().add("hint-text");
            slotList.getChildren().add(empty);
            return;
        }
        for (SaveSlotInfo info : infos) {
            slotList.getChildren().add(buildSlotRow(info));
        }
    }

    /** 单个存档位行：`存档 N：摘要（存档时间）  [读取] [新游戏]`（卡片样式）。 */
    private HBox buildSlotRow(SaveSlotInfo info) {
        StringBuilder text = new StringBuilder()
                .append(info.slot().displayName())
                .append("：")
                .append(info.describe());
        if (info.savedAt() != null) {
            text.append("（").append(info.savedAt()).append("）");
        }
        Label summary = new Label(text.toString());
        summary.setMinWidth(260);
        summary.getStyleClass().add("slot-summary");

        Button loadButton = new Button("读取");
        loadButton.setDisable(!info.occupied());
        styleMenuButton(loadButton, 88, 34);
        loadButton.setOnAction(event -> onSlotLoad(info.slot()));

        Button newGameButton = new Button("新游戏");
        styleMenuButton(newGameButton, 88, 34);
        newGameButton.setOnAction(event -> onSlotNewGame(info.slot()));

        HBox row = new HBox(12, summary, loadButton, newGameButton);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 12, 8, 12));
        row.getStyleClass().add("save-slot-row");
        return row;
    }

    /** 新建存档：占用下一个空存档位并开始新游戏（兼容入口，供旧测试调用）。 */
    @FXML
    protected void onNewGameButtonClick() {
        onNewSaveButtonClick();
    }

    /** 新建存档：占用下一个空存档位并开始新游戏（主菜单「新建存档」入口）。 */
    @FXML
    protected void onNewSaveButtonClick() {
        if (rejectIfRunning()) {
            return;
        }
        assembleGame(gameManager.nextSlot(), true);
    }

    /** 读取存档：读取当前存档位（兼容入口，供旧测试与 FXML 调用）。 */
    @FXML
    protected void onLoadButtonClick() {
        onSlotLoad(gameManager.currentSlot());
    }

    /** 在指定存档位开始新游戏：无视该档历史进度（玩家显式选择该档，不做二次确认）。 */
    protected void onSlotNewGame(SaveSlot slot) {
        if (rejectIfRunning()) {
            return;
        }
        assembleGame(slot, true);
    }

    /** 读取指定存档位；空档只提示不进入。 */
    protected void onSlotLoad(SaveSlot slot) {
        if (rejectIfRunning()) {
            return;
        }
        if (!gameManager.hasSavedGame(slot)) {
            setStatusMessage(slot.displayName() + " 是空档，请先点击该档的「新游戏」。");
            buildSlotList();
            return;
        }
        assembleGame(slot, false);
    }

    /** 已在游戏中则提示并返回 true，避免重复装配。 */
    private boolean rejectIfRunning() {
        if (assembled) {
            setStatusMessage("游戏已在运行中。");
            return true;
        }
        return false;
    }


    /**
     * 装配游戏闭环。
     *
     * @param slot    目标存档位（读取或新建都作用于它，之后的保存也落回它）
     * @param newGame true=强制新档；false=读取存档
     */
    private void assembleGame(SaveSlot slot, boolean newGame) {
        GameState state = newGame ? gameManager.startNewGame(slot) : gameManager.start(slot);
        Player player = state.getPlayer();

        // E P3：收集图鉴 / FarmScore / 毕业——全部绑定当前 GameState，随存档往返（验收规范 §一百三十二）。
        CollectionService collectionService = new BasicCollectionService(state);
        FarmScoreService farmScoreService = new BasicFarmScoreService(state);
        FarmRankService farmRankService = new BasicFarmRankService();
        GraduationService graduationService = new BasicGraduationService(state, farmScoreService);

        Farm farm = new BasicFarm();
        FarmGameModel model = new FarmGameModel(GameClockFactory.createConfiguredClock());
        model.setFarm(farm);
        // P4 表现层单例：不进入任何领域规则或存档。
        AudioService audioService = BasicAudioService.shared();

        // P4 评价升级只做表现反馈，不参与 FarmScore/Graduation 判定。
        // 记录装配时的当前评价，随后每次真实加分动作完成后比较；147 由毕业音效独占。
        FarmRank[] presentedRank = {farmRankService.rankOf(farmScoreService.totalScore())};
        UnaryOperator<String> withRankPresentation = baseMessage -> {
            FarmRank current = farmRankService.rankOf(farmScoreService.totalScore());
            FarmRank previous = presentedRank[0];
            presentedRank[0] = current;
            if (previous != null
                    && current.ordinal() > previous.ordinal()
                    && !current.isGraduationRank()) {
                audioService.playSfx(AudioService.Sfx.RANK_UP);
                return baseMessage + " · 评价升级：" + current.getDisplayName();
            }
            return baseMessage;
        };

        // 恢复 A 的土地/作物快照、D 的时钟（精确到分钟）、天气。
        FarmStateAdapter.restore(state, farm);
        restoreClock(state, model);
        restoreWeather(state, model);

        // B P3：解锁价格唯一来自 balance-config。P3 文档未冻结正式数值，当前资源是
        // 明确标记的测试配置；P4 只替换 JSON 平衡值，不改 Controller/Service。
        LandUnlockPriceProvider landUnlockPriceProvider =
                JsonLandUnlockPriceProvider.fromClasspath();
        if (newGame) {
            applyConfiguredLockedPlots(farm, landUnlockPriceProvider);
        }

        // P2：背包 = 存档聚合里的同一个实例（读档时已由 SqliteSaveService 填好），
        // 收获肥料奖励会直接进它，保存时也直接取它，不存在第二份背包。
        Inventory inventory = state.getInventory();

        // P2：生命记忆（C）与随机事件（D）——读档时先灌回内存服务/状态，
        // 之后收获、跨日都在这份"继续的记录"上累加。
        MemoryService memoryService = new BasicMemoryService();
        for (CropMemory memory : state.getMemories()) {
            if (memory != null) {
                memoryService.save(memory);
            }
        }
        restoreEvent(state, model);
        CropMemoryFactRecorder memoryFacts = new CropMemoryFactRecorder(memoryService);
        // v5 旧档兼容：历史版本只把施肥次数存在 CropMemory。启动时把两侧事实对齐，
        // 此后 Crop 是成长/每日限制的运行态来源，Memory 继续负责永久档案。
        synchronizeFertilizerRuntimeFromMemories(farm, memoryService);

        // E 存档前统一回填运行态。
        // 存档 gameDay 直接取 GameClock.getGameDay()（第 1 天起）
        gameManager.setBeforeSaveHook(() -> {
            FarmStateAdapter.capture(state, farm);
            state.setGameDay(model.getGameClock().getGameDay());
            state.setWorldTotalMinutes(model.getWorldTimeTotalMinutes());
            state.setCurrentWeather(model.getWeatherState().getWeatherType());
            state.setWeatherDayIndex(model.getWeatherState().getDayIndex());
            LocalDateTime saveRealTime = model.getGameClock().getRealTime();
            model.getGameClock().setLastRealTime(saveRealTime);
            state.setLastRealTime(saveRealTime.toString());
            captureMemories(state, memoryService);
            state.setActiveEvent(model.getEventState());
        });

        // B P0 经济入口保持唯一 Player。
        // 新游戏严格保持 Player/GameManager 的正式初始状态：500 金币、三种种子库存均为 0。
        // 不得用 buySeed() “赠送”起始种子，否则会真实扣款。
        EconomyService economy = new EconomyServiceImpl(player);

        // A 基础服务。
        LandService land = new BasicLandService(economy);
        LandUnlockService landUnlockService = new BasicLandUnlockService(
                economy, landUnlockPriceProvider);
        LandUnlockController landUnlockController = new LandUnlockController(landUnlockService);
        PlantingService planting = new BasicPlantingService(economy, model.getGameClock());
        WateringService watering = new BasicWateringService();

        // B P1/P3：先完成 Decoration + Set + Buff，再把强类型结果交给 A/C 消费。
        DecorationService decorationService = new BasicDecorationService(farm, state);
        syncDecorations(collectionService, decorationService);
        SetService setService = new BasicSetService(decorationService, state);
        setService.refresh();

        // 关键：生产 BuffService 必须携带 SetService。
        // 自然之息进入成长倍率；丰收之魂/传奇之光仍通过 SetService 独立接口消费，
        // 不塞入 BuffSnapshot.priceRate，避免最终售价/传奇概率重复加成。
        BuffService buffService = new BasicBuffService(decorationService, setService);

        // E P3：分数变化后立即评估 147，而不是等到下一次手动保存。
        GraduationController graduationController = new GraduationController(
                state, model, graduationService);

        // C P1/P2/P3：施肥、首次传奇奖励、日志、展示台。
        FertilizerService fertilizerService = new BasicFertilizerService(memoryService);
        LegendaryFirstRewardService firstRewardService =
                new BasicLegendaryFirstRewardService(collectionService);
        LogService logService = new BasicLogService();
        ShowcaseService showcaseService = new BasicShowcaseService(memoryService);
        ShowcaseController showcaseController = new ShowcaseController(showcaseService);

        // A 成长只消费最终 OperationRate：
        // 1 + (基础浇水 bonus × B wateringMultiplier)
        //   + (C 施肥 bonus × B fertilizerMultiplier)。
        // A 不重算 B/C 领域规则。
        GrowthService growth = new BasicGrowthService(
                watering,
                crop -> resolveOperationRate(
                        farm, crop, watering, fertilizerService, buffService));

        // C 完整收获：品质装饰分、DecorationPriceRate、SetPriceRate、传奇套装、
        // 首次奖励、图鉴、Memory、HarvestLog 全部进入同一业务入口。
        HarvestService harvest = buildHarvestService(
                economy, land, model, memoryService, inventory, collectionService,
                buffService, setService, firstRewardService, logService,
                outcome -> {
                    showcaseController.refresh();
                    if (outcome.isLegendary()) {
                        audioService.playSfx(AudioService.Sfx.LEGENDARY);
                    } else if (outcome.getQuality() == com.fieldstory.farm.model.Quality.RARE
                            || outcome.getQuality() == com.fieldstory.farm.model.Quality.EPIC) {
                        audioService.playSfx(AudioService.Sfx.RARE);
                    }
                    setStatusMessage(withRankPresentation.apply(
                            "收获：" + outcome.getQuality().getDisplayName()
                                    + " · +" + outcome.getSellPrice() + " 金币"));
                    graduationController.evaluateNow();
                    gameManager.saveNow();
                });
        WitherService wither = new BasicWitherService();

        // A P2：正式生产世界引擎。在线 FarmController 与后续离线模拟必须复用这一套
        // Growth / Wither / Weather / Event 领域规则，MainController 不再自行计算成长或枯萎。
        WorldSimulationService worldSimulationService = new BasicWorldSimulationService(
                growth, wither, model.getWeatherService(), model.getEventService());

        // 第二轮：正式生产离线链。测试若显式注入 OfflineSimulationService 则优先使用；
        // 正式运行默认创建 BasicOfflineSimulationService，并与在线 FarmController 复用
        // 同一个 WorldSimulationService / GrowthService / BuffService。
        OfflineSimulationService startupOfflineSimulation = offlineSimulationService != null
                ? offlineSimulationService
                : new BasicOfflineSimulationService(
                        farm,
                        model.getGameClock(),
                        new BasicWorldTimeService(),
                        worldSimulationService,
                        growth,
                        model.getWeatherService(),
                        model.getWeatherState(),
                        model.getEventService(),
                        model.getEventState(),
                        buffService,
                        memoryService);

        // 固定启动顺序：读档/恢复世界状态 → 算离线时长 → 离线模拟 → 有进度立即保存 → 再建 FarmView。
        runOfflineSimulationStep(model, startupOfflineSimulation);

        // P4 表现层：进入农场后按当前事件选择普通/事件主题。
        audioService.updateForEvent(model.getEventState().getEventType());

        // A ViewController：保留 LOCKED 请求出口，同时增加 C 施肥/Memory/背包接线。
        FarmViewController farmViewController = new FarmViewController(
                farm, land, planting, watering, harvest, model.getGameClock(), economy,
                fertilizerService, memoryService, inventory, audioService);
        FarmView farmView = farmViewController.getView();

        // B P3：A 的 LOCKED 点击出口正式接到 B 的确认弹窗/解锁服务；成功后立即刷新并保存。
        LandUnlockPopupView landUnlockPopup = new LandUnlockPopupView(landUnlockController);
        farmViewController.setOnLockedPlotClicked(soil -> landUnlockPopup.showFor(farmView, soil));
        landUnlockController.addOnUnlockSucceeded(() -> {
            farmView.refreshAll();
            audioService.playSfx(AudioService.Sfx.PURCHASE);
            setStatusMessage("土地解锁成功，已自动保存。");
            gameManager.saveNow();
        });

        // E P3：147 首次毕业提供最小可见 UI；P4 再补动画/音效。
        GraduationPopupView graduationPopup = new GraduationPopupView();
        graduationController.setOnGraduated(graduation -> {
            setStatusMessage("FarmScore 147/147：永恒花园达成！");
            audioService.playSfx(AudioService.Sfx.GRADUATION);
            graduationPopup.showFor(farmView, graduation);
        });

        // B 商店与装饰控制器继续使用同一 decoration/buff/set 实例。
        ShopService shopService = new BasicShopService(economy, decorationService);
        DecorationController decorationController =
                new DecorationController(decorationService, buffService);
        ShopController shopController = new ShopController(shopService);

        DecorationOverlayView decorationOverlay =
                new DecorationOverlayView(farm, farmView, decorationController);
        decorationOverlay.setMessageSink(this::setStatusMessage);
        SceneManager.getInstance().mount(SceneManager.Slot.CENTER, decorationOverlay);

        // 购买/放置/移动/收回成功后自动保存；B 不直接写 SQL。
        shopController.addOnPurchaseSucceeded(() -> {
            audioService.playSfx(AudioService.Sfx.PURCHASE);
            gameManager.saveNow();
        });
        // E P3：放置/移动/收回成功 → 先重算套装 collected/active，再落盘。
        decorationController.addOnChanged(() -> {
            setService.refresh();
            setStatusMessage(withRankPresentation.apply("装饰布局已更新。"));
            graduationController.evaluateNow();
            gameManager.saveNow();
        });
        // E P3：首次成功购买某类型装饰即永久解锁图鉴。
        shopController.addOnDecorationPurchased(() -> {
            syncDecorations(collectionService, decorationService);
            setService.refresh();
            setStatusMessage(withRankPresentation.apply("装饰图鉴已更新。"));
            graduationController.evaluateNow();
        });

        BusinessToolbarView businessToolbar = new BusinessToolbarView(
                shopController, decorationController, decorationOverlay);

        // D 状态栏保持原实现；B 经营入口与 C 展示台入口由 E 装配。
        StatusView statusView = new StatusView(model, player);
        CollectionController collectionController = new CollectionController(
                collectionService, farmScoreService, farmRankService, setService);
        buildTopBar(statusView, businessToolbar, collectionController, showcaseController, audioService);

        // 正式生产主链：FarmController -> WorldSimulationService。
        // Decoration / Set Growth Buff 与 Wither Buff 仅通过窄 resolver 注入，
        // MainController 不再维护第二套成长/枯萎算法。
        FarmController farmLoop = new FarmController(
                model,
                statusView,
                worldSimulationService,
                buffService::getGrowthRate,
                buffService::getWitherProbabilityMultiplier);

        // 最小事实桥：世界引擎只产“新成熟列表 / 日结摘要”；
        // C/E 在外层消费这些事实写 Memory，并在每次推进后刷新 UI。
        farmLoop.setOnCropsMatured(crops ->
                memoryFacts.recordMatured(crops, currentWorldHour(model)));
        farmLoop.setOnDaySettled(result -> memoryFacts.recordDaily(farm, result));
        farmLoop.setOnWorldAdvanced(() -> {
            memoryFacts.recordActiveEvent(farm, model.getEventState());
            audioService.updateForEvent(model.getEventState().getEventType());
            farmViewController.getView().setCurrentGameDay(model.getGameClock().getGameDay());
            farmViewController.getView().refreshAll();
        });

        // 兼容 P3 之前已经满收集但未写 graduation 的旧档：UI 已绑定后评估一次。
        // 普通 146 或以下不会产生任何状态变化。
        if (graduationController.evaluateNow()) {
            gameManager.saveNow();
        }

        // “开始新游戏”完成装配后立即建立正式存档。
        // GameManager.startNewGame() 本身只创建内存状态；若依赖窗口正常关闭才保存，
        // 在 IDE 直接 Stop、异常退出等情况下，下一次“读取存档”会找不到这局。
        // 此处保存时 beforeSaveHook 已注册，会把初始 Farm + 第 0 天一起写入 SQLite。
        if (newGame) {
            gameManager.saveNow();
        }

        farmLoop.startGameLoop();

        assembled = true;
        if (graduationController.isGraduated()) {
            setStatusMessage("FarmScore 147/147：永恒花园已达成。");
        } else {
            setStatusMessage("点击农田开始：开垦 → 播种 → 浇水 → 等待成长。");
        }
    }

    /**
     * 读档还原时钟。
     *
     * <p>{@link GameState#getGameDay()} 与 {@code GameClock.getGameDay()} 同口径（第 1 天起）：
     * 第 d 天恢复成该日 06:00 的时钟总分钟数 {@code (d - 1) * MINUTES_PER_DAY + DAY_START}。
     * {@code gameDay <= 0}（空档/时钟未接入的旧档）时保持时钟初值（第 1 天 06:00）。
     */
    private static void restoreClock(GameState state, FarmGameModel model) {
        long savedMinutes = state.getWorldTotalMinutes();
        if (savedMinutes >= 0) {
            model.restoreWorldTime((int) savedMinutes);
        } else {
            long savedDay = state.getGameDay();
            if (savedDay > 0) {
                int totalMinutes = (int) ((savedDay - 1) * GameConstants.MINUTES_PER_DAY
                        + GameConstants.DAY_START);
                model.restoreWorldTime(totalMinutes);
            }
        }

        String lastRealTime = state.getLastRealTime();
        if (lastRealTime != null && !lastRealTime.isBlank()) {
            try {
                model.getGameClock().setLastRealTime(LocalDateTime.parse(lastRealTime.trim()));
            } catch (DateTimeParseException malformed) {
                // 旧档/坏数据没有可靠现实时间基准时按 0 分钟离线处理，绝不猜测。
                model.getGameClock().setLastRealTime(null);
            }
        }
    }

    /** 读档还原天气（未记录则保持初值 SUNNY / 第 1 天）。 */
    private static void restoreWeather(GameState state, FarmGameModel model) {
        WeatherType weather = state.getCurrentWeather();
        if (weather != null) {
            model.getWeatherState().setWeatherType(weather);
            model.getWeatherState().setDayIndex(
                    Math.max(1, state.getWeatherDayIndex()));
        }
    }

    /** 读档还原当前随机事件（事件期间退出，回来不能凭空消失，验收 §九十一）。 */
    private static void restoreEvent(GameState state, FarmGameModel model) {
        EventState saved = state.getActiveEvent();
        if (saved == null) {
            return;
        }
        EventState live = model.getEventState();
        live.setEventType(saved.getEventType());
        live.setStartWorldTime(saved.getStartWorldTime());
        live.setEndWorldTime(saved.getEndWorldTime());
        live.setTargetCropType(saved.getTargetCropType());
        live.setPayload(saved.getPayload());
    }

    /**
     * E P2 启动集成：离线一段（《验收规范》§八十四固定启动顺序第 6~8 步）。
     *
     * <p>原始离线真实分钟只从统一 {@link com.fieldstory.farm.model.GameClock#calculateOfflineDuration()}
     * 取得（正式游戏时间统一走 GameClock，此处不读取任何系统时间）；随后交给 B 的
     * {@link OfflineSimulationService#simulate(long)}。一旦产生离线进度
     * （{@link OfflineSimulationResult#hasOfflineProgress()}），立即事务落盘，使"离开期间
     * 发生的变化"随存档固化。B 的离线日志/弹窗是紧随其后的步骤，不在此处实现。
     */
    private void runOfflineSimulationStep(FarmGameModel model,
                                          OfflineSimulationService simulationService) {
        OfflineSimulationResult result =
                OfflineStartupStep.run(model.getGameClock(), simulationService);
        if (result != null && result.hasOfflineProgress()) {
            gameManager.saveNow();
        }
    }

    /**
     * 旧档兼容与双状态收口：Crop 驱动当前生命周期成长，CropMemory 驱动永久档案。
     * 两边有一边记录更完整时取较新的事实，并立即对齐；不增加施肥次数，只搬运已有事实。
     */
    private static void synchronizeFertilizerRuntimeFromMemories(
            Farm farm, MemoryService memoryService) {
        if (farm == null || memoryService == null) {
            return;
        }
        for (Soil soil : farm.getSoils()) {
            Crop crop = soil == null ? null : soil.getCrop();
            if (crop == null || crop.getCropUuid() == null) {
                continue;
            }
            CropMemory memory = memoryService.findMemory(crop.getCropUuid())
                    .orElseGet(() -> memoryService.createMemory(crop));
            int count = Math.max(crop.getFertilizerCount(), memory.getFertilizerCount());
            long lastDay = Math.max(
                    crop.getLastFertilizedGameDay(),
                    memory.getLastFertilizeGameDay());
            crop.setFertilizerCount(count);
            crop.setLastFertilizedGameDay(lastDay);
            memory.setFertilizerCount(count);
            memory.setLastFertilizeGameDay(lastDay);
        }
    }

    /** 存档前回填生命记忆：内存服务是唯一权威来源，快照整体重建避免残留。 */
    private static void captureMemories(GameState state, MemoryService memoryService) {
        state.getMemories().clear();
        state.getMemories().addAll(memoryService.listAll());
    }

    /**
     * 新游戏把 balance-config 中“存在价格”的农田设为 LOCKED。
     *
     * <p>不在 BasicFarm/Soil 硬编码坐标；读档也绝不重新上锁，已购买解锁的 EMPTY 状态
     * 完全由 SQLite 快照恢复。
     */
    static void applyConfiguredLockedPlots(Farm farm, LandUnlockPriceProvider priceProvider) {
        if (farm == null || priceProvider == null) {
            return;
        }
        for (Soil soil : farm.getSoils()) {
            if (soil != null
                    && soil.getState() == com.fieldstory.farm.model.SoilState.EMPTY
                    && priceProvider.findUnlockPrice(soil.getRow(), soil.getColumn()).isPresent()) {
                soil.setState(com.fieldstory.farm.model.SoilState.LOCKED);
            }
        }
    }

    /**
     * E P3：以「当前拥有的装饰类型」回填装饰图鉴，天然去重。
     *
     * <p>拥有即代表已成功购买（B 的装饰库存绑定 GameState），因此该同步既服务首次购买即时解锁，
     * 也用于读档时补齐 P3 之前旧档的图鉴（验收规范 §一百一十三）。
     */
    private static void syncDecorations(CollectionService collectionService,
                                        DecorationService decorationService) {
        for (Decoration owned : decorationService.getOwnedDecorations()) {
            if (owned != null && owned.getDecorationType() != null) {
                collectionService.collectDecoration(owned.getDecorationType().getId());
            }
        }
    }

    /**
     * 装配 P2 完整收获事务（验收规范 §一百零三），并适配为 A 视图依赖的 {@link HarvestService}。
     *
     * <p>为什么需要这层适配：A 的 {@code FarmViewController} 只依赖 P0 的
     * {@link HarvestService#harvest(com.fieldstory.farm.model.Soil)}（返回结果码用于提示），
     * 而 P2 事务（品质/传说/记忆/肥料/事件倍率）是 {@link HarvestTransactionService}。
     * 装配层负责把两者接起来，A 的视图代码一行都不用改（决策 D09：不越层改别人的类）。
     *
     * <p>肥料奖励直接进 {@code GameState} 里的同一个背包实例，随存档往返。
     */
    private static HarvestService buildHarvestService(EconomyService economy,
                                                      LandService land,
                                                      FarmGameModel model,
                                                      MemoryService memoryService,
                                                      Inventory inventory,
                                                      CollectionService collectionService,
                                                      BuffService buffService,
                                                      SetService setService,
                                                      LegendaryFirstRewardService firstRewardService,
                                                      LogService logService,
                                                      Consumer<HarvestOutcome> onCollected) {
        QualityService qualityService = new BasicQualityService();

        // 传奇之光只从 B.SetService#getLegendarySetBonusPercent() 进入。
        LegendaryService legendaryService = new BasicLegendaryService(setService);

        // 神秘商人：事件期间目标作物售价 ×2；C 只读取 D 的事件状态。
        EventPriceRateProvider priceRateProvider = cropType -> {
            EventState event = model.getEventState();
            if (cropType != null
                    && event != null
                    && model.getEventService().isMysteryMerchant(event.getEventType())
                    && cropType == event.getTargetCropType()) {
                return GameConstants.EVENT_MYSTERY_MERCHANT_PRICE_RATE;
            }
            return 1.0;
        };

        // 正式完整构造：
        // FinalPrice = Base × Quality × Decoration × Set × Event
        // DecorationPriceRate 与 SetPriceRate 在服务内部严格分离。
        HarvestTransactionService transaction = new BasicHarvestTransactionService(
                economy, land, qualityService, legendaryService, memoryService,
                model.getGameClock(), priceRateProvider,
                firstRewardService, logService,
                buffService, setService, collectionService);

        return new HarvestService() {
            @Override
            public boolean canHarvest(Soil soil) {
                return transaction.canHarvest(soil);
            }

            @Override
            public HarvestResult harvest(Soil soil) {
                HarvestOutcome outcome = transaction.harvest(soil, inventory);
                if (outcome.isSuccess()) {
                    onCollected.accept(outcome);
                }
                return outcome.getResult();
            }
        };
    }

    /**
     * 组装完整 OperationRate：
     * 1 + (基础浇水 bonus × B wateringMultiplier)
     *   + (C 施肥 bonus × B fertilizerMultiplier)。
     */
    private static double resolveOperationRate(Farm farm,
                                               Crop crop,
                                               WateringService wateringService,
                                               FertilizerService fertilizerService,
                                               BuffService buffService) {
        if (crop == null) {
            return 1.0;
        }

        double wateringBonus = wateringService.calculateWaterGrowthBonus(crop);
        double fertilizerBonus = fertilizerService.fertilizerGrowthRate(crop);

        Soil soil = findSoilForCrop(farm, crop);
        if (soil == null || crop.getCropType() == null) {
            return 1.0 + wateringBonus + fertilizerBonus;
        }

        // B 的 OperationModifierBuff 只放大“成长 bonus”，不改变品质固定 +3/+8。
        double wateringMultiplier = buffService.getWateringGrowthMultiplier(
                soil.getRow(), soil.getColumn(), crop.getCropType());
        double fertilizerMultiplier = buffService.getFertilizerGrowthMultiplier(
                soil.getRow(), soil.getColumn(), crop.getCropType());

        return 1.0
                + wateringBonus * wateringMultiplier
                + fertilizerBonus * fertilizerMultiplier;
    }

    private static Soil findSoilForCrop(Farm farm, Crop crop) {
        if (farm == null || crop == null) {
            return null;
        }
        for (Soil soil : farm.getSoils()) {
            if (soil == null || soil.getCrop() == null) {
                continue;
            }
            if (soil.getCrop() == crop
                    || (crop.getCropUuid() != null
                    && crop.getCropUuid().equals(soil.getCrop().getCropUuid()))) {
                return soil;
            }
        }
        return null;
    }

    /** 当前时钟转成项目统一的“游戏小时”口径（gameDay * 24 + gameHour）。 */
    private static long currentWorldHour(FarmGameModel model) {
        return (long) model.getGameClock().getGameDay() * 24L
                + model.getGameClock().getGameHour();
    }

    /** 手动存档入口。 */
    @FXML
    protected void onSaveButtonClick() {
        try {
            gameManager.saveNow();
            setStatusMessage("进度已保存！");
        } catch (IllegalStateException e) {
            setStatusMessage("尚无进行中的游戏，请先点击“开始新游戏”。");
        }
    }

    /** 兼容既有测试/调用：仅状态栏 + 保存入口。 */
    void buildTopBar(StatusView statusView) {
        buildTopBar(statusView, null);
    }

    /**
     * 构建常驻顶栏：D 状态栏 + B 经营入口 + E 保存按钮 + 提示。
     */
    void buildTopBar(StatusView statusView, Node businessToolbar) {
        buildTopBar(statusView, businessToolbar, null, null, null);
    }

    /**
     * 构建常驻顶栏：D 状态栏 + B 经营入口 + E 保存/图鉴按钮 + 提示。
     *
     * <p>保留既有 3 参数签名，兼容原测试和调用。
     */
    void buildTopBar(StatusView statusView, Node businessToolbar,
                     CollectionController collectionController) {
        buildTopBar(statusView, businessToolbar, collectionController, null, null);
    }

    /** 完整顶栏：增加 C P3 展示台入口。 */
    void buildTopBar(StatusView statusView, Node businessToolbar,
                     CollectionController collectionController,
                     ShowcaseController showcaseController) {
        buildTopBar(statusView, businessToolbar, collectionController, showcaseController, null);
    }

    /** P4 完整顶栏：图鉴/展示台使用浮层，并提供声音设置入口。 */
    void buildTopBar(StatusView statusView, Node businessToolbar,
                     CollectionController collectionController,
                     ShowcaseController showcaseController,
                     AudioService audioService) {
        Button saveButton = new Button("保存进度");
        saveButton.setOnAction(event -> onSaveButtonClick());
        styleMenuButton(saveButton, 104, 32);

        topHintLabel = new Label();
        topHintLabel.getStyleClass().add("slot-summary");
        HBox topBar = new HBox(16);
        topBar.getChildren().add(statusView);
        if (businessToolbar != null) {
            topBar.getChildren().add(businessToolbar);
        }
        topBar.getChildren().add(saveButton);

        if (collectionController != null) {
            CollectionPopupView collectionPopup = new CollectionPopupView(collectionController);
            Button collectionButton = new Button("图鉴");
            collectionButton.setOnAction(event -> collectionPopup.toggleBelow(collectionButton));
            styleMenuButton(collectionButton, 88, 32);
            topBar.getChildren().add(collectionButton);
        }

        if (showcaseController != null) {
            ShowcasePopupView showcasePopup = new ShowcasePopupView(showcaseController.getView());
            Button showcaseButton = new Button("展示台");
            showcaseButton.setOnAction(event -> {
                showcaseController.refresh();
                showcasePopup.toggleBelow(showcaseButton);
            });
            styleMenuButton(showcaseButton, 88, 32);
            topBar.getChildren().add(showcaseButton);
        }

        if (audioService != null) {
            AudioSettingsPopupView settingsPopup = new AudioSettingsPopupView(audioService);
            Button settingsButton = new Button("设置");
            settingsButton.setOnAction(event -> settingsPopup.toggleBelow(settingsButton));
            styleMenuButton(settingsButton, 76, 32);
            topBar.getChildren().add(settingsButton);
        }

        topBar.getChildren().add(topHintLabel);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(6, 12, 6, 12));
        topBar.getStyleClass().add("top-bar");

        SceneManager.getInstance().mount(SceneManager.Slot.TOP, topBar);
    }

    /** 统一提示输出，主菜单与开局后的常驻顶栏都可见。 */
    private void setStatusMessage(String message) {
        if (welcomeText != null) {
            welcomeText.setText(message);
        }
        if (topHintLabel != null) {
            topHintLabel.setText(message);
            // P4 最小反馈：收获品质/金币、评价升级等状态文字轻微“浮现”，
            // 动画只作用于 Node 属性，不改变任何游戏时间或业务状态。
            topHintLabel.setOpacity(0.35);
            topHintLabel.setScaleX(0.97);
            topHintLabel.setScaleY(0.97);
            FadeTransition fade = new FadeTransition(Duration.millis(180), topHintLabel);
            fade.setToValue(1.0);
            ScaleTransition scale = new ScaleTransition(Duration.millis(180), topHintLabel);
            scale.setToX(1.0);
            scale.setToY(1.0);
            new ParallelTransition(fade, scale).play();
        }
    }
}
