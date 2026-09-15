package com.fieldstory.farm.controller;

import com.fieldstory.farm.manager.SceneManager;
import com.fieldstory.farm.model.Crop;
import com.fieldstory.farm.model.CropMemory;
import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Farm;
import com.fieldstory.farm.model.GameClock;
import com.fieldstory.farm.model.GrowthStage;
import com.fieldstory.farm.model.Soil;
import com.fieldstory.farm.model.item.Inventory;
import com.fieldstory.farm.service.FertilizeResult;
import com.fieldstory.farm.service.FertilizerService;
import com.fieldstory.farm.service.AudioService;
import com.fieldstory.farm.service.HarvestResult;
import com.fieldstory.farm.service.HarvestService;
import com.fieldstory.farm.service.LandService;
import com.fieldstory.farm.service.MemoryService;
import com.fieldstory.farm.service.PlantingResult;
import com.fieldstory.farm.service.PlantingService;
import com.fieldstory.farm.service.ReclaimResult;
import com.fieldstory.farm.service.WateringResult;
import com.fieldstory.farm.service.WateringService;
import com.fieldstory.farm.service.economy.EconomyService;
import com.fieldstory.farm.view.FarmView;
import javafx.scene.Node;
import javafx.scene.control.Button;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 农场视图控制器（A 模块 P0 视图层；脚手架 §七.4 controller 职责：
 * 接收用户操作 → 调用 Service → 刷新界面）。
 *
 * <p>点击 FARM_PLOT 格 → 选中并弹出操作菜单（UI规范 §11、§12），
 * 按土壤状态决定按钮：EMPTY→开垦、TILLED→播种、PLANTED→浇水、MATURE→收获、
 * WITHERED→铲除（P1，{@link #actionsFor}）。土地/作物状态变化继续统一走正式业务 Service：
 * 开垦→LandService.reclaim、播种→PlantingService.plant、浇水→WateringService.water。
 *
 * <p>P2 启动基线新增一项只读跨模块查询：播种种子选择菜单通过 B 的
 * {@link EconomyService#getSeedCount(CropType)} 显示实时种子库存。控制器不直接访问
 * Player.seedInventory，也不直接修改种子数量。
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
 * <p>纯静态函数 {@link #actionsFor} / {@link #actionMessageFor} /
 * {@link #seedButtonLabel} 只返回枚举/字符串，不依赖 JavaFX 线程，可在无 GUI 线程下单测。
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

    /**
     * B 经济服务：仅用于播种菜单读取实时种子库存。
     * 真正的种子消耗仍由 PlantingService 负责。
     */
    private final EconomyService economyService;

    /** C P1：施肥服务；旧构造为 null 时隐藏施肥入口。 */
    private final FertilizerService fertilizerService;

    /** C P2：生命记忆；在线播种/浇水/施肥事实最终都落到这一个数据源。 */
    private final MemoryService memoryService;

    /** 玩家背包：施肥消耗与收获肥料奖励使用同一实例。 */
    private final Inventory inventory;

    /** P4 表现层音频；null 时完全静默，兼容旧测试构造。 */
    private final AudioService audioService;

    /** 农场画布视图 */
    private final FarmView farmView;

    /**
     * LOCKED 地块点击事件出口。
     *
     * <p>A 只发出“玩家请求解锁这块地”的事件，不依赖 B 的 LandUnlockService，
     * 不读取价格、不扣金币、不修改 LOCKED 状态。E 装配层负责把它接给
     * B 的 LandUnlockController / LandUnlockPopupView。
     */
    private Consumer<Soil> lockedPlotClickHandler = soil -> { };

    /**
     * 装配视图、四个业务 Service（三个 A + 一个 C 收获）、D 的时钟与 B 的只读经济查询入口。
     *
     * @param farm            农场模型
     * @param landService     开垦服务
     * @param plantingService 播种服务
     * @param wateringService 浇水服务
     * @param harvestService  收获服务
     * @param gameClock       游戏时钟
     * @param economyService  B 经济服务；播种菜单只读取实时种子库存
     */
    public FarmViewController(
            Farm farm,
            LandService landService,
            PlantingService plantingService,
            WateringService wateringService,
            HarvestService harvestService,
            GameClock gameClock,
            EconomyService economyService) {
        this(farm, landService, plantingService, wateringService, harvestService,
                gameClock, economyService, null, null, null, null);
    }

    /**
     * C 完整接线构造：增加施肥、生命记忆和同一背包实例。
     *
     * <p>保留旧 7 参数构造用于既有单测/旧装配；生产 MainController 使用本构造。
     */
    public FarmViewController(
            Farm farm,
            LandService landService,
            PlantingService plantingService,
            WateringService wateringService,
            HarvestService harvestService,
            GameClock gameClock,
            EconomyService economyService,
            FertilizerService fertilizerService,
            MemoryService memoryService,
            Inventory inventory) {
        this(farm, landService, plantingService, wateringService, harvestService, gameClock,
                economyService, fertilizerService, memoryService, inventory, null);
    }

    /** P4 正式构造：只比 P3 多一个表现层 AudioService，不改变业务依赖。 */
    public FarmViewController(
            Farm farm,
            LandService landService,
            PlantingService plantingService,
            WateringService wateringService,
            HarvestService harvestService,
            GameClock gameClock,
            EconomyService economyService,
            FertilizerService fertilizerService,
            MemoryService memoryService,
            Inventory inventory,
            AudioService audioService) {
        this.farm = farm;
        this.landService = landService;
        this.plantingService = plantingService;
        this.wateringService = wateringService;
        this.harvestService = harvestService;
        this.gameClock = gameClock;
        this.economyService = economyService;
        this.fertilizerService = fertilizerService;
        this.memoryService = memoryService;
        this.inventory = inventory;
        this.audioService = audioService;
        this.farmView = new FarmView(farm);
        this.farmView.setOnTileSelected(this::onTileSelected);
    }

    /** 农场画布视图。 */
    public FarmView getView() {
        return farmView;
    }

    /**
     * 注入 LOCKED 地块点击事件处理器。
     *
     * <p>这是 A → 外部的唯一土地解锁出口；null 恢复为空操作。
     */
    public void setOnLockedPlotClicked(Consumer<Soil> handler) {
        this.lockedPlotClickHandler = handler == null ? soil -> { } : handler;
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
     * PLANTED 且作物 MATURE→收获、PLANTED 且作物 WITHERED→铲除
     * （只给铲除，不给浇水/收获，验收 §五十四）；LOCKED 只给 REQUEST_UNLOCK，
     * 真正 LOCKED→EMPTY 由 B 模块完成；装饰区（null）无动作。
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
                if (crop != null && crop.getGrowthStage() == GrowthStage.WITHERED) {
                    // P1：枯萎只给铲除（免费，规则 §16.5），不出现浇水/收获按钮
                    return List.of(FarmAction.CLEAR_WITHERED);
                }
                if (crop != null && crop.getGrowthStage() == GrowthStage.MATURE) {
                    return List.of(FarmAction.HARVEST);
                }
                if (crop != null && crop.getGrowthStage() == GrowthStage.SEED) {
                    // 规则：种子阶段不能浇水、不能施肥，不给死按钮。
                    return List.of();
                }
                if (crop != null
                        && (crop.getGrowthStage() == GrowthStage.SPROUT
                        || crop.getGrowthStage() == GrowthStage.GROWING)) {
                    // C P1：SPROUT / GROWING 可施肥；与主动浇水并列显示。
                    return List.of(FarmAction.WATER, FarmAction.FERTILIZE);
                }
                return crop == null ? List.of() : List.of(FarmAction.WATER);
            case LOCKED:
                return List.of(FarmAction.REQUEST_UNLOCK);
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

    /** 纯函数：施肥结果码 → 用户提示文案。 */
    public static String actionMessageFor(FertilizeResult result) {
        switch (result) {
            case SUCCESS:
                return "施肥成功";
            case NOT_ALLOWED_STAGE:
                return "只有幼苗或成长阶段可以施肥";
            case ALREADY_FERTILIZED_TODAY:
                return "今天已经施过肥了";
            case MAX_TIMES_PER_LIFE:
                return "这株作物一生最多施肥3次";
            case NOT_ENOUGH_FERTILIZER:
                return "肥料不足";
            case NO_CROP_OR_MEMORY:
            default:
                return "施肥失败";
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


    /**
     * 纯函数：生成播种种子按钮文案。
     *
     * <p>P2 启动基线格式固定为“种子类型 ×剩余数量”，库存只通过
     * {@link EconomyService#getSeedCount(CropType)} 读取，不直接访问 Player Map。
     *
     * @param type           种子对应作物类型
     * @param economyService B 经济服务
     * @return 例如“小麦 ×3”
     */
    static String seedButtonLabel(CropType type, EconomyService economyService) {
        return type.getDisplayName() + " ×" + economyService.getSeedCount(type);
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
        List<FarmAction> actions = availableActionsFor(soil);
        if (actions.isEmpty()) {
            farmView.hideMenu();
            return;
        }
        farmView.showMenuFor(soil, buildButtons(soil, actions));
    }

    /**
     * 生产可用动作：理论状态机由 {@link #actionsFor(Soil)} 给出；
     * 旧构造未装配 FertilizerService 时隐藏 FERTILIZE，保持向后兼容。
     */
    private List<FarmAction> availableActionsFor(Soil soil) {
        List<FarmAction> actions = actionsFor(soil);
        if (fertilizerService != null || !actions.contains(FarmAction.FERTILIZE)) {
            return actions;
        }
        return actions.stream()
                .filter(action -> action != FarmAction.FERTILIZE)
                .toList();
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
 /** 按当前土壤状态刷新菜单：无可用动作收起，有则原地重开（连续操作）。 */
    private void refreshMenu(Soil soil) {
        List<FarmAction> actions = availableActionsFor(soil);
        if (actions.isEmpty()) {
            farmView.hideMenu();
        } else {
            farmView.showMenuFor(soil, buildButtons(soil, actions));
        }
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
            case FERTILIZE:
                return "施肥";
            case REQUEST_UNLOCK:
                return "解锁";
            case HARVEST:
                return "收获";
            case CLEAR_WITHERED:
                return "铲除";
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
            case FERTILIZE:
                fertilize(soil);
                break;
            case REQUEST_UNLOCK:
                requestUnlock(soil);
                break;
            case HARVEST:
                harvest(soil);
                break;
            case CLEAR_WITHERED:
                clearWithered(soil);
                break;
            default:
                break;
        }
    }

    /**
     * LOCKED 点击出口：只通知外部，不执行 B 的解锁业务。
     *
     * <p>外部处理器返回后刷新当前格与菜单：若 B 已成功把 LOCKED→EMPTY，
     * UI 会立即切换到“开垦”；若用户取消/金币不足，仍保持“解锁”。
     */
    private void requestUnlock(Soil soil) {
        if (soil == null || soil.getState() != com.fieldstory.farm.model.SoilState.LOCKED) {
            return;
        }
        lockedPlotClickHandler.accept(soil);
        farmView.refreshTile(soil);
        refreshMenu(soil);
    }

    /** 开垦：EMPTY→TILLED（验收规范 §十五）。 */
    private void reclaim(Soil soil) {
        ReclaimResult result = landService.reclaim(soil);
        if (result == ReclaimResult.SUCCESS) {
            farmView.setCurrentGameDay(gameClock.getGameDay());
            farmView.refreshTile(soil);
            farmView.animateReclaim(soil);
        } else {
            farmView.showTip(soil, actionMessageFor(result));
        }
        refreshMenu(soil);
    }

    /**
     * 播种入口：弹出种子选择按钮。
     *
     * <p>P2 启动基线显示“种子类型 ×剩余数量”。每次打开菜单都通过
     * EconomyService.getSeedCount() 实时读取库存，不缓存、不直接访问 Player Map。
     * 真正的种子消耗仍由 PlantingService.plant() 负责。
     */
    private void showSeedButtons(Soil soil) {
        List<Node> buttons = new ArrayList<>();
        for (CropType type : CropType.values()) {
            Button button = farmView.createMenuButton(seedButtonLabel(type, economyService));
            button.setOnAction(event -> plant(soil, type));
            buttons.add(button);
        }
        farmView.showMenuFor(soil, buttons);
    }

    /** 播种：TILLED→PLANTED，消耗 1 颗种子（验收规范 §十八、§十九）。 */
    private void plant(Soil soil, CropType type) {
        PlantingResult result = plantingService.plant(soil, type);
        if (result == PlantingResult.SUCCESS) {
            if (memoryService != null && soil.getCrop() != null
                    && memoryService.findMemory(soil.getCrop().getCropUuid()).isEmpty()) {
                memoryService.createMemory(soil.getCrop());
            }
            refreshMenu(soil);
            farmView.setCurrentGameDay(gameClock.getGameDay());
            farmView.refreshTile(soil);
            farmView.animatePlant(soil);
            playSfx(AudioService.Sfx.PLANT);
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
            if (memoryService != null) {
                CropMemory memory = memoryService.findMemory(crop.getCropUuid())
                        .orElseGet(() -> memoryService.createMemory(crop));
                memoryService.recordManualWater(memory, gameClock.getGameDay());
            }
            refreshMenu(soil);
            farmView.setCurrentGameDay(gameClock.getGameDay());
            farmView.refreshTile(soil);
            farmView.animateWater(soil);
            playSfx(AudioService.Sfx.WATER);
        } else {
            farmView.showTip(soil, actionMessageFor(result));
        }
    }

    /**
     * 施肥：仅 SPROUT / GROWING；每日一次、生命周期三次、消耗 1 肥料。
     *
     * <p>具体校验/扣库存/Memory 记录全部由 C.FertilizerService 执行，
     * Controller 不复制业务规则。
     */
    private void fertilize(Soil soil) {
        Crop crop = soil == null ? null : soil.getCrop();
        if (crop == null || fertilizerService == null || memoryService == null) {
            if (soil != null) {
                farmView.showTip(soil, "施肥功能尚未装配");
            }
            return;
        }

        CropMemory memory = memoryService.findMemory(crop.getCropUuid())
                .orElseGet(() -> memoryService.createMemory(crop));
        FertilizeResult result = fertilizerService.fertilize(
                crop, memory, inventory, gameClock.getGameDay());

        if (result == FertilizeResult.SUCCESS) {
            farmView.setCurrentGameDay(gameClock.getGameDay());
            farmView.refreshTile(soil);
            farmView.animateFertilize(soil);
            playSfx(AudioService.Sfx.FERTILIZE);
            refreshMenu(soil);
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
            farmView.setCurrentGameDay(gameClock.getGameDay());
            farmView.refreshTile(soil);
            farmView.animateHarvest(soil);
            playSfx(AudioService.Sfx.HARVEST);
            refreshMenu(soil);
        } else {
            farmView.showTip(soil, actionMessageFor(result));
        }
    }

    /**
     * 铲除枯萎：WITHERED→TILLED、crop=null（验收 §五十四；规则 §16.5 免费，
     * 不扣金币、不发肥料）。土地状态机唯一入口复用
     * {@link LandService#removeCropAndSetTilled}（决策 D20）。
     */
    private void clearWithered(Soil soil) {
        landService.removeCropAndSetTilled(soil);
        refreshMenu(soil);
        farmView.setCurrentGameDay(gameClock.getGameDay());
        farmView.refreshTile(soil);
    }

    private void playSfx(AudioService.Sfx sfx) {
        if (audioService != null) {
            audioService.playSfx(sfx);
        }
    }
}
