package org.etnaframework.core.logging;

import org.etnaframework.core.util.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 提供获得日志记录器的方法
 *
 * @author BlackCat
 * @since 2013-12-23
 */
public class Log {

    private static Logger _getLoggerWith(String prefix, String suffix) {
        String pre = StringTools.isEmpty(prefix) ? "" : prefix + ".";
        String suf = StringTools.isEmpty(suffix) ? "" : "." + suffix;
        StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        try {
            Class<?> lastTime = Class.forName(ste[3].getClassName());
            for (int i = 4; i < ste.length; i++) {
                StackTraceElement e = ste[i];
                Class<?> thisTime = Class.forName(e.getClassName());
                if (lastTime.isAssignableFrom(thisTime)) {
                    lastTime = thisTime;
                } else {
                    break;
                }
            }
            return LoggerFactory.getLogger(pre + lastTime.getName() + suf);
        } catch (ClassNotFoundException e1) {
            return LoggerFactory.getLogger(pre + ste[3].getClassName() + suf);
        }
    }

    /**
     * <pre>
     * 通过回溯当前运行的线程找到调用此方法的类，从而获得正确的代表此类的log对象，可以免去自己指定名称的麻烦
     *
     * 注意如果log在基类中
     * 定义为非static的，在继承类中的log名称将是继承类的而不是基类的
     * 定义为static则在继承类中仍然是基类的名称
     *
     * 使用时请务必要注意，在logback.xml中设置的日志级别是针对log名称而言的，而不是log实际调用的方法
     * 例如在org.etnaframework.core.web.DispatchFilter设置的access_log，其名称为access.org.etnaframework.core.web.DispatchFilter
     * 虽然记录日志时的实际调用方法在DispatchFilter中，但实际日志并不受org.etnaframework包的配置的影响
     * </pre>
     */
    public static Logger getLogger() {
        return _getLoggerWith(null, null);
    }

    /**
     * 通过前缀来获得log对象
     */
    public static Logger getLoggerWithPrefix(String prefix) {
        return _getLoggerWith(prefix, null);
    }

    /**
     * 通过后缀来获得log对象
     */
    public static Logger getLoggerWithSuffix(String suffix) {
        return _getLoggerWith(null, suffix);
    }

    /**
     * 通过指定前缀、后缀来获得log对象
     */
    public static Logger getLoggerWith(String prefix, String suffix) {
        return _getLoggerWith(prefix, suffix);
    }

    /**
     * 直接指定log名称获得log对象
     */
    public static Logger getLogger(String str) {
        return LoggerFactory.getLogger(str);
    }

    /**
     * 获取指定class的log对象
     */
    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

    /**
     * 通过指定类class和前缀来获得log对象
     */
    public static Logger getLoggerWithPrefix(Class<?> clazz, String prefix) {
        String pre = StringTools.isEmpty(prefix) ? "" : prefix + ".";
        return LoggerFactory.getLogger(pre + clazz.getName());
    }

    /**
     * 通过指定类class和后缀来获得log对象
     */
    public static Logger getLoggerWithSuffix(Class<?> clazz, String suffix) {
        String suf = StringTools.isEmpty(suffix) ? "" : "." + suffix;
        return LoggerFactory.getLogger(clazz.getName() + suf);
    }

    /**
     * 通过指定类class和前缀、后缀来获得log对象
     */
    public static Logger getLoggerWith(Class<?> clazz, String prefix, String suffix) {
        String pre = StringTools.isEmpty(prefix) ? "" : prefix + ".";
        String suf = StringTools.isEmpty(suffix) ? "" : "." + suffix;
        return LoggerFactory.getLogger(pre + clazz.getName() + suf);
    }
}
