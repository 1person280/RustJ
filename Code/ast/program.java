/*
 * 编译单元级语法树根（纯数据）。
 * 做什么：承载一次编译的顶层产物：结构体定义 + trait 定义 + 函数列表（impl 方法
 *   已并入函数列表，方法名以 `类型__方法` 与顶层函数隔离）。
 * 提供：defs / tdefs 供编译单元级留存，fns 是代码生成的实际输入。
 */
package ast;

import java.util.ArrayList;
import java.util.List;

public final class program {
    public final List<structdef> defs;
    public final List<traitdef> tdefs;
    public final List<function> fns;

    public program(List<structdef> defs, List<function> fns) {
        this(defs, fns, new ArrayList<>());
    }

    public program(List<structdef> defs, List<function> fns, List<traitdef> tdefs) {
        this.defs = defs;
        this.tdefs = tdefs;
        this.fns = fns;
    }
}
