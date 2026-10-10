/*
 * 函数调用的解析与延迟校验。
 *
 * 做什么：解析调用点的实参列表 `( 表达式, ... )`；并在全部函数解析完成后，
 *   遍历整棵 AST 校验每个调用点——被调函数必须已定义、实参个数必须与形参一致
 *   （支持前向引用，故校验必须延迟到解析结束后统一进行）。
 *
 * 提供什么功能：
 *   - calls(cursor cur)：绑定游标，供解析实参列表。
 *   - parseArgs(logic ex, token name)：解析实参并产出 ast.call 节点。
 *   - validate(List<function> fns)：全 AST 调用点校验，违规以 rustjerror 带行号报错。
 */
package frontend;

import ast.assignstmt;
import ast.block;
import ast.call;
import ast.expr;
import ast.forstmt;
import ast.function;
import ast.ifstmt;
import ast.letstmt;
import ast.returnstmt;
import ast.stmt;
import ast.whilestmt;
import error.rustjerror;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class calls {
    private final cursor cur;

    public calls(cursor cur) {
        this.cur = cur;
    }

    /* 实参列表：调用点处 IDENT 已被上游取出，这里从 '(' 解析到 ')'。 */
    public expr parseArgs(logic ex, token name) {
        cur.expect("(");
        List<expr> args = new ArrayList<>();
        while (!cur.peek(")")) {
            if (!args.isEmpty()) cur.expect(",");
            args.add(ex.parse());
        }
        cur.expect(")");
        return new call(name.text, args, name.line);
    }

    /* 延迟校验：建「函数名 → 形参个数」表，再遍历所有函数体检查每个调用点。 */
    public static void validate(List<function> fns) {
        Map<String, Integer> arity = new HashMap<>();
        /* 2c sysroot：预置自研 runtime 库符号的隐式 arity（用户定义同名函数可覆盖）。 */
        arity.put("rjt_double", 1);
        arity.put("rjt_add", 2);
        arity.put("rjt_mul", 2);
        for (function f : fns) arity.put(f.name, f.params.size());
        for (function f : fns) checkBlock(f.body, arity);
    }

    private static void checkBlock(block b, Map<String, Integer> arity) {
        for (stmt s : b.stmts) checkStmt(s, arity);
        if (b.tail != null) checkExpr(b.tail, arity);
    }

    private static void checkStmt(stmt s, Map<String, Integer> arity) {
        if (s instanceof letstmt l) checkExpr(l.init, arity);
        else if (s instanceof assignstmt a) checkExpr(a.value, arity);
        else if (s instanceof returnstmt r) { if (r.value != null) checkExpr(r.value, arity); }
        else if (s instanceof ifstmt i) { checkExpr(i.cond, arity); checkBlock(i.then, arity); if (i.els != null) checkBlock(i.els, arity); }
        else if (s instanceof whilestmt w) { checkExpr(w.cond, arity); checkBlock(w.body, arity); }
        else if (s instanceof forstmt f) { checkExpr(f.lo, arity); checkExpr(f.hi, arity); checkBlock(f.body, arity); }
    }

    private static void checkExpr(expr e, Map<String, Integer> arity) {
        if (e instanceof call c) {
            Integer want = arity.get(c.name);
            if (want == null) throw new rustjerror(c.line, "未定义的函数: " + c.name);
            if (want != c.args.size())
                throw new rustjerror(c.line, "函数 " + c.name + " 期望 " + want + " 个参数，实际 " + c.args.size());
            for (expr a : c.args) checkExpr(a, arity);
            return;
        }
        if (e instanceof ast.binop b) { checkExpr(b.left, arity); checkExpr(b.right, arity); }
        else if (e instanceof ast.unop u) checkExpr(u.operand, arity);
    }
}
