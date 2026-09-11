package com.fieldstory.farm.dao;

import com.fieldstory.farm.model.item.Inventory;

/**
 * 背包数据访问接口（C 模块 物品·背包·商店域，P2）。
 *
 * <p>契约来源：《C任务跨模块开发约束文档》§五："C 任务负责编写
 * InventoryDAO，完成背包数据读写数据库"。背包相关表由 C 维护，
 * C 绝不修改 player/crop 表（A 组员维护）。
 *
 * <p>P2 交付接口契约与内存实现 {@code impl.BasicInventoryDao}；
 * 项目 pom 暂未引入数据库依赖，数据库实现在引入 JDBC 依赖后
 * 以同名接口替换实现即可，调用方无需改动。
 *
 * <p>存档语义：与 E 模块存档（JsonSaveService）互补——E 负责整体
 * 游戏状态快照，本接口负责背包数据的独立读写，供离线结算
 * （约束文档 §四.3：离线推演完成后 Player、Inventory 数据同步
 * 持久化 DAO）与 D 离线奖励入包后落盘。
 */
public interface InventoryDao {

    /**
     * 保存背包数据（覆盖式快照）。
     *
     * @param inventory 待保存背包；null 视为空背包（清空快照）
     */
    void save(Inventory inventory);

    /**
     * 读取背包数据并重建 Inventory 实例。
     *
     * <p>从未保存时返回空背包。
     *
     * @return 背包实例（每次调用返回新实例，调用方可安全修改）
     */
    Inventory load();

    /** 清空已保存的背包快照。 */
    void clear();
}
