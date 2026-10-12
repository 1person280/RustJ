---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: 5a49738506e3d97c12121c6a14b20785_87fe5d67c5de11f197b3525400248c00
    ReservedCode1: 9ASefKc0p2tH/dgzRT7ekph2k36liVpjphMNFmUpha16FSL9cbeYPtrt2WT3jLsQ8mxWkCwhoZrRkfg5+ZTHSLoayNjQEwxI5ArAI0QaAW0UBBXaB24RtemdzhgHkkv1jyUcJSLyEf5agBHmcTUNsWyR9NdcuFd6dsxsLhyTgX8fQbsjv4d9tgzJt2s=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: 5a49738506e3d97c12121c6a14b20785_87fe5d67c5de11f197b3525400248c00
    ReservedCode2: 9ASefKc0p2tH/dgzRT7ekph2k36liVpjphMNFmUpha16FSL9cbeYPtrt2WT3jLsQ8mxWkCwhoZrRkfg5+ZTHSLoayNjQEwxI5ArAI0QaAW0UBBXaB24RtemdzhgHkkv1jyUcJSLyEf5agBHmcTUNsWyR9NdcuFd6dsxsLhyTgX8fQbsjv4d9tgzJt2s=
---

# 计划 0006 · RustJ 三期实现（真实工程可编译）

> **状态：执行中** —— 三期 3a「结构体」已于 0.2.1 落地、3b「模块系统」已于 0.2.2 落地、3c「泛型基础」已于 0.2.3 落地、3d「trait 静态分发」已于 0.2.4 落地、3e「借用检查基础」已于 0.2.5 落地、**3f「std 白名单」已于 0.2.6 落地并验证**（`std.rs` 退出码断言 62，`bad_std.rs` 带行号编译失败断言，17 例回归不变）；**3g「缓存指纹」与终靶「`ServerCode` 纯逻辑部分编译」仍为计划中**（草案，动手前经 `plan-interrogation` 逐条确认后落地）。
> **归属版本：不绑定游戏版本号** —— 同 [`0004-RustJ二期实现.md`](0004-RustJ二期实现.md) / [`0005-RustJ三期计划.md`](0005-RustJ三期计划.md)：RustJ 是随仓库分发的独立工具，
> 不触碰线格式 → **不触发 `y+1`**、不打 tag、不写 BarekHistory（发布动作按 compliant-delivery 三闸门执行）。
> **上游设计**：本文件承接 [`0005-RustJ三期计划.md`](0005-RustJ三期计划.md)（三期计划，3g/终靶仍在其中）；三期终靶来源见 [`0003-RustJ编译器.md`](0003-RustJ编译器.md)〈四、分期路线〉。

---

## 零、一句话定义

把二期已验证的「最小原生链路 + 语言子集」（2a + 2b.1~2b.5 + 2b.5+ 第一步 bool/短路）继续扩展，
三期以 **3a 结构体 → 3b 模块 → 3c 泛型 → 3d trait → 3e 借用检查 → 3f std 白名单** 六步递进，
使 RustJ 能编译**无宏、无外部 crate 的小 crate 工程**（模块 / 结构体 / trait / 基础泛型 / 借用检查基础 / 编译期内置 std 操作），
并以**本仓 `ServerCode` 纯逻辑部分**（无 bevy、无 `proc_macro`）作为**唯一终靶验收**。

---

## 一、已定决策（经 `plan-interrogation` 逐条确认）

| # | 决策 | 结论 |
|---|---|---|
| 1 | 分期纪律 | 每期以「能编出**可运行且行为正确**的真实产物」为准，不用「能解析」冒充「能编译」；终靶未达前不标「已发布」 |
| 2 | 实现形态 | 纯 Java、零外部工具链；一 class 一文件、单文件 ≤99 行、类与文件名统一小写；AST 纯数据 |
| 3 | 依赖方向 | `frontend → ast`、`backend → ast`；`frontend`、`backend` 各自单向依赖 `error`；`main` 依赖全部（无环） |
| 4 | 后端 | 面向 `arch` 接口发射；COFF / PE 最小子集与自研链接器不变；产物写 `RustJ/out/`；三期仅按需在 `arch` 增发射节点 |
| 5 | 语义 pass | 借用检查（3e）为独立 `frontend/borrows` pass（AST 构建后、codegen 前）；std 白名单（3f）为 `frontend/stdlib` 解析层映射 |
| 6 | 验收 | 每步新增 `.rs` 用例退出码断言 + 全部既有用例回归；负例断言编译失败 + 带行号 `rustjerror` + 退出码 1 |
| 7 | 版本号 | 加性 / 修复 → `z+1`；入口参数 / COFF·PE 产物格式 / 产物目录布局变化 → `y+1` |

---

## 二、源码组织与类拆分

组织范式沿用 0004/0005：**`Code/<宽泛目的>/<具体实现>`** —— 子目录即 Java 包，带 `package` 声明；
**一 class 一文件、单文件 < 100 行、类与文件名统一小写**；每个 class 头部注释写明「做什么」与「对外提供什么功能」。
入口 `main.java` 留在 `Code/` 根、属默认包（入口位置固定不变）。

| 包（目录） | 宽泛目的 | 文件（class） |
|---|---|---|
| 根（默认包） | 入口与流水线编排 | `main` |
| `ast/` | 纯数据语法树节点 | 表达式 `expr` `intlit` `ident` `binop` `unop` `op` `call` `borrow` `deref` `traitdef` `stdnode` `swapnode`；语句 `stmt` `block` `letstmt` `assignstmt` `ifstmt` `whilestmt` `forstmt` `breakstmt` `continuestmt` `returnstmt` `exprstmt`；结构体 `structdef` `structlit` `field` `fieldassignstmt` `program`；函数 `function` |
| `frontend/` | 词法、语法、符号表、语义 pass | `token` `lexer` `cursor` `parser` `stmts` `ctrl` `exprs` `logic` `calls` `locals` `fields` `structs` `modules` `vis` `genparse` `generics` `traits` `borrows` `stdlib` |
| `backend/` | 机器码、目标文件、链接、PE | `arch`（发射接口）`x64`（win-x64 实现）`codebuffer` `codegen` `blockgen` `eval` `logicemit` `coff` `lld` `pe` |
| `cache/` | 增量编译缓存 | `keys` `blockfile` `store` `merge` |
| `error/` | 前后端共用的编译期错误 | `rustjerror` |

**依赖方向（无环）**：`frontend → ast`、`backend → ast`；`frontend`、`backend` 各自单向依赖 `error`；`main` 依赖全部。
三期新增均为**纯数据节点**（`ast`）与**前端语义/解析层**（`frontend`）：3a 新增 `ast/structdef`/`structlit`/`field`/`fieldassignstmt`/`program` + `frontend/fields`/`structs`；
3b 新增 `frontend/modules`（多文件协调）+ `parser`/`exprs`/`fields`/`vis` 扩展 mod/use/pub/路径与可见性；
3c 新增 `frontend/genparse`（泛型类型/签名解析）+ `frontend/generics`（单态化展开）；
3d 新增 `ast/traitdef` + `frontend/traits`（定义登记 + impl 校验 + 调用绑定）；
3e 新增 `ast/borrow`（isMut）/`ast/deref` + `frontend/borrows`（独立语义 pass），backend 仅新增 `leaEax`（borrow）/`loadEaxInd`（deref）两发射节点；
3f 新增 `ast/exprstmt`/`ast/stdnode`/`ast/swapnode` + `frontend/stdlib`（std 白名单映射），backend 修复 swap 展开新增 `movEdxEax`（0x89 0xC2）。

---

## 三、里程碑与执行顺序

| 步 | 内容 | 产出判据 | 状态 |
|---|---|---|---|
| 3a | **结构体**：`struct` 定义、字段访问、构造、`impl` 块方法（含方法内 `self`） | `TestCode/struct.rs` 退出码断言 7 | ✅ 已验证（0.2.1） |
| 3b | **模块系统**：多文件模块、路径解析、`use` 导入、可见性基础 | `TestCode/mod.rs`（依赖 `helper.rs`）退出码断言 35 | ✅ 已验证（0.2.2） |
| 3c | **泛型基础**：泛型函数 / 泛型结构体，单态化展开 | `TestCode/generic.rs` 退出码断言 93 | ✅ 已验证（0.2.3） |
| 3d | **trait**：trait 定义与实现、静态分发（动态分发 / 对象安全后置） | `TestCode/trait.rs` 退出码断言 42 | ✅ 已验证（0.2.4） |
| 3e | **借用检查基础**：所有权 / 借用 / 生命周期基础，未决错误带行号 `rustjerror` | `TestCode/borrow.rs` 退出码断言 17，`bad_move.rs`/`bad_borrow.rs` 编译失败断言 | ✅ 已验证（0.2.5） |
| 3f | **std 白名单**：编译期内置展开（不碰真实 `std.rlib`）——`std::mem::size_of`/`swap`、`std::cmp::max`/`min`，turbofish `::<T>` 语法 | `TestCode/std.rs` 退出码断言 62，`bad_std.rs` 带行号编译失败断言 | ✅ 已验证（0.2.6） |
| 3g | **缓存指纹（2d 补强）**：缓存键 + 依赖指纹 + working/finalized 会话隔离 | 二次编译命中缓存、产物一致 | 计划中 |
| 终靶 | **`ServerCode` 纯逻辑部分**编译 + 确定性测试 | 行为与 `cargo test` 一致，通过其确定性测试 | 计划中 |

> 3a~3e 属 0003「二期」判据（模块 / 结构体 / trait / 基础泛型 / 借用检查基础）在 0004 之后的补齐；3f 以「编译期内置 std 白名单」形态落地（原 0005 草案中 3f 为 sysroot 接入 2c 的 `.rlib` 随 jar 打包解压——该形态未实施，白名单展开不依赖真实 `.rlib`）；3g 与终靶承接 0005 计划。

**3a 已定决策（0.2.1）**：结构体顶层定义、连续 4 字节布局、`[基址+字段序号×4]` 寻址、`let` 字面量逐字段写、`impl` 方法 `self` 按值多字段压栈、符号 `Point::method`、`locals` 加类型维度（新增 `ast/structdef`/`structlit`/`field`/`fieldassignstmt`/`program`、`frontend/fields`/`structs`）；修复 PE 入口点错位 bug——`AddressOfEntryPoint` 改为 `.text` 基址 + main 符号偏移（COFF 主符号 value 携带 main 偏移），入口不再固定为段首。验收：`struct.rs` 退出码断言 7，八例回归不变。

**3b 已定决策（0.2.2）**：`mod foo;` 声明 + 同目录 `foo.rs` 单层模块、`use foo;` 引入模块名（全限定 `foo::bar()` 免 use）、`pub fn`/`pub struct` 跨模块可见（无 pub 跨模块访问报带行号 `rustjerror`）、符号 mangle `foo::bar`→`foo__bar`（入口文件函数保持裸名）、结构体类型按 `foo__Bar` mangle、不同模块允许同名函数/结构体（`math__add` 与 `game__add` 并存）、`impl` 方法符号改 `类型::方法`→`类型__方法`；新增 `frontend/modules` 多文件协调：共享符号表、先模块后入口、统一延迟校验；`parser`/`exprs`/`fields`/`vis` 扩展 mod/use/pub/路径与可见性；缓存键改为「入口源 SHA + 依赖文件声明序 SHA + sysroot 归档 SHA」，任一依赖变化整体失效。验收：`mod.rs`（依赖 `helper.rs`，覆盖跨模块 pub fn / pub struct 构造字段 / `use helper;` 短名）退出码断言 35，八例回归不变。

**3c 已定决策（0.2.3）**：泛型函数 / 泛型结构体单态化展开——`fn foo<T>(x: T)` 泛型参数解析与调用点按实参类型实例化（新增 `frontend/genparse` 泛型类型/签名解析、`frontend/generics` 单态化展开：`instCall`/`inferArg`/`instFn`/`instSt`/`checkInst`/`inferStruct`/`typeOf`/`bareCheck`），`locals` 按类型维度分配、`exprs`/`fields`/`parser`/`structs` 扩展泛型类型节点与调用路径，泛型结构体 `struct Box<T> { v: T }` 构造/字段访问单态化，字段字面量 `lit` 查表适配泛型类型。验收：`generic.rs`（`id(3)`+`bx.get()(41)`+`bx.v(41)`+`add1(7)`=93）退出码断言 93，十二例回归不变。

**3d 已定决策（0.2.4）**：trait 定义与实现、静态分发——`trait 名 { fn 签名; }` 仅声明签名、`impl 名 for 类型 { 方法体 }` 提供实现（新增 `ast/traitdef` 纯数据节点、`frontend/traits` 定义登记 + impl 校验 + 调用绑定），方法签名不支持 `&self`（借用属 3e，沿用 `self` 按值展开字段参数），调用点 `obj.method()` 固有方法优先、未命中查类型实现的 trait 方法、多 trait 同名报「方法名不明确」，符号统一 `类型__方法` 后端零改动，`pub trait` 复用 3b 可见性体系。验收：`trait.rs`（`Dog`/`Cat` 各自 `impl Talk for`，`d.talk()+c.talk()`=42）退出码断言 42，十二例回归不变。

**3e 已定决策（0.2.5）**：最小引用语法 `&expr`/`&mut expr`/`*r` 与编译期借用检查——新增 `ast/borrow`（isMut）、`ast/deref` 纯数据节点、`frontend/borrows` 独立语义 pass（AST 构建后、codegen 前）；规则：i32/bool 标量赋值/传参为 Copy 不移动，结构体赋值/传参为 move（再读报 `use-after-move` 带行号）；同一变量同时仅一个可变借用或多个只读借用、混用报「借用冲突」；NLL 简化版——借用存活到最后一次使用，离开块或末次使用后释放；`*r` 要求 r 为引用；词法层新增 `&`/`*` 单字符 token，`&mut` 由 parser 拼装；backend 仅新增 `leaEax`（borrow）/`loadEaxInd`（deref）两节点发射，ABI/栈布局不变。验收：`borrow.rs`（多只读共存/NLL 再借用/标量 Copy/结构体 move）退出码断言 17，`bad_move.rs`/`bad_borrow.rs` 编译失败断言，十三例回归不变。

**3f 已定决策（0.2.6）**：std 精简白名单（编译期内置展开，不碰真实 `std.rlib`）——集中管理「`std::` 前缀路径 → 内置操作」的语义映射，仅 4 项：`std::mem::size_of::<T>()`（i32=4、bool=1、结构体=字段数×4）、`std::mem::swap(&mut,&mut)`（展开三条指令：暂存 a → a←b → b←暂存，借用复用 3e）、`std::cmp::max(a,b)`/`std::cmp::min(a,b)`；语法点：turbofish `::<T>` 为唯一新语法，lexer 不加新 token（`:` 与 `<` 已有）；路径形态：全限定 `std::mem::size_of::<i32>()` 或 `use std::mem;` 后短名 `mem::xxx` 同样有效；未实现路径抛「std 路径未实现」带行号错误。源码组织：新增 `ast/exprstmt`（表达式语句，仅求值、丢弃结果，使 std 内置操作可以语句形态出现在块内）、`ast/stdnode`（max/min 节点，kind 取 `ast.op.STD_MAX`/`STD_MIN`，携带 args 与 line）、`ast/swapnode`（swap 节点，携带两个 &mut 借用与 line）、`frontend/stdlib`（`build(...)` 已消费 `std` 或短名首段后读取 `::名称`，全限定递归剥一段，命中白名单产出 ast 节点：intlit / swapnode / stdnode）。swap 展开崩溃修复：`movEaxEdx()` 编码为 mov eax,edx（REM 取余用），但 swap 需 mov edx,eax 暂存 a 旧值，方向冲突致 EDX 未保存、写回垃圾值；新增 `movEdxEax()`（0x89 0xC2）修复（`arch` 加 `void movEdxEax();`、`x64` 实现 `buf.put(0x89,0xC2)`、`eval.emitSwap` 改 `out.movEdxEax()`）；改 codegen 后须清 `RustJ/RustJ/incremental` 增量缓存再重编（缓存键=源 SHA+sysroot 归档 SHA，不含编译器版本）。验收：`std.rs` 退出码断言 62，`bad_std.rs` 编译失败断言（带行号），swap 展开修复后双向交换用例 t3=33 / t5=22 / t7=11 / t9=11 全对，十七例回归不变。

---

## 四、验证

```powershell
javac -d build -sourcepath Code (Get-ChildItem Code -Recurse -Filter *.java).FullName
jar cfe RustJ.jar main -C build .
java -jar RustJ.jar TestCode/<用例>.rs
.\RustJ\out\main.exe; echo "exit=$LASTEXITCODE"
```

- **回归（17 例）**：正例 14 项退出码断言全绿 —— `min`（0）/ `arith`（7）/ `ops`（7）/ `flow`（55）/ `call`（407）/ `for`（29）/ `bool`（35）/ `mod`（35）/ `struct`（7）/ `sysroot`（44）/ `trait`（42）/ `generic`（93）/ `borrow`（17）/ `std`（62）；`helper.rs` 为 `mod.rs` 依赖文件，`helper` 用例退出码 0 亦通过。反例 3 项编译失败断言 —— `bad_move` / `bad_borrow` / `bad_std` 均以带行号 `rustjerror` 报错并退出码 1。
- **3a**：`struct.rs` 覆盖结构体构造 / 字段访问 / impl 方法（含方法内 `self`），期望 `exit=7`。
- **3b**：`mod.rs`（依赖 `helper.rs`）覆盖跨模块 pub fn / pub struct 构造字段 / `use helper;` 短名，期望 `exit=35`。
- **3c**：`generic.rs` 为 `id(3)+bx.get()(41)+bx.v(41)+add1(7)`，期望 `exit=93`。
- **3d**：`trait.rs` 为 `d.talk()+c.talk()`（`Dog`/`Cat` 各自 `impl Talk for`），期望 `exit=42`。
- **3e**：`borrow.rs` 覆盖多只读共存 / NLL 再借用 / 标量 Copy / 结构体 move，期望 `exit=17`；`bad_move.rs`/`bad_borrow.rs` 断言编译失败 + 带行号。
- **3f**：`std.rs` 覆盖 size_of / swap / max / min 白名单路径（含 turbofish 全限定与 `use` 短名），期望 `exit=62`；`bad_std.rs` 断言编译失败（第 3 行报「std 路径未实现: std::str::len」带行号）+ 退出码 1。swap 展开修复后双向交换用例 t3=33 / t5=22 / t7=11 / t9=11 全对。
- **缓存提醒**：改后端 / 编译器版本后须清 `RustJ/RustJ/incremental` 增量缓存再重编（缓存键不含编译器版本，否则命中旧产物）。

---

## 五、明确不做

- ❌ 3g 缓存指纹与终靶 `ServerCode` 编译未完成（计划中，见 [`0005-RustJ三期计划.md`](0005-RustJ三期计划.md)）。
- ❌ std 白名单仅 4 项（size_of / swap / max / min），不接真实 `core`/`alloc`/`std` 的 `.rlib`；未实现 std 路径报带行号 `rustjerror`。
- ❌ 不实现 `proc_macro`、不兼容 Cargo 工程元数据（远期 P2，另立计划）。
- ❌ trait 暂不做动态分发 / 对象安全 / 关联类型 / 泛型 trait（3d 范围外）。
- ❌ 不做跨平台分发（远期 P3）；不承诺优化发布构建（定位为 dev 构建 / 本地迭代加速）。
- ❌ 不侵入 `cargo`/`rustc` 环境；不触碰线格式 / 契约 YAML / 服务端 / net 模块；不改游戏三 crate；不升游戏版本号。

---

## 六、关联

- [`0005-RustJ三期计划.md`](0005-RustJ三期计划.md)（三期计划：3g / 终靶承接、逐期判据来源）
- [`0004-RustJ二期实现.md`](0004-RustJ二期实现.md)（二期落地：最小原生链路与既有子集）
- [`0003-RustJ编译器.md`](0003-RustJ编译器.md)（上游定位 / 分期路线，三期终靶来源）
- [README](../../README.md)〈版本历史〉与〈文档〉索引（随三期进度同源对齐）
*（内容由AI生成，仅供参考）*
