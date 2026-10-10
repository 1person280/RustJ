/* PE/COFF 目标文件写出器：write(symbol,text) 单外部符号；write(symbol,text,relocs,extSyms)
 * 写 REL32(0x0004) 重定位表与外部符号表（2c sysroot），条目索引 = 1 + 外部符号索引。 */
package backend;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
public final class coff {
    private static final int MACHINE_AMD64 = 0x8664;
    private static final int SECTION_CHARS = 0x60000020;
    private static final int SYMBOL_EXTERNAL = 2;
    private static final int REL32 = 0x0004;
    public static byte[] write(String symbol, byte[] text) {
        return write(symbol, text, new ArrayList<>(), new ArrayList<>());
    }
    public static byte[] write(String symbol, byte[] text, List<int[]> relocs, List<String> extSyms) {
        byte[] strtab = stringTable(symbol, extSyms);
        int rawPtr = 20 + 40;
        int relPtr = rawPtr + text.length;
        int symPtr = relPtr + relocs.size() * 10;
        int symbols = 1 + extSyms.size();
        ByteBuffer b = ByteBuffer.allocate(symPtr + symbols * 18 + strtab.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) MACHINE_AMD64);
        b.putShort((short) 1);
        b.putInt(0);
        b.putInt(symPtr);
        b.putInt(symbols);
        b.putShort((short) 0);
        b.putShort((short) 0);
        b.put(sectionName(".text"));
        b.putInt(text.length);
        b.putInt(0);
        b.putInt(text.length);
        b.putInt(rawPtr);
        b.putInt(relocs.isEmpty() ? 0 : relPtr);
        b.putInt(0);
        b.putShort((short) relocs.size());
        b.putShort((short) 0);
        b.putInt(SECTION_CHARS);
        b.put(text);
        for (int[] r : relocs) {
            b.putInt(r[0]);
            b.putInt(1 + r[1]);
            b.putShort((short) REL32);
        }
        putSymbol(b, 4, 0, 1);
        int off = 4 + symbol.length() + 1;
        for (String s : extSyms) {
            putSymbol(b, off, 0, 0);
            off += s.length() + 1;
        }
        b.put(strtab);
        return b.array();
    }
    private static void putSymbol(ByteBuffer b, int strOff, int value, int section) {
        b.putInt(0);
        b.putInt(strOff);
        b.putInt(value);
        b.putShort((short) section);
        b.putShort((short) 0);
        b.put((byte) SYMBOL_EXTERNAL);
        b.put((byte) 0);
    }
    private static byte[] sectionName(String name) {
        byte[] out = new byte[8];
        byte[] raw = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(raw, 0, out, 0, Math.min(raw.length, out.length));
        return out;
    }
    private static byte[] stringTable(String symbol, List<String> extSyms) {
        int n = 4 + symbol.length() + 1;
        for (String s : extSyms) n += s.length() + 1;
        ByteBuffer b = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(n);
        b.put(symbol.getBytes(StandardCharsets.US_ASCII));
        b.put((byte) 0);
        for (String s : extSyms) {
            b.put(s.getBytes(StandardCharsets.US_ASCII));
            b.put((byte) 0);
        }
        return b.array();
    }
}
