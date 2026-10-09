/*
 * 逻辑运算短路发射。
 *
 * 做什么：为 `&&` / `||` / `!` 生成短路求值机器码：左操作数决定是否求值右操作数
 *   （`&&` 左 0 跳过、`||` 左非 0 跳过），结果统一规范化 0/1 落 EAX。
 * 为什么独立成类：短路需要标号与分支回填，代码量超出 eval 的 99 行余量；
 *   与 blockgen（语句发射）从 codegen 拆分的先例一致。
 */
package backend;

import ast.expr;
import java.util.Map;

public final class logicemit {
    /* !x：x == 0 时为 1，否则 0。 */
    public static void emitNot(arch out) {
        out.testEaxEax();
        out.setE();
        out.movzxEaxAl();
    }

    /* &&：左为 0 直接得 0；否则求右并规范化（非零→1）。 */
    public static void emitAnd(expr l, expr r, arch out, Map<String, Integer> targets) {
        int lfalse = out.newLabel();
        int done = out.newLabel();
        eval.emit(l, out, targets);
        out.branchIfZero(lfalse);
        eval.emit(r, out, targets);
        norm(out);
        out.jump(done);
        out.bind(lfalse);
        out.movEaxImm(0);
        out.bind(done);
    }

    /* ||：左非 0 直接得 1；否则求右并规范化。 */
    public static void emitOr(expr l, expr r, arch out, Map<String, Integer> targets) {
        int ltrue = out.newLabel();
        int done = out.newLabel();
        eval.emit(l, out, targets);
        out.branchIfNonZero(ltrue);
        eval.emit(r, out, targets);
        norm(out);
        out.jump(done);
        out.bind(ltrue);
        out.movEaxImm(1);
        out.bind(done);
    }

    /* 非零即真：eax = (eax != 0) ? 1 : 0。 */
    private static void norm(arch out) {
        out.testEaxEax();
        out.setNE();
        out.movzxEaxAl();
    }
}
