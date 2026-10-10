// 3b 模块系统用例：跨模块 pub fn 调用、pub struct 跨模块构造与字段方法、use 短名。
mod helper;
use helper;

fn main() -> i32 {
    let p = helper::Point { x: 3, y: 4 };
    let s = p.sum();
    let d = helper::twice(7);
    let u = helper::twice(s);
    return s + d + u;
}
