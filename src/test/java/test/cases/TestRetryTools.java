package test.cases;

import java.util.concurrent.atomic.AtomicInteger;
import org.etnaframework.core.test.EtnaTestCase;
import org.etnaframework.core.test.annotation.TestLauncherClass;
import org.etnaframework.core.util.RetryTools;
import org.junit.Test;
import org.springframework.stereotype.Service;
import test.TestEtnaLaunch;

/**
 * {@link RetryTools}的测试
 *
 * @author BlackCat
 * @since 2017-05-11
 */
@Service
@TestLauncherClass(TestEtnaLaunch.class)
public class TestRetryTools extends EtnaTestCase {

    @Override
    protected void cleanup() throws Throwable {
    }

    /**
     * 测试正常调用
     */
    @Test
    public void testNormal() {
        AtomicInteger i = new AtomicInteger();
        RetryTools.newTask(3)
                  .include(Throwable.class)
                  .process(() -> {
                      i.incrementAndGet();
                      System.out.println("正常调用");
                  });
        assertEquals(1, i.intValue());
    }

    /**
     * 测试没配置include/exclude的情况，会在执行时抛异常
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNoInclude() {
        AtomicInteger i = new AtomicInteger();
        RetryTools.newTask(3)
                  .process(() -> {
                      i.incrementAndGet();
                      System.out.println("没配置include/exclude");
                  });
        assertEquals(1, i.intValue());
    }

    /**
     * 抛出能重试的异常，并进行重试（多次重试都失败，最后抛出）
     */
    @Test
    public void testRetryOnExceptionAndRetry1() {
        AtomicInteger i = new AtomicInteger();
        try {
            RetryTools.newTask(3)
                      .include(Throwable.class)
                      .process(() -> {
                          i.incrementAndGet();
                          throw new NullPointerException("测试抛异常");
                      });
        } catch (NullPointerException ex) {
            assertEquals(3, i.intValue());
        }
    }

    /**
     * 抛出能重试的异常，并进行重试（先抛出异常，后来中途正常）
     */
    @Test
    public void testRetryOnExceptionAndRetry2() {
        AtomicInteger i = new AtomicInteger();
        RetryTools.newTask(3)
                  .include(Throwable.class)
                  .process(() -> {
                      i.incrementAndGet();
                      if (i.intValue() == 2) {
                          return;
                      }
                      throw new NullPointerException("测试抛异常");
                  });
        assertEquals(2, i.intValue());
    }

    /**
     * 抛出不能重试的异常
     */
    @Test
    public void testRetryOnExceptionNotIncluded1() {
        AtomicInteger i = new AtomicInteger();
        try {
            RetryTools.newTask(3)
                      .include(IllegalArgumentException.class)
                      .process(() -> {
                          i.incrementAndGet();
                          throw new NullPointerException("测试抛异常");
                      });
        } catch (NullPointerException ex) {
            assertEquals(1, i.intValue());
        }
    }

    /**
     * 抛出不能重试的异常
     */
    @Test
    public void testRetryOnExceptionExcluded2() {
        AtomicInteger i = new AtomicInteger();
        try {
            RetryTools.newTask(3)
                      .exclude(Throwable.class)
                      .process(() -> {
                          i.incrementAndGet();
                          throw new NullPointerException("测试抛异常");
                      });
        } catch (NullPointerException ex) {
            assertEquals(1, i.intValue());
        }
    }
}
