/*
 * 结构体字段读取表达式节点（纯数据）。
 *
 * 做什么：表示 `p.x` 形式的字段读取：disp 为变量基址 + 字段序号×4 合成偏移，
 *   供后端 loadEax(disp) 从栈帧读入 EAX。
 * 提供什么功能：base 记录基变量栈帧偏移、disp 记录合成读取偏移、line 记录读取行号；
 *   base 与 line 供 3e 借用检查把字段读取视为对基变量的整体读取（p.f 整体借用 p）。
 */
package ast;

public final class field extends expr {
    public final int base;
    public final int disp;
    public final int line;

    public field(int base, int disp, int line) {
        this.base = base;
        this.disp = disp;
        this.line = line;
    }
}
