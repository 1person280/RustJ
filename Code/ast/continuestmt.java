/*
 * continue 语句（纯数据）。
 *
 * 做什么：表示 `continue;`，跳过最近一层循环的剩余循环体；对 for 会先执行步进再判断条件，
 *   对 while 直接回到条件判断。
 * 提供什么功能：无字段；跳转目标由后端在发射时按当前循环上下文决定。
 * 不带值、无标签；循环外使用属语义错误，由前端解析期校验并报错。
 */
package ast;

public final class continuestmt extends stmt {
    public continuestmt() {
    }
}
