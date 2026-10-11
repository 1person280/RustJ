fn add1(x: i32) -> i32 { x + 1 }

fn id<T>(x: T) -> T { x }

struct Box<T> { v: T }

impl Box<T> {
    fn get(&self) -> T { self.v }
}

fn main() -> i32 {
    let a = id(3);
    let c = id(7);
    let bx = Box { v: 41 };
    let g = bx.get();
    let f = bx.v;
    let h = add1(c);
    a + g + f + h
}
