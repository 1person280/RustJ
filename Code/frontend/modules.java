/*
 * 多文件模块协调器（3b 单层模块）。
 * 做什么：入口文件顶层扫描 `mod foo;` 声明（重复报错带行号）；把模块文件按声明序
 *   读入并拼接（供缓存键）；共享一个 structs 符号表完成入口 + 各模块文件的解析，
 *   全部解析后统一做调用点延迟校验（跨模块符号此时已就绪）。
 * 提供：modList / depBytes / concat / parseAll。
 */
package frontend;
import ast.function; import ast.program; import ast.structdef;
import error.rustjerror;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class modules {
    private modules() {}
    /* 词法扫描入口顶层 `mod NAME ;`；深度>0（函数体内）忽略；重复报错。 */
    public static List<String> modList(String code) {
        List<token> tk = new lexer(code).tokenize();
        List<String> mods = new ArrayList<>();
        int depth = 0;
        for (int i = 0; i < tk.size(); i++) {
            token t = tk.get(i);
            if (t.text.equals("{")) { depth++; continue; }
            if (t.text.equals("}")) { depth--; continue; }
            if (depth == 0 && t.text.equals("mod") && i + 2 < tk.size()
                    && tk.get(i + 1).type == token.kind.IDENT && tk.get(i + 2).text.equals(";")) {
                String name = tk.get(i + 1).text;
                if (mods.contains(name)) throw new rustjerror(t.line, "重复的 mod 声明: " + name);
                mods.add(name);
                i += 2;
            }
        }
        return mods;
    }
    /* 按声明序读取各模块文件字节；缺失报 rustjerror（无行号，消息含路径）。 */
    public static List<byte[]> depBytes(Path dir, List<String> mods) {
        List<byte[]> out = new ArrayList<>();
        for (String m : mods) {
            Path p = dir.resolve(m + ".rs");
            try { out.add(Files.readAllBytes(p)); }
            catch (IOException e) { throw new rustjerror(-1, "找不到模块文件: " + p); }
        }
        return out;
    }
    /* 依赖字节拼接：入口之外的全部模块按声明序 concat，供缓存键摘要。 */
    public static byte[] concat(List<byte[]> parts) {
        int total = 0;
        for (byte[] b : parts) total += b.length;
        byte[] out = new byte[total];
        int at = 0;
        for (byte[] b : parts) { System.arraycopy(b, 0, out, at, b.length); at += b.length; }
        return out;
    }
    /* 共享符号表解析各模块（先模块后入口：入口可即时引用模块结构体），最后统一延迟校验。 */
    public static program parseAll(Path dir, String code, List<String> mods, List<byte[]> deps) {
        structs st = new structs();
        List<structdef> defs = new ArrayList<>();
        List<function> fns = new ArrayList<>();
        for (int i = 0; i < mods.size(); i++) {
            String src = new String(deps.get(i), StandardCharsets.UTF_8);
            program pm = new parser(new lexer(src).tokenize(), st, new HashSet<>(), mods.get(i), true).parse();
            defs.addAll(pm.defs);
            fns.addAll(pm.fns);
        }
        program p0 = new parser(new lexer(code).tokenize(), st, new HashSet<>(), "", true).parse();
        defs.addAll(p0.defs);
        fns.addAll(p0.fns);
        calls.validate(fns);
        return new program(defs, fns);
    }
}
