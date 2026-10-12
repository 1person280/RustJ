/*
 * std 精简白名单（3f：编译期内置展开，不碰真实 std.rlib）。
 *
 * 做什么：集中管理「std:: 前缀路径 → 内置操作」的语义映射，仅 4 项：
 *   std::mem::size_of::<T>()   —— i32=4、bool=1、结构体=字段数×4；
 *   std::mem::swap(&mut,&mut)  —— 展开三条指令，借用复用 3e；
 *   std::cmp::max(a,b) / std::cmp::min(a,b)。
 *   语法点：turbofish `::<T>` 为唯一新语法，lexer 不加新 token（:: 与 < 已有）。
 *   路径形态：全限定 std::mem::size_of::<i32>() 免 use；`use std::mem;` 后短名
 *   mem::xxx 同样有效；未实现路径抛「std 路径未实现」带行号。
 *
 * 提供什么功能：build(...)——已消费 `std` 或短名首段后读取 `::名称`（全限定时
 *   递归剥一段），命中白名单产出 ast 节点（intlit / swapnode / stdnode）。
 */
package frontend;

import ast.borrow;
import ast.expr;
import ast.field;
import ast.ident;
import ast.intlit;
import ast.op;
import ast.stdnode;
import ast.swapnode;
import error.rustjerror;
import java.util.ArrayList;
import java.util.List;

public final class stdlib {
    private stdlib() {}

    /* 入口：module 为已消费的首段（"std" 全限定，或 use 短名如 "mem"）。 */
    public static expr build(cursor cur, logic parent, locals syms, structs st, String module, int line) {
        cur.expect("::");
        token name = cur.next(token.kind.IDENT, "std 名称");
        if (module.equals("std")) return build(cur, parent, syms, st, name.text, line);
        String path = "std::" + module + "::" + name.text;
        if (path.equals("std::mem::size_of")) return sizeOf(cur, st, line);
        if (path.equals("std::mem::swap")) return swap(cur, parent, line);
        if (path.equals("std::cmp::max")) return cmp(cur, parent, op.STD_MAX, line);
        if (path.equals("std::cmp::min")) return cmp(cur, parent, op.STD_MIN, line);
        throw new rustjerror(line, "std 路径未实现: " + path);
    }

    /* size_of::<T>()：标量 i32/bool 定值，结构体按字段数×4；未知类型带行号报错。 */
    private static expr sizeOf(cursor cur, structs st, int line) {
        cur.expect("::");
        cur.expect("<");
        token ty = cur.next(token.kind.IDENT, "类型参数");
        cur.expect(">");
        cur.expect("(");
        cur.expect(")");
        if (ty.text.equals("i32")) return new intlit(4);
        if (ty.text.equals("bool")) return new intlit(1);
        String m = st.mangle(ty.text);
        if (st.has(m)) return new intlit(st.fieldCount(m, line) * 4);
        if (st.has(ty.text)) return new intlit(st.fieldCount(ty.text, line) * 4);
        throw new rustjerror(line, "std::mem::size_of 不支持类型: " + ty.text);
    }

    /* swap(&mut a, &mut b)：两个实参必须是 &mut 借用（变量或字段），产出 swapnode。 */
    private static expr swap(cursor cur, logic parent, int line) {
        cur.expect("(");
        borrow a = mutBorrow(parent.parse(), line);
        cur.expect(",");
        borrow b = mutBorrow(parent.parse(), line);
        cur.expect(")");
        return new swapnode(a, b, line);
    }

    /* max/min(a, b)：普通双实参，产出 stdnode，发射期按比较选择。 */
    private static expr cmp(cursor cur, logic parent, int kind, int line) {
        cur.expect("(");
        List<expr> args = new ArrayList<>();
        args.add(parent.parse());
        cur.expect(",");
        args.add(parent.parse());
        cur.expect(")");
        return new stdnode(kind, args, line);
    }

    private static borrow mutBorrow(expr e, int line) {
        if (e instanceof borrow bb && bb.isMut) return bb;
        throw new rustjerror(line, "std::mem::swap 需要两个 &mut 引用参数");
    }
}
