/*
 * 函数调用表达式节点（纯数据）。
 *
 * 做什么：表示对某个函数的调用，例如 `fib(n - 1)`。
 * 提供什么功能：字段 name 保存被调函数名，args 保存实参表达式列表；
 *   line 记录调用点行号，供前端延迟校验（未定义函数/参数个数不符）报错定位。
 * 调用目标（名字 → 标号）由后端发射期解析，节点不持有任何机器码信息。
 */
package ast;

import java.util.List;

public final class call extends expr {
    public final String name;
    public final List<expr> args;
    public final int line;

    public call(String name, List<expr> args, int line) {
        this.name = name;
        this.args = args;
        this.line = line;
    }
}
