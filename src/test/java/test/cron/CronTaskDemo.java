package test.cron;

import org.etnaframework.core.util.DatetimeUtils;
import org.etnaframework.plugin.cron.annotation.Crontab;
import org.springframework.stereotype.Service;

@Service
public class CronTaskDemo {

    @Crontab(cron = "*/5 * * * * *", descr = "测试定时任务")
    public void test001() {
        System.out.println(DatetimeUtils.now());
    }
}
