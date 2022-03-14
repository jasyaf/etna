package test.cases;

import org.etnaframework.core.spring.annotation.Config;
import org.etnaframework.core.test.EtnaTestCase;
import org.etnaframework.core.test.annotation.TestLauncherClass;
import org.etnaframework.core.util.StringTools;
import org.junit.Test;
import org.springframework.stereotype.Service;
import test.TestEtnaLaunch;

/**
 * {@link StringTools}的测试
 *
 * @author BlackCat
 * @since 2015-03-16
 */
@Service
@TestLauncherClass(TestEtnaLaunch.class)
public class TestConfig extends EtnaTestCase {

    @Config("test.String")
    private int testString = 86;

    @Override
    protected void cleanup() throws Throwable {
    }

    @Test
    public void test001() {
        String link = StringTools.getLinkHtml("测试", "http://www.baidu.com", "word", "名侦探狄仁杰");
        String except = "<a href=\"http://www.baidu.com?word=%E5%90%8D%E4%BE%A6%E6%8E%A2%E7%8B%84%E4%BB%81%E6%9D%B0\">测试</a>";
        assertEquals(except, link);
    }
}
