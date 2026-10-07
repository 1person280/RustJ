// 多函数 + 调用：前向引用（main 调用其后定义的 fib/add）、递归与双参传参。
// fib(14) = 377，add(10, 20) = 30，合计 407，作为退出码断言。
fn main() -> i32 {
    fib(14) + add(10, 20)
}

fn fib(n: i32) -> i32 {
    let r = 0;
    if n < 2 { r = n; } else { r = fib(n - 1) + fib(n - 2); }
    r
}

fn add(a: i32, b: i32) -> i32 {
    a + b
}
