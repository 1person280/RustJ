/*
 * 编译单元级代码生成入口。
 *
 * 做什么：把一组 ast.function 编译进同一段 .text——先为每个函数分配标号建出
 *   「函数名 → 标号」调用映射（支持前向引用），再逐函数发射：绑定标号、建立栈帧、
 *   按序发射块内语句、以块尾表达式作为返回值（EAX）、拆除栈帧。
 * 提供什么功能：emit(List<function> fns, arch out)。
 * 语句级发射（含 if/while/for/break/continue）已下放到 backend.blockgen，
 * 本类只负责函数级编排与调用映射，守住「单文件 < 100 行」的上限。
 */
package backend;

import ast.block;
import ast.function;
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
        blockgen.emitBlock(fn.body, out, end, targets, -1, -1);
        out.bind(end);
        out.end();
    }
}
