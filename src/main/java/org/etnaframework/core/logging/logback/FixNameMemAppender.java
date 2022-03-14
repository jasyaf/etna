package org.etnaframework.core.logging.logback;

import java.util.LinkedHashMap;
import java.util.Map;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

/**
 * <pre>
 * 日志容器，将指定的日志存到内存中
 * 现在已知可用的地方是：在BaseJob中注册到此日志容器中，在任务跑的过程中，可以在内存中查到运行日志
 *
 * @author BlackCat
 * @since 2011-12-21 下午8:32:28
 * @param <E>
 */
public class FixNameMemAppender<E> extends UnsynchronizedAppenderBase<E> {

    public static interface FixNameLog {

        public abstract void log(String msg);

        public abstract String getLoggerName();
    }

    // private static final Map<String, Collection<FixNameLog>> FIX_NAME_LOG = new HashMap<String, Collection<FixNameLog>>();
    // private static final Map<String, FixNameLog> FIX_NAME_LOG_MAP = new ConcurrentHashMap<String, FixNameLog>();
    private static final Map<String, FixNameLog> FIX_NAME_LOG_MAP = new LinkedHashMap<String, FixNameLog>();

    public static void register(FixNameLog fnl) {
        String name = fnl.getLoggerName();
        // synchronized (name.intern()) {
        synchronized (FIX_NAME_LOG_MAP) {
            if (FIX_NAME_LOG_MAP.containsKey(name)) {
                throw new IllegalAccessError("cant register FixNameLog:[" + name + "]" + fnl);
            }
            FIX_NAME_LOG_MAP.put(name, fnl);
        }
        // Collection<FixNameLog> list = FIX_NAME_LOG.get(name);
        // if (list == null) {
        // synchronized (FIX_NAME_LOG) {
        // if (list == null) {
        // list = new HashSet<FixNameMemAppender.FixNameLog>();
        // FIX_NAME_LOG.put(name, list);
        // }
        // }
        // }
        // list.add(fnl);
    }

    @Override
    protected void append(E e) {
        if (e instanceof LoggingEvent) {
            LoggingEvent le = (LoggingEvent) e;
            String loggerName = le.getLoggerName();
            FixNameLog fnl = FIX_NAME_LOG_MAP.get(loggerName);
            if (fnl != null) {
                String msg = layout.doLayout(e);
                fnl.log(msg);
            }
            // Collection<FixNameLog> list = FIX_NAME_LOG.get(loggerName);
            // if (list != null) {// 有注册就记录到内存中
            // String msg = layout.doLayout(e);
            // for (FixNameLog fnl : list) {
            // fnl.log(msg);
            // }
            // }
        }
    }

    protected Layout<E> layout;

    public Layout<E> getLayout() {
        return layout;
    }

    public void setLayout(Layout<E> layout) {
        this.layout = layout;
    }

    public static Map<String, FixNameLog> getFixNameLogMap() {
        return FIX_NAME_LOG_MAP;
    }
}
