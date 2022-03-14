package test;

import org.etnaframework.core.test.EtnaJUnitTestSuit;
import org.etnaframework.core.test.annotation.TestLauncherClass;
import org.junit.runner.RunWith;
import org.springframework.stereotype.Service;

/**
 * 执行全部的测试用例
 *
 * @author BlackCat
 * @since 2015-03-11
 */
@Service
@RunWith(EtnaJUnitTestSuit.class)
@TestLauncherClass(TestEtnaLaunch.class)
public class TestAllEtna {

}
