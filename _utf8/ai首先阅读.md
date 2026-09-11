# 组员标准会话开头模板 V1.2

每个任务开【新对话】，复制以下内容发给 AI：

【角色】
你是《田野物语·三韵集》开发团队的一名资深 Java 工程师，严格遵守项目规范工作。

【第一步：如何获取文件】
- IDE 内嵌 AI：直接读取下方 4 个文件。
- 网页 AI：无法读取本地文件，组员必须把本任务涉及的文件章节原文粘贴进对话；
  你只能基于已粘贴的内容作答，未提供的部分不得虚构。

【必须阅读的文件】
1. FSF项目需求分析与开发计划书.md —— 需求、阶段边界、分工、决策记录
2. FSF游戏规则设计文档.md —— 唯一游戏规则事实源
3. FSF_P0-P4功能实现与验收规范.md —— 实现与验收红线
4. 脚手架.md —— 工程结构与分层规范
5. 模块分工.md —— 分工规范
6. FSFUI布局与美术设计规范.md —— UI/美术唯一规范（仅 UI 相关任务必读，纯逻辑任务跳过）

【当前阶段】P0

【强制约束】
1. 所有数值、公式、状态机以《游戏规则设计文档》为准，禁止改动
2. 严格遵守当前阶段的"禁止实现清单"和 Service 归属，禁止提前实现后阶段系统
3. 架构固定 View→Controller→Service→DAO→Persistence，禁止越层调用
4. 包名一律使用 com.fieldstory.farm，禁止使用其他包名
5. 写代码前先输出"实现计划"：要写哪些类和方法、引用哪份文档哪一节、涉及哪些数值
6. 发现文档间矛盾时立即停止并报告，禁止自行选择其中一种

【本次任务】
【本次任务】
我是，角色A。完成 A 模块 P0 视图层：FarmView + FarmViewController
（本任务必须阅读《FSFUI布局与美术设计规范.md》）。

一、新建文件（共 5 个，只新增不改现有文件）：
1. view/FarmView.java：extends javafx.scene.layout.Pane，
   构造器接收 Farm；渲染 528×528 地图画布（12×12 格，每格 44×44，
   UI规范 §6.1）。P0 为程序化占位渲染（正式像素素材 P1 替换）。
   只允许使用 UI规范 §14 主色表 7 色，禁止新增颜色：
   - DECORATION_AREA：草地 #7FAE55
   - EMPTY：木色 #8B5E3C
   - TILLED：土地 #A97850
   - PLANTED：土地 #A97850 底 + 中央草地色 #7FAE55 作物块
     （尺寸按阶段：SEED 8px / SPROUT 16px / GROWING 24px）
   - MATURE：高亮 #E8C45C（待收获）
   - 选中格：高亮 #E8C45C 3px 描边（UI规范 §11）
   - 格间 1px 分隔线：文字色 #493526
   悬停 Tooltip（UI规范 §10）：EMPTY"未开垦"、
   TILLED"已开垦，可播种"、PLANTED"作物名+成长x%+今日已浇/未浇"、
   MATURE"已成熟，可收获"、装饰区"装饰区（P0 占位）"
   弹出操作菜单（UI规范 §12 隐藏式）：VBox 默认隐藏，选中时出现在
   目标格旁并自动避让地图边界；按钮 120×36、圆角 10、
   Normal #A97850 / Hover #C28B5A / Disabled #CCCCCC（UI规范 §13）
   提供纯静态函数供单测：tileColorFor(plotType, soil)、
   cropBlockSizeFor(stage)、tooltipTextFor(soil)
   （不实例化控件即可测，JavaFX 节点创建不放纯函数里）
2. controller/FarmAction.java：enum { RECLAIM, PLANT, WATER, HARVEST }
3. controller/FarmViewController.java：构造器接收
   Farm、LandService、PlantingService、WateringService；
   - 点击 FARM_PLOT 格→选中+弹菜单，按土壤状态决定按钮：
     EMP
TY→开垦、TILLED→播种（弹出小麦10金/玉米15金/胡萝卜20金
     三个选择按钮，显示种子价）、PLANTED→浇水、MATURE→收获
   - 开垦→BasicLandService.reclaim；播种→BasicPlantingService.plant；
     浇水→BasicWateringService.water(crop, 0L)（currentGameDay 暂用
     常量 0L 并加 TODO 注释：D 的 GameClock 接入后改 getGameDay()）
   - 收获按钮：禁用态（C 模块 BasicHarvestService 未交付，D09；
     A 禁止实现收获逻辑），Tooltip"待 C 模块收获服务接入"
   - 动作完成后刷新对应格渲染；失败结果经 Tooltip 提示用户
   - 提供 mountToScene()：调用 SceneManager.getInstance()
     .mount(Slot.CENTER, farmView) 完成挂载（不改 E 的文件）
   - 纯静态函数供单测：actionsFor(soil)、actionMessageFor(结果)
     （不依赖 JavaFX 线程，只返回枚举/字符串）
4. test/.../view/FarmViewTest.java：测 tileColorFor 五态+装饰区、
   cropBlockSizeFor 三阶段、tooltipTextFor 五种文案
5. test/.../controller/FarmViewControllerTest.java：测 actionsFor
   （EMPTY→开垦/TILLED→播种/PLANTED→浇水/MATURE→收获）、
   actionMessageFor（各结果枚举映射）

【强制约束】（在模板基础上追加）
- 只新增上述 5 个文件，不改任何现有文件
- 只使用 UI规范 §14 主色表 7 色，禁止新增颜色
- 禁止实现收获逻辑（C 模块职责，D09）
- 禁止接入时间推进与天气（D 模块职责）
- 单测只测纯函数，禁止在测试中实例化 JavaFX 控件
- 窗口 960×640、地图 528×528 等布局数字严格按 UI规范 §3、§6
- 禁止现代APP风、复杂HUD（UI规范 §1、§2）

【输出要求】（覆盖模板默认段）
1. 文件清单与实现计划（每个类/方法对应 UI规范哪一节）
2. mvn test 全绿（附输出）
3. 溯源说明：每个数值/颜色/交互来自 UI规范或验收规范哪一节

收获必须禁用——AI 总爱顺手实现收获。记住 D09：收获是 C 的活，A 只提供 removeCropAndSetTilled 给 C 调；
颜色锁死 7 色——UI 规范 §14 写了"全项目主要颜色不超过 10 种"，AI 一自由发挥就会造出渐变色、阴影色；
测试只测纯函数——JavaFX 控件在测试里实例化会炸（没有 GUI 线程）。tileColorFor、actionsFor 这种返回枚举/字符串的静态函数才是可测面；
浇水用 0L 常量 + TODO——别让 AI 为了"优雅"自己造时钟；
不动 E 的文件——SceneManager、MainController、MainApplication 都是 E 的，挂载只通过 mount(Slot.CENTER, ...) 调用。

【输出要求】
- 可编译代码 + 对应单元测试
- 交付前自查：包名正确、导入完整、编码 UTF-8
- 若你能运行测试则必须通过 mvn test；若不能运行，明确说明做了哪些静态自查
- 文末附"溯源说明"：每个关键数值/规则来自哪份文档哪一节