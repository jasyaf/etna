package org.etnaframework.core.util;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.etnaframework.core.web.DispatchFilter;
import org.etnaframework.plugin.monitor.SystemMonitor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import com.google.common.collect.Maps;

/**
 * 线程相关的工具类，业务代码请尽量都使用此处的线程池，提高线程利用率
 *
 * @author BlackCat
 * @since 2015-03-24
 */
@Service
public final class ThreadUtils {

    /** 用于获取系统线程相关状态信息 */
    public static final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    // 以下是线程池状态的输出格式

    private static final String executorStatFmt = "%-22s %-13s %-12s %-16s %-12s %-12s %-18s %-16s %-16s %-12s\n";

    private static final String executorStatHeader = String.format(executorStatFmt, "name", "activeCount", "poolSize", "maximumPoolSize", "queueSize", "taskCount", "completedTaskCount",
        "corePoolSize", "keepAliveTime", "coreTimeOut");

    // 以下是etna框架的基础线程池，在业务代码中建议使用，提高线程利用率

    /** 各类监控服务使用，可定时调用 */
    private static ScheduledExecutorService watchdog = new ScheduledThreadPoolExecutor(SystemInfo.CORE_PROCESSOR_NUM, new NamedThreadFactory("Watchdog(Sche)", Thread.MAX_PRIORITY, true));

    /** 用于业务代码中的定时任务 */
    private static ScheduledExecutorService cron = Executors.newScheduledThreadPool(SystemInfo.CORE_PROCESSOR_NUM, new NamedThreadFactory("Cron(Sche)", Thread.NORM_PRIORITY, true));

    /** 默认的业务线程池，使用嵌入式方式启动会复用jetty的线程池，容器内启动则会创建一个线程池。如果需要执行优先级较低、可部分丢弃的任务，建议使用background线程池 */
    private static Executor defaultThreadPool;

    /** 纳入统计的线程池列表 */
    private static List<Object> threadPoolsForStat = CollectionTools.buildList(watchdog, cron, defaultThreadPool);

    /** 纳入监控的线程池列表 */
    private static List<Object> threadPoolsForMonitor = CollectionTools.buildList(defaultThreadPool);

    static {
        // 默认先创建一个线程池作为主线程池
        setDefaultThreadPool(Executors.newCachedThreadPool(new NamedThreadFactory("Default", Thread.NORM_PRIORITY)));
    }

    private ThreadUtils() {
    }

    /**
     * 休眠指定的毫秒数，内部将异常处理了不会对外抛出
     *
     * @return 如果有抛出异常返回false
     */
    public static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e1) {
            return false;
        }
    }

    /**
     * 休眠指定的时间，单位可自行执行，内部将异常处理了不会对外抛出
     *
     * @return 如果有抛出异常返回false
     */
    public static boolean sleep(long sleepTime, TimeUnit unit) {
        return sleep(unit.toMillis(sleepTime));
    }

    /**
     * 获取watchdog线程池，供各类监控服务使用
     */
    public static ScheduledExecutorService getWatchdog() {
        return watchdog;
    }

    /**
     * 获取cron线程池，用于业务代码中的定时任务使用
     */
    public static ScheduledExecutorService getCron() {
        return cron;
    }

    /**
     * 获取默认的业务线程池，额外启动的服务可共享使用，避免开过多的线程耗费CPU资源
     * 注意，如果想要后台运行的任务纳入异常监控机制，请使用{@link BackgroundTask}来执行任务
     */
    public static Executor getDefault() {
        return defaultThreadPool;
    }

    /**
     * 设置默认的业务线程池
     */
    public static void setDefaultThreadPool(Executor defaultThreadPool) {
        if (null != defaultThreadPool) {
            // 删除原来的位置，增加新的默认线程池
            if (null != ThreadUtils.defaultThreadPool) {
                Iterator<Object> it = threadPoolsForStat.iterator();
                while (it.hasNext()) {
                    if (ThreadUtils.defaultThreadPool.equals(it.next())) {
                        it.remove();
                    }
                }
                it = threadPoolsForMonitor.iterator();
                while (it.hasNext()) {
                    if (ThreadUtils.defaultThreadPool.equals(it.next())) {
                        it.remove();
                    }
                }

                // 更换了默认线程池，需要把原先的shutdown
                if (ThreadUtils.defaultThreadPool instanceof ExecutorService) {
                    ExecutorService pool = (ExecutorService) ThreadUtils.defaultThreadPool;
                    pool.shutdown();
                }
            }
            ThreadUtils.defaultThreadPool = defaultThreadPool;
            threadPoolsForStat.add(defaultThreadPool);
            threadPoolsForMonitor.add(defaultThreadPool);
        }
    }

    /**
     * 将一个线程池纳入到 统计&监控
     */
    public static void addThreadPool(BackgroundTaskExecutor threadPool) {
        synchronized (ThreadUtils.class) {
            threadPoolsForStat.add(threadPool);
            threadPoolsForMonitor.add(threadPool);
        }
    }

    /**
     * 将一个线程池纳入到 统计&监控
     */
    public static void addThreadPool(OrderedTaskExecutor threadPool) {
        synchronized (ThreadUtils.class) {
            threadPoolsForStat.add(threadPool);
            threadPoolsForMonitor.add(threadPool);
        }
    }

    /**
     * 将一个线程池纳入到 统计&监控
     */
    public static void addThreadPool(Executor threadPool) {
        synchronized (ThreadUtils.class) {
            threadPoolsForStat.add(threadPool);
            threadPoolsForMonitor.add(threadPool);
        }
    }

    /**
     * 获取所有受监控的线程池
     */
    public static List<Object> getThreadPoolsForMonitor() {
        synchronized (ThreadUtils.class) {
            return threadPoolsForMonitor;
        }
    }

    /**
     * 输出所有的线程池状态信息
     */
    public static String getExecutorInfo() {
        StringBuilder tmp = new StringBuilder();
        tmp.append(executorStatHeader);
        for (Object e : threadPoolsForStat) {
            if (e != null) {
                if (e instanceof ThreadPoolExecutor) {
                    ThreadPoolExecutor ex = (ThreadPoolExecutor) e;
                    tmp.append(String.format(executorStatFmt, ex.getThreadFactory()
                                                                .toString(), ex.getActiveCount(), ex.getPoolSize(), ex.getMaximumPoolSize(), ex.getQueue()
                                                                                                                                               .size(), ex.getTaskCount(),
                        ex.getCompletedTaskCount(), ex.getCorePoolSize(), HumanReadableUtils.timeSpan(ex.getKeepAliveTime(TimeUnit.MILLISECONDS)), ex.allowsCoreThreadTimeOut()));
                } else if (e instanceof QueuedThreadPool) {
                    QueuedThreadPool ex = (QueuedThreadPool) e;
                    int poolSize = ex.getThreads();
                    int activeCount = poolSize - ex.getIdleThreads();
                    int max = ex.getMaxThreads();
                    tmp.append(String.format(executorStatFmt, ex.getName(), activeCount, poolSize, max, "", "", "", "", HumanReadableUtils.timeSpan(ex.getIdleTimeout()), ""));
                } else if (e instanceof ExecutorThreadPool) {
                    ExecutorThreadPool ex = (ExecutorThreadPool) e;
                    int poolSize = ex.getThreads();
                    int activeCount = poolSize - ex.getIdleThreads();
                    tmp.append(String.format(executorStatFmt, e.getClass()
                                                               .getSimpleName(), activeCount, poolSize, "", "", "", "", "", "", ""));
                } else if (e instanceof BackgroundTaskExecutor) { // 后台线程池，跟ThreadPoolExecutor一样处理即可
                    ThreadPoolExecutor ex = ((BackgroundTaskExecutor) e).executor;
                    tmp.append(String.format(executorStatFmt, ex.getThreadFactory()
                                                                .toString(), ex.getActiveCount(), ex.getPoolSize(), ex.getMaximumPoolSize(), ex.getQueue()
                                                                                                                                               .size(), ex.getTaskCount(),
                        ex.getCompletedTaskCount(), ex.getCorePoolSize(), HumanReadableUtils.timeSpan(ex.getKeepAliveTime(TimeUnit.MILLISECONDS)), ex.allowsCoreThreadTimeOut()));
                } else if (e instanceof OrderedTaskExecutor) { // 将各线程的时间累加起来计算
                    ThreadPoolExecutor[] exs = ((OrderedTaskExecutor) e).executors;
                    int activeCount = 0;
                    int poolSize = 0;
                    int maximumPoolSize = 0;
                    int queueSize = 0;
                    int taskCount = 0;
                    int completedTaskCount = 0;
                    int corePoolSize = 0;
                    long keepAliveTime = 0;
                    for (ThreadPoolExecutor ex : exs) {
                        activeCount += ex.getActiveCount();
                        poolSize += ex.getPoolSize();
                        queueSize += ex.getQueue()
                                       .size();
                        taskCount += ex.getTaskCount();
                        completedTaskCount += ex.getCompletedTaskCount();
                        corePoolSize += ex.getCorePoolSize();
                        maximumPoolSize += ex.getMaximumPoolSize();
                        keepAliveTime += ex.getKeepAliveTime(TimeUnit.MILLISECONDS);
                    }
                    tmp.append(String.format(executorStatFmt, exs[0].getThreadFactory()
                                                                    .toString(), activeCount, poolSize, maximumPoolSize, queueSize, taskCount, completedTaskCount, corePoolSize,
                        HumanReadableUtils.timeSpan(keepAliveTime), ""));
                } else { // 未支持的线程池，什么都显示不了
                    tmp.append(String.format(executorStatFmt, e.getClass()
                                                               .getSimpleName(), "", "", "", "", "", "", "", "", "", ""));
                }
            }
        }
        return tmp.toString();
    }

    /**
     * 输出系统线程状态信息，支持按条件筛选
     */
    public static String getThreadsDetailInfo(String name, boolean onlyRunning, int maxFrames) {
        boolean nameFilter = StringTools.isNotEmpty(name);
        ThreadInfo[] infos;
        synchronized (threadMXBean) { // 如果并发执行会导致崩溃
            infos = threadMXBean.dumpAllThreads(true, true);
        }
        boolean cpuTimeEnabled = false;
        try {
            cpuTimeEnabled = threadMXBean.isThreadCpuTimeEnabled();// 测试是否启用了线程 CPU 时间测量。
        } catch (UnsupportedOperationException e) {
        }
        boolean cpuTimeSupported = threadMXBean.isThreadCpuTimeSupported();// 测试 Java 虚拟机实现是否支持任何线程的 CPU 时间测量。支持任何线程 CPU 时间测定的 Java 虚拟机实现也支持当前线程的 CPU 时间测定。
        StringBuilder tmp = new StringBuilder();
        List<ThreadInfo> list = Arrays.asList(infos);

        String fmt = "%-8s%-8s%-14s%-8s%-8s%-20s%s\n";
        if (cpuTimeSupported) {
            // Bugfix @2015-09-23 by yuanhaoliang
            // 由于threadMXBean.getThreadCpuTime()返回是动态的，导致排序的时候，A>B,B>C,有可能出现A<C，于是排序出错。
            // threadId -> threadCpuTime
            Map<Long, Long> threadCpuTimeMap = Maps.newHashMap();
            for (ThreadInfo threadInfo : list) {
                threadCpuTimeMap.put(threadInfo.getThreadId(), threadMXBean.getThreadCpuTime(threadInfo.getThreadId()));
            }
            Comparator<ThreadInfo> allCpuTimeComparator = (o1, o2) -> {
                long id1 = o1.getThreadId();
                long id2 = o2.getThreadId();
                int r = Long.compare(threadCpuTimeMap.get(id2), threadCpuTimeMap.get(id1));
                return r == 0 ? Long.compare(id2, id1) : r;
            };
            Collections.sort(list, allCpuTimeComparator);
            tmp.append(String.format(fmt, "RANK", "ID", "STATE ", "Blocks", "Waits", "CpuTime↓", "Name"));
        } else {
            tmp.append(String.format(fmt, "RANK", "ID↑", "STATE ", "Blocks", "Waits", "CpuTime", "Name"));
        }
        int rank = 1;
        for (ThreadInfo info : list) {
            if (_isFilted(info, nameFilter, name, onlyRunning)) {
                continue;
            }
            long id = info.getThreadId();
            long cupTime = cpuTimeEnabled ? threadMXBean.getThreadCpuTime(id) : -1;
            tmp.append(String.format(fmt, rank++, id, info.getThreadState(), info.getBlockedCount(), info.getWaitedCount(), HumanReadableUtils.timeSpan(cupTime / 1000), info.getThreadName()));
        }
        tmp.append("cpuTimeSupported:")
           .append(cpuTimeSupported)
           .append("\tcpuTimeEnabled:")
           .append(cpuTimeEnabled)
           .append("\n");
        tmp.append("\n");
        tmp.append("\n");
        int i = -1;
        for (ThreadInfo info : list) {
            i++;
            if (_isFilted(info, nameFilter, name, onlyRunning)) {
                continue;
            }
            tmp.append("--------------- (ID = ");
            tmp.append(i);
            tmp.append(") ------------------------------------------------------------------------------------------------------------------------\n");
            tmp.append(printThreadInfo(info, maxFrames));
        }
        return tmp.toString();
    }

    /**
     * 线程过滤，进行条件筛选
     */
    private static boolean _isFilted(ThreadInfo info, boolean nameFilter, String name, boolean onlyRunnable) {
        if (nameFilter) {
            if (!info.getThreadName()
                     .toLowerCase()
                     .contains(name.toLowerCase())) {
                return true;
            }
        }
        if (onlyRunnable) {
            if (Thread.State.RUNNABLE != info.getThreadState() && Thread.State.BLOCKED != info.getThreadState()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 打印线程信息，抄自threadInfo.toString()
     */
    public static String printThreadInfo(ThreadInfo info, int maxFrames) {
        StringBuilder sb = new StringBuilder("\"" + info.getThreadName() + "\"" + " Id=" + info.getThreadId() + " " + info.getThreadState());
        if (info.getLockName() != null) {
            sb.append(" on " + info.getLockName());
        }
        if (info.getLockOwnerName() != null) {
            sb.append(" owned by \"" + info.getLockOwnerName() + "\" Id=" + info.getLockOwnerId());
        }
        if (info.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (info.isInNative()) {
            sb.append(" (in native)");
        }
        sb.append('\n');
        int i = 0;
        StackTraceElement[] stackTrace = info.getStackTrace();
        for (; i < stackTrace.length && i < maxFrames; i++) {
            StackTraceElement ste = stackTrace[i];
            sb.append("\tat " + ste.toString());
            sb.append('\n');
            if (i == 0 && info.getLockInfo() != null) {
                Thread.State ts = info.getThreadState();
                switch (ts) {
                case BLOCKED:
                    sb.append("\t-  blocked on " + info.getLockInfo());
                    sb.append('\n');
                    break;
                case WAITING:
                    sb.append("\t-  waiting on " + info.getLockInfo());
                    sb.append('\n');
                    break;
                case TIMED_WAITING:
                    sb.append("\t-  waiting on " + info.getLockInfo());
                    sb.append('\n');
                    break;
                default:
                    break;
                }
            }
            for (MonitorInfo mi : info.getLockedMonitors()) {
                if (mi.getLockedStackDepth() == i) {
                    sb.append("\t-  locked " + mi);
                    sb.append('\n');
                }
            }
        }
        if (i < stackTrace.length) {
            sb.append("\t...");
            sb.append('\n');
        }
        LockInfo[] locks = info.getLockedSynchronizers();
        if (locks.length > 0) {
            sb.append("\n\tNumber of locked synchronizers = " + locks.length);
            sb.append('\n');
            for (LockInfo li : locks) {
                sb.append("\t- " + li);
                sb.append('\n');
            }
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * 一次提交多个任务，并行执行，当所有任务都执行完后再进行下一步，如果有异常则会抛出中断
     */
    public static void multi(MultiJob... pj) {
        // 由于latch.await();会处于线程休眠状态,即使cdl减到0,也不会及时唤醒,提高此线程的优先级有助于尽快往下走.
        Thread.currentThread()
              .setPriority(Thread.MAX_PRIORITY);
        if (pj.length > 0) {
            CountDownLatch latch = new CountDownLatch(pj.length);
            for (MultiJob j : pj) {
                j.latch = latch;
                getDefault().execute(j);
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            for (MultiJob j : pj) {
                if (null != j.throwable) {
                    throw new RuntimeException(j.throwable);
                }
            }
        }
        Thread.currentThread()
              .setPriority(Thread.NORM_PRIORITY);
    }

    /**
     * 带名称的线程工厂类，提供创建线程的功能
     */
    public static class NamedThreadFactory implements ThreadFactory {

        /** 线程组 */
        protected final ThreadGroup group;

        /** 保证原子操作的整数 */
        protected final AtomicInteger threadNumber = new AtomicInteger(1);

        /** 名字前缀 */
        protected final String namePrefix;

        /** 默认优先级 */
        protected int priority = Thread.NORM_PRIORITY;

        /** 是否为守护线程 */
        protected boolean daemon = false;

        public NamedThreadFactory(String namePrefix, int priority, boolean daemon) {
            this(namePrefix);
            this.daemon = daemon;
            this.priority = priority;
        }

        public NamedThreadFactory(String namePrefix, int priority) {
            this(namePrefix);
            this.priority = priority;
        }

        public NamedThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread()
                                                             .getThreadGroup();
            this.namePrefix = namePrefix;
        }

        /**
         * 创建一个新的线程
         */
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            t.setDaemon(daemon);
            t.setPriority(priority);
            return t;
        }

        public String getNamePrefix() {
            return namePrefix;
        }

        @Override
        public String toString() {
            return getNamePrefix();
        }
    }

    /**
     * 后台线程池任务，将MDC中的用户信息等也带上了，在日志中会记录是谁的操作触发的，方便回溯问题
     */
    public static abstract class BackgroundTask implements Runnable {

        private Map<String, String> mdc = MDC.getCopyOfContextMap(); // 由初始化任务的线程将这个信息写入

        @Override
        public final void run() {
            try {
                if (null != mdc) {
                    MDC.setContextMap(mdc);
                }
                process();
            } catch (Throwable ex) {
                // 执行过程中抛异常会发邮件报警
                String title = ex.getClass()
                                 .getSimpleName() + ":BackgroundTask";
                DispatchFilter.sendMail(title, ex);
            } finally {
                if (null != mdc) {
                    MDC.clear(); // 清除当前线程中记录的TAG
                }
            }
        }

        protected abstract void process() throws Throwable;
    }

    /**
     * 后台任务（不保证执行顺序）执行器
     */
    public static class BackgroundTaskExecutor {

        private String name;

        private ThreadPoolExecutor executor;

        /** 当排队的任务数达到多少时，启动报警并不再接受新的任务，防止内存溢出，如果为0表示不启用限制，注意要开启了线程池监控组件{@link SystemMonitor}才能起到限制作用 */
        private int queueLimit = 0;

        /** 当前线程池是否爆了，由{@link SystemMonitor}扫描启动了保护 */
        private volatile boolean taskExceed = false;

        public BackgroundTaskExecutor(String name, int num) {
            this.name = name;
            this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(num, new NamedThreadFactory(name, Thread.MIN_PRIORITY));
        }

        public BackgroundTaskExecutor(String name, int num, int queueLimit) {
            this(name, num);
            this.queueLimit = queueLimit;
        }

        public void execute(BackgroundTask task) {
            // 当等待执行的后台任务数达到上限时，停止接收新任务，防止堆积任务过多爆内存，起保护作用
            if (taskExceed) {
                return;
            }
            executor.execute(task);
        }

        public String getName() {
            return name;
        }

        public int getQueueLimit() {
            return queueLimit;
        }

        public void setTaskExceed(boolean taskExceed) {
            this.taskExceed = taskExceed;
        }

        /**
         * 获取等待执行的后台任务数
         */
        public int getQueueSize() {
            return executor.getQueue()
                           .size();
        }
    }

    /**
     * 后台任务（可根据指定的关键对象，按提交顺序执行）执行器
     */
    public static class OrderedTaskExecutor {

        private String name;

        private ThreadPoolExecutor[] executors;

        /** 当排队的任务数达到多少时，启动报警并不再接受新的任务，防止内存溢出，如果为0表示不启用限制，注意要开启了线程池监控组件{@link SystemMonitor}才能起到限制作用 */
        private int queueLimit = 0;

        /** 当前线程池是否爆了，由{@link SystemMonitor}扫描启动了保护 */
        private volatile boolean taskExceed = false;

        public OrderedTaskExecutor(String name, int num) {
            this.name = name;
            this.executors = new ThreadPoolExecutor[num];
            for (int i = 0; i < num; i++) {
                this.executors[i] = (ThreadPoolExecutor) Executors.newFixedThreadPool(1, new NamedThreadFactory(name, Thread.NORM_PRIORITY));
            }
        }

        public OrderedTaskExecutor(String name, int num, int queueLimit) {
            this(name, num);
            this.queueLimit = queueLimit;
        }

        public void execute(Object key, BackgroundTask task) {
            // 当等待执行的后台任务数达到上限时，停止接收新任务，防止堆积任务过多爆内存，起保护作用
            if (taskExceed) {
                return;
            }
            // 根据key对象确定是由哪个固定的线程执行任务
            int k = Math.abs(Objects.hashCode(key)) % executors.length;
            executors[k].execute(task);
        }

        public String getName() {
            return name;
        }

        public int getQueueLimit() {
            return queueLimit;
        }

        public void setTaskExceed(boolean taskExceed) {
            this.taskExceed = taskExceed;
        }

        /**
         * 获取等待执行的后台任务数
         */
        public int getQueueSize() {
            int count = 0;
            for (ThreadPoolExecutor executor : executors) {
                count += executor.getQueue()
                                 .size();
            }
            return count;
        }
    }

    public static abstract class MultiJob implements Runnable {

        private CountDownLatch latch;

        private Throwable throwable;

        public abstract void process() throws Throwable;

        @Override
        public final void run() {
            try {
                process();
            } catch (Throwable t) {
                throwable = t;
            } finally {
                if (null != latch) {
                    latch.countDown();
                }
            }
        }
    }
}
