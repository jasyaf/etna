package org.etnaframework.core.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.etnaframework.core.logging.Log;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.springframework.util.StopWatch;

/**
 * 接口测试基类，各测试用例类在此类基础上继承，测试方法名称将按字典序被调用
 *
 * @author BlackCat
 * @since 2015-01-06
 */
@RunWith(EtnaJUnitRunner.class)
public abstract class EtnaTestCase extends Assert {

    protected final Logger log = Log.getLogger();

    /**
     * 测量某个方法或某段代码在高并发场景下的表现
     *
     * <pre>
     * 参考自github上面junit官方推荐的一个工具类，做出了少许的修改：
     * <a href="https://github.com/junit-team/junit/wiki/Multithreaded-code-and-concurrency">Multithreaded code and
     * concurrency </a>
     * </pre>
     *
     * @param message 与assert类似，相当于某个任务的任务名
     * @param runnables 要运行的任务
     * @param maxTimeoutSeconds 最大允许执行总时间(单位:秒)
     * @param maxThreadPoolSize 线程池最大大小
     */
    public static void assertConcurrent(final String message, final List<? extends Runnable> runnables,
        final int maxTimeoutSeconds, final int maxThreadPoolSize) throws InterruptedException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start(message);

        final int numThreads = runnables.size();
        final List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<Throwable>());
        final ExecutorService threadPool = Executors.newFixedThreadPool(
            numThreads > maxThreadPoolSize ? maxThreadPoolSize : numThreads);
        try {
            final CountDownLatch afterInitBlocker = new CountDownLatch(1);
            final CountDownLatch allDone = new CountDownLatch(numThreads);
            for (final Runnable submittedTestRunnable : runnables) {
                threadPool.submit(new Runnable() {

                    public void run() {
                        try {
                            afterInitBlocker.await(); // 相当于加了个阀门等待所有任务都准备就绪
                            submittedTestRunnable.run();
                        } catch (final Throwable e) {
                            exceptions.add(e);
                        } finally {
                            allDone.countDown();
                        }
                    }
                });
            }
            // start all test runners
            afterInitBlocker.countDown();
            assertTrue(message + " timeout! More than" + maxTimeoutSeconds + "seconds",
                allDone.await(maxTimeoutSeconds, TimeUnit.SECONDS));
            stopWatch.stop();
            System.out.println(stopWatch.prettyPrint());
        } finally {
            threadPool.shutdownNow();
        }
        assertTrue(message + "failed with exception(s)" + exceptions, exceptions.isEmpty());
    }

    /**
     * 测试单个任务并发执行
     */
    protected void testConcurrency(int iterations, final Runnable runnable) throws InterruptedException {
        System.out.println("Single Instance Concurrent Job Interation: " + iterations);
        long watch = System.currentTimeMillis();

        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

        for (int i = 0; i < iterations; i++) {
            pool.execute(() -> runnable.run());
        }

        pool.shutdown();
        // 10分钟内线程必须跑完
        Assert.assertTrue(pool.awaitTermination(10, TimeUnit.MINUTES));

        System.out.println("Concurrency execute: " + (System.currentTimeMillis() - watch) + "ms.");
    }

    /**
     * 测试组执行清理操作，在测试用例组执行开始前和执行完毕后调用（用例组里面的单个用例执行前后不会调用），该操作将在@{@link AfterClass}之前进行
     */
    protected abstract void cleanup() throws Throwable;
}
