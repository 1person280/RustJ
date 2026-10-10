/*
 * 结构体字面量表达式节点（纯数据）。
 *
 * 做什么：表示 `Point { x: 3, y: 4 }` 构造：把逐字段求值的 arg 写入目标变量槽的
 *   [基址 + 声明序号×4]。
 * 提供什么功能：typeName 为结构体名；fields 为源码顺序字段名，args 为对应字段值；
 *   indexes 为每个字段在结构体里的声明序号（源码可乱序，槽位按声明序号对齐）。
 *   目标基址（offset）不在本节点：构造只出现在 let 初始化，由 letstmt 提供基址，
 *   后端在语句层合成「基址 + 序号×4」的最终偏移。
 */
package ast;

import java.util.List;

public final class structlit extends expr {
    public final String typeName;
    public final List<String> fields;
    public final List<Integer> indexes;
    public final List<expr> args;
    public final int line;

    public structlit(String typeName, List<String> fields, List<Integer> indexes, List<expr> args, int line) {
        this.typeName = typeName;
        this.fields = fields;
        this.indexes = indexes;
        this.args = args;
        this.line = line;
    }
}
