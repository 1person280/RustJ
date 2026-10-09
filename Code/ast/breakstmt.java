/*
 * break 语句（纯数据）。
 *
 * 做什么：表示 `break;`，立即退出最近一层 for/while 循环。
 * 提供什么功能：无字段；循环出口标号由后端在发射时按当前循环上下文决定。
 * 不带值、无标签；循环外使用属语义错误，由前端解析期校验并报错。
 */
package ast;

public final class breakstmt extends stmt {
    public breakstmt() {
    }
}
