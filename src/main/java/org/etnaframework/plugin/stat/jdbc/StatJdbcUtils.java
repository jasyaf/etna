package org.etnaframework.plugin.stat.jdbc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.util.TimeSpanStat;
import org.slf4j.Logger;

/**
 * 用于统计调用jdbcTemplate的方法的执行情况
 *
 * @author dragonlai
 * @since 2015.06.09
 */
public class StatJdbcUtils {

    private static final Logger log = Log.getLogger();

    public static class JdbcStat extends TimeSpanStat {

        public JdbcStat(String name, Logger log) {
            super(name, log);
        }

        /** 执行的最新一条SQL语句 */
        private String lastSql;

        private StackTraceElement stackTraceElement;

        public String getLastSql() {
            return lastSql;
        }

        public StackTraceElement getStackTraceElement() {
            return stackTraceElement;
        }

        public void setStackTraceElement(StackTraceElement stackTraceElement) {
            this.stackTraceElement = stackTraceElement;
        }

        @Override
        protected void warn(long end, long begin, Object arg) {
            log.error("SLOW_PROCESS:{}:{} [{}ms]\n", new Object[] {
                name,
                arg,
                end - begin
            });
        }

        @Override
        public void record(long end, long begin, int count, Object arg) {
            super.record(end, begin, count, arg);
            lastSql = count > 1 ? arg + "(BATCH-" + count + ")" : arg.toString();
        }
    }

    private static final ConcurrentHashMap<StackTraceElement, JdbcStat> stat;

    private static volatile boolean rswitch = true;

    private static volatile long startTime;

    static {
        stat = new ConcurrentHashMap<StackTraceElement, JdbcStat>();
        startTime = System.currentTimeMillis();
    }

    /**
     * 决定是否要统计SQL
     */
    static void recordSwitch(boolean rs) {
        rswitch = rs;
    }

    /**
     * 统计归零
     */
    synchronized static void reset() {
        stat.clear();
        startTime = System.currentTimeMillis();
    }

    /**
     * 获取统计信息
     */
    static Map<StackTraceElement, JdbcStat> getStat() {
        return stat;
    }

    /**
     * 返回统计时间（单位：ms）
     */
    static long getStatTime() {
        long s = System.currentTimeMillis() - startTime;
        return s > 0 ? s : 1; // 防止分母为0导致除数异常
    }

    static boolean isIgnore(StackTraceElement ste) {
        String clazz = ste.getClassName();
        // 预计被排除的包名前缀，有此前缀的类不会被纳入统计
        return clazz.startsWith("org.etnaframework.jdbc.");
    }

    /**
     * 记录执行的SQL用于统计数量
     *
     * @param begin SQL执行开始时间
     */
    public static void record(String sql, int count, long begin) {
        if (rswitch && count > 0) { // count必须为正整数
            StackTraceElement[] ste = Thread.currentThread().getStackTrace();
            StackTraceElement trace = null;
            // 统计主要是记录来自DAO的调用，通过试验发现，实际运行时stackTrace表中的元素
            // 一般是从下标3或4开始是来自DAO的调用，统计从这里开始，统计时会排除掉jdbcTemplate所在的包
            int index = 3;
            for (; index < ste.length; index++) {
                if (!isIgnore(ste[index])) {
                    trace = ste[index];
                    break;
                }
            }
            JdbcStat js = stat.get(trace);
            long end = System.currentTimeMillis();
            if (null == js) {
                synchronized (stat) {
                    if (null == stat.get(trace)) {
                        js = new JdbcStat("JdbcStat", log);
                        js.record(end, begin, count, sql);
                        stat.put(trace, js);
                    }
                }
            } else {
                js.record(end, begin, count, sql);
            }
        }
    }
}
