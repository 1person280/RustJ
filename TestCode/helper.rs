// 3b 依赖模块：pub struct 与 pub fn 供入口跨模块访问；方法随结构体可见。
pub struct Point { x: i32, y: i32 }

impl Point {
    fn sum(&self) -> i32 {
        return self.x + self.y;
    }
}

pub fn twice(n: i32) -> i32 {
    return n * 2;
}
