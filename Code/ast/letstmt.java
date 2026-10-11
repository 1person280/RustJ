/*
 * let 绑定语句（纯数据）。
 *
 * 做什么：表示函数/块内的一条 `let 名字 = 表达式;`。
 * 提供什么功能：字段 name 为变量名、offset 为该变量的栈帧偏移，init 为初始化表达式；
 *   line 为绑定行号，slots 为变量占用的栈槽数（标量 1，结构体为字段数）。
 *   slots>1 且 init 为 ident 时表示结构体 move 拷贝；3e 借用检查依赖 name/line 报错定位。
 */
package ast;

public final class letstmt extends stmt {
    public final String name;
    public final int offset;
    public final expr init;
    public final int line;
    public final int slots;

    public letstmt(String name, int offset, expr init, int line, int slots) {
        this.name = name;
        this.offset = offset;
        this.init = init;
        this.line = line;
        this.slots = slots;
    }
}
