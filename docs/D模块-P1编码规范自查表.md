# D 模块 P1 编码规范自查表（世界环境模块 · 天气系统）

> 作者：D 模块（zsl）｜ 阶段：P1（v0.2.0-playable）｜ 编码：UTF-8
> 自查范围：批次 A（P0 遗留收尾）+ 批次 B（天气系统）+ 批次 C（UI 接入）+ 批次 D（测试）

---

## 一、六项编码规范自查

### 1. 命名规范

| 检查项 | 规范 | 结果 |
|---|---|---|
| 包名统一 `com.fieldstory.farm` | 任务约束 | ✅ 全部符合 |
| 接口在包根、实现类 `Basic` 前缀放 `impl` 子包 | 决策 D13 | ✅ `WeatherState`/`BasicWeatherState`、`WeatherService`/`BasicWeatherService`、`GameClock`/`BasicGameClock` |
| 类名大驼峰、方法名小驼峰 | Java 规范 | ✅ |
| 常量全大写下划线 | Java 规范 | ✅ `WEATHER_PROB_SUNNY` 等 |
| 测试类 `XxxTest` | JUnit5 约定 | ✅ |

### 2. 异常规范

| 检查项 | 规范 | 结果 |
|---|---|---|
| `setTotalMinutes(int)` 对 `< 0` 抛 `IllegalArgumentException` | 任务约束 | ✅ |
| `BasicGameClock(int)` 构造器对 `< 0` 抛 `IllegalArgumentException` | 任务约束 | ✅ |
| `TestGameClock.advance(int)` 对 `< 0` 抛 `IllegalArgumentException` | 任务约束 | ✅ |
| 异常信息含非法值 | 可调试性 | ✅ 如 `"总分钟数不得为负: -1"` |
| 不吞异常、不抛裸 `Exception` | Java 规范 | ✅ |

### 3. 线程规范

| 检查项 | 规范 | 结果 |
|---|---|---|
| 随机统一走 `RandomProvider`（静态单例 `Random`） | 规则文档 §九十 | ✅ 无 `new Random()` |
| 无自建线程 / 无 `Thread.sleep` 于业务代码 | 验收规范 | ✅ |
| 天气状态非并发共享（单线程 UI 模型） | 架构约束 | ✅ |

### 4. 资源规范

| 检查项 | 规范 | 结果 |
|---|---|---|
| 无未关闭的 IO / 流 | Java 规范 | ✅ D 模块不涉及 IO |
| 无内存泄漏（无静态可变集合累积） | Java 规范 | ✅ |
| 工具类私有构造禁止实例化 | Java 规范 | ✅ `RandomProvider`、`GameConstants` |

### 5. 方法长度规范

| 检查项 | 规范 | 结果 |
|---|---|---|
| 单方法 ≤ 50 行 | 团队规范 | ✅ 最长 `rollDailyWeather` 约 15 行 |
| 单一职责 | 团队规范 | ✅ 每个方法只做一件事 |
| 无深层嵌套（≤ 3 层） | 团队规范 | ✅ `rollDailyWeather` 为 if-else 链 |

### 6. 调试方式规范

| 检查项 | 规范 | 结果 |
|---|---|---|
| 无 `System.out.println` 于业务代码 | 团队规范 | ✅ |
| 无 `printStackTrace` | 团队规范 | ✅ |
| 演示加速走 `DemoGameClock`（×12），不改正式数值 | 规则文档 §九 | ✅ |
| 测试推进走 `TestGameClock.advance(int)` | 规则文档 §九 | ✅ |

---

## 二、阶段红线自查

| # | 检查项 | 依据 | 结果 |
|---|---|---|---|
| 1 | 天气概率 40/25/20/15 与规则文档一致 | 规则 §十九 | ✅ |
| 2 | 天气倍率 1.0/1.5/0.5/2.0 与规则文档一致 | 规则 §十九 | ✅ |
| 3 | 天气品质分 0/5/8/15、上限 0/20/24/45 与规则文档一致 | 规则 §三十五 | ✅ |
| 4 | 天气生成经 `RandomProvider`，无 `new Random()` | 规则 §九十 | ✅ |
| 5 | 所有数值经 `GameConstants` 引用，无魔法数字 | 验收规范 | ✅ |
| 6 | `WeatherType` 枚举常量集不改动（存档兼容） | 验收规范 §七十三 | ✅ |
| 7 | D 模块不持有 Crop 天气记录字段（属 A 模块） | 验收规范 §五十 | ✅ |
| 8 | D 模块不实现枯萎/品质/装饰/事件业务 | 模块分工 | ✅ |
| 9 | D 模块不直接写 SQL | 验收规范 §七十五 | ✅ |
| 10 | `StatusView` 只读，不调用 `rollDailyWeather` | 验收规范 §3.1 | ✅ |
| 11 | 不提前实现 P2（离线模拟/随机事件/传说作物/CropMemory/生命故事） | 验收规范 §四十七 | ✅ |
| 12 | 不实现/调用/UI 展示任何收获逻辑 | 任务约束 | ✅ |
| 13 | 颜色仅用 7 色主色表，无渐变/阴影/半透明/自造色 | 任务约束 | ✅ |
| 14 | 测试只测纯函数，不实例化 JavaFX 控件（除既有 StatusViewTest 的 FX 线程适配） | 任务约束 | ✅ |
| 15 | 不动 E 的文件（SceneManager/MainController/MainApplication） | 任务约束 | ✅ |
| 16 | 不修改 A/C/B 模块的类 | 任务约束 | ✅ |
| 17 | 文档矛盾已报告，未自行裁定 | 任务约束 | ✅ 见接口约定文档 §七 |
| 18 | 包名统一 `com.fieldstory.farm` | 任务约束 | ✅ |
| 19 | 编码 UTF-8 | 任务约束 | ✅ |
| 20 | `mvn test` 全绿 | 任务约束 | ✅ 246 tests, 0 failures |

---

## 三、测试覆盖自查

| 测试类 | 用例数 | 覆盖点 |
|---|---:|---|
| `WeatherServiceTest` | 8 | 固定种子复现、概率分布、倍率、品质分、上限、判定、写入状态、显示名/图标 |
| `BasicWeatherStateTest` | 3 | 无参构造、双参构造、setter/getter 往返 |
| `WeatherTypeTest` | 3 | 常量集、`name()` 稳定性、`valueOf` 往返 |
| `FarmGameModelTest` | 9 | 时钟聚合、tick 委托、存档恢复、Farm 字段、天气聚合（+4） |
| `StatusViewTest` | 9 | 天气显示、NPE 保护、时间/游戏日文本 |
| `GameConstantsTest` | 4 | 经济/地图/时间/P0 倍率常量 |
| `BasicGameClockTest` | 13 | 初始值、tick、跨日、边界、**负值校验（+3）** |
| `DemoGameClockTest` | 5 | 每次 tick 推进 120 分钟、跨日、构造器边界 |
| `TestGameClockTest` | 8 | `advance(int)` 推进、跨日、setGameDay/setGameHour、构造器边界 |

---

## 四、溯源说明

| 关键项 | 来源文档 | 章节 |
|---|---|---|
| 天气概率 40/25/20/15 | 规则文档 | §十九 |
| 天气成长倍率 1.0/1.5/0.5/2.0 | 规则文档 | §十九 |
| 天气品质分 0/5/8/15、上限 0/20/24/45 | 规则文档 | §三十五 |
| 每天 00:00 生成天气 | 规则文档 | §十九、§八十一 |
| 随机统一走 RandomProvider | 规则文档 | §九十 |
| 演示/测试时钟（×12） | 规则文档 | §九 |
| 时间换算（1 现实分钟 = 1 游戏小时） | 规则文档 | §5.1 |
| 接口在包根、实现类在 impl | 决策记录 | D13 |
| 时间字段统一 long | 决策记录 | D14 |
| 分层架构固定 | 验收规范 | §3.1 |
| Service 不得直接写 SQL | 验收规范 | §七十五 |
| P1 不得改变 P0 基础数值 | 验收规范 | §四十七 |
