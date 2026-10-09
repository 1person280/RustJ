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
| `error/` | 共用编译期错误 | `rustjerror` |

## 版本历史

| 版本 | 日期 | 说明 |
|---|---|---|
| **0.1.0** | 2026-10-07 | **函数调用**：2b.4 多函数 + 函数调用落地——多 `fn` 声明、栈传参、递归与前向引用、未定义函数/实参个数延迟校验；产物文件名固定 `main.o`/`main.exe`；新增 `ast/call`、`frontend/calls`；示例 `call.rs` 退出码断言 407，`min/arith/ops/flow` 回归不变。未做：`for`/`break`/`continue`、`bool`/短路求值、结构体、模块、泛型、sysroot 接入（2c）、增量缓存（2d）。下一版本目标：见 [计划 0004 · RustJ 二期实现](docs/0004-RustJ二期实现.md) 2b.5+ |

## 文档

- [计划 0003 · RustJ 编译器](docs/0003-RustJ编译器.md) —— 上游定位 / 分发 / 分期
- [计划 0004 · RustJ 二期实现](docs/0004-RustJ二期实现.md) —— 最小原生链路落地计划与进度
- [计划 0005 · RustJ 三期计划](docs/0005-RustJ三期计划.md) —— 三期路线（结构体/模块/泛型·trait/sysroot/增量缓存）与产出判据

## 许可

GPLv3，见 [LICENSE](LICENSE)。
