/*
 * 解引用表达式节点（纯数据，3e）。
 *
 * 做什么：表示 `*r` 解引用：r 必须是通过借用产生的引用（frontend.borrows 校验），
 *   语义检查通过后由 backend.eval 发射为「先取地址到 EAX、再按 EAX 间接读」。
 * 提供什么功能：无行为，仅承载 operand（被解引用的表达式）与 line（解引用行号）。
 */
package ast;

public final class deref extends expr {
    public final expr operand;
    public final int line;

    public deref(expr operand, int line) {
        this.operand = operand;
        this.line = line;
    }
}
