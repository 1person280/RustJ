/*
 * x86-64 指令字节缓冲与标号回填；call(label<0) 视为外部库符号（2c sysroot），
 * 发射 E8 占位并登记 REL32 重定位；externLabel/clearExterns 供 coff 使用。
 */
package backend;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
public final class codebuffer {
    private byte[] buf = new byte[256];
    private int len;
    private final List<Integer> labelPos = new ArrayList<>();
    private final List<int[]> patches = new ArrayList<>();
    public static final List<String> extSyms = new ArrayList<>();
    public static final List<int[]> extRelocs = new ArrayList<>();
    void put(int... bytes) {
        for (int b : bytes) write(b);
    }
    void putInt(int value) {
        write(value & 0xFF);
        write((value >>> 8) & 0xFF);
        write((value >>> 16) & 0xFF);
        write((value >>> 24) & 0xFF);
    }
    int newLabel() {
        labelPos.add(-1);
        return labelPos.size() - 1;
    }
    void bind(int label) {
        labelPos.set(label, len);
    }
    void jump(int label) {
        put(0xE9);
        patch(label);
    }
    void call(int label) {
        put(0xE8);
        if (label < 0) {
            int site = len;
            putInt(0);
            extRelocs.add(new int[] { site, -label - 1 });
        } else {
            patch(label);
        }
    }
    void branchIfZero(int label) {
        put(0x85, 0xC0);
        put(0x0F, 0x84);
        patch(label);
    }
    void branchIfNonZero(int label) {
        put(0x85, 0xC0);
        put(0x0F, 0x85);
        patch(label);
    }
    byte[] bytes() {
        for (int[] p : patches) {
            writeIntAt(p[1], labelPos.get(p[0]) - (p[1] + 4));
        }
        return Arrays.copyOf(buf, len);
    }
    static int externLabel(String name) { extSyms.add(name); return -extSyms.size(); }
    public static void clearExterns() { extSyms.clear(); extRelocs.clear(); }
    private void patch(int label) {
        int site = len;
        putInt(0);
        patches.add(new int[] { label, site });
    }
    private void write(int b) {
        if (len == buf.length) grow();
        buf[len++] = (byte) b;
    }
    private void writeIntAt(int pos, int value) { buf[pos] = (byte) (value & 0xFF); buf[pos + 1] = (byte) ((value >>> 8) & 0xFF); buf[pos + 2] = (byte) ((value >>> 16) & 0xFF); buf[pos + 3] = (byte) ((value >>> 24) & 0xFF); }
    private void grow() { byte[] bigger = new byte[buf.length * 2]; System.arraycopy(buf, 0, bigger, 0, len); buf = bigger; }
}
