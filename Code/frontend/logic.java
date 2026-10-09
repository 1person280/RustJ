/*
 * 逻辑表达式子解析器（布尔层）。
 *
 * 做什么：负责短路逻辑 `||` / `&&` 与比较层的解析，构成表达式最外层优先级：
 *   `||` 最低，其次 `&&`，再其次比较；算术与一元层在 exprs。
 * 为什么独立成类：exprs 已接近「单文件 < 100 行」上限，布尔层与算术层职责分离
 *   （与 stmts/ctrl 拆分的先例一致），也便于短路语义集中维护。
 */
package frontend;

import ast.binop;
import ast.expr;
import ast.op;

public final class logic {
    private final cursor cur;
    private final exprs ex;

    public logic(cursor cur, locals syms) {
        this.cur = cur;
        this.ex = new exprs(cur, syms, this);
    }

    public expr parse() {
        return or();
    }

    /* ||：短路逻辑或，左非 0 则跳过右（结果 1）。 */
    private expr or() {
        expr left = and();
        while (cur.peek("||")) {
            cur.advance();
            left = new binop(op.OR, left, and());
        }
        return left;
    }

    /* &&：短路逻辑与，左为 0 则跳过右（结果 0）。 */
    private expr and() {
        expr left = compare();
        while (cur.peek("&&")) {
            cur.advance();
            left = new binop(op.AND, left, compare());
        }
        return left;
    }

    /* 比较：委托算术层 add，结果 i32 0/1（cmp + setcc + movzx，见 backend.eval）。 */
    private expr compare() {
        expr left = ex.add();
        while (isCompare()) {
            int kind = compareKind(cur.take().text);
            left = new binop(kind, left, ex.add());
        }
        return left;
    }

    private boolean isCompare() {
        return cur.peek("==") || cur.peek("!=") || cur.peek("<")
                || cur.peek("<=") || cur.peek(">") || cur.peek(">=");
    }

    private int compareKind(String o) {
        if (o.equals("==")) return op.EQ;
        if (o.equals("!=")) return op.NE;
        if (o.equals("<=")) return op.LE;
        if (o.equals(">=")) return op.GE;
        if (o.equals("<")) return op.LT;
        return op.GT;
    }
}
