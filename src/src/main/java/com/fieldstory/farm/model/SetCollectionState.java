package com.fieldstory.farm.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 套装收集状态（E 模块 P3 持久化；验收规范 §一百一十八，套装规则由 B 模块负责）。
 *
 * <p>「已收集」与「已激活」必须分开保存（验收规范 §一百一十八）：
 * <ul>
 *   <li>{@link #getCollected()} <b>setCollected</b>：曾经完整完成过某套装，永久记录，
 *       决定 FarmScore 的套装分是否保留；</li>
 *   <li>{@link #getActive()} <b>setActive</b>：当前全部成员仍然放置，决定套装 Buff 是否生效。</li>
 * </ul>
 * 玩家完成套装后收起一个成员：{@code collected=true} 但 {@code active=false}，
 * FarmScore 仍保留套装分，但 Buff 停止（验收规范 §一百一十八）。
 *
 * <p>本类只保存状态；套装成员判定与 Buff 计算属 B 模块 {@code SetService}，E 不越层实现。
 */
public class SetCollectionState {

    /** 曾经完整完成过的套装 id（永久）。 */
    private final Set<String> collected = new LinkedHashSet<>();

    /** 当前仍全部成员放置、Buff 生效的套装 id。 */
    private final Set<String> active = new LinkedHashSet<>();

    /** setCollected：永久完成的套装 id 集合（可变）。 */
    public Set<String> getCollected() {
        return collected;
    }

    /** setActive：当前生效的套装 id 集合（可变）。 */
    public Set<String> getActive() {
        return active;
    }
}
