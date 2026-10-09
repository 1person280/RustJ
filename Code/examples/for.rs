// for/break/continue 验收：
// 1) for i in 0..10：continue 跳过偶数，累加奇数 1+3+5+7+9=25，s>20 时 break；
// 2) while j < 100：j==3 时 continue、j==5 时 break，w 共累加 4 次得 4；
// 期望退出码 25+4=29（覆盖 for/while 各自的 break/continue 与 if 嵌套）。
fn main() -> i32 {
    let s = 0;
    for i in 0..10 {
        if i % 2 == 0 {
            continue;
        }
        s = s + i;
        if s > 20 {
            break;
        }
    }
    let w = 0;
    let j = 0;
    while j < 100 {
        j = j + 1;
        if j == 3 {
            continue;
        }
        w = w + 1;
        if j == 5 {
            break;
        }
    }
    s + w
}
