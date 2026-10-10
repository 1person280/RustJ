/*
 * RustJ 最小 runtime 库（2c sysroot）。
 *
 * 做什么：纯 Java 手写 x86-64 机器码，生成三个自研库函数
 *   rjt_double(x)->x*2、rjt_add(a,b)->a+b、rjt_mul(a,b)->a*b 的 COFF .o，
 *   并组装为 ar 归档字节（runtime.ar），供 lld 链接期合并符号与 .text。
 * 提供什么功能：SYMBOLS（隐式全局库符号）、archive()（runtime.ar 字节）。
 */
package backend;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class rtlib {
    public static final List<String> SYMBOLS = List.of("rjt_double", "rjt_add", "rjt_mul");

    private rtlib() {
    }

    /* runtime.ar：三个库 .o 组装为 ar 归档（名字左对齐、数字右对齐、偶对齐补 \n）。 */
    public static byte[] archive() {
        byte[] d1 = coff.write("rjt_double", bytes(0x55, 0x48, 0x89, 0xE5, 0x8B, 0x45, 0x10, 0x03, 0xC0, 0x48, 0x89, 0xEC, 0x5D, 0xC3));
        byte[] d2 = coff.write("rjt_add", bytes(0x55, 0x48, 0x89, 0xE5, 0x8B, 0x45, 0x10, 0x03, 0x45, 0x18, 0x48, 0x89, 0xEC, 0x5D, 0xC3));
        byte[] d3 = coff.write("rjt_mul", bytes(0x55, 0x48, 0x89, 0xE5, 0x8B, 0x45, 0x10, 0x0F, 0xAF, 0x45, 0x18, 0x48, 0x89, 0xEC, 0x5D, 0xC3));
        String[] names = { "rt_double.o", "rt_add.o", "rt_mul.o" };
        byte[][] data = { d1, d2, d3 };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("!<arch>\n".getBytes(StandardCharsets.US_ASCII));
        for (int i = 0; i < data.length; i++) {
            byte[] h = new byte[60];
            Arrays.fill(h, (byte) ' ');
            byte[] nm = names[i].getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(nm, 0, h, 0, Math.min(16, nm.length));
            field(h, 28, "0");
            field(h, 34, "0");
            field(h, 40, "0");
            field(h, 48, "100644");
            field(h, 58, String.valueOf(data[i].length));
            h[58] = '`';
            h[59] = '\n';
            out.writeBytes(h);
            out.writeBytes(data[i]);
            if ((data[i].length & 1) == 1) out.write('\n');
        }
        return out.toByteArray();
    }

    private static byte[] bytes(int... b) {
        byte[] out = new byte[b.length];
        for (int i = 0; i < b.length; i++) out[i] = (byte) b[i];
        return out;
    }

    /* 右对齐写入 ar 头数字字段（to 为字段结束偏移）。 */
    private static void field(byte[] h, int to, String s) {
        byte[] v = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(v, 0, h, to - v.length, v.length);
    }
}
