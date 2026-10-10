---
name: compliant-delivery
description: RustJ 合规交付技能——把改动按「三闸门」合规地走完 提交(commit) → 推送(push) → 发布(release)：提交前过 洁癖/架构/产物/文档/测试 五道红线；推送遵循分支与"不重写历史"纪律；发布采用独立语义版本 `x.y.z`——不兼容（入口参数 / 产物格式）`y+1`，加性 / 修复 `z+1`，Release 标题格式 `x.y.z：<4字简述>`（版本信息必须 4 个汉字），并同步 README + tag + GitHub Release。凡用户要求提交、commit、推送、push、打 tag、发版本、写 release note，或问"这个改动能不能提交/合规吗"时使用——即使用户只说"帮我提交""推一下""发个版本"。
---

# RustJ 合规交付（提交 / 推送 / 发布）

本技能把「提交 → 推送 → 发布」拆成三道互相独立、**逐关放行**的闸门。**上一关未过，禁止进入下一关**；任何"先推了再说"的行为都视为违规。

一句话判据：**红线全过（五道）→ 号对齐（独立版本 `x.y.z`，各按判据 +1）。**

> RustJ 是独立小仓库（单人维护、纯 Java），不设 CLA / BarekHistory / 契约 YAML / 双端 exe 分发——这些是上游 [Cute Of Duty](https://github.com/1person280/Cute-of-Duty) 的机制，不适用。

---

## 第一关：合规提交（commit）

### 1.1 提交前五道红线（缺一不可）

| 红线 | 判据 | 检查方式 |
|---|---|---|
| **洁癖** | 单文件 ≤ 99 行（≤99，含注释）；**一 class 一文件、类与文件名统一小写**；禁循环依赖 | 逐文件核对 + `git diff --stat` |
| **架构** | 依赖方向 `frontend → ast`、`backend → ast` 互不引用；`error` 单向供前后端依赖；AST 保持纯数据；后端面向 `arch` 接口 | 搜 `import` 核对依赖图 |
| **产物** | 碰 COFF / PE 格式或 `main.java` 入口属**不兼容变更**，README 快速开始与版本号必须同步；产物只写 `RustJ/out/`（已 gitignore） | 对照 README〈快速开始〉 |
| **文档** | 碰语言子集 / 分期进度 → 同步 `docs/0004-RustJ二期实现.md`；碰架构分层 → 同步 `docs/0003-RustJ编译器.md`；README 结构表随动 | 逐处核对 |
| **测试** | `javac -d build -sourcepath Code Code\main.java` 退出码 0 + `TestCode/` 全部用例退出码断言通过（min=0 / arith=7 / ops=7 / flow=55）；新特性必配新 `.rs` 用例 | 运行 `RustJ/out/<函数名>.exe` 后查 `ERRORLEVEL` |

Java 硬规范：异常统一走 `error/rustjerror`；标号回填只归 `codebuffer`；禁止为省行数把多个 class 塞进一个文件。

### 1.2 提交信息规范

```
<type>(<scope>): <一句话为什么>
```

- `type` ∈ `feat` / `fix` / `refactor` / `docs` / `test` / `chore` / `perf`。
- `<scope>` 用**包名**（如 `frontend`、`backend`）。
- **一次提交只做一件事**；**重构与功能不得混在同一提交**。
- 正文写"为什么"（Why），不是"改了什么"（What）。

### 1.3 入库 / 不入库

入库前 `git status --short` 逐条确认。**禁止提交**（见 [.gitignore](../../../.gitignore)）：

`/RustJ.jar`、`/RustJ/`（运行产物）、`/build/`、`*.class`。

### 1.4 授权

- AI/工具**不得代填 author**；提交前核对 `git config user.name` / `user.email` 与登记身份一致。
- **不重写历史、不 force-push**。

---

## 第二关：合规推送（push）

- 从 `main` 拉分支，命名语义化：`feat/xxx`、`refactor/xxx`。
- **禁 force-push 到 `main`**；**禁重写公共历史**。
- 推送前先本地跑通全量验证（1.1 测试红线），不要拿 CI 当第一次编译。
- `origin = https://github.com/1person280/RustJ.git`。

---

## 第三关：合规发布（release）

### 3.1 独立版本号 `x.y.z`

RustJ **不绑定游戏版本号**，采用自己的语义版本：

| 段 | 何时 +1 | 触发条件 |
|---|---|---|
| **`x`** | 内核级重写 | 前端 / 后端整体推倒重来，旧 jar 不可用 |
| **`y`** | **不兼容**变更 | 入口参数变化 / COFF / PE 产物格式变化 / 产物目录布局变化 |
| **`z`** | 加性 / 修复 | 语言子集扩张（向后兼容）、bug 修复、文档 |

**热修复版本（hotfix）规约**：某版（如 `0.1.0`）发布后的跟进小版（`0.1.1`、`0.1.2`）一律视为 **`0.1.x` 系热修复版本**——`z` 段顺延 +1、`y` 段不动，向前兼容。热修复版走轻量流程：纯文档 / 工具类改动免全量重跑测试红线，但 README / tag / Release 三处同源对齐照常，不得漂移。

### 3.2 发布三件套（必须一次性对齐）

1. **README**：碰语言子集或分期进度时同步「这是什么」与 `docs/0004` 进度。
2. **打 tag**：`git tag x.y.z`（**无 v 前缀**）+ `git push origin x.y.z`。
3. **GitHub Release**：标题格式 `x.y.z：<4字简述>`（**版本信息必须 4 个汉字**，如 `0.1.0：控制流成`——凑不足 4 字时换措辞，不得多字少字）；正式发布不加 `--prerelease`。

> RustJ 是源码分发（clone + javac），**不打 exe/jar zip 资产**；jar 本身不入库。

### 3.3 发布红线

- 版本号三段判据不得用错；tag 无 v 前缀。
- **版本信息必须 4 个汉字**；README / tag / Release 三处同源，不得漂移。
- 未验证的特性**不得**写成"已完成"。
- 每次发布前全量跑 1.1 测试红线，附退出码凭据。

---

## 快速检查表（TL;DR）

- 提交前：五道红线过了？`git status` 无该入库之外的杂物（jar / build / *.class）？
- 提交信息：`<type>(<包名>): 为什么`，一次一件事。
- 推送：分支语义化，`main` 不 force-push、不重写历史；推送前本地验证全绿。
- 发布：`x.y.z`（`y` = 入口/产物不兼容、`z` = 加性/修复），无 v 前缀；标题 `x.y.z：<4字简述>`（4 个汉字）；README + tag + Release 一次对齐。
- 验证命令：`javac -d build -sourcepath Code Code\main.java` → `jar cfe RustJ.jar main -C build .` → `java -jar RustJ.jar TestCode/<用例>.rs` → 产物 `ERRORLEVEL` 断言。
