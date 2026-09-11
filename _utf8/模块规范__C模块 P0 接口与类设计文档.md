# 《田野物语·三韵集》C任务（物品&商店背包模块）跨模块开发约束文档
> 文档用途：明确C任务（物品Item、背包Inventory、商店Shop模块）与小组其他成员A/B/D模块之间的接口约束、数据契约、禁止行为、集成注意事项，规避代码冲突、接口不匹配、数据不同步问题，保障后期整体项目可以顺利合并运行。
> 参考基准：工程脚手架文档 `v0.0.1‑skeleton`，强制遵循 MVC+Service+DAO分层架构。

## 一、小组模块分工总览
| 组员          | 负责模块           | 核心产出                                                     |
| ------------- | ------------------ | ------------------------------------------------------------ |
| A组员         | P0农场基础闭环     | Player、Farm、Soil、Crop模型；GrowthService、FarmController、FarmView；农场土地、作物生长、收获逻辑 |
| B组员         | P1天气品质策略模块 | WeatherService、QualityService、BuffService；天气、品质评分、肥料业务逻辑 |
| C组员（本人） | 物品‑背包‑商店模块 | Item、ItemType、Inventory、ShopModel；ShopController；InventoryView、ShopView；物品买卖、背包存取 |
| D组员         | P2离线与特色系统   | OfflineSimulationService、EventService、MemoryService；离线模拟、随机事件、生命记忆、传说作物 |

> 重要：**C模块不重复实现Player、Farm、Crop、天气相关业务逻辑，只调用A/B/D对外暴露的公开接口**。

## 二、C模块与A组员（农场基础模块）接口约束【最高优先级，最容易冲突】
A组员产出：`Player`、`Crop`、`Farm`、`EconomyService`

### 1. 数据契约
1. ✅ C模块**不自己新建Player类**，直接复用A组员提供的`model.player.Player`。
   - C模块只读取：`player.gold`、`player.fertilizer`；
   - 修改金币、肥料**禁止直接set赋值**，必须调用A提供的公开方法：
     `player.addGold()` / `player.consumeGold()` / `player.addFertilizer()` / `player.consumeFertilizer()`
2. 作物收获产出物品：
   - A模块`Harvest`收获完成后，产出收获产物Item，调用C模块`Inventory.addItem()`把产物放入背包；
   - ❗双方约定ItemType枚举，**收获产物统一使用枚举 HARVEST_PRODUCE**，枚举文件由C维护，A组员直接引用该枚举，不要自己新建一套物品类型。
3. 种子购买：C商店模块卖出种子Item，A模块在播种时读取背包内的种子Item，消耗该Item再执行播种。

### 2. 禁止行为（冲突避坑）
1. ❌ C任务禁止复制、改写A组员的`Player`实体类，不新增私有字段到Player；需要扩展属性提前小组沟通。
2. ❌ C模块不要自己写金币增减逻辑，全部调用Player对外公开方法，防止A、C两边同时修改玩家金币造成数据不一致。
3. ❌ C模块不能操作Soil、Crop对象内部状态；播种、开垦、收获全部交给A的FarmController处理，C只负责背包、商店UI。

### 3. 集成联调注意点
1. 合并代码前，确认`Player`类的方法签名保持一致，不私自修改方法参数。
2. 收获流程时序：A收获作物 → 生成Item对象 → 调用C的Inventory.addItem()；**A负责生成产物，C只做背包存储**。

## 三、C模块与B组员（天气品质、肥料模块）约束
B组员产出：QualityService、BuffService、肥料业务逻辑

### 1. 数据契约
1. 肥料作为一种`ItemType.FERTILIZER`类型的物品，由C模块背包管理；
2. **使用肥料逻辑归属B/A模块**：C模块背包只负责存放肥料、展示肥料数量；
   - 当玩家在背包选中肥料，交给A的FarmController，再调用B相关Service执行施肥业务；
   - C不实现施肥的业务计算（成长加成、品质加分）。
3. 商店可以售卖肥料Item，C只处理购买扣除金币、加入背包；肥料的业务效果由B模块实现。

### 2. 禁止行为
❌ C模块禁止在ShopController/Inventory中写肥料的业务计算公式（成长倍率、品质加分），这属于B模块职责。

## 四、C模块与D组员（离线模拟、事件系统）约束
D组员产出：OfflineSimulationService、EventService（神秘商人事件）

### 1. 数据契约
1. 神秘商人事件：D模块触发事件，触发“某类作物售价×2”，**售价倍率变量存放在D的EventService**；
   - C模块ShopModel在计算出售价格时，读取D模块提供的公开事件倍率接口，C不要自己写事件逻辑。
2. 离线模拟：离线过程产生的奖励（小动物来访产出种子/肥料），D模块生成Item，调用C的`Inventory.addItem()`存入背包。
3. 离线存档加载：D做离线推演完成后，Player、Inventory数据要同步持久化DAO。

### 2. 禁止行为
❌ C模块不实现任何随机事件、离线时间推演逻辑；只提供物品存入背包的接口给D调用。

## 五、DAO数据库层约束（持久化，全部组员共同遵守）
1. Inventory背包、Item的数据持久化：**C任务负责编写InventoryDAO，完成背包数据读写数据库**。
2. PlayerDAO、CropDAO由A组员开发；C模块不能修改DAO内部SQL。
3. 数据库表约定：
   - C负责维护背包相关表；
   - C绝对不能修改player、crop表的字段，如需新增字段必须小组开会统一修改数据库脚本。

> 风险：如果私自修改别人负责的表，合并后数据库会报字段不存在，程序直接崩溃。

## 六、UI层约束（视图View）
1. C模块实现`InventoryView`、`ShopView`，**不修改A组员的FarmView农场主画布代码**。
2. 窗口跳转：农场主界面(A)点击商店按钮，触发回调打开C的ShopView；
   - A的FarmView只做按钮点击事件转发，商店弹窗渲染全部交给C。
3. 所有View遵循脚手架规范：View**只读取Model，不能修改Model数据**；所有操作交给Controller。

## 七、公共枚举与常量（极易产生冲突点！重点）
1. `ItemType`枚举属于C模块维护，A/B/D组员只能引用，禁止各自复制一份ItemType，否则编译两个不同枚举类，运行时判等全部失效。
2. 全局常量`GameConstants`放在util包，所有人共用，修改常量需要小组沟通，禁止每个人自己写一套魔法数字。

## 八、代码合并与版本控制约束（Git）
1. C任务只修改这些包下文件：
    - `model.item`（Item、ItemType、Inventory、ShopModel）
    - `controller.ShopController`
    - `view.InventoryView ShopView`
    - `dao.InventoryDAO`
    - `util.GameConstants(少量修改)`

2. ⚠️尽量不要修改A/B/D组员的java文件；必须修改的，先沟通，不要直接提交修改别人的代码。

3. 提交commit语义：`feat(C‑模块): 完成背包添加物品逻辑`，带上C模块标识，方便代码review。

    ## 九、接口约定清单（后期集成测试用）
    > C模块对外提供给其他组员调用的公开方法，其他组员会调用这些接口，签名一旦确定不要随意改动。

    ```java
    // 给A/D组员调用：向背包增加物品
    public boolean Inventory.addItem(Item item)
    
    // 给A/D组员调用：移除背包指定槽位物品
    public Item Inventory.removeItem(int slotId)
    
    // 商店对外，供外部触发购买
    public boolean ShopModel.buyItem(Item item, Player player)
    
    // 商店对外，供外部触发出售
    public boolean ShopModel.sellItem(Item item, Player player)
    ```

    
