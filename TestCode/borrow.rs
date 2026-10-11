// 3e 借用检查合法用例：多次只读借用、NLL 释放后复用、标量 Copy、结构体 move。
// 退出码断言 17：a=*r1+*r2=6，b=*r3=3，c=m+m=8，q.x+q.y+c=6+3+8=17。
struct P { x: i32, y: i32 }

fn main() -> i32 {
    let v = 3;
    let r1 = &v;          // 只读借用 1
    let r2 = &v;          // 只读借用 2（与 r1 共存）
    let a = *r1 + *r2;    // 多次只读借用同时读取
    let r3 = &v;          // NLL：r1/r2 末次使用已过，借用已释放，可再借用
    let b = *r3;
    let m = 4;
    let c = m + m;        // 标量 Copy：m 可多次使用不移动
    let p = P { x: a, y: b };
    let q = p;            // 结构体 move：p 失效，但之后不再使用 p
    q.x + q.y + c
}
