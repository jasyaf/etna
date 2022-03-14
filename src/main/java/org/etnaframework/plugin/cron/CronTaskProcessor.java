package org.etnaframework.plugin.cron;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.etnaframework.core.spring.IgnoredPackages;
import org.etnaframework.core.spring.SpringContext;
import org.etnaframework.core.spring.annotation.OnContextInited;
import org.etnaframework.core.util.ReflectionTools;
import org.etnaframework.core.util.SystemInfo;
import org.etnaframework.core.util.SystemInfo.RunEnv;
import org.etnaframework.core.util.ThreadUtils;
import org.etnaframework.core.util.ThreadUtils.NamedThreadFactory;
import org.etnaframework.jedis.BaseJedisLock;
import org.etnaframework.plugin.cache.annotation.LocalCache;
import org.etnaframework.plugin.cron.annotation.Crontab;
import org.springframework.aop.framework.Advised;
import org.springframework.stereotype.Service;

/**
 * 定时任务初始化处理器
 *
 * @author BlackCat
 * @since 2018-1-19
 */
@Service
public final class CronTaskProcessor {

    /** 本机所有的定时任务集合 */
    private static Set<CronTaskMeta> cronTasks = new LinkedHashSet<>();

    /** 线程池：用于定时任务分发 */
    private ScheduledExecutorService cronBoss = Executors.newScheduledThreadPool(SystemInfo.CORE_PROCESSOR_NUM, new NamedThreadFactory("Cron(Boss)", Thread.MAX_PRIORITY, true));

    /** 线程池：用于实际执行定时任务 */
    private ExecutorService cronWorker = Executors.newCachedThreadPool(new NamedThreadFactory("Cron(Worker)", Thread.NORM_PRIORITY, true));

    /**
     * 获取所有的归档的定时任务信息
     */
    public static Set<CronTaskMeta> getCronTasks() {
        return cronTasks;
    }

    /**
     * 列举所有的Spring托管bean，将所有标注了{@link Crontab}的方法列举出来，初始化定时任务
     */
    @OnContextInited
    protected void init() throws Throwable {

        // 将专用线程池纳入监控，在/stat接口可查看状态
        ThreadUtils.addThreadPool(cronBoss);
        ThreadUtils.addThreadPool(cronWorker);

        for (Object bean : SpringContext.getBeansOfType(Object.class)
                                        .values()) {
            if (IgnoredPackages.filter(bean)) {
                continue;
            }
            if (bean instanceof Advised) { // Spring生成的类包含很多非业务方法，直接找回本来的类减少扫描次数
                bean = ((Advised) bean).getTargetSource()
                                       .getTarget();
                if (null == bean) {
                    continue;
                }
            }
            for (Method m : ReflectionTools.getAllMethodsInSourceCodeOrder(bean.getClass())) {
                Crontab c = m.getAnnotation(Crontab.class);
                if (null != c) {
                    String location = "类" + bean.getClass()
                                                .getName() + "加@" + Crontab.class.getSimpleName() + "的方法" + m.getName();
                    // 防止跟AOP混用时，由于生成类不包含私有方法导致出错，故限制
                    if (Modifier.isPrivate(m.getModifiers())) {
                        throw new IllegalArgumentException(location + "不能为private");
                    }
                    // 写在基类中的static方法，在继承类中会被重复调用造成混乱，故限制
                    if (Modifier.isStatic(m.getModifiers())) {
                        throw new IllegalArgumentException(location + "不能为static");
                    }
                    // 定时调用的方法不能带参数，也没有地方可以供传参，故限制
                    if (m.getParameterCount() > 0) {
                        throw new IllegalArgumentException(location + "不允许带有任何参数");
                    }
                    addCronTask(bean, m, c.cron(), c.descr(), c.mutex(), c.reportUnfinishedOnNextStart(), o -> {
                    });
                }
            }
        }
    }

    /**
     * 提交定时任务
     *
     * @param mutex 是否在多机部署时带有互斥锁（即同一个任务同一个执行器只有一个实例能执行），如果为null则表示本机执行（所有实例都执行）
     * @param callback 定时任务执行完成之后，将定时任务的返回值通知到对应的调用模块，目前专用于{@link LocalCache}的定时加载机制
     */
    public CronTaskMeta addCronTask(Object bean, Method m, String cron, String descr, Class<? extends BaseJedisLock> mutex, boolean reportUnfinishedOnNextStart, CronTaskCallback<Object> callback) {
        CronTaskMeta meta = new CronTaskMeta(cronBoss, cronWorker, bean, m, cron, descr, mutex, reportUnfinishedOnNextStart, callback);
        if (cronTasks.add(meta)) {
            // 在下次执行的时间点，提前将任务提交，然后每次当前任务开始前就预先提交下次的，这样就实现了持续执行
            // 单机定时任务直接放入执行，集群互斥定时任务只有生产环境才可以执行，防止本机测试对线上造成干扰
            if (meta.isSingleTask() || SystemInfo.RUN_ENV.equals(RunEnv.release)) {
                cronBoss.schedule(meta, meta.calcNextTime().fromNowToNextTimeMs, TimeUnit.MILLISECONDS);
            }
        } else {
            throw new IllegalArgumentException("不允许重复添加位于" + meta.getLocation() + "的定时任务");
        }
        return meta;
    }
}
