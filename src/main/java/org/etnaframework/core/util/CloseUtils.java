package org.etnaframework.core.util;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import org.etnaframework.core.logging.Log;
import org.slf4j.Logger;

/**
 * 关闭的辅助工具类
 *
 * @author BlackCat
 * @since 2012-9-27
 */
public class CloseUtils {

    private static final Logger log = Log.getLogger();

    /**
     * 关闭对象，关闭前会判断是否为null，可能抛出异常
     */
    public static void close(Closeable c) throws IOException {
        if (c != null) {
            c.close();
        }
    }

    /**
     * 关闭对象，关闭前会判断是否为null，可能抛出异常
     *
     * <pre>
     * 注意：
     * Closing this socket will also close the socket's {@link java.io.InputStream InputStream} and {@link java.io.OutputStream OutputStream}.
     * If this socket has an associated channel then the channel is closed as well.
     * </pre>
     */
    public static void close(Socket c) throws IOException {
        if (c != null) {
            c.close();
        }
    }

    /**
     * 关闭对象，方法内是使用了catch，不会抛出异常
     */
    public static void closeSilently(Closeable c) {
        try {
            close(c);
        } catch (Throwable e) {
            log.info("", e);
        }
    }

    /**
     * 关闭对象，方法内是使用了catch，不会抛出异常
     */
    public static void closeSilently(Socket c) {
        try {
            close(c);
        } catch (Throwable e) {
            log.info("", e);
        }
    }

    private CloseUtils() {
    }
}
