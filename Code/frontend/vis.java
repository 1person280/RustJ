/*
 * 模块可见性与归属表（3b）。
 *
 * 做什么：登记每个符号（mangle 后名）所属模块与 pub 标记；cur 表示当前正在
 *   解析的模块（"" = 入口文件）。require() 在跨模块引用时校验目标 pub：
 *   目标模块与当前模块不同且无 pub 标记 → 报 rustjerror 带行号。
 * 提供：pubName / note / require（note 在符号定义时登记，require 在引用点调用）。
 */
package frontend;

import error.rustjerror;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class vis {
    /** 当前解析模块名（"" 表示入口文件）；由 parser 在解析各文件前设置。 */
    public String cur = "";
    private final Set<String> pub = new HashSet<>();
    private final Map<String, String> modOf = new HashMap<>();

    /* 登记 pub 符号（mangle 后名）。 */
    public void pubName(String n) {
        pub.add(n);
    }

    /* 符号是否 pub（impl 方法可见性跟随结构体）。 */
    public boolean isPub(String n) {
        return pub.contains(n);
    }

    /* 登记符号归属模块；入口文件符号记 ""。 */
    public void note(String n, String m) {
        modOf.put(n, m);
    }

    /* 跨模块引用校验：目标与当前模块不同且非 pub → 报错。 */
    public void require(String n, int line) {
        String m = modOf.get(n);
        if (m != null && !m.equals(cur) && !pub.contains(n)) {
            throw new rustjerror(line, "跨模块访问需要 pub: " + n);
        }
    }
}
