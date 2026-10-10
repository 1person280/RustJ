/*
 * 字段赋值语句（纯数据）。
 *
 * 做什么：表示 `p.x = 表达式;` 左值写回：disp 为变量基址 + 字段序号×4 合成偏移，
 *   value 为右值表达式；后端 storeEax(disp) 完成写入。
 * 与 assignstmt 的关系：assignstmt 写整个 i32 变量槽，本节点写结构体的单个字段槽。
 */
package ast;

public final class fieldassignstmt extends stmt {
    public final int disp;
    public final expr value;

    public fieldassignstmt(int disp, expr value) {
        this.disp = disp;
        this.value = value;
    }
}
