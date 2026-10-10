package cache;
/*
 * cache.store —— 缓存条目存取：命中还原与写入。
 * 由 backend/cache.java 拆分而来：本类承担“查询 + 落盘”职责，
 * 以缓存键扫块文件命中后还原 main.o/main.exe 到 out/，
 * 未命中则把新条目追加到首个未满块（满则新建块），上限内不丢缓存。
 */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class store {
    private store() {}
    /* 遍历块文件按键查记录；命中还原 main.o/main.exe 到 out/ 并返回 true。 */
    public static boolean lookup(Path f, String key, Path out) throws Exception {
        for (Path p : blockfile.blocks(f)) {
            byte[] all = Files.readAllBytes(p);
            int off = 0;
            while (off < all.length) {
                int kl = blockfile.getInt(all, off), ol = blockfile.getInt(all, off + 4 + kl + 8), el = blockfile.getInt(all, off + 4 + kl + 8 + 4 + ol);
                if (kl == key.length() && key.equals(new String(all, off + 4, kl, StandardCharsets.US_ASCII))) {
                    Files.createDirectories(out);
                    Files.write(out.resolve("main.o"), blockfile.copy(all, off + 4 + kl + 8 + 4, ol));
                    Files.write(out.resolve("main.exe"), blockfile.copy(all, off + 4 + kl + 8 + 4 + ol + 4, el));
                    return true;
                }
                off += 4 + kl + 8 + 4 + ol + 4 + el;
            }
        }
        return false;
    }
    /* 写新条目：先落 working/<会话>/，再追加到首个未满块（无则新建 block.%03d.bin），最后删会话。 */
    public static void store(Path f, String key, byte[] o, byte[] exe, long limit) throws Exception {
        byte[] e = blockfile.entry(key, o, exe);
        Path work = f.getParent().resolve("working").resolve(String.valueOf(System.currentTimeMillis()));
        Files.createDirectories(work);
        Files.write(work.resolve("entry.bin"), e);
        List<Path> bs = blockfile.blocks(f);
        boolean done = false;
        for (Path p : bs) {
            if (Files.size(p) + e.length <= limit) { Files.write(p, blockfile.concat(Files.readAllBytes(p), e)); done = true; break; }
        }
        if (!done) { Files.createDirectories(f); Files.write(f.resolve(String.format("block.%03d.bin", bs.size())), e); }
        Files.delete(work.resolve("entry.bin"));
        Files.delete(work);
    }
}
