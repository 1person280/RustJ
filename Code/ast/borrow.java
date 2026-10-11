/*
 * 借用表达式节点（纯数据，3e）。
 *
 * 做什么：表示 `&expr`（isMut=false）与 `&mut expr`（isMut=true），是借用检查
 *   的最小引用语法载体；operand 为被借用表达式，line 记录借用创建行号供冲突报错。
 * 提供什么功能：无行为，仅承载 isMut/operand/line 三个字段；语义检查由
 *   frontend.borrows 完成，机器码由 backend.eval 发射（lea 取地址）。
 */
package ast;

public final class borrow extends expr {
    public final boolean isMut;
    public final expr operand;
    public final int line;

    public borrow(boolean isMut, expr operand, int line) {
        this.isMut = isMut;
        this.operand = operand;
        this.line = line;
    }
}
