/*
 * 控制流语句解析（if / while / for / return / break / continue）。
 *
 * 做什么：把 stmts 里控制流相关的语句形态独立成类，使 stmts 只保留块骨架与
 *   let/赋值，每个文件都守住「单文件 < 100 行」的上限。
 * 循环语义：for 为半开区间 [lo, hi)、步长 +1；break 退出最近一层循环，
 *   continue 跳过剩余循环体（for 仍执行步进）；两者在循环外使用即报错。
 *
 * 提供什么功能：
 *   - ctrl(cursor cur)：绑定游标。
 *   - parse(locals syms, exprs ex, stmts st, int depth)：解析一条控制流语句；
 *     depth 为当前循环嵌套层数，循环体由 st.parseBlock(syms, depth + 1) 递归。
 */
package frontend;

import ast.block;
import ast.breakstmt;
import ast.continuestmt;
import ast.expr;
import ast.forstmt;
import ast.ifstmt;
import ast.returnstmt;
import ast.stmt;
import ast.whilestmt;
import error.rustjerror;

public final class ctrl {
    private final cursor cur;

    public ctrl(cursor cur) {
        this.cur = cur;
    }

    public stmt parse(locals syms, exprs ex, stmts st, int depth) {
        if (cur.peek("if")) return parseIf(syms, ex, st, depth);
        if (cur.peek("while")) return parseWhile(syms, ex, st, depth);
        if (cur.peek("for")) return parseFor(syms, ex, st, depth);
        if (cur.peek("return")) return parseReturn(ex);
        return parseJump(depth);
    }

    private ifstmt parseIf(locals syms, exprs ex, stmts st, int depth) {
        cur.expect("if");
        expr cond = ex.parse();
        block then = st.parseBlock(syms, depth);
        block els = null;
        if (cur.peek("else")) {
            cur.advance();
            els = st.parseBlock(syms, depth);
        }
        return new ifstmt(cond, then, els);
    }

    private whilestmt parseWhile(locals syms, exprs ex, stmts st, int depth) {
        cur.expect("while");
        expr cond = ex.parse();
        return new whilestmt(cond, st.parseBlock(syms, depth + 1));
    }

    private forstmt parseFor(locals syms, exprs ex, stmts st, int depth) {
        cur.expect("for");
        token nm = cur.next(token.kind.IDENT, "循环变量名");
        cur.expect("in");
        expr lo = ex.parse();
        cur.expect("..");
        expr hi = ex.parse();
        int offset = syms.declare(nm.text, nm.line);
        return new forstmt(offset, lo, hi, st.parseBlock(syms, depth + 1));
    }

    private returnstmt parseReturn(exprs ex) {
        cur.expect("return");
        expr value = cur.peek(";") ? null : ex.parse();
        if (cur.peek(";")) cur.advance();
        return new returnstmt(value);
    }

    /* break/continue：先消费关键字，再校验是否处于循环内（depth>0），循环外报错。 */
    private stmt parseJump(int depth) {
        token t = cur.take();
        if (!t.text.equals("break") && !t.text.equals("continue"))
            throw new rustjerror(t.line, "期望 break/continue");
        if (cur.peek(";")) cur.advance();
        if (depth == 0) throw new rustjerror(t.line, t.text + " 不在循环内");
        return t.text.equals("break") ? new breakstmt() : new continuestmt();
    }
}
