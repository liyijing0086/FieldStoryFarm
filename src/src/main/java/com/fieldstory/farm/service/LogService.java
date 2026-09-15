package com.fieldstory.farm.service;

import com.fieldstory.farm.model.HarvestLog;
import com.fieldstory.farm.model.OfflineLog;
import com.fieldstory.farm.model.OfflineSimulationResult;

import java.util.List;
import java.util.Optional;

/**
 * P2 统一日志服务接口。
 *
 * <p>B 模块使用 {@link #buildOfflineLog(OfflineSimulationResult)} 将结构化离线模拟结果
 * 转换为玩家可读的离线日志；C 模块收获事务使用 {@link #append(HarvestLog)} 与
 * {@link #listAll()} 维护收获日志。
 *
 * <p>该接口只处理日志领域数据，不执行世界模拟，也不直接访问数据库；SQLite 持久化
 * 仍由 E 模块的 DAO / 启动装配层负责。
 *
 * <p>{@code buildOfflineLog} 保持为唯一抽象方法，使 B 的 Controller 测试和轻量 stub
 * 可以继续使用 lambda；收获日志方法提供默认“未支持”实现，正式默认实现
 * {@code BasicLogService} 会覆盖它们。
 */
@FunctionalInterface
public interface LogService {

    /**
     * 根据一次离线模拟结果生成玩家可读离线日志。
     *
     * @param result 离线模拟结构化结果
     * @return 无有效离线推进时返回 Optional.empty()
     */
    Optional<OfflineLog> buildOfflineLog(OfflineSimulationResult result);

    /**
     * 追加一条收获日志。
     *
     * <p>正式默认实现会覆盖本方法。默认实现故意抛异常，避免只实现离线日志的轻量
     * stub 被误用于收获事务时静默丢失日志。
     *
     * @param log 收获日志
     */
    default void append(HarvestLog log) {
        throw new UnsupportedOperationException(
                "HarvestLog append is not supported by this LogService implementation"
        );
    }

    /**
     * 返回全部收获日志快照。
     *
     * <p>正式默认实现会覆盖本方法。
     *
     * @return 收获日志列表
     */
    default List<HarvestLog> listAll() {
        throw new UnsupportedOperationException(
                "HarvestLog listing is not supported by this LogService implementation"
        );
    }
}
