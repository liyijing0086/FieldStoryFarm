package com.fieldstory.farm.manager;

/**
 * 游戏状态机阶段：主菜单 → 游戏中 → 暂停 → 退出。
 *
 * <p>状态切换统一由 {@link GameManager} 管理。
 */
public enum GamePhase {
    /** 主菜单：尚未开始游戏 */
    MAIN_MENU,

    /** 游戏中 */
    PLAYING,

    /** 暂停 */
    PAUSED,

    /** 退出（已保存并关闭） */
    EXITING
}
