/*
 * 一个函数的语法树表示（纯数据）。
 *
 * 做什么：承载 `fn 名字(参数: i32, ...) -> i32 <块>` 的整体结构。
 * 提供什么功能：
 *   - name：函数名，后端据它建立「名字 → 标号」的调用映射，入口符号恒为 main；
 *   - params：参数名列表（按声明顺序），参数值经栈传入，位于 [rbp+16+8i]；
 *   - body：函数体块（语句列表 + 尾表达式），尾表达式的值即返回值（EAX）；
 *   - frameBytes：栈帧字节数，由语法分析期按局部变量总数算出（参数不计入，
 *     它们在调用方栈上，不占被调方局部槽）。
 */
package ast;

import java.util.List;

public final class function {
    public final String name;
    public final List<String> params;
    public final block body;
    public final int frameBytes;

    public function(String name, List<String> params, block body, int frameBytes) {
        this.name = name;
        this.params = params;
        this.body = body;
        this.frameBytes = frameBytes;
    }
}
