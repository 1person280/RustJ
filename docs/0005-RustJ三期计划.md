# 计划 0005 · RustJ 三期计划（真实工程可编译）

> **状态：计划中（草案）** —— 本文件规划三期路线与产出判据，具体子期决策在动手前经 `plan-interrogation` 逐条确认后落地。
> **归属版本：不绑定游戏版本号** —— 沿用 [`0003-RustJ编译器.md`](0003-RustJ编译器.md) 定位：RustJ 是随仓库分发的独立工具，
> 不触碰线格式 → **不触发 `y+1`**、不打 tag、不写 BarekHistory（发布动作按 compliant-delivery 三闸门执行）。
> **上游设计**：本计划承接 [`0003`](0003-RustJ编译器.md)〈四、分期路线〉的**三期（终靶）**；二期落地见 [`0004-RustJ二期实现.md`](0004-RustJ二期实现.md)。

---

## 零、一句话定义

把二期已验证的「最小原生链路 + 语言子集」（2a + 2b.1~2b.5 + 2b.5+ 第一步 bool/短路）继续扩展，
使 RustJ 能编译**无宏、无外部 crate 的小 crate 工程**（模块 / 结构体 / trait / 基础泛型 / 借用检查基础），
并以**本仓 `ServerCode` 纯逻辑部分**（无 bevy、无 `proc_macro`）作为**唯一终靶验收**。

---

## 一、范围承接

三期工作 = 二期 2b.5+ 剩余序列（结构体 → 模块 → 泛型/trait）+ 2c sysroot 接入 + 2d 增量缓存：

| 来源 | 内容 |
|---|---|
| 2b.5+ 第二步 | 结构体（struct / 字段 / impl 方法） |
| 2b.5+ 第三步 | 模块系统（多文件、路径、`use`） |
| 2b.5+ 第四步 | 类型系统 / 泛型 / trait |
| 0003 P1 二期后置 | 2c sysroot 接入（core/alloc/std `.rlib` 随 jar 打包解压） |
| 0003 P1 二期后置 | 2d 增量缓存（缓存键 / 依赖指纹 / working·finalized 会话隔离） |

**分期纪律沿用**：每期以「能编出**可运行且行为正确**的真实产物」为准，不用「能解析」冒充「能编译」；终靶未达前不标「已发布」。

---

## 二、里程碑与执行顺序（草案）

| 步 | 内容 | 产出判据（草案） |
|---|---|---|
| 3a | **结构体**：`struct` 定义、字段访问、构造、`impl` 块方法（含方法内 `self`） | 新增 `examples/struct.rs` 退出码断言；全部既有用例回归不变 |
| 3b | **模块系统**：多文件模块、路径解析、`use` 导入、可见性基础 | 新增 `examples/mod.rs` 退出码断言；回归不变 |
| 3c | **泛型基础**：泛型函数 / 泛型结构体，单态化展开 | 新增 `examples/generic.rs` 退出码断言；回归不变 |
| 3d | **trait**：trait 定义与实现、静态分发（动态分发 / 对象安全后置） | 新增 `examples/trait.rs` 退出码断言；回归不变 |
| 3e | **借用检查基础**：所有权 / 借用 / 生命周期基础，未决错误带行号 `rustjerror` | 合法用例可编译、非法用例报带行号错误 |
| 3f | **sysroot 接入（2c）**：core / alloc / std 的 `.rlib` 随 jar 打包、首次运行解压 | 编译引用 `std` 的用例成功并正确执行 |
| 3g | **增量缓存（2d）**：缓存键 + 依赖指纹 + working/finalized 会话隔离 | 二次编译命中缓存、产物一致 |
| 终靶 | **`ServerCode` 纯逻辑部分**编译 + 确定性测试 | 行为与 `cargo test` 一致，通过其确定性测试 |

> 3a~3e 属 0003「二期」判据（模块 / 结构体 / trait / 基础泛型 / 借用检查基础）在 0004 之后的补齐；3f/3g 为 0003 P1 后置项。
> 顺序仅草案：trait 若依赖泛型则后置，方法重载 / 关联类型等均不在 3d 范围。

---

## 三、已定决策（沿用，逐期确认）

| 决策 | 结论 |
|---|---|
| 实现形态 | 纯 Java、零外部工具链；一 class 一文件、单文件 ≤99 行、类与文件名统一小写；AST 纯数据；依赖方向 `frontend → ast`、`backend → ast`、`error` 单向 |
| 后端 | 面向 `arch` 接口发射；COFF / PE 最小子集与自研链接器不变；产物写 `RustJ/out/` |
| sysroot | 预编译 `.rlib` 随 jar 打包、首次运行解压到 `RustJ/sysroot/`（0003 §2.2）；`lld` 需支持读写 `ar` 归档内的 `.o` / `.rmeta` |
| 增量缓存 | 缓存键 + 依赖指纹（SVH 式）判逻辑失效；working/finalized 会话隔离防并发损坏；物理回收交给 ZGC |
| 验收 | 每步新增 `.rs` 用例退出码断言 + 全部既有用例回归；终靶以 `ServerCode` 确定性测试为准 |
| 版本号 | 加性 / 修复 → `z+1`；入口参数 / COFF·PE 产物格式 / 产物目录布局变化 → `y+1` |

---

## 四、验证

```powershell
javac -d build -sourcepath Code (Get-ChildItem Code -Recurse -Filter *.java).FullName
jar cfe RustJ.jar main -C build .
java -jar RustJ.jar Code/examples/<用例>.rs
.\RustJ\out\main.exe; echo "exit=$LASTEXITCODE"
```

- **回归**：`min`（0）/ `arith`（7）/ `ops`（7）/ `flow`（55）/ `call`（407）/ `for`（29）/ `bool`（35）七例退出码断言不变。
- **每步新用例**：按上表对应退出码断言。
- **终靶**：`ServerCode` 纯逻辑部分经 RustJ 编译后的行为与 `cargo test` 一致。

---

## 五、明确不做

- ❌ 不实现 `proc_macro`、不兼容 Cargo 工程元数据（远期 P2，另立计划）。
- ❌ 不做跨平台分发（远期 P3）。
- ❌ 不承诺优化发布构建（定位为 dev 构建 / 本地迭代加速）。
- ❌ 不侵入 `cargo`/`rustc` 环境（`RUSTC_WRAPPER` 远期可选）。
- ❌ 不触碰线格式 / 契约 YAML / 服务端 / net 模块；不改游戏三 crate；不升游戏版本号。
- ❌ trait 暂不做动态分发 / 对象安全 / 关联类型 / 泛型 trait（列 3d 范围外）。

---

## 六、关联

- [`0003-RustJ编译器.md`](0003-RustJ编译器.md)（上游定位 / 分期路线，三期终靶来源）
- [`0004-RustJ二期实现.md`](0004-RustJ二期实现.md)（二期落地：最小原生链路与既有子集）
- [README](../../README.md)〈版本历史〉与〈文档〉索引（随三期进度同源对齐）
