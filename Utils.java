package club.whuhu.jrpc;

import java.io.Closeable;

public class Utils {
    public static void closeSilently(Closeable stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (Exception e) {
        }
    }
}
