package org.etnaframework.plugin.cron.cmd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.etnaframework.core.util.DatetimeUtils;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.HumanReadableUtils;
import org.etnaframework.core.util.NetUtils;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.util.SystemInfo;
import org.etnaframework.core.util.SystemInfo.RunEnv;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.annotation.CmdPath;
import org.etnaframework.core.web.cmd.HttpCmd;
import org.etnaframework.plugin.cron.CronTaskMeta;
import org.etnaframework.plugin.cron.CronTaskMeta.MutexTaskInfo;
import org.etnaframework.plugin.cron.CronTaskProcessor;
import org.springframework.stereotype.Controller;

/**
 * ruiz定时任务统计信息接口
 *
 * 显示了通过ruiz框架自带的cron线程池提交执行的定时任务的统计信息
 *
 * @author BlackCat
 * @since 2017-05-22
 */
@Controller
public class StatCronCmd extends HttpCmd {

    @CmdPath("/stat/cron")
    public void index(HttpEvent he) throws Throwable {
        StringBuilder info = new StringBuilder(SystemInfo.HOSTNAME);
        info.append("(")
            .append(NetUtils.getLocalSampleIP())
            .append(")\n\n");
        info.append("当多实例部署时（注意需要确保时间同步）：\n");
        info.append("【本机】每个实例都会同时执行任务，适用数据获取型任务，例如定时拉取配置信息\n");
        info.append("【集群】各实例竞争锁，抢到者执行，即同时只有一个实例执行任务，适用数据处理型任务\n");
        info.append("　　　　为了防止开发/测试干扰线上环境，限制只有实例RunEnv=")
            .append(RunEnv.release)
            .append("时才有资格参与竞争（当前实例RunEnv=")
            .append(SystemInfo.RUN_ENV)
            .append("）\n\n");

        int locationLength = 10;
        int cronLength = 12;
        int lastCostMsLength = 12;

        // 第1遍调整格式
        for (CronTaskMeta m : CronTaskProcessor.getCronTasks()) {
            MutexTaskInfo t = m.readMutexInfo();
            if (null == t) {
                locationLength = Math.max(locationLength, m.getLocation()
                                                           .length());
                cronLength = Math.max(cronLength, m.getCron()
                                                   .length());
                lastCostMsLength = Math.max(lastCostMsLength, HumanReadableUtils.timeSpan(m.getLastCostMs())
                                                                                .length());
            } else {
                locationLength = Math.max(locationLength, t.location.length());
                cronLength = Math.max(cronLength, t.cron.length());
                cronLength = Math.max(cronLength, m.getCron()
                                                   .length());
                lastCostMsLength = Math.max(lastCostMsLength, HumanReadableUtils.timeSpan(t.lastCostMs)
                                                                                .length());
            }
        }
        cronLength += 2;
        String format = "%-" + locationLength + "s %-5s %-" + cronLength + "s %-19s %" + lastCostMsLength + "s %-20s %s %s\n";

        // 代码位置 类别 cron 最近一次执行开始时间 最近一次执行结束时间 执行耗时 附加信息
        info.append(StringTools.format(format, "location", "type", "cron", "lastStartTime", "lastCostMs", "nextStartTime", "descr", ""));
        List<StatCronOutput> list = new ArrayList<>();

        // 第2遍完成输出
        for (CronTaskMeta m : CronTaskProcessor.getCronTasks()) {
            MutexTaskInfo t = m.readMutexInfo();
            if (null == t) { // 取用本机的任务信息
                String msg = StringTools.format(format, new Object[] {
                    m.getLocation(),
                    m.isSingleTask() ? "本机" : "集群",
                    m.getCron(),
                    null != m.getLastStartTime() ? DatetimeUtils.format(m.getLastStartTime()) : "-",
                    m.isRunning() ? "running..." : HumanReadableUtils.timeSpan(m.getLastCostMs()),
                    null == m.getNextStartTime() ? "RunEnv=" + SystemInfo.RUN_ENV + " Disabled" : DatetimeUtils.format(m.getNextStartTime()),
                    m.getDescr(),
                    ""
                });
                list.add(new StatCronOutput(msg, m.getNextStartTime()));
            } else { // 从redis取任务信息
                String msg = StringTools.format(format, new Object[] {
                    t.location,
                    m.isSingleTask() ? "本机" : "集群",
                    m.getCron(),
                    null != t.lastStartTime ? DatetimeUtils.format(t.lastStartTime) : "-",
                    t.running ? "running..." : HumanReadableUtils.timeSpan(t.lastCostMs),
                    null == m.getNextStartTime() ? DatetimeUtils.format(t.nextStartTime) : DatetimeUtils.format(m.getNextStartTime()),
                    m.getDescr(),
                    "由" + t.hostname + "执行"
                });
                list.add(new StatCronOutput(msg, t.nextStartTime));
            }
        }

        // 按下次开始时间由近到远排序后输出
        list.sort(Comparator.comparing(o -> String.valueOf(o.nextStartTime)));
        for (StatCronOutput e : list) {
            info.append(e.console);
        }
        he.writeText(info);
    }

    public static class StatCronOutput {

        public String console;

        public Datetime nextStartTime;

        public StatCronOutput(String console, Datetime nextStartTime) {
            this.console = console;
            this.nextStartTime = nextStartTime;
        }
    }
}
