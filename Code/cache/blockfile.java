package cache;
/*
 * cache.blockfile —— 缓存块文件（block.*.bin）的二进制读写原语。
 * 由 backend/cache.java 拆分而来：本类只管字节层面的
 * 条目编码、块文件枚举与数组工具，不含键、命中判定与合并策略，
 * 让 store/merge 复用同一紧凑布局（LE 定长头 + 变长载荷）。
 */
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class blockfile {
    private blockfile() {}
    /* 枚举 f 下按名排序的 block.*.bin；f 非目录时返回空表。 */
    static List<Path> blocks(Path f) throws Exception {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(f)) return out;
        try (Stream<Path> s = Files.list(f)) {
            s.filter(p -> p.getFileName().toString().startsWith("block.")).sorted().forEach(out::add);
        }
        return out;
    }
    /* 编码一条缓存记录：kl|key|ts|ol|obj|el|exe，LE 序，ts 为写入时刻。 */
    static byte[] entry(String key, byte[] o, byte[] exe) {
        byte[] k = key.getBytes(StandardCharsets.US_ASCII);
        return ByteBuffer.allocate(4 + k.length + 8 + 4 + o.length + 4 + exe.length).order(ByteOrder.LITTLE_ENDIAN).putInt(k.length).put(k).putLong(System.currentTimeMillis()).putInt(o.length).put(o).putInt(exe.length).put(exe).array();
    }
    /* 拼接 a、b 为新的字节数组（写块时追加条目）。 */
    static byte[] concat(byte[] a, byte[] b) { byte[] r = new byte[a.length + b.length]; System.arraycopy(a, 0, r, 0, a.length); System.arraycopy(b, 0, r, a.length, b.length); return r; }
    /* 从 a 的 off 起截取 len 字节。 */
    static byte[] copy(byte[] a, int off, int len) { byte[] r = new byte[len]; System.arraycopy(a, off, r, 0, len); return r; }
    /* 读 a[off..off+4) 为小端 int。 */
    static int getInt(byte[] a, int off) { return (a[off] & 0xFF) | ((a[off + 1] & 0xFF) << 8) | ((a[off + 2] & 0xFF) << 16) | ((a[off + 3] & 0xFF) << 24); }
}
