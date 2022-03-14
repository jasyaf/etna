package org.etnaframework.plugin.cron.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.etnaframework.core.util.SystemInfo.RunEnv;
import org.etnaframework.jedis.BaseJedisLock;

/**
 * 定时任务组件，加到想要定时执行的方法上就可以了，注意最小单位是秒
 * 服务启动后初始化完毕时开始启用，访问/stat/cron即可查看所有的定时任务及执行情况
 *
 * @author BlackCat
 * @since 2018-01-18
 */
@Inherited
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Crontab {

    /**
     * 和类UNIX系统上的cron表达式语法一样，只是第一列多了秒
     * 例如 10 * * * * MON-FRI 表示 从周一到周五，每分钟的第10秒 执行
     *
     * 注意：
     * 1、如果本次执行尚未结束，却到了下一次触发的时间点，这种情况会发出告警且不执行下次任务（集群互斥任务如果锁被其他实例抢到则仍会由其他实例执行）
     * 2、集群互斥任务如果同一个方法上cron周期跟其他执行的实例不同，在本机触发检查时会发出告警（参见{@link CronTaskMeta#run()}的实现）
     */
    String cron();

    /**
     * 集群互斥任务执行规则（多机部署时，需要保证每个实例时间同步！！）
     *
     * 1、如果服务单机部署，则是否配置此项没有影响，定时任务会正常触发
     *
     * 2、如果服务是多机部署的，设置了此项，则定时任务每次触发时只有一个实例会执行（谁先抢到谁执行，适用数据处理类任务）
     * 如不设置此项，则定时任务会在每个实例上都触发，适用数据获取类任务，例如定时拉取配置文件
     *
     * 3、为了防止测试干扰，只有运行环境为{@link RunEnv#release}才会执行集群互斥定时任务
     * （如需本机测试，则需要修改启动参数增加-DRunEnv=release）
     */
    Class<? extends BaseJedisLock> mutex() default BaseJedisLock.class;

    /**
     * 下一次执行时，如发现上一次的任务还没有执行完，是否要发出告警信息
     * 在任何情况下，下一次执行时如果上一次的任务未完成，这一次的任务都不会触发
     */
    boolean reportUnfinishedOnNextStart() default true;

    /**
     * 该定时任务的内容描述，会在/stat/cron接口显示出来
     */
    String descr();
}
