package com.fieldstory.farm.manager;

import com.fieldstory.farm.model.GameState;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.persistence.SqliteSaveService;
import com.fieldstory.farm.service.SaveService;
import com.fieldstory.farm.util.GameConstants;

import java.util.Objects;

/**
 * 游戏管理器（E 存档与引擎模块；脚手架 §七 manager 包：GameManager/SceneManager）。
 *
 * <p>职责（模块分工 E 行 P0：GameManager）：
 * <ul>
 *   <li>全局唯一入口（单例，{@link #getInstance()}）；测试可经构造器注入 SaveService；</li>
 *   <li>游戏状态机：主菜单 → 游戏中 → 暂停 → 退出（{@link GamePhase}）；</li>
 *   <li>生命周期：Init（构造装配）→ start（读档/新档）→ 游玩 → saveNow（手动/关键节点）
 *       → saveAndExit（退出，验收 §四十二：保存→记录世界时间→退出）；</li>
 *   <li>模块协调：统一持有并初始化各系统引用（当前为 SaveService 与会话状态 GameState；
 *       经济/土地/天气/时钟系统由 A/B/C/D 交付后在此统一装配，本管理器不越层实现业务）。</li>
 * </ul>
 *
 * <p>P0 退出后不推进世界（离线模拟属 P2）；读档只恢复退出瞬间状态。
 */
public class GameManager {

    /**
     * 新游戏初始金币（B 模块 §9：不得再写 500，统一取自 {@link GameConstants#INITIAL_GOLD}）。
     * 保留为兼容别名，新代码请直接引用 {@link GameConstants#INITIAL_GOLD}。
     */
    public static final int INITIAL_GOLD = GameConstants.INITIAL_GOLD;

    private static final String DEFAULT_PLAYER_NAME = "农夫";

    /** 全局唯一实例（单例，P1 起默认 {@link SqliteSaveService} 写 {@code data/farm.db}） */
    private static volatile GameManager instance;

    private final SaveService saveService;
    private GameState state;
    private GamePhase phase;

    /**
     * 存档前回填钩子：落盘前把「运行中的游戏对象」（如 A 模块 {@code Farm} 的
     * 土地/作物）同步进 {@link GameState} 快照，使退出自动保存与手动保存都能
     * 覆盖农场进度（由装配层注册，见 {@code MainController}）。
     */
    private Runnable beforeSaveHook;

    /**
     * 构造管理器并装配存档服务。
     *
     * @param saveService 存档服务（测试可注入内存/临时文件实现）
     */
    public GameManager(SaveService saveService) {
        this.saveService = Objects.requireNonNull(saveService, "SaveService 不能为空");
        this.phase = GamePhase.MAIN_MENU;
    }

    /**
     * 全局唯一实例（单例）：P1 起默认使用 {@link SqliteSaveService}（{@code data/farm.db}，
     * 首次运行会把旧 {@code data/save.json} 一次性迁移进 SQLite，验收规范 §七十四）。
     * 全游戏共享此入口。
     */
    public static GameManager getInstance() {
        if (instance == null) {
            synchronized (GameManager.class) {
                if (instance == null) {
                    instance = new GameManager(new SqliteSaveService());
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

    /** 是否存在可恢复的历史存档。 */
    public boolean hasSavedGame() {
        return saveService.hasSave();
    }

    /**
     * 开始游戏（主菜单 → 游戏中）：
     * <pre>
     * 有存档 → 读取数据库（P1：SQLite）恢复到退出瞬间（不做离线成长）
     * 无存档 → 新建游戏（金币 500，游戏天数 0）
     * 存档损坏/版本不符 → 降级为新建游戏，不让启动崩溃
     * </pre>
     *
     * @return 当前会话游戏状态
     */
    public GameState start() {
        if (state == null) {
            try {
                if (saveService.hasSave()) {
                    state = saveService.load();
                }
            } catch (IllegalStateException e) {
                System.err.println("[GameManager] 存档不可用，将新建游戏: " + e.getMessage());
                state = null;
            }
            if (state == null) {
                state = newGame();
            }
        }
        phase = GamePhase.PLAYING;
        return state;
    }

    /**
     * 直接开始新游戏会话（不读旧档，也不写盘；调用 {@link #saveNow()} 后落盘）。
     */
    public GameState startNewGame() {
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
     * 手动 / 关键节点（如每日结束）自动保存：把当前会话状态写入存档，不改变阶段。
     */
    public void saveNow() {
        if (state == null) {
            throw new IllegalStateException("游戏尚未启动，无法保存");
        }
        refreshBeforeSave();
        saveService.save(state);
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
     * 退出游戏：保存当前状态后进入 {@link GamePhase#EXITING}。
     * （验收 §四十二：保存当前状态 → 记录世界时间 → 退出；退出后不推进世界。）
     */
    public void saveAndExit() {
        if (state != null && (phase == GamePhase.PLAYING || phase == GamePhase.PAUSED)) {
            refreshBeforeSave();
            saveService.save(state);
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
