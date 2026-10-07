/*
 * 语法分析器（编译单元级入口）。
 *
 * 做什么：循环解析源文件里的全部函数（支持前向引用：调用点先于定义也可），
 *   并把函数体块（语句 + 尾表达式）的解析委托给 frontend.stmts；
 *   解析完成后对全部调用点做一次延迟校验（frontend.calls）。
 *
 * 提供什么功能：
 *   - parser(List<token> tokens)：绑定 token 流。
 *   - parse()：产出 ast.function 列表（每个含函数名、参数、函数体块与栈帧字节数）。
 */
package frontend;

import ast.block;
import ast.function;
import error.rustjerror;
import java.util.ArrayList;
import java.util.List;

public final class parser {
    private final cursor cur;

    public parser(List<token> tokens) {
        this.cur = new cursor(tokens);
    }

    public List<function> parse() {
        List<function> fns = new ArrayList<>();
        while (cur.isIdent()) fns.add(parseFn());
        if (!cur.peek("")) throw new rustjerror(cur.take().line, "期望 \"fn\"");
        calls.validate(fns);
        return fns;
    }

    private function parseFn() {
        cur.expect("fn");
        token nm = cur.next(token.kind.IDENT, "函数名");
        locals syms = new locals();
        List<String> params = parseParams(syms);
        cur.next(token.kind.ARROW, "->");
        cur.expect("i32");
        block body = new stmts(cur).parse(syms);
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
