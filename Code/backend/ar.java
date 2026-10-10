/*
 * ar 归档读取器（2c sysroot）。
 *
 * 做什么：解析 ar 归档字节，跳过 "!<arch>\n" 全局头与各成员 60 字节头，
 *   返回成员数据字节（处理偶对齐补位）。
 * 提供什么功能：members(byte[] archive) -> List<byte[]>。
 */
package backend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ar {
    private ar() {
    }

    public static List<byte[]> members(byte[] archive) {
        List<byte[]> out = new ArrayList<>();
        int p = 8;
        while (p + 60 <= archive.length) {
            int size = 0;
            for (int i = 0; i < 10; i++) {
                char c = (char) (archive[p + 48 + i] & 0xFF);
                if (c >= '0' && c <= '9') size = size * 10 + (c - '0');
            }
            byte[] d = Arrays.copyOfRange(archive, p + 60, p + 60 + size);
            out.add(d);
            p += 60 + size + (size & 1);
        }
        return out;
    }
}
