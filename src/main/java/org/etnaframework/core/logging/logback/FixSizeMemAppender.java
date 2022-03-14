package org.etnaframework.core.logging.logback;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.etnaframework.core.util.CircularQueue;
import org.slf4j.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

/**
 * 日志容器，可以将所有的日志事件做缓存
 *
 * @author BlackCat
 * @since 2010-9-20 下午09:12:01
 */
public class FixSizeMemAppender<E> extends UnsynchronizedAppenderBase<E> {

    public static class ScaleableLog extends FixSizeLog {

        /**
         * 日志缓存器对应的名字
         */
        private String loggerName;

        private CircularQueue<String> queue; // 注意：不是线程安全的

        public ScaleableLog(String loggerName, int size) {
            this.loggerName = loggerName.intern(); // 用于同步
            this.queue = new CircularQueue<String>(size);
        }

        @Override
        public void log(String msg) {
            // synchronized (loggerName) {
            queue.addToTail(msg);
            // }
        }

        /**
         * 获得所有的日志记录
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Iterator<String> iterator = queue.iterator(); iterator.hasNext(); ) {
                sb.append(iterator.next());
            }
            return sb.toString();
        }

        @Override
        public String getLoggerName() {
            return loggerName;
        }

        @Override
        public void setSize(int size) {
            // synchronized (loggerName) {
            queue.setMaxSize(size);
            // }
        }

        @Override
        public String tail(int num) {
            return toString(); // 不支持，所以直接忽略参数
        }

        @Override
        public String sub(int begin, int end) {
            return toString(); // 不支持，所以直接忽略参数
        }
    }

    /**
     * 固定大小的日志缓存器
     *
     * @author BlackCat
     */
    public static class FixSizeLog {

        /**
         * 日志缓存器对应的名字
         */
        private String loggerName;

        /**
         * 缓存日志的数组
         */
        private String[] bufferedLog;

        /**
         * 当前缓存中的日志数量
         */
        private AtomicInteger index = new AtomicInteger(0);

        /**
         * 日志缓存区大小
         */
        private int size;

        private FixSizeLog() {
        }

        /**
         * 构造固定大小的日志记录器
         *
         * @param loggerName 日志对应的名字，一般为类名
         * @param size 日志缓存区的大小
         */
        public FixSizeLog(String loggerName, int size) {
            this.loggerName = loggerName;
            this.size = size;
            this.bufferedLog = new String[size];
        }

        /**
         * 记录日志信息msg
         *
         * @param msg 日志信息
         */
        public void log(String msg) {
            bufferedLog[index.getAndIncrement() % size] = msg;
        }

        /**
         * 获得所有的日志记录
         */
        @Override
        public String toString() {
            return sub(0, size);
        }

        public String tail(int num) {
            int end = index.get();
            int begin = end - num;
            if (begin < 0) {
                begin = 0;
            }
            return sub(begin, end);
        }

        public String sub(int begin, int end) {
            if (begin < 0 || end > size) {
                throw new IndexOutOfBoundsException();
            }
            StringBuilder sb = new StringBuilder();
            int offset = index.get();
            int _end = end;
            if (offset <= size) { // 说明没有填满
                _end = Math.min(end, offset);
                offset = 0;
            }

            for (int i = begin; i < _end; i++) {
                int idx = (i + offset) % size;
                String item = bufferedLog[idx];
                if (item != null) {
                    sb.append(item);
                }
            }
            return sb.toString();
        }

        /**
         * 获得日志缓存器对应的名字，一般为类名
         */
        public String getLoggerName() {
            return loggerName;
        }

        public void setSize(int size) {
            // FixSizeLog 改不了
        }
    }

    /**
     * 日志容器
     */
    private static final Map<String, FixSizeLog> FIX_SIZE_LOG_MAP = new TreeMap<String, FixSizeLog>(); // 2012-10-13 用TreeMap 这样能按key来排序

    /**
     * 获得loggerName对应的日志缓存器
     */
    public static FixSizeLog getFixSizeLog(String loggerName) {
        return FIX_SIZE_LOG_MAP.get(loggerName);
    }

    /**
     * 获得log对应的日志缓存器
     */
    public static FixSizeLog getFixSizeLog(Logger log) {
        return FIX_SIZE_LOG_MAP.get(log.getName());
    }

    /**
     * 由于取的机会很少，不需要过多考虑同步问题，所以一旦有错重来一次就行了
     */
    public static Collection<FixSizeLog> getAllLog() {
        return FIX_SIZE_LOG_MAP.values();
    }

    /**
     * 日志事件转换器，将日志事件转换成字符串
     */
    protected Layout<E> layout;

    /**
     * 日志容器中每一个日志缓存器的大小
     */
    protected int size = 100;

    /**
     * <pre>
     * 2012-10-13
     * 日志容器打算放多少个log
     *
     * 如果超过这个log_size时，为了考虑不占用太多内存，需要把原来的size调小
     */
    protected int log_size = 500;

    private Executor exe = Executors.newSingleThreadExecutor();// 2012-10-19 为了解决线程安全问题，直接放到另一个线程上异步处理

    /**
     * 处理一个日志事件
     */
    @Override
    protected void append(final E e) {
        if (e instanceof LoggingEvent) {
            exe.execute(new Runnable() {// 2012-10-19 为了解决线程安全问题，直接放到另一个线程上异步处理

                @Override
                public void run() {
                    LoggingEvent le = (LoggingEvent) e;
                    String loggerName = le.getLoggerName();
                    FixSizeLog fsl = FIX_SIZE_LOG_MAP.get(loggerName);
                    // 2012-10-13 判断是否需要shrink
                    int m = FIX_SIZE_LOG_MAP.size() / log_size;
                    boolean needShrink = m > 0;
                    int newSize = needShrink ? size / (m + 1) : size;
                    // if (needShrink) {
                    // System.err.println(newSize);
                    // }
                    if (fsl == null) {
                        // synchronized (this) {// 简单同步 // 2012-10-19 为了解决线程安全问题，直接放到另一个线程上异步处理,所以也就不用同步了
                        fsl = new ScaleableLog(loggerName, newSize);
                        FIX_SIZE_LOG_MAP.put(loggerName, fsl);
                        // }
                    } else if (needShrink) {
                        fsl.setSize(newSize);
                    }

                    fsl.log(layout.doLayout(e));
                }
            });
        }
    }

    /**
     * 获得日志事件转换器
     */
    public Layout<E> getLayout() {
        return layout;
    }

    /**
     * 获得日志容器中默认的日志缓存器大小
     */
    public int getSize() {
        return size;
    }

    /**
     * 设置日记事件转换器
     */
    public void setLayout(Layout<E> layout) {
        this.layout = layout;
    }

    /**
     * 设置日志容器中默认的日志缓存器大小
     */
    public void setSize(int size) {
        this.size = size;
    }
}
