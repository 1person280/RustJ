/*
 * trait 定义登记与静态分发绑定（3d）。
 * 做什么：登记 `trait 名 { fn 方法([&]self[, 名: i32]*); ... }` 声明（方法名 +
 *   显式参数个数，self 不计）；解析 `impl 名 for 类型 { 方法体 }`——校验 trait 已
 *   定义且当前可见（支持 `模块::名` 路径）、方法覆盖完整、显式参数个数一致、无多余
 *   方法、不重复实现同一 trait；为 obj.method() 调用点提供绑定：固有方法优先，未命中
 *   查 trait 方法，多个 trait 声明同名方法且类型都实现时编译期报「方法名不明确」。
 *   方法符号统一 type__m（与固有方法同形），后端零改动。
 * 为什么独立成类：与 generics/vis 同构的跨阶段符号表，独立后 structs/fields 不膨胀。
 * 提供：parseTrait / parseTraitImpl / bind / has。
 */
package frontend;
import ast.function; import ast.traitdef; import error.rustjerror;
import java.util.ArrayList; import java.util.HashMap; import java.util.HashSet;
import java.util.LinkedHashMap; import java.util.List; import java.util.Map; import java.util.Set;

public final class traits {
    private final structs st;
    /* trait 名(mangle 后) → (方法名 → 显式参数个数)，LinkedHashMap 保声明序。 */
    private final Map<String, Map<String, Integer>> decls = new HashMap<>();
    /* 类型(mangle 后) → 已实现 trait 名集合，防重复实现 + 供绑定统计同名方法来源。 */
    private final Map<String, Set<String>> done = new HashMap<>();

    public traits(structs st) { this.st = st; }
    public boolean has(String name) { return decls.containsKey(name); }

    /* trait 声明：mangle 后登记声明表（不占结构体/函数名字空间），pub 进 vis 供跨模块引用。 */
    public traitdef parseTrait(cursor cur, boolean pub) {
        cur.expect("trait");
        token nm = cur.next(token.kind.IDENT, "trait 名");
        String name = st.mangle(nm.text);
        if (decls.containsKey(name)) throw new rustjerror(nm.line, "重复的 trait 定义: " + name);
        st.v.note(name, st.v.cur);
        if (pub) st.v.pubName(name);
        Map<String, Integer> ms = new LinkedHashMap<>();
        cur.expect("{");
        while (!cur.peek("}")) {
            cur.expect("fn");
            token m = cur.next(token.kind.IDENT, "方法名");
            if (ms.containsKey(m.text)) throw new rustjerror(m.line, "trait 方法重复: " + m.text);
            ms.put(m.text, sigExtra(cur, m.line));
            cur.expect(";");
        }
        cur.expect("}");
        decls.put(name, ms);
        return new traitdef(name, new ArrayList<>(ms.keySet()), nm.line);
    }

    /* 签名显式参数个数：self 必选（& 可选，均按值），显式参数仅 i32。 */
    private int sigExtra(cursor cur, int line) {
        cur.expect("(");
        if (cur.peek("&")) cur.advance();
        cur.expect("self");
        int extra = 0;
        while (!cur.peek(")")) {
            cur.expect(",");
            cur.next(token.kind.IDENT, "参数名");
            cur.expect(":");
            cur.expect("i32");
            extra++;
        }
        cur.expect(")");
        return extra;
    }

    /* trait 实现：trait 名支持裸名或 `模块::名` 路径，可见性由 vis.require 校验；
       右侧结构体类型（泛型模板暂拒）；方法体复用 genparse.parseMethod，解析完成后
       统一校验并登记 done[type]。返回的方法函数并入编译单元函数表。 */
    public List<function> parseTraitImpl(cursor cur, token tr) {
        String tname;
        if (cur.peek("::")) { cur.advance(); token m = cur.next(token.kind.IDENT, "trait 名"); tname = tr.text + "__" + m.text; }
        else tname = st.mangle(tr.text);
        if (!has(tname)) throw new rustjerror(tr.line, "未定义的 trait: " + tr.text);
        st.v.require(tname, tr.line);
        cur.expect("for");
        token ty = cur.next(token.kind.IDENT, "结构体名");
        String type = st.has(ty.text) ? ty.text : st.mangle(ty.text);
        if (!st.has(type) && !st.g.isGenSt(type)) throw new rustjerror(ty.line, "未定义的结构体: " + ty.text);
        if (st.g.isGenSt(type)) throw new rustjerror(ty.line, "暂不支持泛型结构体实现 trait: " + ty.text);
        Set<String> seen = done.get(type);
        if (seen != null && seen.contains(tname)) throw new rustjerror(tr.line, "重复实现 trait: " + tr.text);
        Map<String, Integer> want = decls.get(tname);
        List<function> out = new ArrayList<>();
        Map<String, Boolean> got = new HashMap<>();
        boolean pub = st.v.isPub(type);
        List<String> fs = st.fieldNames(type, ty.line);
        cur.expect("{");
        while (!cur.peek("}")) {
            /* reg=false：trait 方法符号不进顶层名字空间，多 trait 同名方法可共存（冲突在 bind 报不明确）。 */
            function m = genparse.parseMethod(cur, st.g, type, fs, pub, false, false);
            String mn = m.name.substring(type.length() + 2);
            if (got.containsKey(mn)) throw new rustjerror(tr.line, "方法重复: " + mn);
            if (!want.containsKey(mn)) throw new rustjerror(tr.line, "trait " + tr.text + " 未声明方法: " + mn);
            if (m.params.size() - fs.size() != want.get(mn))
                throw new rustjerror(tr.line, "方法 " + mn + " 参数个数与 trait 声明不符");
            got.put(mn, Boolean.TRUE);
            /* 固有方法遮蔽同名 trait 方法（绑定规则固有优先），遮蔽时 trait 方法不可达、不产出符号，避免 fns 中同名符号 arity 冲突。 */
            if (!st.hasOwn(type, mn)) out.add(m);
        }
        cur.expect("}");
        for (String mn : want.keySet())
            if (!got.containsKey(mn)) throw new rustjerror(tr.line, "trait " + tr.text + " 方法未实现: " + mn);
        if (seen == null) { seen = new HashSet<>(); done.put(type, seen); }
        seen.add(tname);
        return out;
    }

    /* 调用绑定：固有方法优先；否则统计该类型实现且声明了 method 的 trait 数——
       0 个交回 type__m 让延迟校验报未定义，1 个取 type__m，多个报「方法名不明确」。 */
    public String bind(String type, String method, int line) {
        if (st.hasOwn(type, method)) return type + "__" + method;
        Set<String> ds = done.get(type);
        if (ds != null) {
            int n = 0;
            for (String tr : ds) if (decls.get(tr).containsKey(method)) n++;
            if (n > 1) throw new rustjerror(line, "方法名不明确: " + method);
        }
        return type + "__" + method;
    }
}
