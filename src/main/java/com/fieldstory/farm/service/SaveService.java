package com.fieldstory.farm.service;

import com.fieldstory.farm.model.GameState;

/**
 * 存档服务接口（计划书 §3.2 P0 Service；验收规范 §四十）。
 *
 * <p>P0 由 {@code persistence.JsonSaveService}（JSON 临时存档）实现；
 * P1 由 SqliteSaveService 替换，业务层调用方式不变。
 *
 * <p>Controller 不得感知存档文件位置（验收规范 §三十九）。
 */
public interface SaveService {

    /** 当前是否存在可读取的存档。 */
    boolean hasSave();

    /** 保存当前游戏状态到持久化层。 */
    void save(GameState state);

    /**
     * 读取存档并还原为游戏状态。
     *
     * @return 存档对应的状态；不存在存档时返回 {@code null}
     */
    GameState load();
}
