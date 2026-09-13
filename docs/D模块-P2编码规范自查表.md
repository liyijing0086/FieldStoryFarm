# D 模块 P2 编码规范自查表

> 版本：P2 · v0.3.0-feature
> 负责人：D（zsl）· 世界环境模块
> 依据：《FSF_P0-P4功能实现与验收规范》§3.1/§七十五、《FSFUI布局与美术设计规范》、会话强制约束

---

## 一、六项编码规范自查

### 1. 命名

| 检查项 | 结果 | 说明 |
|---|---|---|
| 包名统一 `com.fieldstory.farm.*` | ✅ | 全部新类符合 |
| 接口在包根、实现类以 `Basic` 前缀放 `impl` | ✅ | `EventState`/`BasicEventState`、`EventService`/`BasicEventService`（决策 D13） |
| 枚举常量 `UPPER_SNAKE_CASE` | ✅ | `METEOR_SHOWER` 等 |
| 常量 `UPPER_SNAKE_CASE` 且带 `EVENT_` 前缀 | ✅ | `EVENT_PROB_*`、`EVENT_DURATION_*`、`EVENT_QUALITY_*` |
| 方法名动词开头、语义清晰 | ✅ | `rollDailyEvent`、`expireIfNeeded`、`isEventActive` |

### 2. 异常

| 检查项 | 结果 | 说明 |
|---|---|---|
| 不吞异常 | ✅ | 无空 catch |
| 参数校验抛标准异常 | ✅ | `BasicGameClock.setTotalMinutes` 负值抛 `IllegalArgumentException`（P1 已实现） |
| UI 层 NPE 保护 | ✅ | `StatusView.buildEventText()` 对 null 返回占位「无事件」 |
| 不抛受检异常污染接口 | ✅ | 接口方法无 `throws` |

### 3. 线程

| 检查项 | 结果 | 说明 |
|---|---|---|
| 随机源线程安全 | ✅ | 统一经 `RandomProvider`（内部 `java.util.Random`，规则 §九十） |
| 无自建线程/线程池 | ✅ | D 模块无并发逻辑 |
| 状态对象非共享可变 | ✅ | `BasicEventState` 由单一 `BasicEventService` 持有 |

### 4. 资源

| 检查项 | 结果 | 说明 |
|---|---|---|
| 无未关闭 IO/DB 资源 | ✅ | D 模块不直接读写文件/SQL（验收 §七十五） |
| 无内存泄漏隐患 | ✅ | 无静态集合累积 |
| 常量集中管理 | ✅ | 全部数值经 `GameConstants` |

### 5. 方法长度

| 检查项 | 结果 | 说明 |
|---|---|---|
| 单方法 ≤ 50 行 | ✅ | 最长 `rollDailyEvent` 约 40 行 |
| 单一职责 | ✅ | 每个方法只做一件事 |
| 圈复杂度可控 | ✅ | `rollDailyEvent` 为线性 if-else 链 |

### 6. 调试方式

| 检查项 | 结果 | 说明 |
|---|---|---|
| 无 `System.out.println` 残留 | ✅ | 生产代码无调试输出 |
| 无 `printStackTrace` | ✅ | — |
| 固定种子可复现 | ✅ | `RandomProvider.setSeed(12345L)` 测试覆盖 |
| 测试可独立运行 | ✅ | 纯函数测试，无 JavaFX 依赖 |

---

## 二、会话强制约束自查清单

| 检查项 | 结果 |
|---|---|
| 未实现任何收获逻辑 | ✅ |
| UI 只用 7 色主色表，无渐变、阴影、自造色 | ✅ |
| 单元测试只测纯函数，未实例化 JavaFX 控件 | ✅ |
| 未为 A 模块单独提供浇水时间方法，未造第二时钟 | ✅ |
| 未修改 E 的 SceneManager / MainController / MainApplication | ✅ |
| 所有随机通过 RandomProvider，支持 setSeed | ✅ |
| 事件概率符合 74/5/8/10/3 | ✅ |
| 未实现 P3 的图鉴、套装、FarmScore、展示台、土地解锁、永恒花园 | ✅ |
| 未实现离线模拟（由 B 模块执行），D 只供规则 | ✅ |
| P2 未改变 P0/P1 已有的公开接口 | ✅ |
| 未修改 A/C/B/E 模块的类，只输出接口约定文档 | ✅ |
| 事件状态机图与状态转移表已输出 Drawio XML | ✅ |
| active_event 表字段与 E 模块确认（文档提出） | ✅ |
| P1 遗留矛盾已确认处理（上报，未自行裁定） | ✅ |

---

## 三、7 色主色表使用核对

| 色值 | 用途 | 是否使用 |
|---|---|---|
| #7FAE55 | 小动物来访状态色 | ✅ |
| #A97850 | 神秘商人状态色 | ✅ |
| #75B7D9 | 流星夜状态色 | ✅ |
| #FFF3DD | 背景/表体色 | ✅ |
| #8B5E3C | 表头/虚线转移色 | ✅ |
| #493526 | 文字色/描边色 | ✅ |
| #E8C45C | 彩虹日状态色 | ✅ |

无渐变、无阴影、无半透明叠加、无自造色值。

---

## 四、溯源说明

| 检查项来源 | 文档 | 章节 |
|---|---|---|
| 分层架构固定 | 《FSF_P0-P4功能实现与验收规范.md》 | §3.1 |
| Service 不得直接写 SQL | 《FSF_P0-P4功能实现与验收规范.md》 | §七十五 |
| 随机统一 RandomProvider | 《FSF游戏规则设计文档.md》 | §九十 |
| 事件概率 74/5/8/10/3 | 《FSF游戏规则设计文档.md》 | §四十七 |
| 接口在包根、实现类在 impl | 《FSF项目需求分析与开发计划书.md》 | 决策 D13 |
| 7 色主色表 | 《FSFUI布局与美术设计规范.md》 | 颜色规范 |
