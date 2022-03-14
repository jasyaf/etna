package org.etnaframework.plugin.cron;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.etnaframework.plugin.cache.annotation.LocalCache;
import org.etnaframework.core.spring.SpringContext;
import org.etnaframework.core.util.DatetimeUtils;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.HumanReadableUtils;
import org.etnaframework.core.util.JsonObjectUtils;
import org.etnaframework.core.util.NetUtils;
import org.etnaframework.core.util.RetryTools;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.util.SystemInfo;
import org.etnaframework.core.util.ThreadUtils;
import org.etnaframework.core.web.DispatchFilter;
import org.etnaframework.jedis.BaseJedisLock;
import org.springframework.aop.framework.Advised;
import org.springframework.scheduling.support.CronSequenceGenerator;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * 每个定时任务的具体内容
 *
 * @author BlackCat
 * @since 2018-01-18
 */
public class CronTaskMeta implements Runnable {

    public static final String mutexLockPrefix = "mutexTaskLock:";

    public static final String mutexInfoPrefix = "mutexTaskInfo:";

    /** 线程池引用：用于定时任务分发 */
    private ScheduledExecutorService cronBoss;

    /** 线程池引用：用于实际执行定时任务 */
    private ExecutorService cronWorker;

    /** 复用Spring的cron表达式解析组件 */
    private CronSequenceGenerator sequenceGenerator;

    /** 定时任务方法的位置 */
    private String location;

    /** 定时任务cron表达式内容 */
    private String cron;

    /** 是否在多机部署时带有互斥锁（即同一个任务同一个执行器只有一个实例能执行），如果为null则表示本机执行（所有实例都执行） */
    private BaseJedisLock mutexLock;

    /** 定时任务的说明 */
    private String descr;

    /** Spring托管bean */
    private Object bean;

    /** 定时任务调用的方法 */
    private Method method;

    /** 定时任务上次开始时间点 */
    private Datetime lastStartTime;

    /** 是否正在执行中 */
    private volatile boolean running;

    /** 上次执行耗时毫秒 */
    private long lastCostMs;

    /** 定时任务下次开始时间点 */
    private Datetime nextStartTime;

    /** 下一次执行时，如发现上一次的任务还没有执行完，是否要发出告警信息 */
    private boolean reportUnfinishedOnNextStart;

    /** 定时任务执行完成之后，将定时任务的返回值通知到对应的调用模块，目前专用于{@link LocalCache}的定时加载机制 */
    private CronTaskCallback<Object> callback;

    CronTaskMeta(ScheduledExecutorService cronBoss, ExecutorService cronWorker, Object bean, Method method, String cron, String descr, Class<? extends BaseJedisLock> mutex,
        boolean reportUnfinishedOnNextStart, CronTaskCallback<Object> callback) {
        this.cronBoss = cronBoss;
        this.cronWorker = cronWorker;

        this.bean = bean;
        if (bean instanceof Advised) { // 如果是经过aspect代理的对象，则需要取到被代理的（真实的）对象，否则会出现$$EnhancerBySpringCGLIB$$26c5da53这样很长的类名后缀
            try {
                this.bean = ((Advised) bean).getTargetSource()
                                            .getTarget();
                this.location = this.bean.getClass()
                                         .getSimpleName() + "." + method.getName();
            } catch (Exception e) {
            }
        }
        if (null == location) {
            this.location = bean.getClass()
                                .getSimpleName() + "." + method.getName();
        }

        this.method = method;
        this.method.setAccessible(true);
        this.cron = cron;
        this.descr = descr;
        this.mutexLock = BaseJedisLock.class.equals(mutex) || null == mutex ? null : SpringContext.getBean(mutex);
        this.reportUnfinishedOnNextStart = reportUnfinishedOnNextStart;
        this.callback = callback;
        try {
            this.sequenceGenerator = new CronSequenceGenerator(cron);
        } catch (Throwable ex) {
            // 将报错信息翻译一下，让人看得更明白
            throw new IllegalArgumentException("定时任务" + location + "的cron配置" + cron + "解析失败", ex);
        }
    }

    /**
     * 执行原始方法，获得返回值
     */
    Object invoke() throws Throwable {
        try {
            return this.method.invoke(this.bean);
        } catch (InvocationTargetException ex) {
            // 如果是执行方法内容时抛出异常，需要【提取到原始的异常】然后再抛出，这样报错提示才会更清晰
            if (null != ex.getCause()) {
                throw ex.getCause();
            }
            throw ex;
        }
    }

    @Override
    public void run() {
        // boss线程负责任务调度，将下次执行的任务提交，然后在worker线程开始本次任务（预先提交是为了防止本次执行超时，导致下次任务不能按时触发）
        NextTimeInfo next = calcNextTime();
        cronBoss.schedule(this, next.fromNowToNextTimeMs, TimeUnit.MILLISECONDS);

        // 如果服务器还没启动完成，先不执行定时任务，防止某些资源没初始化好，一运行就出错了
        if (!SpringContext.isContextInited()) {
            return;
        }

        cronWorker.execute(() -> {

            // 如果发现前一个任务还没执行完，发出告警之后本次就不再执行，防止出现任务堆积爆内存（但接下来的定时检查仍会继续）
            if (running) {
                if (reportUnfinishedOnNextStart) { // 只有配置了告警才会发出警报
                    String content = StringTools.concatln(new Object[] {
                        "时间=" + DatetimeUtils.format(next.now, Datetime.DF_yyyy_MM_dd_HHmmss_SSS),
                        "位置=" + getLocation(),
                        "说明=" + getDescr(),
                        "cron=" + getCron(),
                        "开始于=" + DatetimeUtils.format(lastStartTime, Datetime.DF_yyyy_MM_dd_HHmmss_SSS),
                        "已执行=" + HumanReadableUtils.timeSpan(next.now.getTime() - lastStartTime.getTime())
                    });
                    DispatchFilter.sendMail("定时任务未能在触发周期内结束", content);
                }
                return;
            }

            boolean obtainLock = false;

            // 进入执行周期，首先标记开始执行
            try {
                this.lastStartTime = next.now;
                this.running = true;
                Object result = null;

                if (null == mutexLock) { // 本机任务，直接执行
                    result = invoke();
                } else { // 集群互斥任务，只有生产环境才会执行（在CronTaskProcessor.init控制），抢到锁了才会真正执行

                    // 1、查询任务信息，如果发现redis里面存的cron和本机的不一致，需发出告警，并试着用本机配置覆盖
                    MutexTaskInfo info = readMutexInfo();

                    if (null != info && !getCron().equals(info.cron)) {
                        String content = StringTools.concatln(new Object[] {
                            "时间=" + DatetimeUtils.now()
                                                 .toString(Datetime.DF_yyyy_MM_dd_HHmmss_SSS),
                            "位置=" + getLocation(),
                            "说明=" + getDescr(),
                            "cron本机配置=" + getCron(),
                            "cron冲突配置=" + info.cron,
                            "导致配置冲突者=" + info.hostname,
                            "",
                            "本机cron配置将会尝试覆盖冲突配置"
                        });
                        DispatchFilter.sendMail("位于" + getLocation() + "的cron配置存在冲突，请检查代码同步状态", content);
                    }

                    // 2、如果上次的任务是本机执行的，为了可以给其他实例机会，防止定时任务总是被固定的实例执行，稍稍做一点延迟
                    String myIP = NetUtils.getLocalSampleIP();
                    if (null != info && myIP.equals(info.ip)) {
                        ThreadUtils.sleep(StringTools.randomInt(100, 300));
                    }

                    // 3、开始抢夺锁，以下一次开始时间的时间戳作为标记，以避免本次出现冲突
                    String keyLock = mutexLockPrefix + getLocation() + ":" + DatetimeUtils.format(getNextStartTime(), Datetime.DF_yyyyMMddHHmmss);
                    obtainLock = RetryTools.newTask(mutexLock.jedisConfig()
                                                             .getMaxAttempts())
                                           .include(JedisConnectionException.class)
                                           .process(() -> mutexLock.jedisConfig()
                                                                   .getTemplateByKey(location)
                                                                   .string()
                                                                   .setnx(keyLock, myIP, next.fromNowToNextTimeMs, TimeUnit.MILLISECONDS));
                    if (obtainLock) {
                        // 4A、抢到锁之后开始执行
                        writeMutexInfo(next.fromNowToUnderNextTimeMs);
                        result = invoke();
                    } else {
                        // 4B、如果没有抢到锁，也有可能是抢锁操作重试导致的，对比一下IP确认锁到底有没有被自己抢到
                        String ip = RetryTools.newTask(mutexLock.jedisConfig()
                                                                .getMaxAttempts())
                                              .include(JedisConnectionException.class)
                                              .process(() -> mutexLock.jedisConfig()
                                                                      .getTemplateByKey(location)
                                                                      .string()
                                                                      .get(keyLock));
                        if (myIP.equals(ip)) {
                            writeMutexInfo(next.fromNowToUnderNextTimeMs);
                            result = invoke();
                        }
                    }
                }
                // 如果配置了回调方法，就将返回值通知过去
                if (null != callback) {
                    callback.onCallback(result);
                }
            } catch (Throwable ex) {
                String content = StringTools.concatln(new Object[] {
                    "时间=" + DatetimeUtils.now()
                                         .toString(Datetime.DF_yyyy_MM_dd_HHmmss_SSS),
                    "位置=" + getLocation(),
                    "说明=" + getDescr(),
                    "cron=" + getCron(),
                    "开始于=" + DatetimeUtils.format(lastStartTime, Datetime.DF_yyyy_MM_dd_HHmmss_SSS),
                    StringTools.printTrace(ex, true, 20, 0)
                });
                DispatchFilter.sendMail("位于" + getLocation() + "的定时任务执行报错", content);
            } finally { // 执行结束后标记执行结束
                this.running = false;
                this.lastCostMs = System.currentTimeMillis() - lastStartTime.getTime();
                if (obtainLock) { // 抢到锁者才有资格回写任务信息
                    writeMutexInfo(next.fromNowToUnderNextTimeMs);
                }
            }
        });
    }

    public String getLocation() {
        return location;
    }

    public String getCron() {
        return cron;
    }

    public String getDescr() {
        return descr;
    }

    public Datetime getLastStartTime() {
        return lastStartTime;
    }

    public boolean isRunning() {
        return running;
    }

    public long getLastCostMs() {
        return lastCostMs;
    }

    public Datetime getNextStartTime() {
        return nextStartTime;
    }

    public MutexTaskInfo readMutexInfo() {
        if (null != mutexLock) {
            String keyInfo = mutexInfoPrefix + getLocation();
            return RetryTools.newTask(mutexLock.jedisConfig()
                                               .getMaxAttempts())
                             .include(JedisConnectionException.class)
                             .process(() -> mutexLock.jedisConfig()
                                                     .getTemplateByKey(location)
                                                     .string()
                                                     .getJson(keyInfo, MutexTaskInfo.class)
                             );
        }
        return null;
    }

    private void writeMutexInfo(long ttl) {
        if (null != mutexLock) {
            String keyInfo = mutexInfoPrefix + getLocation();
            RetryTools.newTask(mutexLock.jedisConfig()
                                        .getMaxAttempts())
                      .include(JedisConnectionException.class)
                      .process(() -> mutexLock.jedisConfig()
                                              .getTemplateByKey(location)
                                              .string()
                                              .set(keyInfo, JsonObjectUtils.createJson(new MutexTaskInfo(this)), ttl, TimeUnit.MILLISECONDS)
                      );
        }
    }

    /**
     * 用于判断是否是单机任务
     */
    public boolean isSingleTask() {
        return null == mutexLock;
    }

    @Override
    public int hashCode() {
        return location.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CronTaskMeta that = (CronTaskMeta) o;
        return location.equals(that.location);
    }

    /**
     * 以当前系统时间为参考，计算距离下一次触发还有多少毫秒
     */
    NextTimeInfo calcNextTime() {
        // 系统时间校正时，如果本地时间比标准时间快，校正时时间往后退，如果退到了lastStartTime之前，计算下次任务时又会算到这个时间点，导致已执行的任务再次被触发执行
        // 这里把上次计算的nextStartTime（实质是预计本次执行的时间）和当前时间进行比对，取较靠后者，确保开始执行时间不能回退，避免上述bug
        long now = System.currentTimeMillis();
        long lastPrepareTs = Math.max(now, null != nextStartTime ? nextStartTime.getTime() : 0);
        // 下次触发时间
        this.nextStartTime = new Datetime(sequenceGenerator.next(new Datetime(lastPrepareTs)));
        // 下下次触发时间
        Datetime underNextStartTime = new Datetime(sequenceGenerator.next(nextStartTime));
        return new NextTimeInfo(now, nextStartTime, underNextStartTime);
    }

    public void setExecuteInfo(Datetime lastStartTime, long lastCostMs) {
        this.lastStartTime = lastStartTime;
        this.lastCostMs = lastCostMs;
    }

    static class NextTimeInfo {

        /** 当前时间 */
        Datetime now;

        /** 从当前时间到【下次】触发间隔毫秒 */
        long fromNowToNextTimeMs;

        /** 从当前时间到【下下次】触发间隔毫秒，用于保存集群任务信息 */
        long fromNowToUnderNextTimeMs;

        NextTimeInfo(long now, Datetime nextStartTime, Datetime underNextStartTime) {
            this.now = new Datetime(now);
            this.fromNowToNextTimeMs = nextStartTime.getTime() - now;
            this.fromNowToUnderNextTimeMs = underNextStartTime.getTime() - now;
        }
    }

    /**
     * 互斥定时任务的执行情况，记录到redis备查
     */
    public static class MutexTaskInfo {

        /** 抢到互斥锁的实例机器名 */
        public String hostname;

        /** 抢到互斥锁的机器IP */
        public String ip;

        /** 定时任务类名和方法名，用于回溯定时任务内容 */
        public String location;

        /** 定时任务cron表达式内容 */
        public String cron;

        /** 最近一次执行任务开始时时间 */
        public Datetime lastStartTime;

        /** 是否正在执行中 */
        public boolean running;

        /** 上次执行耗时毫秒 */
        public long lastCostMs;

        /** 定时任务下次开始时间点 */
        public Datetime nextStartTime;

        public MutexTaskInfo() {
        }

        MutexTaskInfo(CronTaskMeta task) {
            this.hostname = SystemInfo.HOSTNAME;
            this.ip = NetUtils.getLocalSampleIP();
            this.location = task.getLocation();
            this.cron = task.getCron();
            this.lastStartTime = task.getLastStartTime();
            this.running = task.isRunning();
            this.lastCostMs = task.getLastCostMs();
            this.nextStartTime = task.getNextStartTime();
        }
    }
}
