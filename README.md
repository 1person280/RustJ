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
java -jar RustJ.jar Code/examples/min.rs

# 运行产物（写入 RustJ/out/）
RustJ/out/min.exe
echo %ERRORLEVEL%
```

## 代码结构

目录范式 **`Code/<宽泛目的>/<具体实现>`**，子目录即 Java 包；**一 class 一文件、单文件 < 100 行（≤99）、类与文件名统一小写**。

| 包 | 职责 | 内容 |
|---|---|---|
| `ast/` | 纯数据语法树 | `expr` `intlit` `ident` `binop` `unop` `op` `call`；`stmt` `block` `letstmt` `assignstmt` `ifstmt` `whilestmt` `forstmt` `breakstmt` `continuestmt` `returnstmt`；`function` |
| `frontend/` | 词法、语法、符号表、调用校验 | `token` `lexer` `cursor` `parser` `stmts` `exprs` `logic` `calls` `locals` |
| `backend/` | 机器码、目标文件、链接、PE | `arch` `x64` `codebuffer` `codegen` `blockgen` `eval` `logicemit` `coff` `lld` `pe` |
| `cache/` | 增量编译缓存（键/块文件/存取/合并） | `keys` `blockfile` `store` `merge` |
| `error/` | 共用编译期错误 | `rustjerror` |

## 缓存机制

增量编译缓存（2d）以**产物级缓存**为颗粒：一个编译会话的 `main.o` + `main.exe` + 元数据（源文件 SHA-256 / sysroot 归档 SHA-256 / 编译时间）作为一个缓存条目，缓存键 = 源文件 SHA-256 + sysroot 归档 SHA-256；命中时跳过 lexer→link 全流程，直接从缓存还原产物到 `RustJ/out/`。

- **存储形态**：条目写入**缓存块文件**，`-RJCC` 三档（`"64K"` / `"16M"` 默认 / `"4G"`）为单个缓存块文件的体积上限；块写满即另开新块文件，不因超出上限而丢弃缓存。
- **合并策略**：每次编译开始前执行一次缓存合并，将未满块重排归并，收拢碎片空间，避免大量仅占 ~90% 上限的块文件长期占用磁盘。
- **会话隔离**：编译中写 `RustJ/incremental/working/<会话>/`，成功后原子移入 `RustJ/incremental/finalized/`；`-RJCC` 档位缺失或非法时回退默认 `"16M"`。
- **失效与回收**：缓存键含源文件与 sysroot 内容哈希，内容变化即自然失效；未命中则正常编译后写入缓存块；磁盘回收为删除旧会话目录（逻辑失效按缓存键判断，物理回收按块文件清理）。

## 版本历史

| 版本 | 日期 | 说明 |
|---|---|---|
| **0.1.0** | 2026-10-07 | **函数调用**：2b.4 多函数 + 函数调用落地——多 `fn` 声明、栈传参、递归与前向引用、未定义函数/实参个数延迟校验；产物文件名固定 `main.o`/`main.exe`；新增 `ast/call`、`frontend/calls`；示例 `call.rs` 退出码断言 407，`min/arith/ops/flow` 回归不变。未做：`for`/`break`/`continue`、`bool`/短路求值、结构体、模块、泛型、sysroot 接入（2c）、增量缓存（2d）。下一版本目标：见 [计划 0004 · RustJ 二期实现](docs/0004-RustJ二期实现.md) 2b.5+ |
| **0.2.0** | 2026-10-10 | **sysroot 接入 + 增量缓存**：2c 自研最小 runtime 库（`backend/ar` + `backend/rtlib`，纯 Java 生成 `runtime.ar`，隐式全局符号）与真 COFF 重定位（lld 合并符号表 + 拼接 `.text` + 回填 rel32）；2d 产物级增量缓存（`cache/` 包四类 `keys`/`blockfile`/`store`/`merge`，缓存键 = 源 SHA-256 + sysroot 归档 SHA-256，`-RJCC` 三档控制缓存块上限，working/finalized 会话隔离，每次编译前合并）；示例 `sysroot.rs` 退出码断言 44，七例回归不变。COFF 产物格式与目录布局变化，版本号 y+1。下一版本目标：见 [计划 0005 · RustJ 三期计划](docs/0005-RustJ三期计划.md) |

## 文档

- [计划 0003 · RustJ 编译器](docs/0003-RustJ编译器.md) —— 上游定位 / 分发 / 分期
- [计划 0004 · RustJ 二期实现](docs/0004-RustJ二期实现.md) —— 最小原生链路落地计划与进度
- [计划 0005 · RustJ 三期计划](docs/0005-RustJ三期计划.md) —— 三期路线（结构体/模块/泛型·trait/sysroot/增量缓存）与产出判据

## 许可

GPLv3，见 [LICENSE](LICENSE)。
