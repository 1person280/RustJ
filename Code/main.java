/*
 * RustJ 命令行入口与编译流水线编排（2c sysroot + 2d 增量缓存）。
 * 提供：-RJT 三期占位、-RJCC 三档控缓存块上限（64K/16M/4G，默认 16M）；
 *   sysroot：RustJ/sysroot/runtime.ar 缺失时从 jar 资源解压，无资源则现场生成；
 *   缓存（cache 包 keys/merge/store）：键 = 源 SHA-256 + sysroot 归档 SHA-256，
 *   每次编译前合并一次，命中直接复制产物到 RustJ/out/，未命中走全流程并写缓存。
 */
import ast.function;
import cache.*;
import backend.codebuffer;
import backend.codegen;
import backend.coff;
import backend.lld;
import backend.rtlib;
import backend.x64;
import frontend.lexer;
import frontend.parser;
import frontend.token;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class main {
    public static void main(String[] args) throws Exception {
        String source = null;
        long limit = 16L * 1024 * 1024;
        for (String arg : args) {
            if (arg.startsWith("-RJT=")) continue;      // 三期占位，暂不生效
            if (arg.startsWith("-RJCC=")) { limit = keys.blockLimit(arg.substring(6)); continue; }
            if (arg.startsWith("-")) continue;
            source = arg;
        }
        if (source == null) {
            System.out.println("用法: java -jar RustJ.jar [选项] <源文件.rs>");
            System.exit(2);
        }
        Path root = Paths.get("").toAbsolutePath();
        Path src = root.resolve(source);
        if (!Files.exists(src)) {
            System.out.println("[RustJ] 找不到源文件: " + src);
            System.exit(2);
        }
        byte[] srcBytes = Files.readAllBytes(src);
        byte[] archive = ensureSysroot(root);
        Path finalized = root.resolve("RustJ").resolve("incremental").resolve("finalized");
        merge.merge(finalized, limit);
        String key = keys.key(srcBytes, archive);
        Path outDir = root.resolve("RustJ").resolve("out");
        if (store.lookup(finalized, key, outDir)) {
            System.out.println("[RustJ] 缓存命中，产物已还原: " + root.relativize(outDir.resolve("main.exe")));
            return;
        }
        String code = new String(srcBytes, StandardCharsets.UTF_8);
        List<token> tokens = new lexer(code).tokenize();
        List<function> fns = new parser(tokens).parse();
        codebuffer.clearExterns();
        byte[] object = coff.write("main", emit(fns), codebuffer.extRelocs, codebuffer.extSyms);
        byte[] exe = lld.link(object, archive);
        Files.createDirectories(outDir);
        Files.write(outDir.resolve("main.o"), object);
        Files.write(outDir.resolve("main.exe"), exe);
        store.store(finalized, key, object, exe, limit);
        System.out.println("[RustJ] 目标文件: " + root.relativize(outDir.resolve("main.o")));
        System.out.println("[RustJ] 可执行文件: " + root.relativize(outDir.resolve("main.exe")));
    }

    /* sysroot：RustJ/sysroot/runtime.ar 缺失时从 jar 资源解压，无资源则现场生成。 */
    private static byte[] ensureSysroot(Path root) throws Exception {
        Path ar = root.resolve("RustJ").resolve("sysroot").resolve("runtime.ar");
        if (!Files.exists(ar)) {
            Files.createDirectories(ar.getParent());
            byte[] data = null;
            try (InputStream in = main.class.getResourceAsStream("/sysroot/runtime.ar")) {
                if (in != null) data = in.readAllBytes();
            }
            if (data == null) data = rtlib.archive();
            Files.write(ar, data);
        }
        return Files.readAllBytes(ar);
    }

    /* 选定 win-x64 后端，把全部函数发为 .text 段字节（入口符号恒为 main）。 */
    private static byte[] emit(List<function> fns) {
        x64 out = new x64();
        codegen.emit(fns, out);
        return out.finish();
    }
}