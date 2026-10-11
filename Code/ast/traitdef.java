/*
 * trait 定义节点（纯数据）。
 * 做什么：表示顶层 `trait 名字 { fn 方法; ... }` 定义；methods 为按声明序的方法名
 *   列表（签名显式参数个数由 frontend.traits 的声明表持有，此处不重复存储）。
 * 提供：name（mangle 后 trait 名）、methods、line。后端零类型知识、不消费本节点，
 *   仅作编译单元级纯数据留存，与 structdef 同构。
 */
package ast;

import java.util.List;

public final class traitdef {
    public final String name;
    public final List<String> methods;
    public final int line;

    public traitdef(String name, List<String> methods, int line) {
        this.name = name;
        this.methods = methods;
        this.line = line;
    }
}
