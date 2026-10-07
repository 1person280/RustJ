/*
 * 编译单元级代码生成入口。
 *
 * 做什么：把一组 ast.function 编译进同一段 .text——先为每个函数分配标号建出
 *   「函数名 → 标号」调用映射（支持前向引用），再逐函数发射：绑定标号、建立栈帧、
 *   按序发射块内语句、以块尾表达式作为返回值（EAX）、拆除栈帧；并负责 if/while 标号布局。
 * 提供什么功能：emit(List<function> fns, arch out)。
 * 语句与表达式的机器码通过 arch 接口下达，与具体目标架构解耦。
 */
package backend;

import ast.assignstmt;
import ast.block;
import ast.function;
import ast.ifstmt;
import ast.letstmt;
import ast.returnstmt;
import ast.stmt;
import ast.whilestmt;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class codegen {
    public static void emit(List<function> fns, arch out) {
        Map<String, Integer> targets = new HashMap<>();
        for (function fn : fns) targets.put(fn.name, out.newLabel());
        for (function fn : fns) emitFn(fn, out, targets);
    }

    private static void emitFn(function fn, arch out, Map<String, Integer> targets) {
        out.bind(targets.get(fn.name));
        out.begin(fn.frameBytes);
        int end = out.newLabel();
        emitBlock(fn.body, out, end, targets);
        out.bind(end);
        out.end();
    }

    private static void emitBlock(block b, arch out, int end, Map<String, Integer> targets) {
        for (stmt s : b.stmts) emitStmt(s, out, end, targets);
        if (b.tail != null) eval.emit(b.tail, out, targets);
    }

    private static void emitStmt(stmt s, arch out, int end, Map<String, Integer> targets) {
        if (s instanceof letstmt l) { eval.emit(l.init, out, targets); out.storeEax(l.offset); }
        else if (s instanceof assignstmt a) { eval.emit(a.value, out, targets); out.storeEax(a.offset); }
        else if (s instanceof returnstmt r) emitReturn(r, out, end, targets);
        else if (s instanceof ifstmt i) emitIf(i, out, end, targets);
        else if (s instanceof whilestmt w) emitWhile(w, out, end, targets);
    }

    /* return：算出返回值（可缺省）后无条件跳到函数尾声标号。 */
    private static void emitReturn(returnstmt r, arch out, int end, Map<String, Integer> targets) {
        if (r.value != null) eval.emit(r.value, out, targets);
        out.jump(end);
    }

    /* if：条件为真走 then，为假走 else（缺省则跳过）；两路汇合于 done。 */
    private static void emitIf(ifstmt s, arch out, int end, Map<String, Integer> targets) {
        int alt = out.newLabel();
        int done = out.newLabel();
        eval.emit(s.cond, out, targets);
        out.branchIfZero(alt);
        emitBlock(s.then, out, end, targets);
        out.jump(done);
        out.bind(alt);
        if (s.els != null) emitBlock(s.els, out, end, targets);
        out.bind(done);
    }

    /* while：循环头重估条件，为假跳出口；循环体尾部无条件跳回循环头。 */
    private static void emitWhile(whilestmt s, arch out, int end, Map<String, Integer> targets) {
        int top = out.newLabel();
        int done = out.newLabel();
        out.bind(top);
        eval.emit(s.cond, out, targets);
        out.branchIfZero(done);
        emitBlock(s.body, out, end, targets);
        out.jump(top);
        out.bind(done);
    }
}
