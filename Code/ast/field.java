/*
 * 字段访问表达式节点（纯数据）。
 *
 * 做什么：表示 `p.x` 读取：变量基址与字段序号在语法分析期已合成为单一 disp，
 *   后端 loadEax(disp) 即可，无需任何类型知识。
 * 提供什么功能：字段 disp = 变量 rbp 偏移 + 字段序号×4（字段连续 4 字节布局，
 *   见决策「内存布局：字段按声明序连续 4 字节，访问 = [基址 + 字段序号×4]」）。
 */
package ast;

public final class field extends expr {
    public final int disp;
    public final int line;

    public field(int disp, int line) {
        this.disp = disp;
        this.line = line;
    }
}
