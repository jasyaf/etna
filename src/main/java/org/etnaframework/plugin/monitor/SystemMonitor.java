package org.etnaframework.plugin.monitor;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.spring.annotation.Config;
import org.etnaframework.core.spring.annotation.OnContextInited;
import org.etnaframework.core.util.CollectionTools;
import org.etnaframework.core.util.CommandService;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.util.SystemInfo;
import org.etnaframework.core.util.SystemInfo.OsEnum;
import org.etnaframework.core.util.ThreadUtils;
import org.etnaframework.core.util.ThreadUtils.BackgroundTaskExecutor;
import org.etnaframework.core.util.ThreadUtils.OrderedTaskExecutor;
import org.etnaframework.core.web.DispatchFilter;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

/**
 * 监控服务器状态的任务，监控项有系统负载、GC、业务线程池等
 *
 * @author BlackCat
 * @since 2015-03-23
 */
@Service
public final class SystemMonitor {

    private static final Logger log = Log.getLogger();

    /** 钉钉群机器人告警，默认为空不启用，只有配置了才生效 */
    @Config("etna.monitor.dingTalkRobotUrl")
    private static String dingTalkRobotUrl;

    /** 系统负载监控间隔秒数，默认1分钟监控一次（因为是平均负载），<=0表示不监控 */
    @Config(value = "etna.monitor.loadMonitorCheckIntervalSec", resetable = false)
    private static int loadMonitorCheckIntervalSec = 60;

    /** 系统负载报警阀值界定，默认是CPU核数的0.7，为了防止太敏感限定最小值是4，可参考http://blog.csdn.net/marising/article/details/5182771 */
    @Config("etna.monitor.loadMonitorWarnThreshold")
    private static double loadMonitorWarnThreshold = Math.max(SystemInfo.CORE_PROCESSOR_NUM * 0.7, 4);

    /** 系统负载退化阀值，默认是CPU核心数，当系统负载超过该值时，{@link SystemMonitor#isLoadAverageHigh()}将会返回true，业务代码中可据此判断做退化处理 */
    @Config("etna.monitor.loadMonitorErrorThreshold")
    private static double loadMonitorErrorThreshold = Math.max(SystemInfo.CORE_PROCESSOR_NUM, 6);

    /** 如果系统负载高（>loadMonitorErrorThreshold），此状态为true，否则为false，可以根据此进行一些功能退化处理，防止服务器挂了 */
    private static volatile boolean loadAverageHigh = false;

    /** 系统负载监控是否开启 */
    private static boolean loadAverageMonitorON;

    /** GC监控检查时间间隔，<=0表示不监控 */
    @Config(value = "etna.monitor.gcMonitorCheckIntervalSec", resetable = false)
    private static int gcMonitorCheckIntervalSec = 5 * 60;

    /** GC监控是否开启 */
    private static boolean gcMonitorON;

    /** 线程池监控检查时间间隔，单位秒，<=0表示不监控 */
    @Config(value = "etna.monitor.threadPoolMonitorCheckIntervalSec", resetable = false)
    private static int threadPoolMonitorCheckIntervalSec = 1;

    /** 线程池监控是否开启 */
    private static boolean threadPoolMonitorON;

    /** 判断线程池是否快要满了，系统可根据此决定是否要做退化处理 */
    private static volatile boolean denialOfServiceAlmost;

    private SystemMonitor() {
    }

    /**
     * 获取当前的钉钉群机器人配置
     */
    public static String getDingTalkRobotUrl() {
        return dingTalkRobotUrl;
    }

    /**
     * 获取当前机器负载
     */
    public static double getLoadAverage() {
        return ManagementFactory.getOperatingSystemMXBean()
                                .getSystemLoadAverage();
    }

    /**
     * 如果系统负载高（>loadMonitorErrorThreshold），此状态为true，否则为false，可以根据此进行一些功能退化处理，防止服务器挂了
     */
    public static boolean isLoadAverageHigh() {
        return loadAverageHigh;
    }

    /**
     * 系统负载监控是否开启
     */
    public static boolean isLoadAverageMonitorON() {
        return loadAverageMonitorON;
    }

    /**
     * 系统负载监控定时任务
     */
    @OnContextInited
    protected static void initLoadAverageMonitor() {
        final OperatingSystemMXBean mx = ManagementFactory.getOperatingSystemMXBean();
        if (mx.getSystemLoadAverage() < 0) {
            log.info("LoadAverageMonitor OFF, jvm can't stat loadaverage on: {}/arch:{}", mx.getName(), mx.getArch());
            return;
        }

        if (loadMonitorCheckIntervalSec <= 0) {
            log.info("LoadAverageMonitor OFF, loadMonitorCheckIntervalSec <= 0 , loadMonitorCheckIntervalSec: {}, System: {}/arch:{}", loadMonitorCheckIntervalSec, mx.getName(), mx.getArch());
            return;
        }
        // macOS本机调试，不需要报告负载高
        if (OsEnum.MAC_OS.equals(SystemInfo.OS)) {
            log.info("LoadAverageMonitor OFF under macOS");
            return;
        }
        log.info("LoadAverageMonitor ON, interval:{}sec, warnThreshold:{}, errorThreshold:{}", new Object[] {
            loadMonitorCheckIntervalSec,
            loadMonitorWarnThreshold,
            loadMonitorErrorThreshold
        });
        loadAverageMonitorON = true;
        ThreadUtils.getWatchdog()
                   .scheduleWithFixedDelay(new Runnable() {

                       @Override
                       public void run() {
                           try {
                               double load = mx.getSystemLoadAverage();
                               log.info("LOAD AVERAGE:{}", load);
                               if (load > loadMonitorWarnThreshold) {
                                   // 获取系统当前的进程状态，找出是什么原因导致系统负载高
                                   StringBuilder loadCurrentStatus = new StringBuilder();
                                   // ubuntu上的top会默认按80个字符的宽度输出，这样基本看不到command，但-w参数又不能被其他系统支持，故设置export COLUMNS=500;规避
                                   CommandService cs = new CommandService("export COLUMNS=500; top -b -c -n 1|grep -Fv \"[\"");
                                   CommandService cs3 = new CommandService("iostat -xk");
                                   try {
                                       cs.execute();
                                       cs3.execute();
                                       loadCurrentStatus.append(cs.getProcessingDetail()
                                                                  .toString());
                                       loadCurrentStatus.append("\n");
                                       loadCurrentStatus.append(cs3.getProcessingDetail()
                                                                   .toString());
                                       loadCurrentStatus.append("\n");
                                       loadCurrentStatus.append("rrqm/s: The number of read requests merged per second that were queued to the device.\n");
                                       loadCurrentStatus.append("wrqm/s: The number of write requests merged per second that were queued to the device.\n");
                                       loadCurrentStatus.append("r/s:    The number of read requests that were issued to the device per second.\n");
                                       loadCurrentStatus.append("w/s:    The number of write requests that were issued to the device per second.\n");
                                       loadCurrentStatus.append("rkB/s:  The number of kilobytes read from the device per second.\n");
                                       loadCurrentStatus.append("wkB/s:  The number of kilobytes written to the device per second.\n");
                                       loadCurrentStatus.append("\n");
                                   } catch (Throwable t) {
                                       loadCurrentStatus.append(StringTools.printThrowable(t));
                                   }
                                   DispatchFilter.sendMail("LOAD AVERAGE:" + load, loadCurrentStatus);
                               }
                               if (load > loadMonitorErrorThreshold) {
                                   loadAverageHigh = true;
                               } else {
                                   loadAverageHigh = false;
                               }
                           } catch (Throwable e) {
                               log.error("", e);
                           }
                       }
                   }, loadMonitorCheckIntervalSec, loadMonitorCheckIntervalSec, TimeUnit.SECONDS);
    }

    /**
     * GC监控是否开启
     */
    public static boolean isGcMonitorON() {
        return gcMonitorON;
    }

    /**
     * GC监控定时任务
     */
    @OnContextInited
    protected static void initGarbageCollectMonitor() {
        if (gcMonitorCheckIntervalSec <= 0) {
            log.info("GarbageCollectMonitor OFF");
            return;
        }
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        if (gcs.size() == 2) { // 目前只能处理默认的GC情况
            final GarbageCollectorMXBean ygc = gcs.get(0);
            final GarbageCollectorMXBean fgc = gcs.get(1);
            if (ygc.getMemoryPoolNames().length < fgc.getMemoryPoolNames().length) {
                log.info("GarbageCollectMonitor ON, interval:{}sec, YongGC:{}, FullGC:{}", new Object[] {
                    gcMonitorCheckIntervalSec,
                    ygc.getName(),
                    fgc.getName()
                });
                gcMonitorON = true;
                ThreadUtils.getWatchdog()
                           .scheduleWithFixedDelay(new Runnable() {

                               private long last; // 记录上次的FullGC次数，用于过滤重复发报警的情况

                               private long lastGap = 0; // 上次的FullGC和YoungGC的时间差

                               private long fullAlarmBegin = 60000; // FullGc 超过60s才开始报警

                               @Override
                               public void run() {
                                   try {
                                       long young = ygc.getCollectionTime();
                                       long full = fgc.getCollectionTime();
                                       long thisFull = fgc.getCollectionCount();
                                       // 如果FullGC所花时间比YongGC时间还高，则要报警，报警之后，如果时差进一步扩大就继续报
                                       if (last != thisFull && full > fullAlarmBegin && full - young > lastGap) {
                                           String detail = SystemInfo.getSytemInfo();
                                           DispatchFilter.sendMail("GC WARNING", detail);
                                           last = thisFull;
                                           lastGap = full - young;
                                       }
                                   } catch (Throwable e) {
                                       log.info("", e);
                                   }
                               }
                           }, gcMonitorCheckIntervalSec, gcMonitorCheckIntervalSec, TimeUnit.SECONDS);
            } else {
                log.info("GarbageCollectMonitor OFF, YongGc.memoryPool:{} < FullGC.memroyPool:{}", Arrays.toString(ygc.getMemoryPoolNames()), Arrays.toString(fgc.getMemoryPoolNames()));
            }
        } else {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (GarbageCollectorMXBean gc : gcs) {
                sb.append(i++)
                  .append(":")
                  .append(gc.getName())
                  .append(";");
            }
            log.info("GarbageCollectMonitor OFF, GarbageCollectorMXBeans.size!=2:{}", sb);
        }
    }

    /**
     * 线程池监控是否开启
     */
    public static boolean isThreadPoolMonitorON() {
        return threadPoolMonitorON;
    }

    /**
     * 判断线程池是否快要满了，系统可根据此决定是否要做退化处理
     */
    public static boolean isDenialOfServiceAlmost() {
        return denialOfServiceAlmost;
    }

    /**
     * 线程池状态监控
     */
    @OnContextInited
    protected static void initThreadPoolMonitor() {
        if (CollectionTools.isEmpty(ThreadUtils.getThreadPoolsForMonitor())) {
            log.info("ThreadPoolMonitor OFF, no threadPool");
            return;
        }
        if (threadPoolMonitorCheckIntervalSec <= 0) {
            log.info("ThreadPoolMonitor OFF");
            return;
        }
        log.info("ThreadPoolMonitor ON, interval:{}sec", threadPoolMonitorCheckIntervalSec);
        threadPoolMonitorON = true;
        ThreadUtils.getWatchdog()
                   .scheduleWithFixedDelay(new Runnable() {

                       /** 防止报告错误报太多（没有必要，已经达到通知目的了），通过记录上一次报告的时间，限制频率 */
                       private long last;

                       private void check(Object e) {
                           // 注意，由于在ThreadUtils上已经做了限制，只能添加BackgroundTaskExecutor/OrderedTaskExecutor
                           // 类型的线程池类型为ThreadPoolExecutor/QueuedThreadPool的一定是主线程，挂了就一定会DenialOfService
                           if (e instanceof ThreadPoolExecutor) {
                               ThreadPoolExecutor ex = (ThreadPoolExecutor) e;
                               int activeCount = ex.getActiveCount();
                               int queueCount = ex.getQueue()
                                                  .size(); // 等待队列的大小
                               int largestPoolSize = ex.getLargestPoolSize();
                               int atLeastRemain = largestPoolSize <= SystemInfo.CORE_PROCESSOR_NUM ? 1 : SystemInfo.CORE_PROCESSOR_NUM; // 最少要剩余的空闲线程数
                               if (activeCount > 0 && activeCount + atLeastRemain > largestPoolSize) {
                                   String threads = ThreadUtils.getThreadsDetailInfo(null, true, 20); // 预先打印好
                                   ThreadUtils.sleep(2000); // 小小暂停一下，防止是那种突然一下子冲过来的情况
                                   activeCount = ex.getActiveCount();
                                   int currQueueCount = ex.getQueue()
                                                          .size(); // 等待队列的大小，如果持续增大，就说明肯定有问题，需要拒绝服务（不然就会挂掉）
                                   if (activeCount > 0 && activeCount + atLeastRemain >= largestPoolSize && currQueueCount > queueCount) { // 打印好了，线程池仍然大量占用，就发出报警邮件
                                       long now = System.currentTimeMillis();
                                       if (now - last > Datetime.MILLIS_PER_MINUTE) {
                                           String detail = String.format("DENIAL OF SERVICE, ACTIVE_SIZE:%s, WAITTING_QUEUE_SIZE:%s, THREADS:\n%s\n\n", activeCount, currQueueCount, threads);
                                           DispatchFilter.sendMail("DenialOfService", detail);
                                           last = now;
                                       }
                                       denialOfServiceAlmost = true;
                                   } else {
                                       denialOfServiceAlmost = false;
                                   }
                               } else {
                                   denialOfServiceAlmost = false;
                               }
                           } else if (e instanceof QueuedThreadPool) {
                               QueuedThreadPool ex = (QueuedThreadPool) e;
                               int poolSize = ex.getThreads();
                               int activeCount = poolSize - ex.getIdleThreads();
                               int largestPoolSize = ex.getMaxThreads();
                               int atLeastReamin = largestPoolSize <= SystemInfo.CORE_PROCESSOR_NUM ? 1 : SystemInfo.CORE_PROCESSOR_NUM; // 最少要剩余的空闲线程数
                               if (activeCount > 0 && activeCount + atLeastReamin >= largestPoolSize) {
                                   String threads = ThreadUtils.getThreadsDetailInfo(null, true, 20); // 预先打印好
                                   ThreadUtils.sleep(2000); // 小小暂停一下，防止是那种突然一下子冲过来的情况
                                   activeCount = poolSize - ex.getIdleThreads(); // 重新获取活跃线程数
                                   if (activeCount > 0 && activeCount + atLeastReamin >= largestPoolSize) { // 打印好了，线程池仍然大量占用，就发出报警邮件
                                       long now = System.currentTimeMillis();
                                       if (now - last > Datetime.MILLIS_PER_MINUTE) { // 一分钟报一次就够了，不用报那么多
                                           String detail = String.format("DENIAL OF SERVICE, ACTIVE_SIZE:%s, THREADS:\n%s\n\n", activeCount, threads);
                                           DispatchFilter.sendMail("DenialOfService:" + activeCount, detail);
                                           last = now;
                                       }
                                       denialOfServiceAlmost = true;
                                   } else {
                                       denialOfServiceAlmost = false;
                                   }
                               } else {
                                   denialOfServiceAlmost = false;
                               }
                           } else if (e instanceof BackgroundTaskExecutor) {
                               BackgroundTaskExecutor ex = (BackgroundTaskExecutor) e;
                               int queueSize = ex.getQueueSize();
                               if (ex.getQueueLimit() > 0 && queueSize > ex.getQueueLimit()) {
                                   String threads = ThreadUtils.getThreadsDetailInfo(null, true, 20); // 预先打印好
                                   ThreadUtils.sleep(2000); // 小小暂停一下，防止是那种突然一下子冲过来的情况
                                   int latestQueueSize = ex.getQueueSize();
                                   if (latestQueueSize > queueSize) { // 打印好了，但待执行任务数持续增大，需要发出报警邮件
                                       long now = System.currentTimeMillis();
                                       if (now - last > Datetime.MILLIS_PER_MINUTE) { // 一分钟报一次就够了，不用报那么多
                                           String detail = String.format("TOO MANY BACKGROUND TASKS (%s), QUEUE_SIZE:%s, THREADS:\n%s\n\n", ex.getName(), latestQueueSize, threads);
                                           DispatchFilter.sendMail("TooManyBackgroundTasks(" + ex.getName() + "):" + latestQueueSize, detail);
                                           last = now;
                                       }
                                       ex.setTaskExceed(true);
                                   } else {
                                       ex.setTaskExceed(false);
                                   }
                               } else {
                                   ex.setTaskExceed(false);
                               }
                           } else if (e instanceof OrderedTaskExecutor) {
                               OrderedTaskExecutor ex = (OrderedTaskExecutor) e;
                               int queueSize = ex.getQueueSize();
                               if (ex.getQueueLimit() > 0 && queueSize > ex.getQueueLimit()) {
                                   String threads = ThreadUtils.getThreadsDetailInfo(null, true, 20); // 预先打印好
                                   ThreadUtils.sleep(2000); // 小小暂停一下，防止是那种突然一下子冲过来的情况
                                   int latestQueueSize = ex.getQueueSize();
                                   if (latestQueueSize > queueSize) { // 打印好了，但待执行任务数持续增大，需要发出报警邮件
                                       long now = System.currentTimeMillis();
                                       if (now - last > Datetime.MILLIS_PER_MINUTE) { // 一分钟报一次就够了，不用报那么多
                                           String detail = String.format("TOO MANY ORDERED TASKS (%s), QUEUE_SIZE:%s, THREADS:\n%s\n\n", ex.getName(), latestQueueSize, threads);
                                           DispatchFilter.sendMail("TooManyOrderedTasks(" + ex.getName() + "):" + latestQueueSize, detail);
                                           last = now;
                                       }
                                       ex.setTaskExceed(true);
                                   } else {
                                       ex.setTaskExceed(false);
                                   }
                               } else {
                                   ex.setTaskExceed(false);
                               }
                           }
                       }

                       @Override
                       public void run() {
                           synchronized (ThreadUtils.class) {
                               for (Object e : ThreadUtils.getThreadPoolsForMonitor()) {
                                   try {
                                       check(e);
                                   } catch (Throwable ex) {
                                       log.error("", ex);
                                   }
                               }
                           }
                       }
                   }, 0, threadPoolMonitorCheckIntervalSec, TimeUnit.SECONDS);
    }
}
