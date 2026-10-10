/*
 * 结构体符号表与 struct/impl 解析（3a + 3b 模块 mangle）。
 * 做什么：登记顶层 struct 字段布局（字段名按声明序，序号即 ×4 偏移）；与函数共享
 *   名字空间；解析 impl 方法为普通函数——方法名 `类型__方法`（3b 起用 __ 连接，
 *   与模块函数 foo__bar 一致），self 按值展开为字段参数。
 *   3b：符号按当前模块 mangle（入口裸名，模块文件 foo__名），pub 可见性登记在 vis。
 * 提供：parseStruct / parseImpl / fieldNames / fieldIndex / fieldCount / useName / vis。
 */
package frontend;
import ast.block;
import ast.function;
import ast.structdef;
import error.rustjerror;
import java.util.*;
public final class structs {
    public final vis v = new vis();
    private final Map<String, List<String>> fields = new HashMap<>();
    private final Set<String> used = new HashSet<>();
    /* 模块前缀：入口 "" 或系统 rjt_* 符号不加前缀，模块文件 foo__。 */
    public String mangle(String n) { return v.cur.isEmpty() || n.startsWith("rjt_") ? n : v.cur + "__" + n; }
    public void useName(String name, int line) { if (!used.add(name)) throw new rustjerror(line, "名字重复定义: " + name); }
    public boolean has(String name) { return fields.containsKey(name); }
    public structdef parseStruct(cursor cur, boolean pub) {
        cur.expect("struct");
        token nm = cur.next(token.kind.IDENT, "结构体名");
        String ty = mangle(nm.text);
        useName(ty, nm.line);
        v.note(ty, v.cur);
        if (pub) v.pubName(ty);
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
        fields.put(ty, fs);
        return new structdef(ty, fs, nm.line);
    }
    public List<function> parseImpl(cursor cur) {
        cur.expect("impl");
        token ty = cur.next(token.kind.IDENT, "结构体名");
        String t = has(ty.text) ? ty.text : mangle(ty.text);
        if (!has(t)) throw new rustjerror(ty.line, "未定义的结构体: " + ty.text);
        List<function> out = new ArrayList<>();
        boolean pub = v.isPub(t);
        List<String> fs = fields.get(t);
        cur.expect("{");
        while (!cur.peek("}")) parseMethod(cur, t, fs, out, pub);
        cur.expect("}");
        return out;
    }
    /* 方法：self 展开为字段参数，显式 i32 参数跟后；pub 跟随结构体。 */
    private void parseMethod(cursor cur, String type, List<String> fs, List<function> out, boolean pub) {
        cur.expect("fn");
        token m = cur.next(token.kind.IDENT, "方法名");
        String sym = type + "__" + m.text;
        useName(sym, m.line);
        v.note(sym, v.cur);
        if (pub) v.pubName(sym);
        cur.expect("("); cur.expect("&"); cur.expect("self");
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
        cur.expect(")"); cur.next(token.kind.ARROW, "->"); cur.expect("i32");
        block body = new stmts(cur, this).parse(syms);
        out.add(new function(sym, params, body, syms.size() * 4));
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
