// 3d trait 静态分发用例：trait 声明、impl for、调用点绑定（固有优先→trait）。
// d.talk() 解析为 Dog__talk（返回 20），c.talk() 解析为 Cat__talk（返回 22），
// 求和 42 作为进程退出码。
trait Talk {
    fn talk(self);
}

struct Dog { w: i32 }
struct Cat { m: i32 }

impl Talk for Dog {
    fn talk(self) -> i32 { return 20; }
}
impl Talk for Cat {
    fn talk(self) -> i32 { return 22; }
}

fn main() -> i32 {
    let d = Dog { w: 1 };
    let c = Cat { m: 2 };
    let s = d.talk() + c.talk();
    return s;
}
