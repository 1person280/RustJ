/*
 * 整数字面量表达式节点（纯数据）。
 *
 * 做什么：承载源码中的 i32 字面量，例如 `1`、`42`；3c 起 bool 字面量
 *   true/false 也复用本节点（isBool=true，值为 1/0），供泛型实例类型推断。
 * 提供什么功能：字段 value 保存字面量数值，供后端装入 EAX；isBool 供
 *   frontend.generics.typeOf 区分实例类型 i32 / bool。
 */
package ast;

public final class intlit extends expr {
    public final int value;
    public final boolean isBool;

    public intlit(int value) {
        this.value = value;
        this.isBool = false;
    }

    public intlit(int value, boolean isBool) {
        this.value = value;
        this.isBool = isBool;
    }
}
