// 3f 负例：std::str::len 不在白名单，编译必须失败并带行号（第 2 行，std 路径未实现）。
fn main() -> i32 {
    std::str::len(0)
}
