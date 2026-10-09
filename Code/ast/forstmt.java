/*
 * for 循环语句（纯数据）。
 *
 * 做什么：表示 `for <变量> in <lo>..<hi> { 循环体 }`，语义为变量从 lo 递增到 hi（不含 hi），
 *   每轮先执行循环体、再步进 +1，条件为 `变量 < hi`。
 * 提供什么功能：字段 offset 为循环变量的栈帧偏移，lo/hi 为上下界表达式，body 为循环体块。
 * 为什么单独成节点而非前端脱糖成 while：continue 必须跳过循环体剩余部分但仍执行步进，
 *   脱糖会让 continue 目标难以表达（会跳过步进导致死循环），显式节点发射语义更直白。
 */
package ast;

public final class forstmt extends stmt {
    public final int offset;
    public final expr lo;
    public final expr hi;
    public final block body;

    public forstmt(int offset, expr lo, expr hi, block body) {
        this.offset = offset;
        this.lo = lo;
        this.hi = hi;
        this.body = body;
    }
}
