---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: 5a49738506e3d97c12121c6a14b20785_87d73af6c5e311f197b3525400248c00
    ReservedCode1: m9j1jPrgD0iW8IklJOFpfdU7NTY4lWHqdX6SSbF45KSFiwEg4LoKIVBH6DIFGo1dCPGZoETu7NJOvi+CaRlSYX5P7a+St1SrtjKqvVwlsyvrhjcMdOrQhXuxOXDhQjRMeuQm3loF7srMDmtnGPbvfb9jWft6eyHzBPwAEAgSVXQwu8YgDQsEnwLbNWM=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: 5a49738506e3d97c12121c6a14b20785_87d73af6c5e311f197b3525400248c00
    ReservedCode2: m9j1jPrgD0iW8IklJOFpfdU7NTY4lWHqdX6SSbF45KSFiwEg4LoKIVBH6DIFGo1dCPGZoETu7NJOvi+CaRlSYX5P7a+St1SrtjKqvVwlsyvrhjcMdOrQhXuxOXDhQjRMeuQm3loF7srMDmtnGPbvfb9jWft6eyHzBPwAEAgSVXQwu8YgDQsEnwLbNWM=
---

# 计划 0007 · RustJ 真实项目追赶（补齐语言缺口以编译 ServerCode 纯逻辑部分）

> **状态：草案** —— 承接 [`0005-RustJ三期计划.md`](0005-RustJ三期计划.md) / [`0006-RustJ三期实现.md`](0006-RustJ三期实现.md)：三期 3a~3f（0.2.1~0.2.6）已验证，3g 缓存指纹与终靶「`ServerCode` 纯逻辑部分编译」仍为计划中；本计划为终靶前置的「语言缺口补齐」专项，**草案**，动手前经 `plan-interrogation` 逐条确认后落地。
> **归属版本：不绑定游戏版本号** —— 同 0005/0006：RustJ 是随仓库分发的独立工具，
> 不触碰线格式 → **不触发 `y+1`**、不打 tag、不写 BarekHistory（发布动作按 compliant-delivery 三闸门执行）。
> **上游设计**：承接 [`0003-RustJ编译器.md`](0003-RustJ编译器.md)〈四、分期路线〉三期终靶；计划与实现现状见 [`0005`](0005-RustJ三期计划.md) / [`0006`](0006-RustJ三期实现.md)。

---

## 零、一句话定义

RustJ（0.2.6）实测无法编译 ServerCode 任何真实文件（`motion.rs` 第 10 行 `use crate::damage::Vec3;` 即被「未定义模块: crate」拦下；全量 58 个源文件扫描显示 enum/match、String、Vec、Option/Result、f32、derive 等特性 20+ 文件高频使用）。
本计划按依赖优先级分四阶段补齐语言缺口，使 RustJ 能编译 `ServerCode` 纯逻辑部分（无 net/tokio/外部 crate 依赖的子集），
以「行为与 `cargo test` 一致、通过其确定性测试」为唯一终靶验收。

---

## 一、范围承接

- **上游**：[`0003-RustJ编译器.md`](0003-RustJ编译器.md)〈四、分期路线〉三期终靶；[`0005-RustJ三期计划.md`](0005-RustJ三期计划.md)（三期计划 3g/终靶仍在其中）；[`0006-RustJ三期实现.md`](0006-RustJ三期实现.md)（三期实现，3a~3f 已验证）。
- **缺口依据**：对 `ServerCode` 58 个源文件的特性扫描 + RustJ 当前能力基线（`i32`/`bool`、`if`/`while`/`for`、`fn`、`struct`+`impl`（self 按值）、`mod`/`use`/`pub` 单层、泛型单态化、trait 静态分发、`&`/`&mut`/`*` 借用基础、std 白名单 4 项 `size_of`/`swap`/`max`/`min`、turbofish）。

---

## 二、已定决策（草案，逐条经 plan-interrogation 确认）

| 决策 | 结论 |
|---|---|
| 实现形态 | 纯 Java、零外部工具链；一 class 一文件、单文件 ≤99 行、类与文件名统一小写；AST 纯数据；依赖方向 `frontend→ast`、`backend→ast`、`error` 单向 |
| 后端 | 面向 `arch` 接口发射；COFF/PE 最小子集与自研链接器不变；产物写 `RustJ/out/`；仅按需增发射节点 |
| 补齐顺序 | 依赖优先级：枚举与模式 → 字符串与集合 → 浮点与方法 → 工程化收口 |
| 验收 | 每阶段新增 `.rs` 用例退出码断言 + 全部既有用例回归（当前 17 例）；负例断言编译失败+带行号 `rustjerror`+退出码 1；改 codegen 后清 `RustJ/RustJ/incremental` 防旧产物误判 |
| 版本号 | 加性/修复 → `z+1`；入口参数/COFF·PE 产物格式/产物目录布局变化 → `y+1` |
| 明确不做 | async/tokio、外部 crate、proc_macro、trait 动态分发/对象安全/关联类型/泛型 trait、跨平台 |

---

## 三、里程碑与执行顺序（草案）

| 步 | 内容 | 产出判据（草案） |
|---|---|---|
| 4a | **枚举与模式**：enum 定义/构造/字段、match（含 `_` 通配、绑定）、if let；Option/Result 最小集（Some/None/Ok/Err + is_some/unwrap 最小方法或 match 用法）；元组结构体 | `TestCode/enum.rs` 退出码断言 + 回归不变 |
| 4b | **字符串与集合**：String/&str 基础（字面量、连接、长度、索引切片最小集）、`format!`（或白名单替代）、Vec 最小集（push/get/len/索引）、数组与切片基础、迭代器最小集（for over Vec/数组） | `TestCode/string.rs`、`TestCode/vec.rs` 退出码断言 + 回归不变 |
| 4c | **浮点与方法**：f32/f64 类型与算术、std 浮点方法白名单（sin/cos/sqrt/abs 等按需）、impl 方法 `&self`/`&mut self` 支持、derive 白名单（Debug/Clone/Copy/PartialEq 编译期内置展开） | `TestCode/float.rs` 退出码断言 + 回归不变 |
| 4d | **工程化收口**：`crate::`/`super::`/`self::` 路径解析、use 任意路径、`pub(crate)` 最小可见性、常量 `const`/`static`、类型别名 `type` | 抽 ServerCode 纯逻辑切片（如 damage/effect.rs 裁剪 net 依赖后）实测编译 + 确定性测试 |
| 终靶 | **`ServerCode` 纯逻辑部分编译** | 行为与 `cargo test` 一致，通过其确定性测试 |

---

## 四、验证

沿用 [`0006`](0006-RustJ三期实现.md) 验证命令模板：

```powershell
javac -d build -sourcepath Code (Get-ChildItem Code -Recurse -Filter *.java).FullName
jar cfe RustJ.jar main -C build .
java -jar RustJ.jar TestCode/<用例>.rs
.\RustJ\out\main.exe; echo "exit=$LASTEXITCODE"
```

- **回归基线 17 例**：正例 14 项退出码断言全绿 —— `min`（0）/ `arith`（7）/ `ops`（7）/ `flow`（55）/ `call`（407）/ `for`（29）/ `bool`（35）/ `mod`（35）/ `struct`（7）/ `sysroot`（44）/ `trait`（42）/ `helper`（0）/ `generic`（93）/ `borrow`（17）/ `std`（62）；反例 3 项编译失败断言 —— `bad_move` / `bad_borrow` / `bad_std`（带行号 `rustjerror`，退出码 1）。
- **每阶段新增用例**：按上表对应 `.rs` 退出码断言。
- **终靶**：`ServerCode` 纯逻辑部分经 RustJ 编译后的行为与 `cargo test` 一致。

---

## 五、明确不做

- ❌ async/await、tokio、线程（net 层不编，终靶范围外）。
- ❌ 外部 crate / Cargo 工程元数据 / proc_macro（0005 延续）。
- ❌ trait 动态分发 / 对象安全 / 关联类型 / 泛型 trait（0005/0006 延续）。
- ❌ 闭包、迭代器全量、宏体系、unsafe、原始指针、Box/Rc/Arc（终靶可后置或裁剪）。
- ❌ 多平台、优化发布构建（定位不变）。

---

## 六、关联

- [`0005-RustJ三期计划.md`](0005-RustJ三期计划.md)（三期计划：3g / 终靶承接）
- [`0006-RustJ三期实现.md`](0006-RustJ三期实现.md)（三期实现：3a~3f 已验证）
- [`0003-RustJ编译器.md`](0003-RustJ编译器.md)（上游定位 / 分期路线）
- [README](../../README.md)〈版本历史〉与〈文档〉索引（随进度同源对齐）
*（内容由AI生成，仅供参考）*
