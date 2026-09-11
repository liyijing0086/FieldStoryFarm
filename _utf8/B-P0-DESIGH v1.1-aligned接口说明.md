//接口说明
int getGold();

boolean canAfford(int amount);

void spendGold(int amount);

void addGold(int amount);

PurchaseResult buySeed(
CropType type,
int quantity
);

int getSeedCount(
CropType type
);

boolean hasSeed(
CropType type,
int quantity
);

boolean consumeSeed(
CropType type,
int quantity
);

int calculateBaseSellPrice(
CropType type
);

//跨模块调用关系
A 开垦：
canAfford(TILL_COST)
spendGold(TILL_COST)

A 播种：
hasSeed(type, 1)
consumeSeed(type, 1)

C 收获：
calculateBaseSellPrice(type)
addGold(price)

E 存档：
Player.gold
Player.seedInventory