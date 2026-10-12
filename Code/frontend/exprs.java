/*
 * 表达式子解析器（优先级分层，3b 并入模块路径，3e 并入引用语法）。
 *
 * 做什么：按优先级自低到高递归下降解析算术层（add → mul → unary → atom）；
 *   布尔层（|| && 与比较）由 frontend.logic 负责。独立成类守住
 *   「单文件 < 100 行」上限，也让「逻辑层」与「算术层」职责分明。
 *   3b：atom 识别 `foo::bar(...)` 路径调用与 `foo::Point { .. }` 路径构造
 *   （符号 mangle 为 foo__bar），裸名调用/构造按当前模块 mangle 并做可见性校验。
 *   3e：unary 层识别 `&expr` / `&mut expr` / `*expr` 产出 borrow/deref 节点；
 *   `&mut` 由 parser 层识别 `&` 后紧跟标识符 mut 判定，`*` 与乘法按一元/二元上下文区分。
 * 提供什么功能：
 *   - add()：算术加减层，供 logic.compare 委托调用。
 *   - atom()：字面量 / 标识符 / 调用 / 构造 / 路径 / 括号。
 */
package frontend;

import ast.binop;
import ast.borrow;
import ast.deref;
import ast.expr;
import ast.ident;
import ast.intlit;
import ast.op;
import ast.unop;
import error.rustjerror;
public final class exprs {
    private final cursor cur;
    private final locals syms;
    private final logic parent;
    private final structs st;

    public exprs(cursor cur, locals syms, logic parent, structs st) {
        this.cur = cur;
        this.syms = syms;
        this.parent = parent;
        this.st = st;
    }

    /* 算术加减层：供 logic.compare 委托，包级可见（同包 frontend）。 */
    expr add() {
        expr left = mul();
        while (cur.peek("+") || cur.peek("-")) {
            int kind = cur.take().text.equals("+") ? op.ADD : op.SUB;
            left = new binop(kind, left, mul());
        }
        return left;
    }

    /* 乘法层：二元 `*` 在左操作数之后出现，与一元 deref（unary 内处理）按上下文区分。 */
    private expr mul() {
        expr left = unary();
        while (cur.peek("*") || cur.peek("/") || cur.peek("%")) {
            String o = cur.take().text;
            int kind = o.equals("*") ? op.MUL : o.equals("/") ? op.DIV : op.REM;
            left = new binop(kind, left, unary());
        }
        return left;
    }

    /* 一元层：`-x` / `!x` / `&x`（只读借用）/ `&mut x`（可变借用）/ `*x`（解引用）。 */
    private expr unary() {
        if (cur.peek("-")) { cur.advance(); return new unop(op.NEG, unary()); }
        if (cur.peek("!")) { cur.advance(); return new unop(op.NOT, unary()); }
        if (cur.peek("&")) {
            token a = cur.take();
            boolean mut = false;
            if (cur.isIdent() && cur.peekTok().text.equals("mut")) { cur.advance(); mut = true; }
            return new borrow(mut, unary(), a.line);
        }
        if (cur.peek("*")) { token s = cur.take(); return new deref(unary(), s.line); }
        return atom();
    }

    private expr atom() {
        if (cur.peek("(")) {
            cur.advance();
            expr inner = parent.parse();  // 括号内是完整表达式（含逻辑层）
            cur.expect(")");
            return inner;
        }
        token t = cur.take();
        if (t.type == token.kind.INT) return new intlit(Integer.parseInt(t.text));
        if (t.type == token.kind.IDENT) {
            if (t.text.equals("true")) return new intlit(1, true);
            if (t.text.equals("false")) return new intlit(0, true);
            if (cur.peek("::") && (t.text.equals("std") || (st.stdShort != null && t.text.equals(st.stdShort))))
                return stdlib.build(cur, parent, syms, st, t.text.equals("std") ? "std" : st.stdShort, t.line);
            if (cur.peek("::")) {
                cur.advance();
                token m = cur.next(token.kind.IDENT, "路径名");
                String full = t.text + "__" + m.text;
                st.v.require(full, t.line);
                token p = new token(token.kind.IDENT, full, t.line);
                if (cur.peek("(")) return st.g.instCall(new calls(cur).parseArgs(parent, p), syms);
                if (cur.peek("{")) return new fields(cur, syms, st, parent).lit(p);
                throw new rustjerror(t.line, "路径后应接调用或构造");
            }
            if (cur.peek("(")) return st.g.instCall(new calls(cur).parseArgs(parent, mangleTok(t)), syms);
            if (cur.peek("{")) return new fields(cur, syms, st, parent).lit(mangleTok(t));
            if (cur.peek(".")) return new fields(cur, syms, st, parent).access(t);
            st.g.bareCheck(st.mangle(t.text), t.line);
            return new ident(syms.offsetOf(t.text, t.line), t.line);
        }
        throw new rustjerror(t.line, "期望表达式，实际是 \"" + t.text + "\"");
    }

    /* 裸名按当前模块 mangle 并做可见性校验（同一模块内通过）。 */
    private token mangleTok(token t) {
        String n = st.mangle(t.text);
        st.v.require(n, t.line);
        return new token(token.kind.IDENT, n, t.line);
    }
}
