/*
 * 赋值语句（纯数据）。
 *
 * 做什么：表示 `名字 = 表达式;`，把右值写回一个已声明变量的栈槽。
 * 提供什么功能：字段 name 为目标变量名、offset 为目标变量偏移，value 为右值表达式；
 *   line 为赋值行号，slots 为目标变量占用的栈槽数（标量 1，结构体为字段数）。
 *   slots>1 且 value 为 ident 时表示结构体 move 拷贝；3e 借用检查依赖 name/line 报错定位。
 */
package ast;

public final class assignstmt extends stmt {
    public final String name;
    public final int offset;
    public final expr value;
    public final int line;
    public final int slots;

    public assignstmt(String name, int offset, expr value, int line, int slots) {
        this.name = name;
        this.offset = offset;
        this.value = value;
        this.line = line;
        this.slots = slots;
    }
}
