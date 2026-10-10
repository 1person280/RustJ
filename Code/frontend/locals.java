/*
 * 局部变量符号表（3a 增加类型维度）。
 *
 * 做什么：为函数体内的 let 变量分配栈帧偏移并登记类型（i32 / 结构体名），
 *   变量被引用时校验声明合法性。
 * 提供什么功能：declare/declareParam 按名分配偏移并登记类型（默认 i32）；
 *   setType 供 let 初始化后回填结构体类型；typeOf 供字段/方法访问查类型；
 *   offsetOf 查偏移；size() 返回变量总数供算栈帧。
 */
package frontend;

import error.rustjerror;
import java.util.HashMap;
import java.util.Map;

public final class locals {
    private final Map<String, Integer> offsets = new HashMap<>();
    private final Map<String, String> types = new HashMap<>();
    private int count;

    public int declare(String name, int line) {
        if (offsets.containsKey(name)) {
            throw new rustjerror(line, "变量重复声明: " + name);
        }
        count++;
        int offset = -4 * count;
        offsets.put(name, offset);
        return offset;
    }

    /* 参数登记：第 index 个参数位于 [rbp+16+8i]（push/call 均为 8 字节宽：rbp+0 存旧 rbp、
       rbp+8 存返回地址、rbp+16 起为参数区），不占局部槽、不计入 size()。 */
    public int declareParam(String name, int index, int line) {
        if (offsets.containsKey(name)) {
            throw new rustjerror(line, "变量重复声明: " + name);
        }
        int offset = 16 + 8 * index;
        offsets.put(name, offset);
        return offset;
    }

    /* 带类型的登记重载：供 let 结构体构造与 self 字段参数登记类型维度。 */
    public int declare(String name, String type, int line) {
        int offset = declare(name, line);
        types.put(name, type);
        return offset;
    }

    /* 按槽数分配：结构体变量占「字段数×4 字节」连续栈空间，返回最低槽（基址）
       偏移 = -4×(count+slots)，字段 [基址+序号×4] 落在分配区间内；slots=1 与
       单槽 declare 等价。 */
    public int declare(String name, String type, int slots, int line) {
        if (offsets.containsKey(name)) {
            throw new rustjerror(line, "变量重复声明: " + name);
        }
        count += slots;
        int offset = -4 * count;
        offsets.put(name, offset);
        types.put(name, type);
        return offset;
    }

    public int declareParam(String name, String type, int index, int line) {
        int offset = declareParam(name, index, line);
        types.put(name, type);
        return offset;
    }

    public int offsetOf(String name, int line) {
        Integer offset = offsets.get(name);
        if (offset == null) throw new rustjerror(line, "未声明的变量: " + name);
        return offset;
    }

    public String typeOf(String name, int line) {
        String type = types.get(name);
        if (type == null) throw new rustjerror(line, "未声明的变量: " + name);
        return type;
    }

    public void setType(String name, String type, int line) {
        if (!offsets.containsKey(name)) throw new rustjerror(line, "未声明的变量: " + name);
        types.put(name, type);
    }

    /* self 伪变量：仅登记类型供字段/方法解析，不分配偏移（`self.f` 映射到字段参数）。 */
    public void declareSelf(String type) {
        types.put("self", type);
    }

    public int size() {
        return count;
    }
}