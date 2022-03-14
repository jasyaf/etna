package org.etnaframework.core.util;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.plugin.monitor.SystemMonitor;
import com.sun.management.GcInfo;

/**
 * 系统信息
 *
 * @author BlackCat
 * @since 2013-12-11
 */
@SuppressWarnings("restriction")
public class SystemInfo {

    /** CPU核心个数 */
    public static final int CORE_PROCESSOR_NUM = Runtime.getRuntime()
                                                        .availableProcessors();

    /** 服务器进程的PID */
    public static final int PID;

    /** 系统主机名称 */
    public static final String HOSTNAME;

    /** 启动类完整名称加参数 */
    public static final String COMMAND_FULL;

    /** 启动类完整名称，不包括参数 */
    public static final String COMMAND;

    /** 服务器启动类类名的简称（不包含包名和Launch） */
    public static final String COMMAND_SHORT;

    /** 服务器启动类和机器hostname，可用于标记一个服务 */
    public static final String RUN_APP_NAME;

    /** 当前操作系统默认的换行符 */
    public static final String LINE_SEPARATOR = System.getProperty("line.separator", "\n");

    /** 当前操作系统类别 */
    public static OsEnum OS;

    /** 当前是否是以嵌入式模式启动的，如果为false表示是在容器中启动的 */
    public static boolean EMBEDDED_MODE = false;

    /** 判断当前是否在单元测试中，如果在测试中才允许修改某些参数，或者增加/去掉某些初始化动作 */
    public static boolean IN_TEST;

    /** 当前的运行环境 */
    public static final RunEnv RUN_ENV = RunEnv.valueOf(System.getProperty("RunEnv", RunEnv.dev.name()));

    static { // 启动时获取启动类等系统信息
        String command_full = "";
        String command_short = "";
        String command = "";
        String hostName = "UNKNOWN";
        int pid = -1;
        try {
            String sunjavacommand = System.getProperty("sun.java.command");
            if (StringTools.isNotEmpty(sunjavacommand)) {
                command_full = sunjavacommand;
                command = command_full.split(" ")[0];
                command_short = command.substring(command.lastIndexOf('.') + 1);
            }
            // 尽量去掉Launch让标题能更短一点
            command_short = !"Launch".equals(command_short) && command_short.endsWith("Launch") ? command_short.substring(0, command_short.length() - "Launch".length()) : command_short;

            String pidAtHostName = ManagementFactory.getRuntimeMXBean()
                                                    .getName();
            int idx = pidAtHostName.indexOf('@');
            if (idx > 0) {
                pid = StringTools.getInt(pidAtHostName.substring(0, idx), pid);
                hostName = pidAtHostName.substring(idx + 1);
            }
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        } finally {
            COMMAND_FULL = command_full;
            COMMAND = command;
            COMMAND_SHORT = command_short;
            PID = pid;
            HOSTNAME = hostName;
            RUN_APP_NAME = "[" + COMMAND_SHORT + "@" + HOSTNAME + "]";
            // 记录当前运行的操作系统
            String os = ManagementFactory.getOperatingSystemMXBean()
                                         .getName();
            if (null != os) {
                if (os.contains("Mac OS") || os.contains("macOS")) {
                    OS = OsEnum.MAC_OS;
                } else if (os.contains("Windows")) {
                    OS = OsEnum.WINDOWS;
                } else if (os.contains("Linux")) {
                    OS = OsEnum.LINUX;
                } else {
                    OS = OsEnum.OTHER;
                }
            }
            // 在eclipse中启动单元测试时其启动类就是下面这个，idea则无法区分是直接运行还是跑单元测试
            if ("org.eclipse.jdt.internal.junit.runner.RemoteTestRunner".equals(COMMAND)) {
                IN_TEST = true;
            } else {
                IN_TEST = false;
            }
        }
    }

    private SystemInfo() {
    }

    private static String _formatMemoryUsage(MemoryUsage mem) {
        String fmt = "初始化%-10s 已使用%-10s 最大%-10s";
        return StringTools.format(fmt, HumanReadableUtils.byteSize(mem.getInit()), HumanReadableUtils.byteSize(mem.getUsed()), HumanReadableUtils.byteSize(mem.getMax()));
    }

    private static String getContrastString(long before, long after) {
        long sub = after - before;
        if (sub > 0) {
            return " ╰→增加" + HumanReadableUtils.byteSize(sub);
        } else if (sub < 0) {
            return " ╰→减少" + HumanReadableUtils.byteSize(-sub);
        }
        return "";
    }

    public static String getSytemInfo() {
        StringBuilder tmp = new StringBuilder();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        tmp.append("RuntimeMXBean\n");
        tmp.append("=============\n");
        tmp.append("启动时间               ")
           .append(DatetimeUtils.format(ManagementFactory.getRuntimeMXBean()
                                                         .getStartTime()))
           .append("\n");
        tmp.append("已运行时间             ")
           .append(HumanReadableUtils.timeSpan(runtimeMXBean.getUptime()))
           .append("\n");
        tmp.append("进程                   ")
           .append(runtimeMXBean.getName())
           .append("\n");
        tmp.append("启动类                 ")
           .append(COMMAND)
           .append("\n");
        tmp.append("虚拟机                 ")
           .append(runtimeMXBean.getVmName())
           .append(" ")
           .append(runtimeMXBean.getVmVersion())
           .append("\n");
        tmp.append("支持的JVM版本          ")
           .append(runtimeMXBean.getSpecVersion())
           .append("\n");
        tmp.append("\n");

        OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        tmp.append("OperatingSystemMXBean\n");
        tmp.append("=====================\n");
        tmp.append("操作系统               ")
           .append(operatingSystemMXBean.getName())
           .append(" ")
           .append(operatingSystemMXBean.getVersion())
           .append("\n");
        tmp.append("体系结构               ")
           .append(operatingSystemMXBean.getArch())
           .append("\n");
        tmp.append("CPU核数                ")
           .append(operatingSystemMXBean.getAvailableProcessors())
           .append("\n");
        double load = operatingSystemMXBean.getSystemLoadAverage();
        tmp.append("系统负载               ")
           .append(load < 0 ? "不可用" : load)
           .append(SystemMonitor.isLoadAverageMonitorON() ? "（监控中）" : "（监控未开启）")
           .append("\n");
        tmp.append("\n");

        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        tmp.append("ThreadMXBean\n");
        tmp.append("============\n");
        tmp.append("当前线程数             ")
           .append(threadMXBean.getThreadCount())
           .append("\n");
        tmp.append("后台线程数             ")
           .append(threadMXBean.getDaemonThreadCount())
           .append("\n");
        tmp.append("峰值线程数             ")
           .append(threadMXBean.getPeakThreadCount())
           .append("\n");
        tmp.append("已启动线程数           ")
           .append(threadMXBean.getTotalStartedThreadCount())
           .append("\n");
        tmp.append("\n");

        tmp.append("ThreadPools\n");
        tmp.append(SystemMonitor.isThreadPoolMonitorON() ? "===监控中===\n" : "=监控未开启=\n");
        tmp.append(ThreadUtils.getExecutorInfo());
        tmp.append("\n");

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        tmp.append("\n");
        tmp.append("MemoryMXBean\n");
        tmp.append("============\n");
        tmp.append("已使用内存             ")
           .append(HumanReadableUtils.byteSize(Runtime.getRuntime()
                                                      .totalMemory() - Runtime.getRuntime()
                                                                              .freeMemory()))
           .append("\n");
        tmp.append("已分配内存             ")
           .append(HumanReadableUtils.byteSize(Runtime.getRuntime()
                                                      .totalMemory()))
           .append("\n");
        tmp.append("最大内存               ")
           .append(HumanReadableUtils.byteSize(Runtime.getRuntime()
                                                      .maxMemory()))
           .append("\n");
        tmp.append("堆内存                 ")
           .append(_formatMemoryUsage(memoryMXBean.getHeapMemoryUsage()))
           .append("\n");
        tmp.append("非堆内存               ")
           .append(_formatMemoryUsage(memoryMXBean.getNonHeapMemoryUsage()))
           .append("\n");
        tmp.append("待回收对象数           ")
           .append(memoryMXBean.getObjectPendingFinalizationCount())
           .append("\n");
        tmp.append("\n");

        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        tmp.append("GarbageCollectorMXBeans\n");
        tmp.append(SystemMonitor.isGcMonitorON() ? "=========监控中========\n" : "=======监控未开启======\n");
        String gcFmt = "%-22s %-15s %-15s %s\n";
        tmp.append(StringTools.format(gcFmt, "GC类型名", "总GC次数", "总GC耗时", "执行GC的内存区间"));
        Set<String> poolNames = new LinkedHashSet<String>();
        for (GarbageCollectorMXBean gc : gcs) {
            tmp.append(StringTools.format(gcFmt, gc.getName(), gc.getCollectionCount(), HumanReadableUtils.timeSpan(gc.getCollectionTime()), Arrays.toString(gc.getMemoryPoolNames())));
            for (String n : gc.getMemoryPoolNames()) {
                poolNames.add(n);
            }
        }
        tmp.append("\n");
        gcFmt = "%-22s %-15s %-15s %-20s %s\n";
        long serverStartTime = ManagementFactory.getRuntimeMXBean()
                                                .getStartTime();
        tmp.append(StringTools.format(gcFmt, "上一次GC的情况", "ID", "开始时间", "结束时间", "耗时"));
        for (GarbageCollectorMXBean gc : gcs) {
            if (gc instanceof com.sun.management.GarbageCollectorMXBean) {
                com.sun.management.GarbageCollectorMXBean g = (com.sun.management.GarbageCollectorMXBean) gc;
                GcInfo gi = g.getLastGcInfo();
                if (gi == null || gi.getStartTime() == 0) {
                    continue;
                }
                String start = DatetimeUtils.format(serverStartTime + gi.getStartTime(), Datetime.DF_HH_mm_ss_S);
                String end = DatetimeUtils.format(serverStartTime + gi.getEndTime(), Datetime.DF_HH_mm_ss_S);
                tmp.append(StringTools.format(gcFmt, "  " + gc.getName(), gi.getId(), start, end, HumanReadableUtils.timeSpan(gi.getDuration())));
                Map<String, MemoryUsage> before = gi.getMemoryUsageBeforeGc();
                Map<String, MemoryUsage> after = gi.getMemoryUsageAfterGc();
                String muFmt = "%-22s %-31s %-20s %-20s %-20s %-20s\n";
                tmp.append(StringTools.format(muFmt, "", "内存区间", "初始化", "已使用", "已分配", "最大可分配"));
                for (String name : poolNames) {
                    MemoryUsage bmu = before.get(name);
                    MemoryUsage amu = after.get(name);
                    if (bmu == null) {
                        continue;
                    }
                    tmp.append(
                        StringTools.format(muFmt, "", name, HumanReadableUtils.byteSize(bmu.getInit()), HumanReadableUtils.byteSize(bmu.getUsed()), HumanReadableUtils.byteSize(bmu.getCommitted()),
                            HumanReadableUtils.byteSize(bmu.getMax())));
                    String init = getContrastString(bmu.getInit(), amu.getInit());
                    String used = getContrastString(bmu.getUsed(), amu.getUsed());
                    String committted = getContrastString(bmu.getCommitted(), amu.getCommitted());
                    String max = getContrastString(bmu.getMax(), amu.getMax());
                    tmp.append(StringTools.format(muFmt, "", "   ╰→After GC", init, used, committted, max));
                }
            }
        }
        return tmp.toString();
    }

    /**
     * 操作系统类型
     */
    public enum OsEnum {
        WINDOWS,
        MAC_OS,
        LINUX,
        OTHER
    }

    /**
     * 运行环境区分
     */
    public enum RunEnv {

        /** 开发环境 */
        dev,

        /** 测试环境 */
        test,

        /** 生产环境 */
        release
    }
}
