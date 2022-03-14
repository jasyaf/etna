package test.cases;

import org.etnaframework.core.test.EtnaTestCase;
import org.etnaframework.core.test.annotation.TestLauncherClass;
import org.etnaframework.core.util.ThreadUtils;
import org.etnaframework.core.web.DispatchFilter;
import org.junit.Test;
import org.springframework.stereotype.Service;
import test.TestEtnaLaunch;

/**
 * 通过日志发送邮件的功能
 *
 * @author BlackCat
 * @since 2015-06-29
 */
@Service
@TestLauncherClass(TestEtnaLaunch.class)
public class TestMail extends EtnaTestCase {

    @Override
    protected void cleanup() throws Throwable {
    }

    @Test
    public void test001() {
        DispatchFilter.sendMail("测试1", "111111");
        DispatchFilter.sendMail("测试2", "222222");
        DispatchFilter.sendMail("测试3", "333333");
        ThreadUtils.sleep(5000);
        System.out.println("DONE");
    }
}
