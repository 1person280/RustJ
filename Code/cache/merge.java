package cache;
/*
 * cache.merge —— 编译前缓存合并。
 * 由 backend/cache.java 拆分而来：本类承担“合并碎片”职责，
 * 每次编译前把各块条目扫入按键去重的映射（后写覆盖先写），
 * 删旧块后按上限重排写出，收拢碎片，避免半满块长期占盘。
 */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class merge {
    private merge() {}
    /* 合并 f 下全部块：扫描条目 → 删旧块 → 按上限重排写出。 */
    public static void merge(Path f, long limit) throws Exception {
        List<Path> bs = blockfile.blocks(f);
        Map<String, byte[]> map = new HashMap<>();
        for (Path p : bs) scan(Files.readAllBytes(p), map);
        for (Path p : bs) Files.delete(p);
        writeBlocks(f, map.values(), limit);
    }
    /* 扫描一个块内全部记录：按键（ASCII）收进映射，同键后写覆盖先写。 */
    private static void scan(byte[] all, Map<String, byte[]> map) {
        int off = 0;
        while (off < all.length) {
            int kl = blockfile.getInt(all, off), ol = blockfile.getInt(all, off + 4 + kl + 8), el = blockfile.getInt(all, off + 4 + kl + 8 + 4 + ol);
            map.put(new String(all, off + 4, kl, StandardCharsets.US_ASCII), blockfile.copy(all, off, 4 + kl + 8 + 4 + ol + 4 + el));
            off += 4 + kl + 8 + 4 + ol + 4 + el;
        }
    }
    /* 按上限把条目流重排为 block.%03d.bin；单条超限时独占新块。 */
    private static void writeBlocks(Path f, Iterable<byte[]> es, long limit) throws Exception {
        Files.createDirectories(f);
        int idx = 0;
        byte[] cur = new byte[0];
        for (byte[] e : es) {
            if (cur.length > 0 && cur.length + e.length > limit) { Files.write(f.resolve(String.format("block.%03d.bin", idx++)), cur); cur = new byte[0]; }
            cur = blockfile.concat(cur, e);
        }
        if (cur.length > 0) Files.write(f.resolve(String.format("block.%03d.bin", idx)), cur);
    }
}
