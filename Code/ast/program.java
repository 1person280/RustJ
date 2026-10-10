/*
 * 编译单元级语法树根（纯数据）。
 *
 * 做什么：承载一次编译的顶层产物：结构体定义列表 + 函数列表（impl 方法已并入
 *   函数列表，方法名以 `类型名::方法名` 与顶层函数隔离）。
 * 提供什么功能：defs 供编译单元级留存，fns 是代码生成的实际输入。
 */
package ast;

import java.util.List;

public final class program {
    public final List<structdef> defs;
    public final List<function> fns;

    public program(List<structdef> defs, List<function> fns) {
        this.defs = defs;
        this.fns = fns;
    }
}
