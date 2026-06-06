# qml4j — 架构重构计划 (RECODE PLAN)

> 本文档是给**新 session** 执行整个项目重构的提示词 + 蓝图。带着它从零上下文开始即可。
> 先读本文件,再 recall 记忆 `project_rhino_migration` / `project_parked_md3_backlog`,
> 再读 `CLAUDE.md`(房规)。

---

## 0. 你的角色与范式 (用户指令,逐字)

Act as an **Expert Java Software Architect**. Strictly adhere to pure Object-Oriented
Programming (OOP), modern Enterprise Engineering standards, and SOLID principles.

🚫 **Strictly Prohibited Anti-Patterns**
- **No Flat Directory Structures** — never dump all files in one package.
- **No Procedural "Static" Sprawl** — no `public static` for business logic; reserve
  `static` exclusively for pure, stateless, framework-level utilities.
- **No God Classes & Hardcoded Branching** — no unrelated methods in one class; replace
  giant if-else / instanceof / switch with polymorphism.

✅ **Required Standards**
- Standard Maven layout; **Package by Feature (DDD)** or **Package by Layer**.
- Encapsulation: group related state + behaviour into cohesive objects.
- **Constructor-based Dependency Injection** + interfaces for collaboration.

⚠️ **Mandatory output format (Chain of Thought):** BEFORE writing ANY Java code in a
refactor step, output a **Markdown ASCII directory tree** of the (sub)structure you are
about to create/modify, with one-line responsibility comments. Only then write code.

---

## 1. Architect 的务实边界 (本项目的工程现实 — 必须遵守)

这是个**嵌入式 QML 引擎库**(ANTLR→AST→Rhino-JS + ASM-对象树→Skija 渲染),north-star 是
在 **Android** 上跑第三方 MD3 组件,且**每帧**做 measure/layout/paint + Rhino 解释求值。
因此在严格 OOP 之上叠加这些不可违背的约束:

1. **不引入 Spring / 运行时 IoC 容器。** 库不该强加容器给使用者;Spring 在 Android 上重、
   启动有开销;引擎的对象图是确定的。**用手动 constructor injection + interface + factory**
   达成 DI 的解耦目标(依赖倒置、可测试),零容器开销。这是 Android/库领域的标准做法。
   (若将来真要编译期 DI,用 Dagger,不用 Spring 反射容器。)
2. **349 个测试是重构的安全网。** 每一步 `mvn test` 必须全绿才提交。这是纯结构重构,
   **不改行为**;红条不提交。
3. **`static` 的合法用途在本项目真实存在但被滥用了。** 生成的字节码(对象树构建)通过
   `INVOKESTATIC` 调用运行时支持库(`RuntimeHelpers`),这是合理的 runtime ABI(类比 JDK
   的 `LambdaMetafactory`)。**正确做法不是把它实例化**(会破坏生成代码的调用约定),而是
   **把那个 46-方法的上帝 utility 拆成多个单一职责的、纯无状态的 framework 工具**,每个仍
   `static`(符合"reserve static for pure stateless framework utilities")但只做一件事。
4. **热路径性能。** 每帧 render 的 dispatch 从 instanceof 链改成多态/visitor 是好的 OO,
   但注意 megamorphic 调用;优先 double-dispatch(item 自己知道怎么 paint/measure)而非反射。
5. **ANTLR 生成的 `AstBuilder` visitor 保留** —— 框架生成的 visitor 是正当的,不是反模式。
6. **`shared-qml/` 是组件/showcase 单一来源**,重构只动 Java,不动 QML 行为。

> 简言之:**消除上帝类、procedural sprawl、instanceof 分派、扁平包、碎片模块** —— 是;
> **照搬 Spring、为 OO 而牺牲每帧性能或生成代码 ABI** —— 不是。每个偏离严格教条之处都已
> 在上面说明理由。

---

## 2. 现状 (重构起点)

模块(Maven multi-module):
```
qml4j-parser   (3 文件)  ANTLR Qml.g4 + AstBuilder + ast/        → io.qml4j.parser
qml4j-engine   (30)      QObject/Property/Binding/DirtyQueue,
                         RuntimeHelpers, js/(Rhino), classloader/  → io.qml4j.engine
qml4j-compiler (6)       QmlCompiler(ASM), TypeRegistry           → io.qml4j.compiler
qml4j-render   (87)      QmlView, Renderer, StockTypes, items/*    → io.qml4j.render
qml4j-demo-desktop (7)   LWJGL/GLFW host                          → io.qml4j.demo
android-shell  (frozen)  旧 APK,别 build,只作参考
m0-smoke       (ignore)  早期 smoke,可删
```
依赖链: parser ← engine ← compiler ← render ← demo。349 测试全绿。Rhino 已是唯一 JS 后端
(ASM JS codegen 已删,见 `project_rhino_migration`)。

### Self-review — 必须消除的反模式 (含定位)
| # | 反模式 | 位置 | 数据 |
|---|---|---|---|
| A | **God class** + emit 分派 | `compiler/bytecode/QmlCompiler.java` | 2282 行, 57× instanceof |
| B | **God class** + 逐 Item 子类手工分派 | `render/Renderer.java` | 1571 行, 39× instanceof, 5× switch |
| C | **多职责类** (编译/实例化/事件/焦点/剪贴板) | `render/QmlView.java` | 993 行, 22× instanceof |
| D | **Procedural static sprawl** | `engine/RuntimeHelpers.java` | 665 行, **46× public static** |
| E | **扁平包** | `render/items/` | 87 个文件挤一个包 |
| F | **碎片模块** (用户要合并) | qml4j-parser/engine/compiler/render | 4 模块 |
| G | 散落的类型 switch | RuntimeHelpers(3), ImageFill(1), Easings(1) | 部分合理(enum),部分应多态 |
| H | **未引用声明**(死 import/字段/方法/局部变量/参数、死代码) | 全工程 | 全部删除 |
| I | **inline 全限定名**(跳过 import 直接 `java.util.Map x` / `io.github.humbleui...` 内联) | 散落各处(RuntimeHelpers/Renderer 等) | 全部改成顶部 `import` + 短名 |

---

## 3. 目标架构 (蓝图)

### 3a. 模块合并 (用户要求 F)
把 4 个 `qml4j-{parser,engine,compiler,render}` **合并成一个模块 `qml4j-core`**;
`qml4j-demo-desktop` 保留为独立 host 模块(依赖 core)。ANTLR 插件、Rhino/Skija/ASM 依赖
归并到 core 的 pom。`m0-smoke` 删除;`android-shell` 不动(frozen)。

```
qml4j/                                  ← 仍是 parent (聚合 pom)
├── pom.xml                             parent: 版本/依赖管理
├── qml4j-core/                         ← 合并后的单一引擎模块
│   ├── pom.xml                         antlr4 + rhino + skija + asm
│   └── src/main/
│       ├── antlr4/io/qml4j/parser/Qml.g4
│       └── java/io/qml4j/
│           ├── parser/                 解析层: Qml4j(facade), AstBuilder(visitor), ast/
│           │   └── ast/                AST 节点(纯数据/值对象)
│           ├── engine/                 运行时核心
│           │   ├── QObject, Signal, Callable …
│           │   ├── binding/            Property, Binding, DirtyQueue, BindingEvaluationContext
│           │   ├── classloader/        ClassLoaderBackend 及实现
│           │   └── js/                 Rhino 桥接: JsRuntime, QmlScope, JsWrap, QtGlobals, RhinoBinding/Handler/Function
│           ├── runtime/                ← 由 RuntimeHelpers 拆出 (反模式 D)
│           │   ├── member/             MemberAccess(读/写/索引) — 仍 static ABI, 单一职责
│           │   ├── invoke/             MethodInvocation, FunctionDispatch
│           │   ├── convert/            数值/真值/字符串强转 (Coercion)
│           │   └── qt/                 QColor(值对象), QtColorFactory, 数学
│           ├── compiler/               ← 拆 QmlCompiler (反模式 A)
│           │   ├── QmlCompiler(facade), TypeRegistry, CompiledUnit
│           │   └── emit/               每种 member 一个 Emitter(多态分派,见 §4-A)
│           └── render/                 ← 拆 QmlView/Renderer (反模式 B/C)
│               ├── QmlView(facade), Loader, EventDispatcher, FocusManager  (拆 C)
│               ├── Renderer(coordinator), paint/RenderVisitor + per-item painter (拆 B)
│               ├── measure/  layout/   measure/layout 流水线
│               ├── text/  font/  icon/ 文字测量/字体解析/图标解析(从 Renderer 拆出)
│               └── items/              ← package by feature (反模式 E)
│                   ├── core/           Item, Rectangle, Text, MouseArea, Image
│                   ├── shape/          Shape, ShapePath, Path*
│                   ├── layout/         Row/Column/Grid + *Layout + LayoutSizing/Attached
│                   ├── animation/      *Animation, Behavior, Easings, GroupAnimation
│                   ├── effect/         MultiEffect, DropShadow, Glow, ColorOverlay, Layer
│                   ├── view/           Repeater, ListView, GridView, ListModel, Component
│                   ├── input/          TextInput, TextEdit, TextField, Keys, FocusScope
│                   └── window/         Window, ApplicationWindow, Control, Button, Label
└── qml4j-demo-desktop/                 host: DesktopMain, GlfwSurfaceBackend, launcher
```
> 包既"按层"(parser/engine/compiler/render)又在 `items/` 内"按 feature",符合用户标准。

### 3b. 关键多态化 (消除 A/B/C/D 的分派)
- **B — 渲染分派**: 不再 `if (node instanceof Rectangle) paintRectangle(...) else if (Text)...`。
  引入 `RenderVisitor`(visit per item type) **或** 在每个 Item 子类上 `void paint(Painter)`
  / `Size measure(...)`(double-dispatch)。Renderer 退化为遍历 + 协调,把 font/icon/text
  测量拆成注入的 `FontResolver`/`IconResolver`/`TextLayout` 协作者。
- **A — emit 分派**: `emitMember` 的 instanceof 链 → `Map<Class<? extends Ast.Member>, MemberEmitter>`
  或 AST visitor;每种 member(PropertyBinding/ChildObject/Behavior/PropertyDeclaration/Signal)
  一个 `MemberEmitter` 实现,构造器注入共享的 `EmitContext`。
- **C — QmlView 拆分**: `Loader`(compile+instantiate)、`EventDispatcher`(pointer/key)、
  `FocusManager`(focus/tab) 各自成类,`QmlView` 变薄 facade 组合它们(constructor injection)。
- **D — RuntimeHelpers 拆分**: 见 §1.3 — 拆成 `member/`、`invoke/`、`convert/`、`qt/` 下
  的单一职责工具;`QColor` 成真正的值对象(封装通道与运算),不是 string + 散落函数。

---

## 4. 分阶段执行计划 (每阶段独立可提交, 全程 349 绿)

> 原则: **先结构后行为不变**。每个 PR 只做一类重构,`mvn test` 绿才 commit。用
> **IDE 级机械重构**(move class / extract class / introduce interface),最小化手写。

- **Phase 0 — 模块合并 (F)。** 纯 Maven/目录重组,包名不变,零代码逻辑改动。
  把 parser/engine/compiler/render 的 `src` 合进 `qml4j-core`,合并 pom 依赖(antlr4 插件、
  rhino、skija、asm),改 demo-desktop 依赖 core,删 m0-smoke。**先输出合并后的 ASCII 树**,
  跑全量测试绿。这是最大但最机械的一步,先做完它再谈 OO 重构。
- **Phase 1 — items 按 feature 分包 (E)。** 把 87 个 item 类 move 进 §3a 的子包
  (core/shape/layout/animation/effect/view/input/window)。纯 move + 改 import + 改
  `StockTypes` 注册引用。无逻辑改动。
- **Phase 2 — 拆 RuntimeHelpers (D)。** 按职责切成 `runtime/member|invoke|convert|qt`。
  每个方法 move 到对应单一职责类(仍 static ABI)。生成代码/JsWrap 的调用点改引用。
  `QColor` 升级为值对象。逐组切、逐组测绿。
- **Phase 3 — 渲染分派多态化 (B)。** 引入 `RenderVisitor`/per-item `paint/measure`,
  把 Renderer 的 instanceof/switch 链替换成多态;抽出 `FontResolver`/`IconResolver`/
  `TextLayout` 协作者(constructor injection)。这是行为敏感区 —— 小步、每步对照渲染测试。
- **Phase 4 — 拆 QmlView (C)。** 抽 `Loader`/`EventDispatcher`/`FocusManager`,QmlView 变
  facade。事件/焦点测试守护。
- **Phase 5 — emit 分派多态化 (A)。** `MemberEmitter` 策略族替换 `emitMember` instanceof 链;
  QmlCompiler 变协调器。编译器测试(QmlCompilerTest)守护。
- **Phase 6 — 扫尾。** 残余 switch → enum 多态 / 策略;补接口与构造器注入处;
  更新 `CLAUDE.md` 把这些 OOP 规约写成长期房规。
- **Phase 7 — 全工程整洁 (H + I)。** 跨整个 codebase 系统清理:
  - **删除所有未引用声明** — 死 `import`、未用的 `private` 字段/方法、未用的局部变量与参数、
    不可达/死代码。借助编译器(`-Xlint:all`)、IDE inspection,或加 `maven-checkstyle` /
    `error-prone` / `RedundantImport`+`UnusedVariable` 规则作为 CI 守护,避免回潮。
  - **纠正所有 inline 全限定名** — 凡 `java.util.Map m = ...` / `new io.github.humbleui.skija.Paint()`
    这类内联 FQN,改为顶部 `import` + 短名。唯一例外: 两个同名类冲突时 import 其一、另一个
    FQN 并加注释说明(房规已有)。
  - 这两项可在每个 Phase 移动类时**顺手做掉本批文件**,Phase 7 做最终全量扫一遍 + 装上守护
    规则,确保此后不再引入。

> 顺序理由: 机械/低风险在前(0/1/2),行为敏感(3/4/5)在后且有最强测试守护。任一阶段可独立
> 停下而保持绿。

---

## 5. 工作流与约束 (房规, 必须遵守)

- 中文回复用户,代码/commit/PR 英文。**每个 PR 前先输出 ASCII 目录树蓝图**(用户强制要求)。
- import 不写 inline FQN;小类单职责;Edit 前先 Read;grep 用裸 `grep`(别 rg);
  Bash 工具是 bash 不是 fish(`$?` 不是 `$status`)。
- 每步 `mvn -q install -DskipTests` EXIT=0 + `mvn test` **349 全绿**才提交,红条不提交。
  纯结构重构,测试数与行为不应变化(测试若需改 import 是允许的,改断言不允许)。
- commit footer: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`。
- 用 TaskCreate 跟踪各 Phase;Phase 内再细分子任务。
- 不动 `shared-qml/`(组件/showcase 行为)、不动 `android-shell`(frozen)。

## 6. 起步动作 (新 session 第一步)
1. recall `project_rhino_migration`、`project_parked_md3_backlog`;读 `CLAUDE.md`。
2. `mvn test` 确认 349 全绿(基线)。
3. 用 **Plan agent** 把 Phase 0(模块合并)展开成精确的文件移动 + pom 合并清单
   (antlr4 source dir、rhino/skija/asm 依赖、testResource shared-qml 的路径都要迁移)。
4. 输出 Phase 0 的 ASCII 树蓝图给用户确认 → 执行 → 全绿 → commit → 进 Phase 1。

> 重构完成后,`project_parked_md3_backlog` 里的 4 项 backlog(icon ligature / 继续 probe /
> dark theme / 回 Android)恢复执行。
