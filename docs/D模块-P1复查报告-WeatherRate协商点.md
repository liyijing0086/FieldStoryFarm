# D 模块 P1 复查报告：成长公式 WeatherRate 协商点

> 作者：D 模块（zsl）｜ 阶段：P1（v0.2.0-playable）｜ 编码：UTF-8
> 触发：合并时发现「D 的 advanceCrops 仍用旧 2 参 applyGrowth(crop, days)，没传 WeatherRate」
> 上位规则：《FSF游戏规则设计文档.md》V4.0
> 阶段红线：《FSF_P0-P4功能实现与验收规范.md》V4.0 §四十九
> 前置文档：《模块规范/D模块 P1 接口与类设计文档.md》§1.4、§5.1、§九

---

## 一、问题定位

### 1.1 现象

P1 成长公式升级为（验收规范 §四十九）：

```text
GrowthDelta = BaseDailyProgress × ElapsedGameDays × WeatherRate × OperationRate
```

（P1 装饰上线后进一步 × DecorationRate，见验收规范 §六十九 D01/D05/D08/D09。）

但合并前代码中，**D 侧调用链没有把 `WeatherRate` 传下去**：

| 文件 | 合并前现状 | 问题 |
|---|---|---|
| `service/GrowthService.java` | `applyGrowth(Crop, double)` 仍是 P0 **2 参**签名 | 无 `WeatherRate` 入参 |
| `service/impl/BasicGrowthService.java` | 注释「天气 Rate P0 固定 1.0，省略」 | P1 未升级，天气倍率被丢弃 |
| `controller/FarmController.java` | `advanceCrops` 调 `applyGrowth(crop, days)` | **D 调用点没传 WeatherRate** |

### 1.2 根因

这是 **D ↔ A 的跨模块协商点**：

- **D 模块**负责「什么时候推进」（`FarmController` 主循环）与「当前天气倍率是多少」（`WeatherService.getGrowthRate`）；
- **A 模块**负责「怎么成长」（`GrowthService` 公式组装）；
- P1 升级公式时，**双方接口必须同时改**：A 的 `applyGrowth` 要加 `WeatherRate` 参数，D 的 `advanceCrops` 要传这个值。

### 1.3 约束

- D 模块 P1 文档 §1.4：「公式的**组装**由 A 模块 GrowthService 完成（A 模块职责，**D 不越界**）」；
- D 模块 P1 文档 §九：「发现文档间矛盾时立即停止并报告，**禁止自行选择其中一种**」；
- 经核查，**A 模块尚未发布 P1 的 `GrowthService` 签名**（`upstream/feature/p0-lyj-farm` 分支仍为 P0 2 参）。

---

## 二、D 侧处理（不越界）

D 模块**不擅自改 A 的接口语义**，只做两件事：

### 2.1 打通「传参通道」（D 侧）

`FarmController` 新增 `currentWeatherRate()`，从 `FarmGameModel` 取当前天气倍率并传给 A：

```java
private double currentWeatherRate() {
    if (model.getWeatherService() == null || model.getWeatherState() == null) {
        return WEATHER_RATE_P0;   // 1.0，未装配时保持 P0 行为
    }
    return model.getWeatherService().getGrowthRate(model.getWeatherState().getWeatherType());
}
```

`advanceCrops` 改为调用 3 参重载：

```java
static void advanceCrops(Farm farm, GrowthService growthService,
                         double elapsedGameDays, double weatherRate) {
    ...
    growthService.applyGrowth(crop, elapsedGameDays, weatherRate);
}
```

### 2.2 提供「向后兼容的过渡重载」（A 接口）

在 `GrowthService` 接口新增 **default 方法**，默认忽略 `weatherRate`、委托 2 参版本：

```java
default void applyGrowth(Crop crop, double elapsedGameDays, double weatherRate) {
    applyGrowth(crop, elapsedGameDays);
}
```

- **不破坏 A 现有实现**：`BasicGrowthService` 无需改动即可编译、测试全绿；
- **A 确认签名后**：override 本方法，将 `weatherRate` 纳入公式即可，**D 侧无需再改**；
- 该 default 方法已用 Javadoc 明确标注为「跨模块协商点（D ↔ A）」。

---

## 三、待 A 模块（lyj）裁定项

| # | 待裁定项 | D 侧建议 | 影响 |
|---|---|---|---|
| 1 | `GrowthService` 是否采用 3 参 `applyGrowth(Crop, double, double weatherRate)` | 建议采用；D 已按此签名传参 | A 需 override 默认实现，把 `weatherRate` 乘入公式 |
| 2 | `calculateGrowthDelta` 是否同步加 `weatherRate` 参数 | 建议同步（保持两方法签名一致） | 影响 A 内部公式与测试 |
| 3 | P1 装饰 `DecorationRate` 是否也走同一参数通道 | 建议 P1 装饰上线时由 B 模块 `BuffService` 提供，A 组装 | 见验收规范 §六十九 |

> **D 模块立场**：D 只提供 `WeatherRate` 数值并传参，**不参与公式组装**（D 模块 P1 文档 §1.4）。
> 上述 3 项由 A 模块（lyj）最终裁定并同步 A 设计文档；D 侧已就绪，A 确认后无需再改 D。

---

## 四、验证

| 验证项 | 结果 |
|---|---|
| `mvnw test` 全量 | **246 tests, 0 failures, 0 errors** |
| 新增 `FarmControllerTest` 用例 | +4（3 参传参、多作物传参、2 参默认 1.0、null 安全） |
| 新增 `GrowthServiceTest` 用例 | +1（default 3 参委托 2 参，向后兼容） |
| 既有 P0/P1 测试 | 全部保持通过（2 参重载默认 `WEATHER_RATE_P0`） |

---

## 五、溯源

| 内容 | 来源 |
|---|---|
| P1 成长公式含 WeatherRate | 验收规范 §四十九 |
| WeatherRate 四态 1.0/1.5/0.5/2.0 | 规则文档 §十九 |
| 公式组装归 A、D 只提供倍率 | D 模块 P1 文档 §1.4、§5.1 |
| 发现矛盾立即报告、禁止自行裁定 | D 模块 P1 文档 §九 |
| P1 装饰 DecorationRate | 验收规范 §六十九 |
