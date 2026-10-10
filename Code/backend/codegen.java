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
    /* 返回 main 符号在 .text 内的偏移，供 coff/lld 定位 PE 入口点（入口必须精确指向 main，
       不能是 .text 起始：impl 方法/前向函数可能排在 main 之前）。 */
    public static int emit(List<function> fns, arch out) {
        Map<String, Integer> targets = new HashMap<>();
        /* 2c sysroot：库符号先注册为外部标号（负数），再分配用户函数内部标号。 */
        for (String s : rtlib.SYMBOLS) targets.put(s, codebuffer.externLabel(s));
        for (function fn : fns) targets.put(fn.name, out.newLabel());
        for (function fn : fns) emitFn(fn, out, targets);
        Integer main = targets.get("main");
        return main == null ? 0 : out.posOf(main);
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
