/*
 * 语法分析器（编译单元级入口，3a 并入 struct/impl）。
 *
 * 做什么：循环解析源文件里的顶层项——struct 定义、impl 方法块、函数
 *   （支持前向引用：调用点先于定义也可），函数体块解析委托 frontend.stmts；
 *   解析完成后对全部调用点做一次延迟校验（frontend.calls）。
 *
 * 提供什么功能：
 *   - parser(List<token> tokens)：绑定 token 流。
 *   - parse()：产出 ast.program（结构体定义列表 + 函数列表，impl 方法并入函数，
 *     方法名以 `类型名::方法名` 隔离）。
 */
package frontend;

import ast.block;
import ast.function;
import ast.program;
import ast.structdef;
import error.rustjerror;
import java.util.ArrayList;
import java.util.List;

public final class parser {
    private final cursor cur;

    public parser(List<token> tokens) {
        this.cur = new cursor(tokens);
    }

    public program parse() {
        structs st = new structs();
        List<structdef> defs = new ArrayList<>();
        List<function> fns = new ArrayList<>();
        while (cur.isIdent()) {
            if (cur.peek("struct")) defs.add(st.parseStruct(cur));
            else if (cur.peek("impl")) fns.addAll(st.parseImpl(cur));
            else fns.add(parseFn(st));
        }
        if (!cur.peek("")) throw new rustjerror(cur.take().line, "期望 \"fn/struct/impl\"");
        calls.validate(fns);
        return new program(defs, fns);
    }

    private function parseFn(structs st) {
        cur.expect("fn");
        token nm = cur.next(token.kind.IDENT, "函数名");
        st.useName(nm.text, nm.line);
        locals syms = new locals();
        List<String> params = parseParams(syms);
        cur.next(token.kind.ARROW, "->");
        cur.expect("i32");
        block body = new stmts(cur, st).parse(syms);
        return new function(nm.text, params, body, syms.size() * 4);
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
