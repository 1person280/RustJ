/*
 * std 比较函数节点（纯数据，3f）。
 *
 * 做什么：表示 `std::cmp::max(a, b)` / `std::cmp::min(a, b)` 的语义映射产物——
 *   kind 取 ast.op.STD_MAX / STD_MIN，携带两个实参；后端按比较结果选择较大/较小值。
 *
 * 提供什么功能：无行为，仅承载 kind / args / line；前端参数即普通表达式，
 *   调用点校验由 frontend.calls 遍历 args，机器码由 backend.eval 发射。
 */
package ast;

import java.util.List;

public final class stdnode extends expr {
    public final int kind;
    public final List<expr> args;
    public final int line;

    public stdnode(int kind, List<expr> args, int line) {
        this.kind = kind;
        this.args = args;
        this.line = line;
    }
}
