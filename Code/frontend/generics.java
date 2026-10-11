/*
 * 泛型符号表与调用点单态化展开（3c）：持有 `fn foo<T>` / `struct Box<T>` 模板，
 *   调用点/构造点按实参类型即时实例化（foo__i32、Box__i32），复制 impl 方法为
 *   Box__i32__get，产物入 instFns 并入最终函数表，后端零感知。提供 addFn/addSt/
 *   addImpl、isGenFn/isGenSt、bareCheck、instCall/instFn/instSt、inferStruct、typeOf、
 *   fieldNames/fieldIndex/fieldType；内部 inferArg/checkInst 负责推断与实例类型校验。
 */
package frontend;
import ast.*; import error.rustjerror; import java.util.*;
public final class generics {
    private static final class tpl {
        final String tp, ret; final List<String> ptypes, params; final block body; final int frame; final boolean pub;
        tpl(String t, String r, List<String> pt, List<String> p, block b, int f, boolean u) { tp=t; ret=r; ptypes=pt; params=p; body=b; frame=f; pub=u; }
    }
    private static final class stpl {
        final String tp; final List<String> fnames, ftypes; final List<function> impls = new ArrayList<>(); final boolean pub;
        stpl(String t, List<String> fn, List<String> ft, boolean u) { tp=t; fnames=fn; ftypes=ft; pub=u; }
    }
    private final Map<String, tpl> fns = new HashMap<>(); private final Map<String, stpl> sts = new HashMap<>();
    private final Map<String, String> instRet = new HashMap<>(); private final Set<String> instSts = new HashSet<>();
    public final List<function> instFns = new ArrayList<>(); public final structs st;
    public generics(structs st) { this.st = st; }
    public boolean isGenFn(String n) { return fns.containsKey(n); }
    public boolean isGenSt(String n) { return sts.containsKey(n); }
    public void addFn(String n, String t, List<String> pt, List<String> p, String r, block b, int f, boolean u) { fns.put(n, new tpl(t, r, pt, p, b, f, u)); }
    public void addSt(String n, String t, List<String> fn, List<String> ft, boolean u) { sts.put(n, new stpl(t, fn, ft, u)); }
    public void addImpl(String n, function m) { sts.get(n).impls.add(m); }
    public String tpOf(String n) { stpl s = sts.get(n); return s == null ? null : s.tp; }
    public List<String> fieldNames(String n, int line) { stpl s = sts.get(n); if (s == null) throw new rustjerror(line, "未定义的泛型结构体: " + n); return s.fnames; }
    public int fieldIndex(String n, String f, int line) { int i = fieldNames(n, line).indexOf(f); if (i < 0) throw new rustjerror(line, "结构体 " + n + " 无字段: " + f); return i; }
    public String fieldType(String n, String f, int line) { return sts.get(n).ftypes.get(fieldIndex(n, f, line)); }
    /* 裸名守卫：泛型模板必须经调用/构造点实例化后才能当普通符号使用，否则报错。 */
    public void bareCheck(String n, int line) { if (isGenSt(n)) throw new rustjerror(line, "泛型结构体未实例化: " + n); if (isGenFn(n)) throw new rustjerror(line, "泛型函数未实例化: " + n); }
    /* 调用点单态化钩子：实参为泛型模板函数（如 id）时，按实参类型推断实例类型并改写为 name__T 调用；非泛型调用原样放行。 */
    public expr instCall(expr e, locals syms) {
        if (!(e instanceof call c)) return e;
        tpl t = fns.get(c.name);
        if (t == null) return e;
        return new call(instFn(c.name, inferArg(t, c.args, syms, c.line), c.line), c.args, c.line);
    }
    /* 推断实例类型：取模板参数列表中第一个 T 位置，用对应实参的类型作为实例类型（typeOf 支持 i32/bool/结构体）。 */
    private String inferArg(tpl t, List<expr> args, locals syms, int line) {
        for (int i = 0; i < t.ptypes.size(); i++) if (t.ptypes.get(i).equals(t.tp)) {
            if (i >= args.size()) throw new rustjerror(line, "无法推断泛型实例类型: " + t.tp);
            return typeOf(args.get(i), syms, line);
        }
        throw new rustjerror(line, "无 T 参数，无法推断泛型实例类型");
    }
    /* 登记函数实例 name__at：首次出现才生成（instRet 去重），产物入 instFns 供最终函数表；返回类型 T→at、其余 i32，供嵌套调用按名反查。 */
    public String instFn(String name, String at, int line) {
        String inst = name + "__" + at;
        if (!instRet.containsKey(inst)) {
            checkInst(at, line);
            tpl t = fns.get(name);
            instFns.add(new function(inst, t.params, t.body, t.frame));
            st.useName(inst, line); st.v.note(inst, st.v.cur);
            if (t.pub) st.v.pubName(inst);
            instRet.put(inst, t.ret.equals(t.tp) ? at : "i32");
        }
        return inst;
    }
    /* 登记结构体实例 name__at：首次出现才注册字段布局并复制 impl 方法为 name__at__方法（去掉原类型前缀再拼实例名）；已实例化直接复用。 */
    public String instSt(String name, String at, int line) {
        String inst = name + "__" + at;
        if (instSts.add(inst)) {
            checkInst(at, line);
            stpl s = sts.get(name);
            st.register(inst, s.fnames);
            st.useName(inst, line); st.v.note(inst, st.v.cur);
            if (s.pub) st.v.pubName(inst);
            for (function m : s.impls) {
                String mn = inst + "__" + m.name.substring(name.length() + 2);
                st.useName(mn, line); st.v.note(mn, st.v.cur);
                if (s.pub) st.v.pubName(mn);
                instFns.add(new function(mn, m.params, m.body, m.frameBytes));
            }
        }
        return inst;
    }
    /* 实例类型校验：仅允许 i32/bool 或已登记结构体；模板（未实例化泛型）与未知类型一律报错。 */
    private void checkInst(String at, int line) {
        if (isGenSt(at)) throw new rustjerror(line, "泛型结构体未实例化: " + at);
        if (!at.equals("i32") && !at.equals("bool") && !st.has(at)) throw new rustjerror(line, "不支持的实例类型: " + at);
    }
    /* 推断结构体构造的实例类型：从第一个 T 字段的对应实参值反推（如 Box { v: 41 } → i32）。 */
    public String inferStruct(String name, List<String> names, List<expr> args, locals syms, int line) {
        stpl s = sts.get(name);
        for (int i = 0; i < s.ftypes.size(); i++) if (s.ftypes.get(i).equals(s.tp)) {
            int j = names.indexOf(s.fnames.get(i));
            if (j < 0) throw new rustjerror(line, "缺少 T 字段无法推断: " + s.fnames.get(i));
            return typeOf(args.get(j), syms, line);
        }
        throw new rustjerror(line, "无 T 字段，无法推断泛型实例类型");
    }
    /* 表达式类型判定：字面量 i32/bool、标识符按栈偏移查登记类型、算术 i32、取非 bool、构造取类型名、调用取已登记实例返回类型。 */
    private String typeOf(expr e, locals syms, int line) {
        if (e instanceof intlit i) return i.isBool ? "bool" : "i32";
        if (e instanceof ident id) return syms.typeOfOffset(id.offset, line);
        if (e instanceof binop) return "i32";
        if (e instanceof unop u) return u.kind == op.NOT ? "bool" : "i32";
        if (e instanceof structlit s) return s.typeName;
        if (e instanceof call c) { String r = instRet.get(c.name); if (r != null) return r; }
        throw new rustjerror(line, "无法推断泛型实例类型");
    }
}
