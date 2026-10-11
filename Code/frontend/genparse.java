/*
 * 泛型声明与方法模板的语法扩展（3c）。
 * 做什么：负责 `fn foo<T>(x: T) -> T`、`struct Box<T> { v: T }`、`impl Box<T>` 的
 *   签名侧解析——typeParam 探测 `<T>`，typeMark 校验类型标记（仅 i32 / 当前类型参数），
 *   parseFn 解析泛型函数并登记模板（非泛型回落普通函数），parseStruct 解析泛型结构体
 *   字段并登记模板，parseMethod 解析方法模板（self 展开字段参数，模板不占用符号）。
 * 提供：typeParam / typeMark / parseFn / parseStruct / parseMethod。
 */
package frontend;
import ast.block; import ast.function; import ast.structdef; import error.rustjerror;
import java.util.ArrayList; import java.util.List;
public final class genparse {
    private genparse() {}
    /* `<T>` 探测：有则消费并返回类型参数名（`<T,T>` 等非法形式在 expect 处报错）。 */
    public static String typeParam(cursor cur) {
        if (!cur.peek("<")) return null;
        cur.advance();
        token p = cur.next(token.kind.IDENT, "类型参数");
        cur.expect(">");
        return p.text;
    }
    /* 类型标记：仅允许 i32 或当前类型参数 T；其他类型报错带行号。 */
    public static String typeMark(cursor cur, String tp) {
        token t = cur.next(token.kind.IDENT, "类型");
        if (!t.text.equals("i32") && !t.text.equals(tp))
            throw new rustjerror(t.line, "未知类型: " + t.text);
        return t.text;
    }
    /* 泛型函数声明：解析参数（T/i32）、返回类型与体；含 T 则登记模板并返回 null。 */
    public static function parseFn(cursor cur, generics g, String base, String tp, boolean pub) {
        locals syms = new locals();
        List<String> params = new ArrayList<>();
        List<String> ptypes = new ArrayList<>();
        cur.expect("(");
        while (!cur.peek(")")) {
            if (!params.isEmpty()) cur.expect(",");
            token p = cur.next(token.kind.IDENT, "参数名");
            cur.expect(":");
            String pt = typeMark(cur, tp);
            syms.declareParam(p.text, pt.equals(tp) ? "T" : pt, params.size(), p.line);
            params.add(p.text); ptypes.add(pt);
        }
        cur.expect(")"); cur.next(token.kind.ARROW, "->");
        String rt = typeMark(cur, tp);
        block body = new stmts(cur, g.st).parse(syms);
        if (tp != null && (ptypes.contains(tp) || rt.equals(tp))) {
            g.addFn(base, tp, ptypes, params, rt, body, syms.size() * 4, pub);
            return null;
        }
        return new function(base, params, body, syms.size() * 4);
    }
    /* 泛型结构体声明：登记模板；返回 structdef 供编译单元留存。 */
    public static structdef parseStruct(cursor cur, generics g, String base, int line, String tp, boolean pub) {
        cur.expect("{");
        List<String> fs = new ArrayList<>();
        List<String> fts = new ArrayList<>();
        while (!cur.peek("}")) {
            if (!fs.isEmpty()) cur.expect(",");
            token f = cur.next(token.kind.IDENT, "字段名");
            if (fs.contains(f.text)) throw new rustjerror(f.line, "字段重复: " + f.text);
            cur.expect(":");
            String ft = typeMark(cur, tp);
            fs.add(f.text); fts.add(ft);
        }
        cur.expect("}");
        if (tp != null && fts.contains(tp)) g.addSt(base, tp, fs, fts, pub);
        return new structdef(base, fs, line);
    }
    /* 方法模板：self 展开为字段参数，显式 i32 参数跟后；模板不占符号，pub 跟随结构体。
     * reg=false 用于 trait 实现方法：trait 方法符号不进入顶层名字空间（多 trait 可共存
     * 同名方法，冲突由 traits.bind 在调用点按「方法名不明确」报错），故跳过 useName 登记。 */
    public static function parseMethod(cursor cur, generics g, String type, List<String> fs, boolean pub, boolean templ, boolean reg) {
        cur.expect("fn");
        token m = cur.next(token.kind.IDENT, "方法名");
        String sym = type + "__" + m.text;
        if (!templ && reg) { g.st.useName(sym, m.line); g.st.v.note(sym, g.st.v.cur); if (pub) g.st.v.pubName(sym); }
        cur.expect("("); if (cur.peek("&")) cur.advance(); cur.expect("self");
        locals syms = new locals();
        syms.declareSelf(type);
        List<String> params = new ArrayList<>();
        for (int i = 0; i < fs.size(); i++) {
            syms.declareParam(fs.get(i), type, i, m.line);
            params.add(fs.get(i));
        }
        while (!cur.peek(")")) {
            cur.expect(",");
            token p = cur.next(token.kind.IDENT, "参数名");
            syms.declareParam(p.text, params.size(), p.line);
            cur.expect(":");
            cur.expect("i32");
            params.add(p.text);
        }
        cur.expect(")"); cur.next(token.kind.ARROW, "->");
        String rt;
        if (templ && g.tpOf(type) != null && g.tpOf(type).equals(cur.peekTok().text)) rt = cur.take().text;
        else { cur.expect("i32"); rt = "i32"; }
        block body = new stmts(cur, g.st).parse(syms);
        return new function(sym, params, body, syms.size() * 4);
    }
}
