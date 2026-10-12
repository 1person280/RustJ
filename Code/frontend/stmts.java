/*
 * 语句与块解析。
 *
 * 做什么：把「块 := '{' 语句* 尾表达式? '}'」及其中的语句形态（let / 赋值）
 *   从 parser 里独立出来；if / while / for / return / break / continue 等控制流语句
 *   继续下放到 frontend.ctrl，使每个文件都守住「单文件 < 100 行」的上限。
 *
 * 提供什么功能：
 *   - stmts(cursor cur)：绑定游标。
 *   - parse(locals syms)：解析一个块，返回 ast.block（语句列表 + 可选尾表达式）。
 *   - parseBlock(locals syms, int depth)：解析嵌套块；depth 为外层循环嵌套层数，
 *     供控制流语句判断 break/continue 是否处于循环内。
 * 3e：let/赋值节点携带变量名与行号（base），供 frontend.borrows 借用检查报错定位。
 */
package frontend;

import ast.assignstmt;
import ast.block;
import ast.expr;
import ast.exprstmt;
import ast.fieldassignstmt;
import ast.ident;
import ast.letstmt;
import ast.stmt;
import ast.structlit;
import java.util.ArrayList;
import java.util.List;

public final class stmts {
    private final cursor cur;
    private final ctrl ctl;
    private final structs st;

    public stmts(cursor cur, structs st) {
        this.cur = cur;
        this.st = st;
        this.ctl = new ctrl(cur);
    }

    public block parse(locals syms) {
        return parseBlock(syms, 0);
    }

    /* 解析嵌套块；if/while/for 的循环体由 ctrl 通过本方法递归，depth 用于 break/continue 校验。 */
    block parseBlock(locals syms, int depth) {
        cur.expect("{");
        logic ex = new logic(cur, syms, st);
        List<stmt> list = new ArrayList<>();
        while (true) {
            if (cur.peek("let")) list.add(parseLet(syms, ex));
            else if (cur.peek("if") || cur.peek("while") || cur.peek("for")
                    || cur.peek("return") || cur.peek("break") || cur.peek("continue"))
                list.add(ctl.parse(syms, ex, this, depth));
            else if (cur.isIdent() && cur.ahead(1).equals("=")) list.add(parseAssign(syms, ex));
            else if (cur.isIdent() && cur.ahead(1).equals("::")
                    && (cur.peekTok().text.equals("std") || (st.stdShort != null && cur.peekTok().text.equals(st.stdShort))))
                list.add(parseStdStmt(syms, ex));
            else break;
        }
        expr tail = cur.peek("}") ? null : ex.parse();
        if (cur.peek(";")) cur.advance();
        cur.expect("}");
        return new block(list, tail);
    }

    /* std 表达式语句（如 std::mem::swap(&mut a, &mut b);）：解析内置操作表达式，结果丢弃。 */
    private stmt parseStdStmt(locals syms, logic ex) {
        token t = cur.take();
        expr e = stdlib.build(cur, ex, syms, st, t.text.equals("std") ? "std" : st.stdShort, t.line);
        if (cur.peek(";")) cur.advance();
        return new exprstmt(e, t.line);
    }

    private letstmt parseLet(locals syms, logic ex) {
        cur.expect("let");
        token nm = cur.next(token.kind.IDENT, "变量名");
        cur.expect("=");
        expr init = ex.parse();
        if (cur.peek(";")) cur.advance();
        if (init instanceof structlit g)
            return new letstmt(nm.text, syms.declare(nm.text, g.typeName, st.fieldCount(g.typeName, nm.line), nm.line), init, nm.line, st.fieldCount(g.typeName, nm.line));
        if (init instanceof ident iv) {
            String ty = syms.typeOfOffset(iv.offset, iv.line);
            if (!ty.equals("i32"))
                return new letstmt(nm.text, syms.declare(nm.text, ty, st.fieldCount(ty, nm.line), nm.line), init, nm.line, st.fieldCount(ty, nm.line));
        }
        return new letstmt(nm.text, syms.declare(nm.text, nm.line), init, nm.line, 1);
    }

    /* 赋值：普通变量写槽；`p.x = v` 按变量基址 + 字段序号×4 合成 disp 写字段槽。 */
    private stmt parseAssign(locals syms, logic ex) {
        token nm = cur.take();
        int offset = syms.offsetOf(nm.text, nm.line);
        if (cur.peek(".")) {
            cur.advance();
            token f = cur.next(token.kind.IDENT, "字段名");
            int disp = offset + st.fieldIndex(syms.typeOf(nm.text, nm.line), f.text, f.line) * 4;
            cur.expect("=");
            expr value = ex.parse();
            if (cur.peek(";")) cur.advance();
            return new fieldassignstmt(nm.text, offset, disp, value, nm.line);
        }
        cur.expect("=");
        expr value = ex.parse();
        if (cur.peek(";")) cur.advance();
        String t = syms.typeOf(nm.text, nm.line);
        int slots = t.equals("i32") ? 1 : st.fieldCount(t, nm.line);
        return new assignstmt(nm.text, offset, value, nm.line, slots);
    }

}
