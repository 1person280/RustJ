// 3f std 白名单：size_of 标量+结构体、swap 交换、max/min、use std::mem 短名。
// 退出码固定 62：4+1+12+7+5+22+11（size i32 + size bool + size Point + max + min + 交换后 x + 交换后 y）。
use std::mem;

struct Point { x: i32, y: i32, z: i32 }

fn main() -> i32 {
    let a = std::mem::size_of::<i32>();    // 4
    let b = std::mem::size_of::<bool>();   // 1
    let c = std::mem::size_of::<Point>();  // 12
    let m = std::cmp::max(3, 7);           // 7
    let n = std::cmp::min(9, 5);           // 5
    let x = 11;
    let y = 22;
    mem::swap(&mut x, &mut y);           // 短名映射 std::mem::swap，x=22, y=11
    a + b + c + m + n + x + y              // 62
}
