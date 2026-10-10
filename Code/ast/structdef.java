/*
 * 结构体定义节点（纯数据）。
 *
 * 做什么：表示顶层 `struct 名字 { 字段: i32, ... }` 定义。
 * 提供什么功能：name 为结构体名（与函数共享名字空间），fields 为按声明序的字段名
 *   列表——字段序号即内存偏移（×4 字节）。布局与 impl 方法由 frontend.structs
 *   登记使用，后端零类型知识、不消费本节点，仅作编译单元级纯数据留存。
 */
package ast;

import java.util.List;

public final class structdef {
    public final String name;
    public final List<String> fields;
    public final int line;

    public structdef(String name, List<String> fields, int line) {
        this.name = name;
        this.fields = fields;
        this.line = line;
    }
}
