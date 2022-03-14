package test.cases;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.etnaframework.core.test.EtnaTestCase;
import org.etnaframework.core.test.annotation.TestDescr;
import org.etnaframework.core.test.annotation.TestLauncherClass;
import org.etnaframework.core.util.ThreadUtils;
import org.etnaframework.jedis.JedisLock;
import org.etnaframework.jedis.JedisTemplate;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPool;
import test.TestEtnaLaunch;

/**
 * 基于Jedis的分布式锁测试
 * 完整的测试大概需要时间：2分钟
 * Created by yuanhaoliang on 2017-08-14.
 */
@Service
@TestLauncherClass(TestEtnaLaunch.class)
public class TestJedisLock extends EtnaTestCase {

    /**=================================================*/
    /**====================参数配置======================*/

    /** redis的host */
    private static String redisHost = "127.0.0.1";

    /** redis的端口 */
    private static int redisPort = 6379;

    /** 没有密码则设为null */
    private static String password = null;

    /** ================================================= */

    private static JedisTemplate ___jedisTemplateInner;

    private static JedisTemplate getJedisTemplate() {
        if (___jedisTemplateInner == null) {
            GenericObjectPoolConfig config = new GenericObjectPoolConfig();
            config.setMaxTotal(100);
            config.setMaxIdle(10);

            JedisPool jedisPool = password == null ? new JedisPool(config, redisHost, redisPort,
                15 * 1000) : new JedisPool(config, redisHost, redisPort, 15 * 1000, password);
            ___jedisTemplateInner = new JedisTemplate(jedisPool);
        }
        return ___jedisTemplateInner;
    }

    @Override
    protected void cleanup() throws Throwable {
        // 删除测试所用到的KEY
        JedisTemplate jedisTemplate = getJedisTemplate();
        for (LockName lock : LockName.values()) {
            jedisTemplate.key()
                         .del(lock.toString());
        }
    }

    @Test
    @TestDescr("测试等待获取锁")
    public void testTryLockWait() throws InterruptedException {
        JedisTemplate jedisTemplate = getJedisTemplate();

        ThreadUtils.getDefault()
                   .execute(() -> {
                       JedisLock lock = jedisTemplate.getLock(LockName.testTryLockWait.toString());
                       lock.lock();
                   });

        JedisLock lock = jedisTemplate.getLock(LockName.testTryLockWait.toString());

        long startTime = System.currentTimeMillis();
        lock.tryLock(3, TimeUnit.SECONDS);
        long time = System.currentTimeMillis() - startTime;
        assertTrue(time >= 2990L && time <= 3100L);
    }

    @Test
    @TestDescr("测试强行解锁")
    public void testForceUnlock() {

        JedisTemplate jedisTemplate = getJedisTemplate();
        JedisLock lock = jedisTemplate.getLock(LockName.testForceUnlock.toString());
        lock.lock();
        lock.forceUnlock();
        assertFalse(lock.isLocked());

        lock = jedisTemplate.getLock(LockName.testForceUnlock.toString());
        Assert.assertFalse(lock.isLocked());
    }

    @Test
    @TestDescr("测试自动释放锁")
    public void testExpire() throws InterruptedException {
        JedisTemplate jedisTemplate = getJedisTemplate();

        JedisLock lock = jedisTemplate.getLock(LockName.testExpire.toString());
        lock.lock(2, TimeUnit.SECONDS);

        final long startTime = System.currentTimeMillis();
        Thread t = new Thread() {

            public void run() {
                JedisTemplate jedisTemplate = getJedisTemplate();
                JedisLock lock1 = jedisTemplate.getLock(LockName.testExpire.toString());

                lock1.lock();
                long spendTime = System.currentTimeMillis() - startTime;
                Assert.assertTrue(spendTime < 2050);
                lock1.unlock();
            }
        };

        t.start();
        t.join();

        lock.unlock();
    }

    @Test
    @TestDescr("测试锁自动续时间")
    public void testExpirationRenewal() throws InterruptedException {

        JedisTemplate jedisTemplate = getJedisTemplate();

        final CountDownLatch latch = new CountDownLatch(1);

        Thread t = new Thread() {

            @Override
            public void run() {
                JedisLock lock = jedisTemplate.getLock(LockName.testExpirationRenewal.toString());
                lock.lock();
                latch.countDown();
                try {
                    Thread.sleep(35000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        t.start();

        Assert.assertTrue(latch.await(1, TimeUnit.SECONDS));
        JedisLock lock = jedisTemplate.getLock(LockName.testExpirationRenewal.toString());
        t.join();
        Assert.assertTrue(lock.isLocked());
    }

    @Test
    @TestDescr("测试持锁个数")
    public void testGetHoldCount() {
        JedisTemplate jedisTemplate = getJedisTemplate();

        JedisLock lock = jedisTemplate.getLock(LockName.testGetHoldCount.toString());
        Assert.assertEquals(0, lock.getHoldCount());
        lock.lock();
        Assert.assertEquals(1, lock.getHoldCount());
        lock.unlock();
        Assert.assertEquals(0, lock.getHoldCount());

        lock.lock();
        lock.lock();
        Assert.assertEquals(2, lock.getHoldCount());
        lock.unlock();
        Assert.assertEquals(1, lock.getHoldCount());
        lock.unlock();
        Assert.assertEquals(0, lock.getHoldCount());
    }

    @Test
    @TestDescr("在别的线程里测试锁是否在本线程")
    public void testIsHeldByCurrentThreadOtherThread() throws InterruptedException {
        JedisTemplate jedisTemplate = getJedisTemplate();

        JedisLock lock = jedisTemplate.getLock(LockName.testIsHeldByCurrentThreadOtherThread.toString());
        lock.lock();

        Thread t = new Thread() {

            public void run() {
                JedisLock lock = jedisTemplate.getLock(LockName.testIsHeldByCurrentThreadOtherThread.toString());
                Assert.assertFalse(lock.isHeldByCurrentThread());
            }

            ;
        };

        t.start();
        t.join();
        lock.unlock();

        Thread t2 = new Thread() {

            public void run() {
                JedisLock lock = jedisTemplate.getLock(LockName.testIsHeldByCurrentThreadOtherThread.toString());
                Assert.assertFalse(lock.isHeldByCurrentThread());
            }

            ;
        };

        t2.start();
        t2.join();
    }

    @Test
    @TestDescr("测试是否本线程获取的锁")
    public void testIsHeldByCurrentThread() {
        JedisTemplate jedisTemplate = getJedisTemplate();

        JedisLock lock = jedisTemplate.getLock(LockName.testIsHeldByCurrentThread.toString());
        Assert.assertFalse(lock.isHeldByCurrentThread());
        lock.lock();
        Assert.assertTrue(lock.isHeldByCurrentThread());
        lock.unlock();
        Assert.assertFalse(lock.isHeldByCurrentThread());
    }

    @Test
    @TestDescr("测试在别的线程是否为已锁状态")
    public void testIsLockedOtherThread() throws InterruptedException {
        JedisTemplate jedisTemplate = getJedisTemplate();

        JedisLock lock = jedisTemplate.getLock(LockName.testIsLockedOtherThread.toString());
        lock.lock();

        Thread t = new Thread() {

            public void run() {
                JedisLock lock = jedisTemplate.getLock(LockName.testIsLockedOtherThread.toString());
                Assert.assertTrue(lock.isLocked());
            }

            ;
        };

        t.start();
        t.join();
        lock.unlock();

        Thread t2 = new Thread() {

            public void run() {
                JedisLock lock = jedisTemplate.getLock(LockName.testIsLockedOtherThread.toString());
                Assert.assertFalse(lock.isLocked());
            }

            ;
        };

        t2.start();
        t2.join();
    }

    @Test
    @TestDescr("测试是否已锁")
    public void testIsLocked() {
        JedisTemplate jedisTemplate = getJedisTemplate();

        JedisLock lock = jedisTemplate.getLock(LockName.testIsLocked.toString());
        Assert.assertFalse(lock.isLocked());
        lock.lock();
        Assert.assertTrue(lock.isLocked());
        lock.unlock();
        Assert.assertFalse(lock.isLocked());
    }

    @Test(expected = IllegalMonitorStateException.class)
    @TestDescr("测试解锁失败")
    public void testUnlockFail() throws InterruptedException {
        JedisTemplate jedisTemplate = getJedisTemplate();

        JedisLock lock = jedisTemplate.getLock(LockName.testUnlockFail.toString());
        Thread t = new Thread() {

            public void run() {
                JedisLock lock = jedisTemplate.getLock(LockName.testUnlockFail.toString());
                lock.lock();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                lock.unlock();
            }

            ;
        };

        t.start();
        t.join(400);

        try {
            lock.unlock();
        } catch (IllegalMonitorStateException e) {
            t.join();
            throw e;
        }
    }

    @Test
    @TestDescr("测试取锁解锁")
    public void testLockUnlock() {
        JedisTemplate jedisTemplate = getJedisTemplate();

        java.util.concurrent.locks.Lock lock = jedisTemplate.getLock(LockName.testLockUnlock.toString());
        lock.lock();
        lock.unlock();

        lock.lock();
        lock.unlock();
    }

    @Test
    @TestDescr("测试锁的可重入特性")
    public void testReentrancy() throws InterruptedException {
        JedisTemplate jedisTemplate = getJedisTemplate();

        java.util.concurrent.locks.Lock lock = jedisTemplate.getLock(LockName.testReentrancy.toString());
        Assert.assertTrue(lock.tryLock());
        Assert.assertTrue(lock.tryLock());
        lock.unlock();
        // 测试自动续时间
        Thread.currentThread()
              .sleep(TimeUnit.SECONDS.toMillis(30 * 2));
        Thread thread1 = new Thread() {

            @Override
            public void run() {
                JedisLock lock1 = jedisTemplate.getLock(LockName.testReentrancy.toString());
                Assert.assertFalse(lock1.tryLock());
            }
        };
        thread1.start();
        thread1.join();
        lock.unlock();
    }

    @Test
    @TestDescr("测试单个redis客户端实例的并发性")
    public void testConcurrency_SingleInstance() throws InterruptedException {
        JedisTemplate jedisTemplate = getJedisTemplate();
        final AtomicInteger lockedCounter = new AtomicInteger();

        int iterations = 15;// 并发性会受到jedis连接池数量的影响，如果连接池太小，会由于拿不到连接永远执行不完。因为订阅消息会占用连接不释放。
        testConcurrency(iterations, () -> {
            Lock lock = jedisTemplate.getLock(LockName.testConcurrency_SingleInstance.toString());
            lock.lock();
            String id = Thread.currentThread()
                              .getName();
            System.err.println(id + " get lock.");
            lockedCounter.incrementAndGet();
            lock.unlock();
            System.err.println(id + " unlock.");
        });

        Assert.assertEquals(iterations, lockedCounter.get());
    }

    @Test
    @TestDescr("测试多个redis客户端实例循环的并发性")
    public void testConcurrencyLoop_MultiInstance() throws InterruptedException {
        final int instanceCount = 8; // 实例数
        final int iterations = 10;   // 每个实例的并发线程数
        final AtomicInteger lockedCounter = new AtomicInteger();
        final int connectTimeout = 15000; // redis 连接超时时间

        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setMaxTotal(iterations * 3);  // 连接池最大个数

        CountDownLatch latch = new CountDownLatch(iterations * instanceCount);

        testConcurrency(instanceCount, () -> {
            JedisPool jedisPool = password == null ? new JedisPool(config, redisHost, redisPort,
                connectTimeout) : new JedisPool(config, redisHost, redisPort, connectTimeout, password);
            JedisTemplate jedisTemplate = new JedisTemplate(jedisPool);
            try {
                testConcurrency(iterations, () -> {
                    jedisTemplate.getLock(LockName.testConcurrencyLoop_MultiInstance.toString())
                                 .lock();
                    String id = Thread.currentThread()
                                      .getName();
                    System.err.println(System.currentTimeMillis() + ":" + id + " get lock.");
                    lockedCounter.incrementAndGet();
                    jedisTemplate.getLock(LockName.testConcurrencyLoop_MultiInstance.toString())
                                 .unlock();
                    latch.countDown();
                    System.err.println(System.currentTimeMillis() + ":" + id + " unlock.");
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        latch.await(5, TimeUnit.MINUTES);
        Assert.assertEquals(instanceCount * iterations, lockedCounter.get());
    }

    @Test
    @TestDescr("测试多个redis客户端实例的并发性")
    public void testConcurrency_MultiInstance() throws InterruptedException {
        int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();
        final int connectTimeout = 15000; // redis 连接超时时间

        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setMaxTotal(iterations * 3);  // 连接池最大个数

        testConcurrency(iterations, () -> {
            JedisPool jedisPool = password == null ? new JedisPool(config, redisHost, redisPort,
                connectTimeout) : new JedisPool(config, redisHost, redisPort, connectTimeout, password);
            JedisTemplate jedisTemplate = new JedisTemplate(jedisPool);

            Lock lock = jedisTemplate.getLock(LockName.testConcurrency_MultiInstance.toString());
            lock.lock();
            String id = Thread.currentThread()
                              .getName();
            System.err.println(System.currentTimeMillis() + ":" + id + " get lock.");
            lockedCounter.incrementAndGet();
            lock.unlock();
            System.err.println(System.currentTimeMillis() + ":" + id + " unlock.");
        });

        Assert.assertEquals(iterations, lockedCounter.get());
    }

    private enum LockName {
        testTryLockWait,
        testForceUnlock,
        testExpire,
        testExpirationRenewal,
        testGetHoldCount,
        testIsHeldByCurrentThreadOtherThread,
        testIsHeldByCurrentThread,
        testIsLockedOtherThread,
        testIsLocked,
        testUnlockFail,
        testLockUnlock,
        testReentrancy,
        testConcurrency_SingleInstance,
        testConcurrencyLoop_MultiInstance,
        testConcurrency_MultiInstance
    }
}
