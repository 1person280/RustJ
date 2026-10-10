/*
 * 结构体构造与字段/方法解析（3a）。
 *
 * 做什么：承接 exprs.atom 下放的三个形态：`Point { ... }` 结构体字面量（逐字段
 *   值表达式，槽位按声明序号对齐）、`p.x` 字段读取（变量基址 + 序号×4 合成单一
 *   disp）、`p.m(...)` 方法调用（self 按值展开为字段参数，与显式实参一道逆序压栈，
 *   被调方第 i 字段位于 [rbp+16+8i]）。
 * 为什么独立成类：exprs 已接近「单文件 < 100 行」上限，与 logic/ctrl 拆分先例一致。
 */
package frontend;

import ast.call;
import ast.expr;
import ast.field;
import ast.ident;
import ast.structlit;
import error.rustjerror;
import java.util.ArrayList;
import java.util.List;

public final class fields {
    private final cursor cur;
    private final locals syms;
    private final structs st;
    private final logic parent;

    public fields(cursor cur, locals syms, structs st, logic parent) {
        this.cur = cur;
        this.syms = syms;
        this.st = st;
        this.parent = parent;
    }

    /* `Point { 字段: 值, ... }`：校验结构体已定义、字段存在且不重复、个数一致。 */
    expr lit(token ty) {
        List<String> fs = st.fieldNames(ty.text, ty.line);
        cur.expect("{");
        List<String> names = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        List<expr> args = new ArrayList<>();
        while (!cur.peek("}")) {
            if (!names.isEmpty()) cur.expect(",");
            token f = cur.next(token.kind.IDENT, "字段名");
            if (names.contains(f.text)) throw new rustjerror(f.line, "字段重复: " + f.text);
            cur.expect(":");
            args.add(parent.parse());
            names.add(f.text);
            indexes.add(st.fieldIndex(ty.text, f.text, f.line));
        }
        cur.expect("}");
        if (names.size() != fs.size())
            throw new rustjerror(ty.line, "结构体 " + ty.text + " 需 " + fs.size() + " 个字段，实际 " + names.size());
        return new structlit(ty.text, names, indexes, args, ty.line);
    }

    /* `p.x` 字段读取，或 `p.m(...)` 方法调用：args=[字段0..字段N-1, 显式实参...]，
       逆序压栈后栈顶即字段0，正对调用约定 [rbp+16+8×字段序号]；方法体内 self 已
       展开为字段参数（8 字节间隔），`self.f` 直接映射到参数变量 f。 */
    expr access(token obj) {
        cur.advance();
        token f = cur.next(token.kind.IDENT, "字段名");
        String type = syms.typeOf(obj.text, obj.line);
        if (!st.has(type)) throw new rustjerror(obj.line, "类型 " + type + " 不支持字段/方法");
        boolean slf = obj.text.equals("self");
        if (cur.peek("(")) {
            List<expr> args = new ArrayList<>();
            for (int i = 0; i < st.fieldCount(type, obj.line); i++)
                args.add(slf ? new ident(syms.offsetOf(st.fieldNames(type, obj.line).get(i), obj.line))
                             : new field(syms.offsetOf(obj.text, obj.line) + i * 4, obj.line));
            cur.advance();
            boolean first = true;
            while (!cur.peek(")")) {
                if (!first) cur.expect(",");
                first = false;
                args.add(parent.parse());
            }
            cur.expect(")");
            return new call(type + "::" + f.text, args, obj.line);
        }
        if (slf) return new ident(syms.offsetOf(f.text, f.line));
        return new field(syms.offsetOf(obj.text, obj.line) + st.fieldIndex(type, f.text, f.line) * 4, obj.line);
    }
}
