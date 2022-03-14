package test;

import java.util.TimeZone;
import org.etnaframework.core.web.EtnaServer;

/**
 * 测试启动etna服务器
 *
 * @author BlackCat
 * @since 2015-03-10
 */
public class TestEtnaLaunch {

    public static void main(String[] args) throws Throwable {
        // 时区使用帝都时间（GMT+8），为了处理方便，返回的时间戳也是带有时区信息的如 2015-01-09 14:00:00+0800
        System.setProperty("user.timezone", "GMT+8");
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+8"));
        EtnaServer.start("jettyConfig.xml", args);
    }
}
