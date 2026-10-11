// 3e 非法用例：结构体 move 后再读，use-after-move，编译必须失败。
// 预期 stderr 含「第 8 行」（move 发生处）与 use-after-move。
struct P { x: i32, y: i32 }

fn main() -> i32 {
    let p = P { x: 1, y: 2 };
    let q = p;            // p move 到 q，p 从此失效
    p.x                  // use-after-move：再读 p
}
