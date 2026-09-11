# 《田野物语 · 三韵集》
# B模块 P0 接口与类设计文档

## 1. 文档信息

|项目|内容|
|---|---|
|模块|B：玩家与经营模块|
|开发阶段|P0|
|游戏版本|`v0.1.0-core`|
|设计版本|`B-P0-DESIGN v1.1-aligned`|
|上一设计版本|`B-P0-DESIGN v1.0`|
|状态|接口冻结候选版|
|主要职责|金币、种子库存、种子购买、基础经济|
|主要协作模块|A土地与作物、C品质与收获、D世界环境、E存档与组装|

本版本是在B模块原P0设计基础上的接口对齐版本。

B模块P0仍然负责：

```text
金币
+
种子库存
+
种子购买
+
统一经济入口
+
P0基础售价访问
```

不扩展到P1的完整商店、肥料、装饰和Buff。

---

# 2. P0模块目标

B模块解决玩家经营过程中的资源问题。

完整关系：

```text
新游戏
↓
拥有500金币
↓
购买种子
↓
金币减少
↓
种子库存增加
↓
A播种时消耗种子
↓
C收获出售
↓
金币增加
↓
继续购买种子
```

B模块的核心职责不是“再造一个商店系统”，而是：

> 为所有模块提供统一的金币与种子资源操作入口。

---

# 3. 架构原则

项目继续遵循：

```text
View
↓
Controller
↓
Service
↓
Model
↓
DAO / Persistence
```

B模块内部遵循：

```text
Player
负责保存状态

EconomyService
负责定义经济业务

EconomyServiceImpl
负责执行经济业务
```

因此：

```text
Model = 现在有多少钱、多少种子

Service = 怎样购买、怎样扣钱、怎样增加资源
```

禁止Controller、A模块、C模块直接修改：

```text
Player.gold
Player.seedInventory
```

业务修改统一通过：

```text
EconomyService
```

---

# 4. 接口与实现类拆分规则

按照D13包规范：

```text
接口
放在模块包根目录

实现类
放在impl子包
```

所以：

```text
EconomyService
        ↓
EconomyServiceImpl
```

采用：

```text
接口 + 实现类
```

方式。

但：

```text
Player
CropType
PurchaseResult
```

属于数据模型或枚举，不强行制造对应接口。

也就是说，不建立：

```text
PlayerInterface
PlayerImpl

CropTypeInterface
CropTypeImpl
```

这种没有业务价值的抽象。

---

# 5. 最终核心类型

P0 B模块最终保留：

|类型|性质|职责|归属|
|---|---|---|---|
|`Player`|Model|保存金币和种子库存|B|
|`PurchaseResult`|Enum|表示购买结果|B|
|`EconomyService`|Interface|P0统一经济业务入口|B|
|`EconomyServiceImpl`|Implementation|经济接口默认实现|B|
|`CropType`|共享Enum|作物类型、种子价、基础售价|全项目共享|
|`GameConstants`|共享Utility|初始金币、开垦价格等公共常量|全项目共享|

---

# 6. 不建立的类型

P0不新增：

```text
Wallet
GoldWallet

SeedType

SeedInventory
SeedInventoryImpl

SeedShopService
SeedShopServiceImpl

Item
WheatSeed
CornSeed
CarrotSeed
```

原因：

## 6.1 金币已经属于Player状态

唯一金币状态：

```text
Player.gold
```

无需形成：

```text
Player
↓
Wallet
↓
GoldWallet
↓
balance
```

---

## 6.2 种子库存已经属于Player

唯一种子库存：

```java
Map<CropType, Integer> seedInventory;
```

无需再维护：

```text
SeedInventoryImpl.seeds
```

否则会产生两份库存状态。

---

## 6.3 不建立SeedType

全项目统一：

```java
CropType.WHEAT
CropType.CORN
CropType.CARROT
```

禁止同时出现：

```java
SeedType.WHEAT
CropType.WHEAT
```

---

## 6.4 P0不建立ShopService

P0快捷购买由：

```java
EconomyService.buySeed(...)
```

负责。

P1再扩展完整商店。

---

# 7. CropType共享契约

## 7.1 设计结论

`CropType`作为全项目共享类型，同时作为：

```text
种子价格
+
基础售价
```

的唯一数据源。

B模块不得自行维护价格switch。

统一契约：

```java
package com.fieldstory.farm.model;

public enum CropType {

    WHEAT(10, 50),
    CORN(15, 70),
    CARROT(20, 60);

    private final int seedPrice;
    private final int baseSellPrice;

    CropType(
            int seedPrice,
            int baseSellPrice) {

        this.seedPrice = seedPrice;
        this.baseSellPrice = baseSellPrice;
    }

    /**
     * 获取该作物种子的P0基础价格。
     */
    public int getSeedPrice() {
        return seedPrice;
    }

    /**
     * 获取该作物的P0基础出售价格。
     */
    public int getBaseSellPrice() {
        return baseSellPrice;
    }
}
```

对应数据：

|CropType|种子价格|基础售价|
|---|---:|---:|
|WHEAT|10|50|
|CORN|15|70|
|CARROT|20|60|

---

# 8. 价格单一数据源原则

禁止B模块继续出现：

```java
switch (type) {

    case WHEAT -> 10;
    case CORN -> 15;
    case CARROT -> 20;
}
```

也禁止再次出现：

```java
switch (type) {

    case WHEAT -> 50;
    case CORN -> 70;
    case CARROT -> 60;
}
```

统一：

```java
type.getSeedPrice();
```

和：

```java
type.getBaseSellPrice();
```

因此：

```text
CropType
=
P0价格唯一数据源
```

A、B、C不得各自复制价格表。

---

# 9. GameConstants经济公共常量

公共经济常量使用：

```java
GameConstants.INITIAL_GOLD
GameConstants.TILL_COST
```

其中：

```text
INITIAL_GOLD = 500

TILL_COST = 5
```

B模块不再在Player中定义：

```java
Player.INITIAL_GOLD
```

避免出现：

```text
Player.INITIAL_GOLD
GameManager.INITIAL_GOLD
GameConstants.INITIAL_GOLD
```

三份相同常量。

统一原则：

```text
GameConstants
↓
唯一数值定义

GameManager
↓
新游戏初始化

Player
↓
只保存初始化后的状态
```

如果现有`GameManager.INITIAL_GOLD`暂时不能删除，为兼容旧代码可改为：

```java
public static final int INITIAL_GOLD =
        GameConstants.INITIAL_GOLD;
```

不能重新：

```java
= 500;
```

---

# 10. Player类设计

## 10.1 职责

`Player`是玩家资源状态对象。

P0至少保存：

```text
gold

seedInventory
```

建议字段：

```java
package com.fieldstory.farm.model;

import java.util.EnumMap;
import java.util.Map;

public class Player {

    private int gold;

    private Map<CropType, Integer> seedInventory;

    public Player() {
        // 供对象创建 / JSON恢复使用。
        // 新游戏500金币由GameManager + GameConstants负责初始化。
    }

    public int getGold() {
        return gold;
    }

    public void setGold(int gold) {
        this.gold = gold;
    }

    public Map<CropType, Integer> getSeedInventory() {
        return seedInventory;
    }

    /**
     * 仅用于状态恢复/序列化。
     * 正常游戏业务禁止直接调用，
     * 种子变化应经过EconomyService。
     */
    public void setSeedInventory(
            Map<CropType, Integer> seedInventory) {

        this.seedInventory = seedInventory;
    }
}
```

---

# 11. Player职责边界

Player可以：

```text
保存金币

保存三种种子数量

提供状态getter

提供持久化恢复需要的setter
```

Player不负责：

```text
判断钱够不够

扣钱

奖励金币

计算价格

购买种子

判断库存是否足够

消耗种子

出售作物
```

上述全部属于：

```text
EconomyService
```

---

# 12. Player唯一状态源

游戏运行时：

```text
GameManager
↓
GameState
↓
Player
```

B模块不得自己：

```java
new Player()
```

创建第二份活动玩家。

正确取得当前Player：

```text
GameManager
↓
currentState()
↓
getPlayer()
```

然后传入：

```java
new EconomyServiceImpl(player)
```

从而保证：

```text
UI看到的金币
=
保存的金币
=
EconomyService修改的金币
```

---

# 13. PurchaseResult设计

```java
package com.fieldstory.farm.model.economy;

public enum PurchaseResult {

    /**
     * 购买成功。
     */
    SUCCESS,

    /**
     * 玩家金币不足。
     */
    INSUFFICIENT_GOLD,

    /**
     * 购买数量非法。
     */
    INVALID_QUANTITY
}
```

P0暂时只需要三个结果。

---

# 14. EconomyService最终接口

包：

```text
com.fieldstory.farm.service.economy
```

最终冻结签名：

```java
package com.fieldstory.farm.service.economy;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.economy.PurchaseResult;

/**
 * B模块P0统一经济服务接口。
 *
 * 所有金币和种子资源的业务修改
 * 原则上必须通过本接口完成。
 */
public interface EconomyService {

    // ========================
    // 金币
    // ========================

    /**
     * 获取当前金币余额。
     */
    int getGold();

    /**
     * 判断当前金币是否足够支付指定金额。
     */
    boolean canAfford(int amount);

    /**
     * 支付指定金币。
     *
     * 调用前应通过canAfford完成余额检查。
     *
     * @throws IllegalArgumentException amount < 0
     * @throws IllegalStateException 金币不足
     */
    void spendGold(int amount);

    /**
     * 增加金币。
     *
     * @throws IllegalArgumentException amount < 0
     */
    void addGold(int amount);


    // ========================
    // 种子购买
    // ========================

    /**
     * 购买指定类型和数量的种子。
     *
     * 价格必须通过CropType.getSeedPrice()取得。
     */
    PurchaseResult buySeed(
            CropType type,
            int quantity
    );


    // ========================
    // 种子库存
    // ========================

    /**
     * 查询指定种子的数量。
     */
    int getSeedCount(
            CropType type
    );

    /**
     * 判断指定种子是否足够。
     */
    boolean hasSeed(
            CropType type,
            int quantity
    );

    /**
     * 消耗指定数量种子。
     *
     * 库存不足时返回false，
     * 并保证库存状态不变化。
     */
    boolean consumeSeed(
            CropType type,
            int quantity
    );


    // ========================
    // P0基础出售价格
    // ========================

    /**
     * 获取P0基础售价。
     *
     * 本方法自身不保存价格表，
     * 实现必须委托：
     * CropType.getBaseSellPrice()
     */
    int calculateBaseSellPrice(
            CropType type
    );
}
```

---

# 15. EconomyServiceImpl空实现骨架

包：

```text
com.fieldstory.farm.service.economy.impl
```

类：

```java
package com.fieldstory.farm.service.economy.impl;

import com.fieldstory.farm.model.CropType;
import com.fieldstory.farm.model.Player;
import com.fieldstory.farm.model.economy.PurchaseResult;
import com.fieldstory.farm.service.economy.EconomyService;

/**
 * EconomyService的P0默认实现。
 *
 * 当前类设计阶段仅提供方法签名，
 * 具体业务逻辑在P0正式开发时实现。
 */
public class EconomyServiceImpl
        implements EconomyService {

    private final Player player;

    public EconomyServiceImpl(Player player) {
        this.player = player;
    }

    @Override
    public int getGold() {
        // TODO P0
        return 0;
    }

    @Override
    public boolean canAfford(int amount) {
        // TODO P0
        return false;
    }

    @Override
    public void spendGold(int amount) {
        // TODO P0
    }

    @Override
    public void addGold(int amount) {
        // TODO P0
    }

    @Override
    public PurchaseResult buySeed(
            CropType type,
            int quantity) {

        // TODO P0：
        // 单价从type.getSeedPrice()取得
        return null;
    }

    @Override
    public int getSeedCount(
            CropType type) {

        // TODO P0
        return 0;
    }

    @Override
    public boolean hasSeed(
            CropType type,
            int quantity) {

        // TODO P0
        return false;
    }

    @Override
    public boolean consumeSeed(
            CropType type,
            int quantity) {

        // TODO P0
        return false;
    }

    @Override
    public int calculateBaseSellPrice(
            CropType type) {

        // TODO P0：
        // 最终实现直接：
        // return type.getBaseSellPrice();

        return 0;
    }
}
```

当前阶段保证：

```text
接口存在
+
实现类存在
+
方法签名确定
+
其他模块可以引用
+
工程能够编译
```

但暂不实现经济业务。

---

# 16. P0正式开发时的buySeed规则

正式实现：

```text
buySeed(type, quantity)
↓
检查type
↓
检查quantity > 0
↓
type.getSeedPrice()
↓
单价 × quantity
↓
canAfford(totalPrice)
↓
金币不足
→ INSUFFICIENT_GOLD

金币足够
↓
扣金币
↓
种子库存增加
↓
SUCCESS
```

必须保证：

```text
扣金币
+
增加种子
```

属于同一次完整业务。

不能出现：

```text
钱扣了
种子没增加
```

---

# 17. A模块对接接口

A负责：

```text
土地状态机
开垦
播种
作物创建
```

## 17.1 开垦

A调用：

```java
economyService.canAfford(
        GameConstants.TILL_COST
);

economyService.spendGold(
        GameConstants.TILL_COST
);
```

B负责：

```text
判断金币
扣金币
```

A负责：

```text
检查土地状态
EMPTY → TILLED
```

---

## 17.2 播种

A调用：

```java
economyService.hasSeed(
        cropType,
        1
);

economyService.consumeSeed(
        cropType,
        1
);
```

B负责：

```text
检查种子库存
消耗种子
```

A负责：

```text
检查TILLED
创建Crop
TILLED → PLANTED
```

播种阶段：

```text
不得再次扣金币
```

---

# 18. C模块对接接口

C负责：

```text
基础收获
```

C需要基础售价时调用：

```java
int price =
        economyService
                .calculateBaseSellPrice(
                        crop.getCropType()
                );
```

B内部最终：

```java
return type.getBaseSellPrice();
```

然后C调用：

```java
economyService.addGold(price);
```

C不直接：

```java
player.setGold(...)
```

---

# 19. D模块对接

D负责：

```text
GameClock
RandomProvider
状态栏
```

D显示金币时可以：

```text
只读Player.getGold()
```

或者由Controller读取：

```java
economyService.getGold()
```

但不得通过状态栏修改：

```text
Player.gold
Player.seedInventory
```

B模块P0不依赖：

```text
RandomProvider
WeatherType
EventType
```

---

# 20. E模块对接

E负责：

```text
GameManager
GameState
JSON存档
SceneManager
```

E需要保存：

```text
Player.gold

Player.seedInventory
```

活动Player来源：

```text
GameManager
↓
currentState()
↓
getPlayer()
```

EconomyServiceImpl应绑定这同一个Player。

B不得自己保存JSON。

B不得自己创建：

```text
SaveService
JsonSaveService
```

---

# 21. 场景组装约定

如果B在P0提供种子快捷购买面板：

```text
B UI
↓
SceneManager.Slot.RIGHT
```

B不得：

```java
new Scene(...)
new Stage(...)
setRoot(...)
```

场景统一交给E的：

```java
SceneManager.mount(...)
```

---

# 22. P0包结构最终版

```text
com.fieldstory.farm
│
├── model
│   │
│   ├── Player.java                  ← B维护
│   │
│   ├── CropType.java                ← 全项目共享
│   │
│   └── economy
│       └── PurchaseResult.java       ← B维护
│
├── service
│   └── economy
│       │
│       ├── EconomyService.java       ← B维护
│       │
│       └── impl
│           └── EconomyServiceImpl.java
│
└── util
    └── GameConstants.java            ← 全项目共享
```

说明：

```text
CropType
GameConstants
```

是共享资产。

B只引用确认后的统一版本，不另建副本。

---

# 23. P0跨模块接口冻结表

|调用者|B提供接口|用途|
|---|---|---|
|A|`canAfford(amount)`|开垦前余额检查|
|A|`spendGold(amount)`|开垦扣金币|
|A|`hasSeed(type, quantity)`|播种前检查|
|A|`consumeSeed(type, quantity)`|播种消耗|
|B/UI|`buySeed(type, quantity)`|购买种子|
|B/UI|`getGold()`|显示金币|
|B/UI|`getSeedCount(type)`|显示库存|
|C|`calculateBaseSellPrice(type)`|基础售价访问|
|C|`addGold(amount)`|出售后增加金币|
|D|`getGold()`或只读Player|状态栏显示|
|E|`Player.gold`|存档|
|E|`Player.seedInventory`|存档|

---

# 24. 输入与异常约定

## addGold

```text
amount < 0
→ IllegalArgumentException
```

## spendGold

```text
amount < 0
→ IllegalArgumentException
```

余额不足：

```text
IllegalStateException
```

推荐调用顺序：

```text
canAfford()
↓
spendGold()
```

## buySeed

```text
quantity <= 0
→ INVALID_QUANTITY
```

金币不足：

```text
INSUFFICIENT_GOLD
```

购买成功：

```text
SUCCESS
```

## consumeSeed

库存不足：

```text
false
```

并保证库存不变化。

---

# 25. P0范围限制

B模块P0只实现：

```text
金币

种子库存

种子购买

经济统一入口

基础售价访问
```

明确不实现：

```text
肥料

装饰

装饰Buff

品质倍率

事件倍率

神秘商人

土地解锁

套装

传说奖励

离线经济模拟

完整ShopService
```

---

# 26. P1兼容设计

P1加入：

```text
完整商店
装饰
肥料
Buff
品质售价
```

种子购买仍继续：

```java
economyService.buySeed(...)
```

不重写P0种子购买。

---

# 27. P2兼容设计

P2完整收获：

```text
Quality
↓
Legendary
↓
价格计算
↓
EconomyService
↓
金币
```

未来可在不破坏P0接口的情况下增加：

```java
calculateSellPrice(...)
```

但P0阶段禁止提前定义复杂品质、装饰、事件参数。

---

# 28. P0测试要求

正式实现后至少测试：

### 金币

```text
初始新游戏金币 = 500

余额判断正确

金币收入正确

金币支出正确

金币不能为负
```

### 买种子

```text
WHEAT 单价10

CORN 单价15

CARROT 单价20

多数量购买总价正确

金币不足购买失败

非法quantity失败

失败时金币和库存均不变化
```

### 库存

```text
查询正确

消费正确

库存不足返回false

失败不改变库存
```

### 基础售价

```text
WHEAT = 50

CORN = 70

CARROT = 60
```

并验证B没有第二份价格switch。

---

# 29. 最终职责边界

```text
                 B 玩家与经营模块

                       Player
                  /              \
               gold          seedInventory
                  \              /
                   \            /
                    EconomyService
                           △
                           │
                 EconomyServiceImpl

                    /       |       \
                   /        |        \
                  ↓         ↓         ↓

          A 土地/播种    B 买种      C 收获
```

核心原则：

```text
Player
=
唯一玩家资源状态

EconomyService
=
唯一经济业务入口

CropType
=
P0作物价格唯一数据源

GameConstants
=
公共常量唯一数据源
```

---

# 30. 设计冻结结论

B模块P0冻结为：

```text
Player

PurchaseResult

EconomyService
EconomyServiceImpl
```

依赖共享：

```text
CropType
GameConstants
GameState
GameManager
```

其中：

```text
CropType
负责作物类型、种子价格、基础售价

GameConstants
负责INITIAL_GOLD和TILL_COST
```

B不得重新创建这些共享数据的副本。

本设计版本：

```text
B-P0-DESIGN v1.1-aligned
```

作为P0编码前接口冻结候选版本。