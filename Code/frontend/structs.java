/*
 * 结构体符号表与 struct/impl 解析（3a + 3b 模块 mangle + 3c 泛型模板）。
 * 做什么：登记顶层 struct 字段布局（字段名按声明序，序号即 ×4 偏移）；与函数共享
 *   名字空间；解析 impl 方法为普通函数——方法名 `类型__方法`（3b 起用 __ 连接，
 *   与模块函数 foo__bar 一致），self 按值展开为字段参数。
 *   3b：符号按当前模块 mangle（入口裸名，模块文件 foo__名），pub 可见性登记在 vis。
 *   3c：`<T>` 模板由 frontend.generics 持有（不占 fields），构造点实例化后经
 *   register 登记为普通结构体；方法模板由 genparse.parseMethod 产出存入 generics。
 * 提供：parseStruct / parseImpl / fieldNames / fieldIndex / fieldCount / useName / register / vis。
 */
package frontend;
import ast.function;
import ast.structdef;
import error.rustjerror;
import java.util.*;
public final class structs {
    public final vis v = new vis();
    public final generics g = new generics(this);
    private final Map<String, List<String>> fields = new HashMap<>();
    private final Set<String> used = new HashSet<>();
    /* 模块前缀：入口 "" 或系统 rjt_* 符号不加前缀，模块文件 foo__。 */
    public String mangle(String n) { return v.cur.isEmpty() || n.startsWith("rjt_") ? n : v.cur + "__" + n; }
    public void useName(String name, int line) { if (!used.add(name)) throw new rustjerror(line, "名字重复定义: " + name); }
    public boolean has(String name) { return fields.containsKey(name); }
    /* 泛型实例化时登记字段布局（构造点已推断实例类型），之后与普通结构体一致。 */
    public void register(String type, List<String> fs) { fields.put(type, fs); }
    /* 结构体声明：mangle 后登记名字空间/可见性；`<T>` 模板委托 genparse.parseStruct 登记到 generics，普通结构体直接登记字段布局。 */
    public structdef parseStruct(cursor cur, boolean pub) {
        cur.expect("struct");
        token nm = cur.next(token.kind.IDENT, "结构体名");
        String ty = mangle(nm.text);
        useName(ty, nm.line);
        v.note(ty, v.cur);
        if (pub) v.pubName(ty);
        String tp = genparse.typeParam(cur);
        if (tp != null) return genparse.parseStruct(cur, g, ty, nm.line, tp, pub);
        cur.expect("{");
        List<String> fs = new ArrayList<>();
        while (!cur.peek("}")) {
            if (!fs.isEmpty()) cur.expect(",");
            token f = cur.next(token.kind.IDENT, "字段名");
            if (fs.contains(f.text)) throw new rustjerror(f.line, "字段重复: " + f.text);
            cur.expect(":");
            cur.expect("i32");
            fs.add(f.text);
        }
        cur.expect("}");
        register(ty, fs);
        return new structdef(ty, fs, nm.line);
    }
    /* impl 解析：按结构体名定位（泛型走 generics 表）；方法统一由 genparse.parseMethod 产出——泛型存模板（addImpl），普通直接进方法表。 */
    public List<function> parseImpl(cursor cur) {
        cur.expect("impl");
        token ty = cur.next(token.kind.IDENT, "结构体名");
        String t = has(ty.text) ? ty.text : mangle(ty.text);
        if (!has(t) && !g.isGenSt(t)) throw new rustjerror(ty.line, "未定义的结构体: " + ty.text);
        List<function> out = new ArrayList<>();
        boolean gen = g.isGenSt(t);
        boolean pub = v.isPub(t);
        List<String> fs = gen ? g.fieldNames(t, ty.line) : fields.get(t);
        if (cur.peek("<")) {
            if (!gen) throw new rustjerror(ty.line, "结构体 " + ty.text + " 非泛型，不能写类型参数");
            cur.advance(); cur.next(token.kind.IDENT, "类型参数"); cur.expect(">");
        }
        cur.expect("{");
        while (!cur.peek("}")) {
            function m = genparse.parseMethod(cur, g, t, fs, pub, gen);
            if (gen) g.addImpl(t, m); else out.add(m);
        }
        cur.expect("}");
        return out;
    }
    public List<String> fieldNames(String type, int line) {
        List<String> fs = fields.get(type);
        if (fs == null) throw new rustjerror(line, "未定义的结构体: " + type);
        return fs;
    }
    public int fieldIndex(String type, String field, int line) {
        List<String> fs = fieldNames(type, line);
        for (int i = 0; i < fs.size(); i++) if (fs.get(i).equals(field)) return i;
        throw new rustjerror(line, "结构体 " + type + " 无字段: " + field);
    }
    public int fieldCount(String type, int line) { return fieldNames(type, line).size(); }
}
