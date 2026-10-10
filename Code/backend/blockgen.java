/*
 * 语句级代码生成（块与语句发射，3a 支持结构体构造/字段写回）。
 *
 * 做什么：承接 codegen 下放的语句发射——按序发射块内语句、处理块尾表达式，
 *   负责 return / if / while / for 的标号布局（循环 brk/cont：break 跳 brk，
 *   continue 跳 cont，-1 表示不在循环内）；let 初始化若是结构体构造则逐字段
 *   写入目标变量槽，字段赋值直接写合成偏移槽。
 */
package backend;

import ast.assignstmt;
import ast.block;
import ast.breakstmt;
import ast.continuestmt;
import ast.fieldassignstmt;
import ast.forstmt;
import ast.ifstmt;
import ast.letstmt;
import ast.returnstmt;
import ast.stmt;
import ast.structlit;
import ast.whilestmt;
import java.util.Map;

public final class blockgen {
    public static void emitBlock(block b, arch out, int end, Map<String, Integer> targets, int brk, int cont) {
        for (stmt s : b.stmts) emitStmt(s, out, end, targets, brk, cont);
        if (b.tail != null) eval.emit(b.tail, out, targets);
    }
    private static void emitStmt(stmt s, arch out, int end, Map<String, Integer> targets, int brk, int cont) {
        if (s instanceof letstmt l) emitLet(l, out, targets);
        else if (s instanceof assignstmt a) { eval.emit(a.value, out, targets); out.storeEax(a.offset); }
        else if (s instanceof fieldassignstmt fa) { eval.emit(fa.value, out, targets); out.storeEax(fa.disp); }
        else if (s instanceof returnstmt r) { if (r.value != null) eval.emit(r.value, out, targets); out.jump(end); }
        else if (s instanceof ifstmt i) emitIf(i, out, end, targets, brk, cont);
        else if (s instanceof whilestmt w) emitWhile(w, out, end, targets, brk, cont);
        else if (s instanceof forstmt f) emitFor(f, out, end, targets, brk, cont);
        else if (s instanceof breakstmt) out.jump(brk);
        else if (s instanceof continuestmt) out.jump(cont);
    }
    /* let：init 为结构体构造则逐字段写入 [基址+序号×4]，否则整体求值写单槽。 */
    private static void emitLet(letstmt l, arch out, Map<String, Integer> targets) {
        if (l.init instanceof structlit g) {
            for (int i = 0; i < g.args.size(); i++) {
                eval.emit(g.args.get(i), out, targets);
                out.storeEax(l.offset + g.indexes.get(i) * 4);
            }
        } else { eval.emit(l.init, out, targets); out.storeEax(l.offset); }
    }
    /* if：条件为真走 then，为假走 else（缺省跳过）；两路汇合于 done；循环上下文透传。 */
    private static void emitIf(ifstmt s, arch out, int end, Map<String, Integer> targets, int brk, int cont) {
        int alt = out.newLabel();
        int done = out.newLabel();
        eval.emit(s.cond, out, targets);
        out.branchIfZero(alt);
        emitBlock(s.then, out, end, targets, brk, cont);
        out.jump(done);
        out.bind(alt);
        if (s.els != null) emitBlock(s.els, out, end, targets, brk, cont);
        out.bind(done);
    }
    /* while：循环头重估条件，为假跳出口；continue 跳循环头。 */
    private static void emitWhile(whilestmt s, arch out, int end, Map<String, Integer> targets, int brk, int cont) {
        int top = out.newLabel();
        int done = out.newLabel();
        out.bind(top);
        eval.emit(s.cond, out, targets);
        out.branchIfZero(done);
        emitBlock(s.body, out, end, targets, done, top);
        out.jump(top);
        out.bind(done);
    }
    /* for：i=lo；top: i>=hi 跳 done；循环体（break 跳 done、continue 跳 step）；step: i=i+1 回 top。 */
    private static void emitFor(forstmt s, arch out, int end, Map<String, Integer> targets, int brk, int cont) {
        eval.emit(s.lo, out, targets);
        out.storeEax(s.offset);
        int top = out.newLabel();
        int step = out.newLabel();
        int done = out.newLabel();
        out.bind(top);
        out.loadEax(s.offset);
        out.pushEax();
        eval.emit(s.hi, out, targets);
        out.movEcxEax();
        out.popEax();
        out.cmpEaxEcx();
        out.setL();
        out.movzxEaxAl();
        out.branchIfZero(done);
        emitBlock(s.body, out, end, targets, done, step);
        out.bind(step);
        out.loadEax(s.offset);
        out.pushEax();
        out.movEaxImm(1);
        out.popEcx();
        out.addEaxEcx();
        out.storeEax(s.offset);
        out.jump(top);
        out.bind(done);
    }
}
