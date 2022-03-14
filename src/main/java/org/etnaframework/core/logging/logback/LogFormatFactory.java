package org.etnaframework.core.logging.logback;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 获得 统计日志对应的 格式化字符串
 *
 * @author BlackCat
 * @since 2010-10-19
 */
public class LogFormatFactory {

    /**
     * 存放日志记录器的容器
     */
    private static final Map<String, LogFormatFactory> LFF_CACHE_MAP = new ConcurrentHashMap<String, LogFormatFactory>(1);

    /**
     * 获得日志记录器，每一个split对应一个日志记录器
     */
    public static LogFormatFactory getInstance(String split) {
        LogFormatFactory lff = LFF_CACHE_MAP.get(split);
        if (lff == null) {
            LogFormatFactory new_lff = new LogFormatFactory(split);
            LogFormatFactory new_lff1 = LFF_CACHE_MAP.put(split, new_lff);
            lff = new_lff1 == null ? new_lff : new_lff1;
        }
        return lff;
    }

    private String[] formats;

    /** 每个日志记录器都有一个split，用于区分其他的日志记录器 */
    private String split;

    private LogFormatFactory(String split) {
        this.split = split;
        this.formats = new String[100];
        formats[0] = "";
        formats[1] = "{}";
        for (int i = 2; i < formats.length; i++) {
            StringBuilder tmp = new StringBuilder();
            for (int j = 1; j < i; j++) {
                tmp.append("{}").append(this.split);
            }
            tmp.append("{}");
            formats[i] = tmp.toString();
        }
    }

    public String getFormat(int argsLen) {
        if (formats.length > argsLen) {
            return formats[argsLen];
        }
        // 超过一定数量就动态生成，不再缓存
        StringBuilder tmp = new StringBuilder();
        for (int i = 1; i < argsLen; i++) {
            tmp.append("{}").append(this.split);
        }
        tmp.append("{}");
        return tmp.toString();
    }

    public String getFormat(Object[] args) {
        if (args == null) {
            return "";
        }
        return getFormat(args.length);
    }
}
