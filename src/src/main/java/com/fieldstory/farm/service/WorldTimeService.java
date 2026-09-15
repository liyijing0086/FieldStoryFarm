package com.fieldstory.farm.service;

import java.util.List;

/**
 * 世界时间服务接口（A 模块 P2）。
 *
 * <p>统一世界时间口径（决策 D14：gameDay×24+gameHour，long）与离线结算
 * 封顶/分段切点计算（规则文档 §七；验收规范 §八十三、§八十八）。
 *
 * <p>全部为纯函数：不读系统时间（规则文档 §八 禁止 System.currentTimeMillis）、
 * 不依赖 GameClock、不 new Random，仅依据入参计算。
 */
public interface WorldTimeService {

    /** 世界小时口径（决策 D14）：gameDay*24+gameHour，long。 */
    long toWorldHour(int gameDay, int gameHour);

    /** 离线结算上限（验收§八十三）：min(raw, 72)，负数/0 返回 0。 */
    long capOfflineRealMinutes(long rawOfflineRealMinutes);

    /** 分段切点（规则§八十三）：输入起止世界小时+事件结束+作物成熟时刻，
     *  输出含起止点的升序去重切点列表；事件结束为 null 则忽略。 */
    List<Long> segmentCutPoints(long startWorldHour, long endWorldHour,
                                Long eventEndWorldHour,
                                List<Long> cropMatureWorldHours);
}
