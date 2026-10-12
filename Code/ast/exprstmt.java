/*
 * 表达式语句节点（纯数据，3f）。
 *
 * 做什么：表示仅求值、丢弃结果的表达式语句（如 `std::mem::swap(&mut a, &mut b);`），
 *   使 std 内置操作能以语句形态出现在块内。
 *
 * 提供什么功能：无行为，仅承载表达式 e 与行号 line；发射由 backend.blockgen 分派
 *   （求值后结果不入槽），借用检查由 frontend.borrows 遍历 e。
 */
package ast;

public final class exprstmt extends stmt {
    public final expr e;
    public final int line;

    public exprstmt(expr e, int line) {
        this.e = e;
        this.line = line;
    }
}
