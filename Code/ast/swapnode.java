/*
 * std::mem::swap 节点（纯数据，3f）。
 *
 * 做什么：表示 `std::mem::swap(&mut a, &mut b)` 的语义映射产物——两个真实
 *   &mut 借用（复用 3e 借用检查），后端展开为三条指令：
 *   暂存 a → a ← b → b ← 暂存。
 *
 * 提供什么功能：无行为，仅承载两个借用表达式与行号；借用冲突检查由
 *   frontend.borrows 按 borrow 节点复用 3e 规则，机器码由 backend.eval 发射。
 */
package ast;

public final class swapnode extends expr {
    public final borrow a;
    public final borrow b;
    public final int line;

    public swapnode(borrow a, borrow b, int line) {
        this.a = a;
        this.b = b;
        this.line = line;
    }
}
