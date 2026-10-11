// 3e 非法用例：可变借用与已有活跃只读借用冲突，编译必须失败。
// 预期 stderr 含「第 6 行」（冲突借用 &mut v 创建处）与「借用冲突」。
fn main() -> i32 {
    let v = 1;
    let r = &v;           // 只读借用
    let s = &mut v;       // 可变借用：与活跃只读借用 r 冲突
    let a = *r;
    let b = *s;
    a + b
}
