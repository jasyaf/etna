package org.etnaframework.core.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.etnaframework.core.web.HttpEvent;
import org.slf4j.Logger;

/**
 * 按时间段进行统计的基类
 *
 * @author BlackCat
 * @since 2015-06-29
 */
public class TimeSpanStat {

    /** 处理总次数 */
    protected AtomicLong all_num = new AtomicLong();

    /** 处理总时长 */
    protected AtomicLong all_span = new AtomicLong();

    protected Logger log;

    /** 改造成统计最近10分钟内最大值 */
    protected volatile long max_span;

    protected String name;

    /** 慢的总个数 */
    protected AtomicLong slow_num = new AtomicLong();

    /** 慢的总时长 */
    protected AtomicLong slow_span = new AtomicLong();

    protected int slowThreshold;

    protected String tableHeader;

    protected String htmlTableHeader;

    protected String timeStatFmt;

    protected String timeStatHtmlFmt;

    protected boolean warn;

    public TimeSpanStat(String name, int slowThreshold, boolean warn, Logger log) {
        this.name = name;
        this.slowThreshold = slowThreshold;
        this.log = log;
        this.warn = warn;
        initFormat(35, 0);
    }

    public TimeSpanStat(String name, Logger log) {
        this(name, 1000, true, log);
    }

    public long getAllNum() {
        return all_num.get();
    }

    /**
     * 重新设置all_num值
     */
    public void setAllNum(long all_num) {
        this.all_num.set(all_num);
    }

    public long getAllSpan() {
        return all_span.get();
    }

    /**
     * 重新设置all_span值
     */
    public void setAllSpan(long all_span) {
        this.all_span.set(all_span);
    }

    public long getSlowNum() {
        return slow_num.get();
    }

    /**
     * 重新设置slow_num值
     */
    public void setSlowNum(long slow_num) {
        this.slow_num.set(slow_num);
    }

    public long getSlowSpan() {
        return slow_span.get();
    }

    /**
     * 重新设置slow_span值
     */
    public void setSlowSpan(long slow_span) {
        this.slow_span.set(slow_span);
    }

    /**
     * 提取最大时长
     */
    public long getMaxSpan() {
        return max_span;
    }

    /**
     * 设置最大时长
     */
    public void setMaxSpan(long max_span) {
        this.max_span = max_span;
    }

    public String getTableHeader() {
        return tableHeader;
    }

    /** 暂时只处理 times和avg字段的排序，以后有需要再加其它字段 */
    public String getHtmlTableHeader() {
        return htmlTableHeader;
    }

    public String getTableHeader(boolean useTxt) {
        if (useTxt) {
            return getTableHeader();
        }
        return getHtmlTableHeader();
    }

    public void initFormat(int nameLen, int nameFullWidthCharNum) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nameFullWidthCharNum; i++) {
            sb.append("　");
        }
        this.timeStatFmt = "%-" + nameLen + "s %-8s %-20s %-8s %-20s %-20s %-20s %-20s\n";
        this.tableHeader = String.format(timeStatFmt, sb.toString(), "times", "avg", "slow", "slow_avg", "max", "slow_span", "all_span");
        // 暂时只处理 times和avg字段的排序，以后有需要再加其它字段
        this.timeStatHtmlFmt = "<tr><td nowrap>%s</td><td nowrap>%s</td><td nowrap>%s</td><td nowrap>%s</td><td nowrap>%s</td><td nowrap>%s</td><td nowrap>%s</td><td nowrap>%s</td></tr>\n";
        this.htmlTableHeader = String.format(timeStatHtmlFmt, sb.toString(), "<a href=\"?timesOrder=1\">times</a>", "<a href=\"?avgOrder=1\">avg</a>", "slow", "slow_avg", "max", "slow_span",
            "all_span");
    }

    public void record(long end, long begin, Object arg) {
        if (begin <= 0 || end <= 0) {
            return;
        }
        all_num.incrementAndGet();
        long span = end - begin;
        all_span.addAndGet(span);
        if (span >= slowThreshold) {
            slow_num.incrementAndGet();
            slow_span.addAndGet(span);
            if (warn) {
                warn(end, begin, arg);
            }
        }
        if (span > max_span) {
            max_span = span; // 统计最近10分钟间隔内的最大慢时长
        }
    }

    public void record(long end, long begin, int count, Object arg) {
        if (begin <= 0 || end <= 0) {
            return;
        }
        all_num.addAndGet(count);
        long span = end - begin;
        all_span.addAndGet(span);
        if (span / count >= slowThreshold) {
            slow_num.addAndGet(count);
            slow_span.addAndGet(span);
            if (warn) {
                warn(end, begin, arg);
            }
        }
        if (span > max_span) {
            max_span = span;
        }
    }

    @Override
    public String toString() {
        return toString(timeStatFmt, name);
    }

    public String toHtmlString() {
        return toString(timeStatHtmlFmt, name);
    }

    public String toString(String first) {
        return toString(timeStatFmt, first);
    }

    public String toHtmlString(String first) {
        return toString(timeStatHtmlFmt, first);
    }

    public String toString(String format, String first) {
        long all_numTMP = all_num.get(); // 请求总次数
        long all_spanTMP = all_span.get(); // 请求总时长
        long slow_numTMP = slow_num.get(); // 慢的总个数
        long slow_spanTMP = slow_span.get(); // 慢的总时长
        long allAvg = all_numTMP > 0 ? all_spanTMP / all_numTMP : 0; // 请求平均时长
        long slowAvg = slow_numTMP > 0 ? slow_spanTMP / slow_numTMP : 0; // 慢的平均时长
        return String.format(format, first, all_numTMP > 0 ? all_numTMP : "", HumanReadableUtils.timeSpan(allAvg), slow_numTMP > 0 ? slow_numTMP : "", HumanReadableUtils.timeSpan(slowAvg),
            HumanReadableUtils.timeSpan(max_span), HumanReadableUtils.timeSpan(slow_spanTMP), HumanReadableUtils.timeSpan(all_spanTMP));
    }

    protected void warn(long end, long begin, Object arg) {
        log.error("SLOW_PROCESS:{}:{} [{}ms]\n", new Object[] {
            name,
            arg,
            end - begin
        });
    }

    public boolean isNeedReset() {
        return all_num.get() < 0 || all_span.get() < 0;
    }

    /**
     * 按次数由多到少排序
     */
    public static <T extends TimeSpanStat> void sort(List<T> list, HttpEvent he) {
        boolean avgOrder = he.getBool("avgOrder", false);

        // 默认按次数倒序排序
        Collections.sort(list, new Comparator<T>() {

            @Override
            public int compare(T o1, T o2) {
                long a1 = o1 == null ? 0 : o1.getAllNum();
                long a2 = o2 == null ? 0 : o2.getAllNum();
                return (int) (a2 - a1);
            }
        });
        if (avgOrder) {
            Collections.sort(list, new Comparator<T>() {

                @Override
                public int compare(T o1, T o2) {
                    long o1AllNum = o1 == null ? 0 : o1.getAllNum();
                    long o1AllSpan = o1 == null ? 0 : o1.getAllSpan();
                    long o2AllNum = o2 == null ? 0 : o2.getAllNum();
                    long o2AllSpan = o2 == null ? 0 : o2.getAllSpan();
                    return (int) ((o2AllNum == 0 ? 0 : o2AllSpan / o2AllNum) - (o1AllNum == 0 ? 0 : o1AllSpan / o1AllNum));
                }
            });
        }
    }
}
