# RECODE 执行进度 (session handoff)

> **新 session 接续步骤**:① 读本文件 → ② 读 `RECODE_PLAN.md`(总蓝图/角色/约束)
> → ③ 读 `CLAUDE.md`(房规)→ ④ recall memory `project_recode_progress`。
> 然后 `mvn -o -pl qml4j-core test` 确认 481 全绿(基线),再继续 Phase 6。

## 当前位置
- 分支 `phase0/merge-into-core`,HEAD = Phase 5 commit (`33e9138`)
- **Phase 0/1/2 + 3.1–3.7 + 4 + 5 全部完成**,481 测试全绿,full reactor(core + demo)绿
- 真机(LWJGL desktop showcases)已验证至 3.5 渲染无退化;Phase 4(事件/焦点)/5(emit)纯结构,由 FocusScopeTest/KeysTest/QmlViewTest/QmlCompilerTest + 全 QML 编译路径守护
- **下一步:Phase 6**(残余 switch → enum 多态/策略;合理 enum switch 如 line.edge/plan.op/hex.length 保留)

## 已完成 commit(新→旧)
```
33e9138 Phase 5    polymorphic member emit via MemberEmitter strategy map + EmitContext
a24f16e Phase 4.3  extract EventDispatcher; QmlView is now a facade (993->185)
9d7ee91 Phase 4.2  extract FocusManager from QmlView
27fb6eb Phase 4.1  extract Loader from QmlView
(3.7)   Phase 3.7  measure polymorphic: Item.measure(TextLayout) + Text/Button override; paintNode inlined
28e869e Phase 3.6  text input paint polymorphic; paintNode fully empty
5de4295 Phase 3.5  Button + Text paint polymorphic
c1673b9 Phase 3.4d MultiEffect paint via Painter.drawMultiEffect
cb24e82 Phase 3.4c Shape paint via Painter.drawShape
bc5c5c7 Phase 3.4b Image paint via Painter.drawImage
26433bc Phase 3.4a Painter skeleton + Window/Rectangle paint
323ca4e Phase 3.3  extract TextLayout
e08b13f Phase 3.2  extract FontResolver + IconResolver
862d5ba Phase 3.1  runLayout polymorphic (Item.layout)
fbb1315 Phase 2    split RuntimeHelpers into runtime/ + drop 33 dead methods
2f6b86e Phase 1    split items/ into 8 feature subpackages
b80cdc6 Phase 0    merge 4 modules into qml4j-core
```

## 架构现状
- **模块**:`qml4j-core`(合并 parser/engine/compiler/render)+ `qml4j-demo-desktop`(host)。`m0-smoke` 删除;`android-shell` frozen 不动。
- **items/**:按 feature 8 子包 `core/shape/layout/animation/effect/view/input/window`。
- **runtime/**:`member/`(MemberAccess, DelegateScope)、`invoke/`(MethodInvocation, Scheduler)、`convert/`(Coercion)、`qt/`(QColor 值对象, QtColorFactory)。RuntimeHelpers 已删;字节码**从不**调用它(Rhino 迁移后),33 个死方法已删。
- **render/ 协作者**:`FontResolver`(146)、`IconResolver`(111)、`TextLayout`(193)、`Painter`(603,public,封装 skija 原语)。
- **Renderer 1571 → ~600 行**。paint/measure 分派**全部多态化**:所有可绘制 item `@Override Item.paint(Painter,...)`,有内容尺寸的 item(Text/Button)`@Override Item.measure(TextLayout)`。`paintNode` 已内联进 `drawForced`(canvas 参数本就 unused)。Renderer 再无 per-item instanceof 绘制/测量分派。
- **TextLayout 现为 public**(item 子类经 `measure(TextLayout)` 调它,镜像 Painter);`measureText`/`measureButton` public;`measureControl` 已删(Button 测量抽成 `measureButton`,彻底多态)。

## render/ 拆分现状(Phase 4 完成)
- **QmlView 993 → 185 行**,纯 facade,组合三协作者(constructor injection),public API/KEY_*/FocusListener 全保留:
  - `Loader`(编译+实例化:parse→bytecode→compound/singleton 解析+qmldir),注入 `(QmlEngine, TypeRegistry)`,`setResources` setter;`loader::instantiate` 作 renderer 的 ComponentFactory。
  - `FocusManager`(焦点/Tab:setFocus/clearFocus/focused/moveFocusByTab/scanInitialFocus),`setRoot` setter;失焦清选区内联(不依赖 text-edit statics)。
  - `EventDispatcher`(pointer/key/文本编辑/clipboard + 全部 hitTest*),注入 `(FocusManager, Renderer)`,`setRoot`/`setClipboard` setter;`KEY_*` 留 QmlView,内部引用 `QmlView.KEY_*`。

## compiler emit 现状(Phase 5 完成)
- **QmlCompiler.emitMember 的 instanceof 链已消除**:`Map<Class<?>, MemberEmitter> memberEmitters`(keyed on `m.getClass()`)分派 —— PropertyBinding/ChildObject/BehaviorMember/PropertyDeclaration → emit;Signal/Function → reject(保留原 defensive 消息)。
- **EmitContext**(bytecode 包,不可变参数对象)封装 emitObjectBody→emitMember 间线程的 16 个状态;emitObjectBody 每个 object body 构建一次。
- PropertyBinding 内联块抽成 `emitPropertyBinding(m,ctx)`(体逐字、ctx 解构前导桥接);ChildObject/Behavior/PropertyDeclaration 为 thin adapter,解构 ctx 调原深层 helper(**helper 签名未动**,有界风险)。
- **务实偏离**:未拆成 `compiler/emit/` 下独立 Emitter 类(需暴露约 30 个私有 helper,对一次性编译路径不成比例);emitter 用 QmlCompiler 方法引用。emitMember 之外仍有 value/expr 形状的 instanceof(ExpressionValue/StatementBlockValue/LiteralExpr 等)—— 属 Phase 6 范畴或合理保留。

## 剩余工作
- **Phase 6**:残余 switch → enum 多态/策略(合理的 enum switch 如 line.edge/plan.op/hex.length 保留)。
- **Phase 7**:全工程清理 —— 删死代码、inline FQN → import、装 CI 守护(checkstyle/error-prone)。注意 Phase 1/2 注入的 import 紧贴 package 行的小格式瑕疵在此统一整理。

## 关键约定(必守)
- **每步**:`mvn -o install -DskipTests`(编译+demo)EXIT 0 + `mvn -o -pl qml4j-core test` **481 全绿**才 commit。纯结构重构,测试数与行为不变。红条不提交。
- commit footer:`Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`。每个 PR/阶段前先输出 ASCII 目录树蓝图(用户强制)。
- **Painter 模式**:`Item.paint(Painter)` double-dispatch。Painter(public, render 包)封装 skija;**item 子类不 import skija**(唯一例外 `Image.skiaImage` 字段)。复杂/font-heavy 的绘制(Image/Shape/MultiEffect/文字输入控件)整体作为 `Painter.drawXxx(item,...)` 原语,item.paint 委托;简单几何(Window/Rectangle)用低层原语 + 逻辑在 item;Text/Button 用中层文字原语 + 分支逻辑在 item。
- Painter 经 `renderer.fonts()/icons()/textLayout()/paint()/resources()`(package-private accessor)拿协作者。`Renderer.parseColor`(public static)、`applyAlpha`/`sigma`/`drawForced`(package-private)给 Painter 用。
- TextLayout:度量/wrap/elide helper 多为 **static**;`wrapFor`/`topOffset`/`measureText`/`measureControl` 是实例(用 fonts/icons 或 te 缓存)。
- `items → render` 依赖**已存在**(TextEditable 的 `caretIndexAt(...,Renderer)` 回调 + `Item.paint(Painter)`),所以 item 类 import render 包类型 OK。
- 公开 API 不能改签名:`Renderer.parseColor`、`resolveLoader`、`caretIndexFor`/`caretIndexForTextEdit`/`moveCaretVerticalForTextEdit`、`render`/`layoutOnly`/`setResourceLoader`/`setComponentFactory`/`dispose`。
- **机械操作手法**:大批量搬迁用 python 脚本(string-marker cut + replace);跨子包/跨类 import 缺失用「编译器驱动注入」(循环 `mvn compile` → 解析中文「找不到符号 类/变量 X」→ 注入 import);每步顺手删本批 unused import。注意 linter 会自动清 Renderer 的 unused import(其改动是 intentional,别 revert)。

## 真机测试
```bash
mvn -q -pl qml4j-demo-desktop exec:java                          # launcher
mvn -q -pl qml4j-demo-desktop exec:java -Dexec.args=ButtonShowcase
```
退出 exit code 137 是正常的(NVIDIA libEGL teardown SIGSEGV,已 SIGKILL 绕过)。
broken showcases(DefaultProp/M45/QtObject)launcher 已排除。

## 接续 prompt(贴给新 session)
> 继续 qml4j 的 RECODE 架构重构。先读 `RECODE_STATUS.md`(当前进度)、`RECODE_PLAN.md`(总蓝图)、`CLAUDE.md`(房规),recall memory `project_recode_progress`。确认 `mvn -o -pl qml4j-core test` 481 全绿,然后执行 **Phase 3.7**(measure 多态化:Item.measure(TextLayout) + Text/Control/Button override,消除 measure/drawForced 的 instanceof),按既有小步节奏:每步 full reactor 绿 + 独立 commit。
