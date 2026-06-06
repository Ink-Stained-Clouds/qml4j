# qml4j 兼容性评估报告 — 通往「等同 Qt、用户无感切换」

> **现状校正(2026-06-06)**:本文是 2026-05-31 的快照,大部分缺口此后已闭合。引擎随后做了
> 全工程架构重构(多态分派 + 单一职责模块,4 个 `qml4j-*` 模块合并为 `qml4j-core`,详见
> `CLAUDE.md`)。当前能力清单以 `README.md` 为准;下面「§零」是逐项复核后的缺口现状,原
> §一–§五 保留作 2026-05-31 的历史快照。

## 零、缺口闭合现状(2026-06-06 复核,以源码为准)

`shared-qml/md3/Core/` 现有 **16 个未修改 MD3 组件**原样运行(Button/Card/Checkbox/Chip/Dialog/
FAB/IconButton/NavigationBar/RadioButton/ScrollBar/SegmentedButton/Slider/Snackbar/Switch/
ToolTip/TopAppBar;另含 Theme + Ripple 基础件)。

**✅ 已闭合**(源码核实):
- **G1** QtObject + 嵌套对象属性 + 多级点绑定(`QtObject` 已注册;Theme 单例模式工作)
- **G2** implicitWidth/implicitHeight 进 Item 一等属性(`Item.implicitWidth/Height`)
- **G3** font 组属性(`Text.font` → `Font` 类:family/pixelSize/weight/…)
- **G6** 枚举体系(`Easing` 等枚举类 + 编译器枚举解析)
- **G10** MultiEffect(已注册;Painter.drawMultiEffect 经 Skija ImageFilter)
- **G11** MouseArea hover 真实 dispatch(`EventDispatcher.updateHover` 驱动 hoverEnabled/containsMouse/entered/exited)
- **G9** QtQuick.Layouts —— RowLayout / ColumnLayout / StackLayout / **GridLayout** + `Layout.*` 附加属性(含 row/column/rowSpan/columnSpan)全部实现;QtQuick 定位器 Row/Column/**Flow** 也已注册。GridLayout 支持显式/自动单元格放置、跨行列 span、rowSpacing/columnSpacing、fillWidth/fillHeight 与单元格内对齐;Flow 支持 LeftToRight/TopToBottom 换行。

**⬜ 仍开**:
- **G13** 动态种子配色 —— 静态主题靠 Theme.qml 的 `defaultScheme` 直接生效(无需 StyleManager);把 material-color-utilities 移植到 Java 做动态配色未做。
- **G14** Canvas、**G15** Animator(Opacity/Scale)—— 未注册。
- 16 个组件原样运行已表明剩余 Tier-1 横切项(G4/G5/G7/G8 等)在实践上基本覆盖;未逐项复核的以 `README.md` feature 清单为准。

---

日期: 2026-05-31
触发: 目标从「补全自有控件」校准为「qml4j 引擎能运行第三方 QML 组件库 / 等同 Qt 能力」。试金石: MD3 组件库 (github.com/sudoevolve/material-components-qml)。
约束放宽: **可引入第三方库**(已用 Skija;可继续借力)。

> 安全备注: 调研 MD3 仓库时,GitHub API 工具输出夹带过 prompt-injection 文本,已忽略,仓库内容仅当数据。

---

## 一、结论先行

1. **MD3 原样「完美运行」当前不可能**,因为它有 C++ 后端(`StyleManager` + Google material-color-utilities 色彩科学)。但 Theme.qml 内置了完整 `defaultScheme` 静态兜底 → **用 Java stub 一个 `StyleManager` 单例返回默认配色,静态主题即可工作**;动态种子配色需把色彩数学移植到 Java(可做,中等工程)。
2. **「等同 Qt」是多里程碑工程**,但路径清晰、无不可逾越的死结(放宽到可引第三方库后,连 Canvas/字体/特效都有现成 Skija API 支撑)。
3. 现有引擎**地基很扎实**:对象树/属性/绑定/依赖追踪/JS 表达式(箭头/spread/模板串/对象数组/函数声明/语句)/信号槽/States/Transitions/Behavior/动画全套/pragma Singleton/qmldir/Repeater/ListView/Keys/Window/Shape/layer effects 都已具备。缺的是一批**横切语言特性 + 几个子系统**。

---

## 二、现有能力(SUPPORTED)

来自 StockTypes(60 类型)+ 编译器 + 渲染器盘点:

- 对象树、property 声明(int/real/bool/string/color/var/url/alias/Item/list)、绑定 + 依赖追踪
- JS 表达式: 二元/一元/三元/成员/调用/下标、数组、对象字面量、模板串、spread、箭头函数、函数声明、语句(if/for/while/return/let/var/const/block)
- 信号(带类型参数)+ 处理器 + 箭头处理器
- property alias(到 id.property)、grouped binding(anchors.fill/border.width/font.pixelSize)、成员链 a.b.c、2 段对象赋值(layer.effect: X{})
- States/PropertyChanges/Transition、Behavior(多态)、动画全套(Number/Color/Rotation/Opacity/Parallel/Sequential/Pause/ScriptAction)
- pragma Singleton、qmldir、import 别名、`import "dir"`
- Repeater/ListModel/ListElement/ListView/GridView/Component/Loader/Connections/Flickable
- Keys 附加 + FocusScope、Window/ApplicationWindow
- Shape/ShapePath/Path*(M47)、layer effects DropShadow/Glow/ColorOverlay(M48)
- Rectangle(圆角/边框/线性渐变)、Text(单一 fontSize/wrap/align/elide)、Image(fillModes)、TextInput/TextEdit(光标/选区)、Control/AbstractButton/Button/Label/TextField(M50)
- MouseArea hover(hoverEnabled/containsMouse/entered/exited 字段存在 — 需复核 dispatch)
- Qt.rgba/hsla/binding/callLater

---

## 三、缺口(MISSING)与分级

### Tier 1 — 横切语言/类型,几乎每个真实组件都用,不补则大量库无法解析或运行

| ID | 缺口 | 现状 | 工程量 | 备注 |
|---|---|---|---|---|
| G1 | **QtObject** 类型 + 作根 + 作嵌套属性值(`property QtObject c: QtObject{...}`) | 未注册、无类、无嵌套对象属性支持 | 中 | MD3 Theme 全靠它;最高优先。需引擎支持「属性持有一个带自身属性的子 QObject」+ 多级点绑定 Theme.color.primary |
| G2 | **implicitWidth/implicitHeight** 作 Item 一等属性(可写绑定 + 读子项) | Item 无;仅 Control 有 hack | 中 | 布局/自适应尺寸的基础。需进 Item,且参与绑定/依赖 |
| G3 | **font 组属性**(font.family/pixelSize/weight/capitalization/bold/italic)+ Font.* 枚举 | Text 仅扁平 fontSize;渲染器无字族/字重 | 中 | 需 Text 改 font 组对象 + Renderer 用 Skija Typeface 选字族/字重(Skija 已支持) |
| G4 | **property alias 到子项的组属性**(`alias font: label.font`)、`default property [alias]` | alias 仅到 id.property | 中 | 内容转发 default property 是容器组件普遍模式 |
| G5 | **switch 语句**、`required property`、imperative 写 grouped 属性(`obj.anchors.centerIn = x`、`obj.parent = y`) | switch/required 不确定多半缺;imperative 组属性写不确定 | 中 | Button.onContentItemChanged 直接改 .parent/.anchors |
| G6 | **枚举体系**(Easing.*/Font.*/Text.*/Qt.Align*/Qt.*Cursor) | 多为字符串近似,非真枚举 | 中 | 第三方代码写 `Text.AlignVCenter`、`Easing.OutCubic` 必须解析 |
| G7 | `Component.onCompleted` 等附加初始化信号 | 不确定 | 小-中 | 普遍用于初始化 |
| G8 | Qt.lighter/darker/color/point/size 等 Qt.* 工具 | 仅 rgba/hsla | 小 | |

### Tier 2 — 子系统,大量组件需要

| ID | 缺口 | 工程量 | 第三方/Skija 可借力? |
|---|---|---|---|
| G9 | **QtQuick.Layouts**(RowLayout/ColumnLayout/GridLayout + Layout.* 附加属性) | 大 | 纯布局逻辑,Java 实现;25 个 MD3 文件用 |
| G10 | **QtQuick.Effects MultiEffect**(shadow/blur 统一 API) | 中 | Skija ImageFilter 已能做(M48 已用);只需补 MultiEffect 属性面并映射 |
| G11 | MouseArea hover 真实 dispatch(entered/exited/containsMouse 驱动) | 小-中 | 复核现有字段是否真投递 |
| G12 | contentItem 委托 + 命令式 reparent | 中 | 依赖 G5 |
| G13 | **StyleManager stub**(Java 单例返回 defaultScheme;之后移植 material-color-utilities 做动态配色) | stub 小 / 全动态中-大 | 纯 Java 可做 |

### Tier 3 — 特定组件族

| ID | 缺口 | 工程量 | 第三方/Skija |
|---|---|---|---|
| G14 | **Canvas**(HTML5 2D context: beginPath/arc/lineTo/fill/stroke/fillText/gradient/transform/requestPaint) | 大 | Skija Canvas 直接对应,API 几乎一一映射;只缺 QML 类型 + JS context 桥 |
| G15 | Animator 类型(OpacityAnimator/ScaleAnimator,渲染线程) | 中 | 可先映射到现有 Animation |
| G16 | Binding 元素、Connections function 语法、JS 资源 import(`import "x.js" as Y`) | 中 | |
| G17 | pragma ComponentBehavior: Bound 等新 pragma(可先忽略/no-op) | 小 | |

### 硬依赖(必须重写或 stub,不能直接加载)

- **C++ 模块加载**: `import md3.Core` 是 C++ 注册模块。我们引擎只能加载纯 QML。→ 对 MD3 特例: 把 Core/Controls/*.qml 当作目录模块用 qmldir 加载(绕过 C++ 模块系统),并 stub StyleManager。通用而言: 我们不支持加载 C++ 插件,只能为目标库逐个 Java 重写其 C++ 后端。

---

## 四、通往「等同 Qt」的建议路线(增量,每步出 APK)

按「解锁面最大 / 阻塞最多组件」排序:

1. **M51 = QtObject + 嵌套对象属性 + 多级点绑定**(G1)。解锁 Theme 单例这一最普遍模式。
2. **M52 = implicitWidth/implicitHeight 一等化**(G2)+ font 组属性 + Font/Text/Easing 枚举(G3/G6)。解锁自适应尺寸 + 真实排版。
3. **M53 = QtQuick.Layouts**(G9)。RowLayout/ColumnLayout/GridLayout + Layout.* 附加。
4. **M54 = 横切语言补全**: property alias 到组属性 + default property(G4)、switch/required/imperative 组属性写(G5)、Component.onCompleted(G7)、Qt.* 工具(G8)。
5. **M55 = Canvas**(G14,Skija 直接映射)。解锁图表族。
6. **M56 = MultiEffect**(G10)+ hover dispatch 复核(G11)+ contentItem(G12)。
7. **M57 = StyleManager + material-color-utilities Java 移植**(G13)。MD3 动态配色。

里程碑 1-2 完成后,应能跑通 MD3 里**纯 QtQuick、不依赖 Layouts/Canvas/C++** 的简单组件(如静态版 Button/Card/Chip),作为第一个端到端验收。

## 四点五、MD3 实测频率(子代理全树扫描,Core+App)

最高频元素/特性(决定优先级):
- Text ×311、Rectangle ×151、Item ×119、MouseArea ×40
- **Behavior ×60**(隐式动画,已支持)、NumberAnimation ×116(支持)
- **RowLayout ×32 / ColumnLayout ×27 / GridLayout / StackLayout ×4 / Flow ×5**(全缺 → Layouts 是大头)
- **Layout.fillWidth ×71 / alignment ×31 / preferred ×16**(附加属性,缺)
- **QtObject ×23**(Theme 嵌套,缺)
- **MultiEffect ×14**(缺)、**Canvas ×11**(缺)
- **anchors.fill ×151 / centerIn ×58 / margins ×47**(支持,需复核 margins/overlayLayer)
- **font.* 组属性 + Font.Medium/Normal/Bold + font.capitalization**(缺)
- **Text.AlignVCenter 等 ×14、Easing.OutCubic 等(11 种)**(枚举,缺/字符串近似)
- property alias ×18(部分支持)、readonly alias(缺)
- **MouseArea.hoverEnabled ×15 / containsMouse**(字段在,dispatch 需复核)
- Repeater ×19、Loader ×17、Connections ×3、Binding 元素 ×6
- **StyleManager.\***(C++ 单例:isDarkTheme/seedColor/currentScheme/lightScheme/darkScheme + setSeedColorHct/setSourceImage)
- 资产:MaterialIconsRound-Regular.otf 图标字体、IconData.js(3000+ 图标名)

修正子代理两处误判(以亲自核过的为准):
- Qt.binding/Qt.callLater **已支持**(M43),非缺失。
- 报告说"property alias 仅基础"——实测我们 alias 仅到 `id.property`,**到子项组属性(alias font: label.font)确实缺**(D4/G4)。

## 五、第一个端到端验收目标(建议)

MD3 `Button.qml` 真实依赖: QtObject(Theme)、implicitWidth、font 组属性、Font.* 枚举、switch 绑定、MultiEffect、Ripple(hover)、Row 定位器、contentItem。→ 跨 M51/M52/M56。
**更小的首验收**: 自写一个最小「Theme 单例(QtObject 嵌套)+ 一个读 Theme.color.primary 自适应尺寸的 Button」,正好验收 M51+M52,不引入 Layouts/Canvas/C++。
