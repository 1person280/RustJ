/*
 * 结构体符号表与 struct/impl 解析（3a）。
 *
 * 做什么：登记顶层 struct 字段布局（字段名按声明序，序号即 ×4 偏移）；与函数共享
 *   名字空间；解析 impl 方法为普通函数——方法名 `类型::方法` 隔离，self 按值展开
 *   为字段参数（被调方第 i 字段 [rbp+16+8i]）。
 * 提供：parseStruct / parseImpl / fieldNames / fieldIndex / fieldCount / useName。
 */
package frontend;

import ast.block;
import ast.function;
import ast.structdef;
import error.rustjerror;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class structs {
    private final Map<String, List<String>> fields = new HashMap<>();
    private final Map<String, Set<String>> methods = new HashMap<>();
    private final Set<String> used = new HashSet<>();
    public void useName(String name, int line) {
        if (!used.add(name)) throw new rustjerror(line, "名字重复定义: " + name);
    }
    public boolean has(String name) { return fields.containsKey(name); }
    public structdef parseStruct(cursor cur) {
        cur.expect("struct");
        token nm = cur.next(token.kind.IDENT, "结构体名");
        useName(nm.text, nm.line);
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
        fields.put(nm.text, fs);
        methods.put(nm.text, new HashSet<>());
        return new structdef(nm.text, fs, nm.line);
    }
    public List<function> parseImpl(cursor cur) {
        cur.expect("impl");
        token ty = cur.next(token.kind.IDENT, "结构体名");
        if (!has(ty.text)) throw new rustjerror(ty.line, "未定义的结构体: " + ty.text);
        List<function> out = new ArrayList<>();
        List<String> fs = fields.get(ty.text);
        cur.expect("{");
        while (!cur.peek("}")) parseMethod(cur, ty.text, fs, out);
        cur.expect("}");
        return out;
    }
    /* 方法：self 展开为字段参数（参数序号=字段序号），显式 i32 参数跟在字段之后。 */
    private void parseMethod(cursor cur, String type, List<String> fs, List<function> out) {
        cur.expect("fn");
        token m = cur.next(token.kind.IDENT, "方法名");
        useName(type + "::" + m.text, m.line);
        cur.expect("(");
        cur.expect("&");
        cur.expect("self");
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
        cur.expect(")");
        cur.next(token.kind.ARROW, "->");
        cur.expect("i32");
        block body = new stmts(cur, this).parse(syms);
        out.add(new function(type + "::" + m.text, params, body, syms.size() * 4));
        methods.get(type).add(m.text);
    }
    public List<String> fieldNames(String type, int line) {
        List<String> fs = fields.get(type);
        if (fs == null) throw new rustjerror(line, "未定义的结构体: " + type);
        return fs;
    }
    public int fieldIndex(String type, String field, int line) {
        for (int i = 0; i < fieldNames(type, line).size(); i++)
            if (fieldNames(type, line).get(i).equals(field)) return i;
        throw new rustjerror(line, "结构体 " + type + " 无字段: " + field);
    }
    public int fieldCount(String type, int line) { return fieldNames(type, line).size(); }
}
