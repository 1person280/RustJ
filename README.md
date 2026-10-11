# RustJ

用 Java 写的、能编译 Rust 的编译器：把 Rust 源码编译成 win-x64 原生可执行文件。**整条工具链纯 Java、零外部依赖**——不依赖 LLVM、clang、rust-lld，链接器为自研 Java 实现（PE/COFF 最小子集，零 import，退出码经 EAX 由 Windows 加载器交付）。

一句话链路：

```
读源码 → 词法 → 语法 → 代码生成（x86-64 机器码发射） → COFF → 自研链接 → PE 可执行文件
```

> 本仓库自 [Cute Of Duty](https://github.com/yxhcf/Cute-of-Duty) 拆出独立开发，上游设计文档见 `docs/`。

## 快速开始

```bash
# 编译（在仓库根目录）
javac -d build $(find Code -name "*.java")   # Windows: 用 dir /s /b Code\*.java

# 打包
jar cfe RustJ.jar main -C build .

# 编译一个 Rust 样例
java -jar RustJ.jar TestCode/min.rs

# 运行产物（写入 RustJ/out/）
RustJ/out/min.exe
echo %ERRORLEVEL%
```

## 代码结构

目录范式 **`Code/<宽泛目的>/<具体实现>`**，子目录即 Java 包；**一 class 一文件、类与文件名统一小写**。

| 包 | 职责 | 内容 |
|---|---|---|
| `ast/` | 纯数据语法树 | `expr` `intlit` `ident` `binop` `unop` `op` `call`；`stmt` `block` `letstmt` `assignstmt` `ifstmt` `whilestmt` `forstmt` `breakstmt` `continuestmt` `returnstmt`；`structdef` `structlit` `field` `fieldassignstmt` `program`；`function` |
| `frontend/` | 词法、语法、符号表、调用校验 | `token` `lexer` `cursor` `parser` `stmts` `exprs` `logic` `calls` `locals` `fields` `structs` |
| `backend/` | 机器码、目标文件、链接、PE | `arch` `x64` `codebuffer` `codegen` `blockgen` `eval` `logicemit` `coff` `lld` `pe` |
| `cache/` | 增量编译缓存（键/块文件/存取/合并） | `keys` `blockfile` `store` `merge` |
| `error/` | 共用编译期错误 | `rustjerror` |

## 代码质量要求

编译器本体（`Code/`）遵循以下判据，**不设单文件行数上限**：

1. **非上帝文件**：每个类单一职责，可读、可拆；文件塞进过多不相关职责时必须按语义拆分或开新子包。
2. **清晰结构**：一 class 一文件、类与文件名统一小写、无环依赖、依赖方向 `frontend → ast` / `backend → ast`。
3. **注释完整**：类头写 Why 型注释（说明该类为什么存在），每个方法写清职责与运行机制，让下一任直接读懂，不靠行数压缩表达。

## 缓存机制

增量编译缓存（2d）以**产物级缓存**为颗粒：一个编译会话的 `main.o` + `main.exe` + 元数据（源文件 SHA-256 / 依赖文件 SHA-256 / sysroot 归档 SHA-256 / 编译时间）作为一个缓存条目，缓存键 = 入口源文件 SHA-256 + 依赖文件按声明序 SHA-256 + sysroot 归档 SHA-256（多文件模块任一依赖变化即整体失效）；命中时跳过 lexer→link 全流程，直接从缓存还原产物到 `RustJ/out/`。

- **存储形态**：条目写入**缓存块文件**，`-RJCC` 三档（`"64K"` / `"16M"` 默认 / `"4G"`）为单个缓存块文件的体积上限；块写满即另开新块文件，不因超出上限而丢弃缓存。
- **合并策略**：每次编译开始前执行一次缓存合并，将未满块重排归并，收拢碎片空间，避免大量仅占 ~90% 上限的块文件长期占用磁盘。
- **会话隔离**：编译中写 `RustJ/incremental/working/<会话>/`，成功后原子移入 `RustJ/incremental/finalized/`；`-RJCC` 档位缺失或非法时回退默认 `"16M"`。
- **失效与回收**：缓存键含源文件与 sysroot 内容哈希，内容变化即自然失效；未命中则正常编译后写入缓存块；磁盘回收为删除旧会话目录（逻辑失效按缓存键判断，物理回收按块文件清理）。

## 版本历史

| 版本 | 日期 | 说明 |
|---|---|---|
| **0.1.0** | 2026-10-07 | **函数调用**：2b.4 多函数 + 函数调用落地——多 `fn` 声明、栈传参、递归与前向引用、未定义函数/实参个数延迟校验；产物文件名固定 `main.o`/`main.exe`；新增 `ast/call`、`frontend/calls`；示例 `call.rs` 退出码断言 407，`min/arith/ops/flow` 回归不变。未做：`for`/`break`/`continue`、`bool`/短路求值、结构体、模块、泛型、sysroot 接入（2c）、增量缓存（2d）。下一版本目标：见 [计划 0004 · RustJ 二期实现](docs/0004-RustJ二期实现.md) 2b.5+ |
| **0.2.0** | 2026-10-10 | **sysroot 接入 + 增量缓存**：2c 自研最小 runtime 库（`backend/ar` + `backend/rtlib`，纯 Java 生成 `runtime.ar`，隐式全局符号）与真 COFF 重定位（lld 合并符号表 + 拼接 `.text` + 回填 rel32）；2d 产物级增量缓存（`cache/` 包四类 `keys`/`blockfile`/`store`/`merge`，缓存键 = 源 SHA-256 + sysroot 归档 SHA-256，`-RJCC` 三档控制缓存块上限，working/finalized 会话隔离，每次编译前合并）；示例 `sysroot.rs` 退出码断言 44，七例回归不变。COFF 产物格式与目录布局变化，版本号 y+1。下一版本目标：见 [计划 0005 · RustJ 三期计划](docs/0005-RustJ三期计划.md) |
| **0.2.1** | 2026-10-10 | **3a 结构体 + 入口定位修复**：结构体顶层定义、连续 4 字节布局、`[基址+字段序号×4]` 寻址、`let` 字面量逐字段写、`impl` 方法 `self` 按值多字段压栈、符号 `Point::method`、`locals` 加类型维度（新增 `ast/structdef`/`structlit`/`field`/`fieldassignstmt`/`program`、`frontend/fields`/`structs`）；修复 PE 入口点错位 bug——`AddressOfEntryPoint` 改为 `.text` 基址 + main 符号偏移（COFF 主符号 value 携带 main 偏移），入口不再固定为段首；示例 `struct.rs` 退出码断言 7，八例回归不变。下一版本目标：见 [计划 0005 · RustJ 三期计划](docs/0005-RustJ三期计划.md) 3b |
| **0.2.2** | 2026-10-10 | **3b 模块系统**：`mod foo;` 声明 + 同目录 `foo.rs` 单层模块、`use foo;` 引入模块名（全限定 `foo::bar()` 免 use）、`pub fn`/`pub struct` 跨模块可见（无 pub 跨模块访问报带行号 `rustjerror`）、符号 mangle `foo::bar`→`foo__bar`（入口文件函数保持裸名）、结构体类型按 `foo__Bar` mangle、不同模块允许同名函数/结构体（`math__add` 与 `game__add` 并存）、`impl` 方法符号改 `类型::方法`→`类型__方法`（新增 `frontend/modules` 多文件协调：共享符号表、先模块后入口、统一延迟校验；`parser`/`exprs`/`fields`/`vis` 扩展 mod/use/pub/路径与可见性）；缓存键改为「入口源 SHA + 依赖文件声明序 SHA + sysroot 归档 SHA」，任一依赖变化整体失效；示例 `mod.rs`（依赖 `helper.rs`，覆盖跨模块 pub fn / pub struct 构造字段 / `use helper;` 短名）退出码断言 35，八例回归不变。下一版本目标：见 [计划 0005 · RustJ 三期计划](docs/0005-RustJ三期计划.md) 3c |
| **0.2.3** | 2026-10-11 | **3c 泛型基础**：泛型函数 / 泛型结构体单态化展开——`fn foo<T>(x: T)` 泛型参数解析与调用点按实参类型实例化（新增 `frontend/genparse` 泛型类型/签名解析、`frontend/generics` 单态化展开：`instCall`/`inferArg`/`instFn`/`instSt`/`checkInst`/`inferStruct`/`typeOf`/`bareCheck`），`locals` 按类型维度分配、`exprs`/`fields`/`parser`/`structs` 扩展泛型类型节点与调用路径，泛型结构体 `struct Box<T> { v: T }` 构造/字段访问单态化，字段字面量 `lit` 查表适配泛型类型；示例 `generic.rs`（`id(3)`+`bx.get()(41)`+`bx.v(41)`+`add1(7)`=93）退出码断言 93，十二例回归不变。下一版本目标：见 [计划 0005 · RustJ 三期计划](docs/0005-RustJ三期计划.md) 3d |
| **0.2.4** | 2026-10-11 | **3d trait 静态分发**：trait 定义与实现、静态分发——`trait 名 { fn 签名; }` 仅声明签名、`impl 名 for 类型 { 方法体 }` 提供实现（新增 `ast/traitdef` 纯数据节点、`frontend/traits` 定义登记 + impl 校验 + 调用绑定），方法签名不支持 `&self`（借用属 3e，沿用 `self` 按值展开字段参数），调用点 `obj.method()` 固有方法优先、未命中查类型实现的 trait 方法、多 trait 同名报「方法名不明确」，符号统一 `类型__方法` 后端零改动，`pub trait` 复用 3b 可见性体系；示例 `trait.rs`（`Dog`/`Cat` 各自 `impl Talk for`，`d.talk()+c.talk()`=42）退出码断言 42，十二例回归不变。下一版本目标：见 [计划 0005 · RustJ 三期计划](docs/0005-RustJ三期计划.md) 3e |
| **0.2.5** | 2026-10-11 | **3e 借用检查基础**：最小引用语法 `&expr`/`&mut expr`/`*r` 与编译期借用检查——新增 `ast/borrow`（isMut）、`ast/deref` 纯数据节点、`frontend/borrows` 独立语义 pass（AST 构建后、codegen 前），规则：i32/bool 标量赋值/传参为 Copy 不移动，结构体赋值/传参为 move（再读报 `use-after-move` 带行号）；同一变量同时仅一个可变借用或多个只读借用、混用报「借用冲突」；NLL 简化版——借用存活到最后一次使用，离开块或末次使用后释放；`*r` 要求 r 为引用；词法层新增 `&`/`*` 单字符 token，`&mut` 由 parser 拼装；backend 仅新增 `leaEax`（borrow）/`loadEaxInd`（deref）两节点发射，ABI/栈布局不变；非法用例 `bad_move.rs`/`bad_borrow.rs` 断言编译失败+带行号错误；示例 `borrow.rs`（多只读共存/NLL 再借用/标量 Copy/结构体 move）退出码断言 17，十三例回归不变。下一版本目标：见 [计划 0005 · RustJ 三期计划](docs/0005-RustJ三期计划.md) 3f |

## 文档

- [计划 0003 · RustJ 编译器](docs/0003-RustJ编译器.md) —— 上游定位 / 分发 / 分期
- [计划 0004 · RustJ 二期实现](docs/0004-RustJ二期实现.md) —— 最小原生链路落地计划与进度
- [计划 0005 · RustJ 三期计划](docs/0005-RustJ三期计划.md) —— 三期路线（结构体/模块/泛型·trait/sysroot/增量缓存）与产出判据

## 许可

GPLv3，见 [LICENSE](LICENSE)。
