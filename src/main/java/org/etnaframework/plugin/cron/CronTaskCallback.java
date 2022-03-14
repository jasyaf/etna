package org.etnaframework.plugin.cron;

/**
 * 回调任务
 *
 * @author BlackCat
 * @since 2018-01-18
 */
public interface CronTaskCallback<Param> {

    void onCallback(Param param);
}
