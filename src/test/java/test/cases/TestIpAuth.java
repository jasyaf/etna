package test.cases;

import org.etnaframework.core.spring.annotation.Config;
import org.etnaframework.core.test.EtnaTestCase;
import org.etnaframework.core.test.annotation.TestLauncherClass;
import org.etnaframework.core.util.IPAuthenticator;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import test.TestEtnaLaunch;

@Service
@TestLauncherClass(TestEtnaLaunch.class)
public class TestIpAuth extends EtnaTestCase {

    @Autowired(required = false)
    private IPAuthenticator ipAuthenticator;

    @Config
    private String test1;

    @Config
    public void setTest2(String str) {
        System.out.println(str);
    }

    @Test
    public void test001() {
        assertTrue("属于本机应该为true", ipAuthenticator.contains("127.0.0.1"));
        assertTrue("属于[172.*.*.*]的范围应该为true", ipAuthenticator.contains("172.5.6.4") && ipAuthenticator.contains("172.0.0.1"));
        assertTrue("属于[173.1.*.*]的范围应该为true", ipAuthenticator.contains("173.1.5.6"));
        assertTrue("属于[174.0.0.*]的范围应该为true", ipAuthenticator.contains("174.0.0.255"));
        assertTrue("属于[175.1.1.6]的范围应该为true", ipAuthenticator.contains("175.1.1.6"));
        assertTrue("属于[192.168.1.1-24]的范围应该为true", ipAuthenticator.contains("192.168.1.23"));
        assertTrue("属于[202.34.25.235-222.0.0.1]的范围应该为true", ipAuthenticator.contains("205.255.255.255"));
        assertFalse("应该为false", ipAuthenticator.contains("162.31.25.2"));
        // assertTrue("本机地址判断应该为true", ipAuthenticator.isContains("127.0.0.1"));
    }

    @Override
    protected void cleanup() throws Throwable {
    }
}
