package com.fieldstory.farm.manager;

import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.persistence.SaveSlot;
import com.fieldstory.farm.persistence.SaveSlotInfo;
import com.fieldstory.farm.persistence.SaveSlotManager;
import com.fieldstory.farm.service.SaveService;
import com.fieldstory.farm.util.GameConstants;

import java.util.List;
import java.util.Objects;

/**
 * 游戏管理器（E 存档与引擎模块；脚手架 §七 manager 包：GameManager/SceneManager）。
 *
 * <p>职责（模块分工 E 行 P0/P2：GameManager）：
 * <ul>
 *   <li>全局唯一入口（单例，{@link #getInstance()}）；测试可经构造器注入 SaveService；</li>
 *   <li>游戏状态机：主菜单 → 游戏中 → 暂停 → 退出（{@link GamePhase}）；</li>
 *   <li>生命周期：Init（构造装配）→ start（读档/新档）→ 游玩 → saveNow（手动/关键节点）
 *       → saveAndExit（退出，验收 §四十二：保存→记录世界时间→退出）；</li>
 *   <li><b>P2 三存档位</b>：所有读档/写档都作用于 {@link #currentSlot()}，
 *       由 {@link SaveSlotManager} 把存档位映射到各自的存档文件（见 {@link SaveSlot}）；
 *       主菜单需要的各档摘要由 {@link #allSlotInfos()} 提供。</li>
 * </ul>
 *
 * <p>P2 起离线模拟属 B 模块；本管理器只负责"读回内存 / 写到文件"，不做任何离线成长。
 */
public class GameManager {

    /**
     * 新游戏初始金币（B 模块 §9：不得再写 500，统一取自 {@link GameConstants#INITIAL_GOLD}）。
     * 保留为兼容别名，新代码请直接引用 {@link GameConstants#INITIAL_GOLD}。
     */
    public static final int INITIAL_GOLD = GameConstants.INITIAL_GOLD;

    private static final String DEFAULT_PLAYER_NAME = "农夫";

    /** 全局唯一实例（单例，P1 起默认 SQLite 存档；P2 起默认三个存档位各一个 .db 文件） */
    private static volatile GameManager instance;

    /** 存档位管理器：把存档位映射到各自的存档服务（P2 三存档位）。 */
    private final SaveSlotManager slotManager;

    /** 当前存档位：所有读档/写档都作用于它（默认存档 1）。 */
    private SaveSlot currentSlot;

    private GameState state;
    private GamePhase phase;

    /**
     * 存档前回填钩子：落盘前把「运行中的游戏对象」（如 A 模块 {@code Farm} 的
     * 土地/作物）同步进 {@link GameState} 快照，使退出自动保存与手动保存都能
     * 覆盖农场进度（由装配层注册，见 {@code MainController}）。
     */
    private Runnable beforeSaveHook;

    /**
     * 构造管理器并装配单个存档服务（兼容 P0/P1 单档装配与既有测试）。
     *
     * <p>此构造下三个存档位共用同一实现，语义等同"只有一个存档位"；
     * 需要真正的多档请用 {@link #GameManager(SaveSlotManager)}。
     *
     * @param saveService 存档服务（测试可注入内存/临时文件实现）
     */
    public GameManager(SaveService saveService) {
        this(SaveSlotManager.single(saveService));
    }

    /**
     * 构造管理器并装配多存档位。
     *
     * @param slotManager 存档位管理器
     */
    public GameManager(SaveSlotManager slotManager) {
        this.slotManager = Objects.requireNonNull(slotManager, "slotManager 不能为空");
        this.currentSlot = SaveSlot.first();
        this.phase = GamePhase.MAIN_MENU;
    }

    /**
     * 全局唯一实例（单例）：P2 起为三个存档位各一个 SQLite 文件
     * （{@link SaveSlot#databaseFile()}），首次运行会把旧 {@code data/save.json}
     * 一次性迁移进"存档 1"（验收规范 §七十四）。
     * 全游戏共享此入口。
     */
    public static GameManager getInstance() {
        if (instance == null) {
            synchronized (GameManager.class) {
                if (instance == null) {
                    instance = new GameManager(SaveSlotManager.defaultManager());
                }
            }
        }
        return instance;
    }

    /** 当前状态机阶段。 */
    public GamePhase currentPhase() {
        return phase;
    }

    /** 当前会话游戏状态；未调用 {@link #start()} 前访问抛出状态异常。 */
    public GameState currentState() {
        if (state == null) {
            throw new IllegalStateException("游戏尚未启动，请先调用 start()");
        }
        return state;
    }

    /** 是否存在可恢复的历史存档（当前存档位）。 */
    public boolean hasSavedGame() {
        return hasSavedGame(currentSlot);
    }

    /** 指定存档位是否存在可恢复的历史存档。 */
    public boolean hasSavedGame(SaveSlot slot) {
        return slotManager.hasSave(slot);
    }

    /** 当前存档位。 */
    public SaveSlot currentSlot() {
        return currentSlot;
    }

    /** 指定存档位的主菜单摘要（空档 / 第 N 天 / 金币 / 存档时间）。 */
    public SaveSlotInfo slotInfo(SaveSlot slot) {
        return slotManager.describe(slot);
    }

    /** 全部存档位摘要（按序号升序，供主菜单逐行展示）。 */
    public List<SaveSlotInfo> allSlotInfos() {
        return slotManager.describeAll();
    }

    /** 下一个可用的新存档位（现有最大序号 + 1，无存档时为存档 1）。 */
    public SaveSlot nextSlot() {
        return slotManager.nextSlot();
    }

    /** 当前存档位的存档服务（读档/写档的唯一出口）。 */
    private SaveService activeService() {
        return slotManager.service(currentSlot);
    }

    /**
     * 开始游戏（主菜单 → 游戏中），使用当前存档位：
     * <pre>
     * 有存档 → 读取数据库（P1：SQLite）恢复到退出瞬间（不做离线成长）
     * 无存档 → 新建游戏（金币 500，游戏天数 0）
     * 存档损坏/版本不符 → 降级为新建游戏，不让启动崩溃
     * </pre>
     *
     * @return 当前会话游戏状态
     */
    public GameState start() {
        return start(currentSlot);
    }

    /**
     * 在指定存档位开始游戏（读取该档；无档则新建）。
     *
     * <p>切换到<b>不同</b>存档位时会重新读档（会话状态属于存档位，不能串档）；
     * 对同一存档位重复调用则复用当前会话（与 P0/P1 的 {@code start()} 语义一致）。
     * 之后的 {@link #saveNow()} 也写回当前存档位，保证"在哪档玩，存档就落在哪档"。
     *
     * @param slot 目标存档位
     * @return 当前会话游戏状态
     */
    public GameState start(SaveSlot slot) {
        SaveSlot target = Objects.requireNonNull(slot, "slot 不能为空");
        boolean slotChanged = target != currentSlot;
        this.currentSlot = target;
        if (state == null || slotChanged) {
            state = loadOrCreate();
        }
        phase = GamePhase.PLAYING;
        return state;
    }

    /** 有档则读档（损坏时降级新建），无档则新建；绝不因读档失败阻断启动。 */
    private GameState loadOrCreate() {
        SaveService saveService = activeService();
        try {
            if (saveService.hasSave()) {
                GameState loaded = saveService.load();
                if (loaded != null) {
                    return loaded;
                }
            }
        } catch (IllegalStateException e) {
            System.err.println("[GameManager] 存档不可用，将新建游戏: " + e.getMessage());
        }
        return newGame();
    }

    /**
     * 在当前存档位直接开始新游戏会话（不读旧档，也不写盘；调用 {@link #saveNow()} 后落盘）。
     */
    public GameState startNewGame() {
        return startNewGame(currentSlot);
    }

    /**
     * 在指定存档位开始新游戏（覆盖该档的旧进度需玩家显式确认，见 {@code MainController}）。
     *
     * @param slot 目标存档位
     * @return 全新会话游戏状态
     */
    public GameState startNewGame(SaveSlot slot) {
        this.currentSlot = Objects.requireNonNull(slot, "slot 不能为空");
        state = newGame();
        phase = GamePhase.PLAYING;
        return state;
    }

    /**
     * 构造全新游戏状态：金币 500、游戏天数 0、无解锁内容、无土地快照。
     * （A Farm 模型交付后，由适配层为新区建默认中心 8×8 土地快照。）
     */
    public static GameState newGame() {
        return new GameState(new Player(DEFAULT_PLAYER_NAME, INITIAL_GOLD), 0L);
    }

    /**
     * 手动 / 关键节点（如每日结束）自动保存：把当前会话状态写入<b>当前存档位</b>，不改变阶段。
     */
    public void saveNow() {
        if (state == null) {
            throw new IllegalStateException("游戏尚未启动，无法保存");
        }
        refreshBeforeSave();
        activeService().save(state);
    }

    /**
     * 注册存档前回填钩子（装配层在农场就绪后调用）；传 {@code null} 可清除。
     * 钩子只应在落盘前把运行态写入 {@link GameState}，不得改变阶段或触发存档。
     */
    public void setBeforeSaveHook(Runnable beforeSaveHook) {
        this.beforeSaveHook = beforeSaveHook;
    }

    /** 执行存档前回填钩子（未注册则跳过）；钩子异常不吞，向上抛出以暴露装配错误。 */
    private void refreshBeforeSave() {
        Runnable hook = this.beforeSaveHook;
        if (hook != null) {
            hook.run();
        }
    }

    /**
     * 退出游戏：把当前状态保存到<b>当前存档位</b>后进入 {@link GamePhase#EXITING}。
     * （验收 §四十二：保存当前状态 → 记录世界时间 → 退出；退出后不推进世界。）
     */
    public void saveAndExit() {
        if (state != null && (phase == GamePhase.PLAYING || phase == GamePhase.PAUSED)) {
            refreshBeforeSave();
            activeService().save(state);
        }
        phase = GamePhase.EXITING;
    }

    /** 暂停：仅允许从游戏中进入暂停。 */
    public void pause() {
        requirePhase(GamePhase.PLAYING, "仅游戏中可暂停");
        phase = GamePhase.PAUSED;
    }

    /** 恢复：仅允许从暂停回到游戏。 */
    public void resume() {
        requirePhase(GamePhase.PAUSED, "仅暂停中可恢复");
        phase = GamePhase.PLAYING;
    }

    private void requirePhase(GamePhase expected, String message) {
        if (phase != expected) {
            throw new IllegalStateException(message + "（当前阶段: " + phase + "）");
        }
    }
}
