/*
 * 语法分析器（编译单元级入口；3a 并入 struct/impl，3b 并入 mod/use/pub）。
 * 做什么：循环解析顶层项——`pub` 前缀、`mod` 声明（仅入口文件）、`use 模块名;`（仅入口）、
 *   struct / impl / 函数；函数与结构体名按当前模块 mangle（入口裸名，模块文件 foo__名），
 *   pub 项登记可见性表；函数体委托 stmts；完成后对调用点做延迟校验（calls）。
 * 提供：parser(List<token>) 入口默认；parser(List<token>, structs, Set, String) 多文件共享；
 *   多文件时 defer=true 跳过本文件校验，由 frontend.modules 统一延迟校验。
 */
package frontend;
import ast.block; import ast.function; import ast.program; import ast.structdef;
import error.rustjerror;
import java.util.ArrayList; import java.util.HashSet; import java.util.List; import java.util.Set;

public final class parser {
    private final cursor cur;
    private final structs st;
    private final Set<String> mods;
    private final String module;
    private final boolean defer;
    public parser(List<token> tokens) { this(tokens, new structs(), new HashSet<>(), "", false); }
    public parser(List<token> tokens, structs st, Set<String> mods, String module) { this(tokens, st, mods, module, false); }
    public parser(List<token> tokens, structs st, Set<String> mods, String module, boolean defer) {
        this.cur = new cursor(tokens);
        this.st = st;
        this.mods = mods;
        this.module = module;
        this.defer = defer;
        st.v.cur = module;
    }
    public program parse() {
        List<structdef> defs = new ArrayList<>();
        List<function> fns = new ArrayList<>();
        while (cur.isIdent()) {
            boolean pub = false;
            if (cur.peek("pub")) { cur.advance(); pub = true; }
            if (cur.peek("struct")) defs.add(st.parseStruct(cur, pub));
            else if (cur.peek("impl")) {
                if (pub) throw new rustjerror(cur.peekTok().line, "pub 只能修饰 fn/struct");
                fns.addAll(st.parseImpl(cur));
            } else if (cur.peek("mod")) parseMod();
            else if (cur.peek("use")) parseUse();
            else fns.add(parseFn(pub));
        }
        if (!cur.peek("")) throw new rustjerror(cur.take().line, "期望 \"fn/struct/impl\"");
        if (!defer) calls.validate(fns);
        return new program(defs, fns);
    }
    /* `mod foo;`：仅入口；登记模块名（重复报错），文件内容由 frontend.modules 读取。 */
    private void parseMod() {
        if (!module.isEmpty()) throw new rustjerror(cur.peekTok().line, "模块文件内不允许 mod 声明");
        cur.expect("mod");
        token n = cur.next(token.kind.IDENT, "模块名");
        if (!mods.add(n.text)) throw new rustjerror(n.line, "重复的 mod 声明: " + n.text);
        cur.expect(";");
    }
    /* `use foo;`：仅入口；校验模块已声明（use 只引模块名，可省）。 */
    private void parseUse() {
        if (!module.isEmpty()) throw new rustjerror(cur.peekTok().line, "模块文件内不允许 use");
        cur.expect("use");
        token n = cur.next(token.kind.IDENT, "模块名");
        if (!mods.contains(n.text)) throw new rustjerror(n.line, "未声明的模块: " + n.text);
        cur.expect(";");
    }
    /* 函数：mangle 后登记名字空间与归属；pub 登记可见性。 */
    private function parseFn(boolean pub) {
        cur.expect("fn");
        token nm = cur.next(token.kind.IDENT, "函数名");
        String name = st.mangle(nm.text);
        st.useName(name, nm.line);
        st.v.note(name, module);
        if (pub) st.v.pubName(name);
        locals syms = new locals();
        List<String> params = parseParams(syms);
        cur.next(token.kind.ARROW, "->");
        cur.expect("i32");
        block body = new stmts(cur, st).parse(syms);
        return new function(name, params, body, syms.size() * 4);
    }
    /* 参数列表 := '(' (名字: i32 (, 名字: i32)*)? ')'；名字按序登记到 [rbp+12+4i]。 */
    private List<String> parseParams(locals syms) {
        cur.expect("(");
        List<String> params = new ArrayList<>();
        while (!cur.peek(")")) {
            if (!params.isEmpty()) cur.expect(",");
            token p = cur.next(token.kind.IDENT, "参数名");
            syms.declareParam(p.text, params.size(), p.line);
            cur.expect(":");
            cur.expect("i32");
            params.add(p.text);
        }
        cur.expect(")");
        return params;
    }
}
