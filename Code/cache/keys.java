package cache;
/*
 * cache.keys —— 缓存键计算与 -RJCC 档位解析。
 * 由 backend/cache.java 拆分而来：本类承担“键”职责，
 * 缓存键 = 源文件 SHA-256 + sysroot 归档 SHA-256，内容一变键即失效；
 * blockLimit 把 -RJCC 三档字符串映射为块文件体积上限（缺省/非法回退 16M）。
 * 只“算键”，不触碰块文件字节，职责边界在键层面。
 */
import java.security.MessageDigest;

public final class keys {
    private keys() {}
    /* -RJCC 档位：64K/4G 特判，其余回退默认 16M。 */
    public static long blockLimit(String v) { return "64K".equals(v) ? 64L * 1024 : "4G".equals(v) ? 4L * 1024 * 1024 * 1024 : 16L * 1024 * 1024; }
    /* 缓存键 = 源 SHA-256 串 + sysroot 归档 SHA-256 串，供命中判定。 */
    public static String key(byte[] s, byte[] a) { return sha(s) + sha(a); }
    /* SHA-256 十六进制摘要；算法不可用时上抛运行时异常。 */
    private static String sha(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte x : md.digest(data)) sb.append(String.format("%02x", x & 0xFF));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
