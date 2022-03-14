package org.etnaframework.jedis;

import java.io.Closeable;
import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.util.SystemInfo;
import org.etnaframework.core.util.ThreadUtils;
import org.etnaframework.core.util.ThreadUtils.NamedThreadFactory;
import com.google.common.collect.Maps;
import redis.clients.jedis.JedisPubSub;

/**
 * 分布式同步锁(可重入锁Reentrant Lock)
 * 参考自Redisson的RedissonLock
 *
 * 获取锁后，如果没有指定自动释放时间，则默认自动释放时间是3秒，
 * 如果该线程超过自动释放时间还活着，则会自动给释放时间续时，达到长期占有锁
 *
 * 利用redis执行lua脚本的原子性进行锁的判断
 *
 * Tips:
 * 1.该实例不能跨线程使用！
 * 2.redis的连接池数量需要足够大，否则连接池会被订阅线程占满，如果并发度足够高的话
 *
 * @see <a href="https://github.com/redisson/redisson/blob/master/redisson/src/main/java/org/redisson/RedissonLock.java">RedissonLock</a>
 *
 * Created by yuanhaoliang on 2017-08-12.
 */
public class JedisLock implements Lock, Serializable, Closeable {

    private static final long serialVersionUID = -6118551138043920769L;

    /** 默认的释放锁时间 */
    static final long DEFAULT_LOCK_LEASE_MS = Datetime.MILLIS_PER_SECOND * 3;

    private static final String unlockMessage = "0";

    /** 锁的定时刷新Map，如果没有指定自动释放时间的锁，只要线程还活着，就要让锁一直持有，直到解锁。 */
    private static final ConcurrentMap<String, ScheduledFuture> expirationRenewalMap = Maps.newConcurrentMap();

    /** 订阅线程池 */
    private static final Executor executor = Executors.newCachedThreadPool(
        new NamedThreadFactory("JedisLock(Sub)", Thread.NORM_PRIORITY));

    /** 刷新锁时间线程池 */
    private static ScheduledExecutorService cron = Executors.newScheduledThreadPool(SystemInfo.CORE_PROCESSOR_NUM,
        new NamedThreadFactory("JedisLock(Sche)", Thread.MAX_PRIORITY, true));

    static {
        ThreadUtils.addThreadPool(executor);
        ThreadUtils.addThreadPool(cron);
    }

    /** redis客户端唯一ID */
    private final UUID id;

    /** 锁超时时间 */
    private long internalLockLeaseTime;

    private JedisTemplate jedisTemplate;

    /** 锁名 */
    private String name;

    /** 是否在订阅广播 */
    private AtomicBoolean isSubscribed = new AtomicBoolean(false);

    /** 广播消息处理器 */
    private PubSub pubSub;

    public JedisLock(String name, JedisTemplate jedisTemplate) {
        this.name = name;
        this.internalLockLeaseTime = DEFAULT_LOCK_LEASE_MS;
        this.id = jedisTemplate.id;
        this.pubSub = new PubSub();
        this.jedisTemplate = jedisTemplate;
    }

    /**
     * redis客户端实例上的锁名
     */
    private String getEntryName() {
        return id + ":" + getLockName();
    }

    /**
     * 锁的广播频道名
     */
    private String getChannelName() {
        return "jedis_lock__channel:" + getLockName();
    }

    /**
     * 获取线程锁名
     * 客户端唯一ID+线程ID
     */
    private String getLockThreadName(long threadId) {
        return id + ":" + threadId;
    }

    /** 加了前缀的锁名 */
    private String getLockName() {
        return "lock:" + name;
    }

    /**
     * 上锁，如果未获取得锁会阻塞线程
     */
    @Override
    public void lock() {
        try {
            lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread()
                  .interrupt();
        }
    }

    /**
     * 请求上锁，除非该线程被中断。
     */
    @Override
    public void lockInterruptibly() throws InterruptedException {
        lockInterruptibly(-1, null);
    }

    /**
     * 尝试获取锁，如果当前就能获得，获得锁并返回true,否则获取不到锁返回false
     */
    @Override
    public boolean tryLock() {
        return tryAcquire(-1, null, Thread.currentThread()
                                          .getId()) == null;
    }

    /**
     * 在等待时间内如果获取到锁，则获得锁并返回true，否则获取不到锁并返回false
     *
     * @param waitTime 等待的时间
     * @param unit 等待的时间单位
     *
     * @return true:获取到锁, false: 获取不到锁
     */
    @Override
    public boolean tryLock(long waitTime, TimeUnit unit) throws InterruptedException {
        return tryLock(waitTime, -1, unit);
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        Boolean opStatus = unlockInner(Thread.currentThread()
                                             .getId());
        if (opStatus == null) {
            // 锁不是本线程获得的！
            throw new IllegalMonitorStateException(
                "attempt to unlock lock, not locked by current thread by node id: " + id + " thread-id: " + Thread.currentThread()
                                                                                                                  .getId());
        }
        if (opStatus) {
            // 如果解锁成功，解除自动续时
            cancelExpirationRenewal();
        }
    }

    @Override
    @Deprecated
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    /**
     * 上锁，如果超出时间，会自动释放锁
     */
    public void lock(long leaseTime, TimeUnit unit) {
        try {
            lockInterruptibly(leaseTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread()
                  .interrupt();
        }
    }

    /**
     * 请求上锁，除非该线程被中断。如果超出时间，会自动释放锁。
     */
    public void lockInterruptibly(long leaseTime, TimeUnit unit) throws InterruptedException {
        long threadId = Thread.currentThread()
                              .getId();
        Long ttl = tryAcquire(leaseTime, unit, threadId);
        // 获得锁，直接返回，不阻塞
        if (ttl == null) {
            return;
        }

        // 否则未获得锁，尝试获得锁

        // 创建了另一个线程进行广播监听
        executor.execute(() -> {
            do {
                try {
                    subscribe();
                } catch (Exception ignore) {
                }
                // 持续订阅，直到取消订阅
            } while (isSubscribed.get());
        });

        // 等订阅成功后再往下走, 预防错过了消息广播
        pubSub.getSubscribeLatch()
              .tryAcquire(DEFAULT_LOCK_LEASE_MS, TimeUnit.MILLISECONDS);
        // 这里会阻塞本线程
        try {
            while (true) {
                // 尝试获得锁
                ttl = tryAcquire(leaseTime, unit, threadId);
                if (ttl == null) {
                    break;
                }

                if (ttl >= 0) {
                    // 等待获得锁方法一：订阅广播，等待解锁
                    // 等待获得锁方法二：等待超时，自行尝试获取锁，万一取到了呢
                    // 防止获得锁的线程挂了没有广播释放锁
                    pubSub.getLatch()
                          .tryAcquire(ttl, TimeUnit.MILLISECONDS);
                } else {
                    pubSub.getLatch()
                          .acquire();
                }
            }
        } finally {
            // 获得锁后，取消订阅
            unsubscribe();
        }
    }

    /**
     * 订阅广播
     */
    private void subscribe() {
        jedisTemplate.execute(jedis -> {
            isSubscribed.set(true);
            jedis.subscribe(pubSub, getChannelName());
        });
    }

    /**
     * 取消广播
     */
    private void unsubscribe() {
        if (isSubscribed.get()) {
            isSubscribed.set(false);
            this.pubSub.unsubscribe();
        }
    }

    /**
     * 尝试获得锁
     */
    private Long tryAcquire(long leaseTime, TimeUnit unit, final long threadId) {
        if (leaseTime != -1) {
            // 如果有指定自动释放时间，则过期自动释放
            return tryLockInner(leaseTime, unit, threadId);
        }

        // 如果没有指定释放时间，则只要线程还活着，就需要一直锁着，所以需要给锁续时间
        Long ttl = tryLockInner(DEFAULT_LOCK_LEASE_MS, TimeUnit.MILLISECONDS, threadId);
        scheduleExpirationRenewal(threadId);
        return ttl;
    }

    /**
     * 给锁续时间
     */
    private void scheduleExpirationRenewal(final long threadId) {
        if (expirationRenewalMap.containsKey(getEntryName())) {
            return;
        }

        ScheduledFuture schedule = cron.schedule(() -> {

            // @formatter:off
            String script=
                "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " + // 如果锁是该线程的，就续时间
                "   redis.call('pexpire', KEYS[1], ARGV[1]); " +
                "   return 1; " +
                "end; " +
                "return 0;";
            // @formatter:on
            jedisTemplate.execute(jedis -> {
                expirationRenewalMap.remove(getEntryName());
                Object r = jedis.eval(script, 1, getLockName(), "" + internalLockLeaseTime,
                    getLockThreadName(threadId));
                if (StringTools.getBool(r, false)) {
                    // 如果续时间成功，则继续续下去。
                    scheduleExpirationRenewal(threadId);
                }
            });
        }, internalLockLeaseTime / 3, TimeUnit.MILLISECONDS);

        if (expirationRenewalMap.putIfAbsent(getEntryName(), schedule) != null) {
            // 任务已存在就取消刚刚创建的任务
            schedule.cancel(true);
        }
    }

    /**
     * 取消自动给锁续时间
     */
    private void cancelExpirationRenewal() {
        ScheduledFuture task = expirationRenewalMap.remove(getEntryName());
        if (task != null) {
            task.cancel(true);
        }
    }

    /**
     * 尝试获得锁，如果直接获得，则返回-1，否则被锁，返回解锁时间(单位：毫秒)，
     *
     * @return null:直接获得锁，>0，解锁时间(ms)
     */
    private Long tryLockInner(long leaseTime, TimeUnit unit, long threadId) {
        internalLockLeaseTime = unit.toMillis(leaseTime);

        // @formatter:off
        String script=
            "if (redis.call('exists', KEYS[1]) == 0) then " +             // 如果锁不存在，则获得锁
            "   redis.call('hset', KEYS[1], ARGV[2], 1); " +
            "   redis.call('pexpire', KEYS[1], ARGV[1]); " +
            "   return nil; " +
            "end; " +
            "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +   // 如果锁存在，而且是同线程请求的锁，可以获取锁
            "   redis.call('hincrby', KEYS[1], ARGV[2], 1); " +           // 锁获得+1
            "   redis.call('pexpire', KEYS[1], ARGV[1]); " +
            "   return nil; " +
            "end; " +
            "return redis.call('pttl', KEYS[1]);";                        // 如果锁存在，则返回过期时间，单位毫秒
        // @formatter:on

        return jedisTemplate.execute(jedis -> {
            Object r = jedis.eval(script, 1, getLockName(), "" + internalLockLeaseTime, getLockThreadName(threadId));
            return StringTools.getLong(r, null);
        });
    }

    /**
     * 执行释放锁
     *
     * @return null:该线程未获得此锁
     */
    private Boolean unlockInner(long threadId) {
        // @formatter:off
       String script=
            "if (redis.call('exists', KEYS[1]) == 0) then " +                 // 如果锁不存在，解锁成功
            "   redis.call('publish', KEYS[2], ARGV[1]); " +                  // 广播解锁成功
            "   return 1; " +                                                 // 返回解锁成功
            "end;" +
            "if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then " +       // 如果锁存在，本线程获得锁数为0，则返回null，该线程未获得锁
            "   return nil;" +
            "end; " +
            "local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1); " + // 该线程获得锁-1
            "if (counter > 0) then " +
            "   redis.call('pexpire', KEYS[1], ARGV[2]); " +                  // 如果该线程还获得锁，重置过期时间
            "return 0; " +                                                    // 返回解锁失败，锁还在
            "else " +
            "   redis.call('del', KEYS[1]); " +                               // 如果该线程已全部释放锁，删除锁key
            "   redis.call('publish', KEYS[2], ARGV[1]); " +                  // 广播解锁成功
            "   return 1; "+                                                  // 返回解锁成功
            "end; " +
            "return nil;";                                                    // 否则肯定不是该线程上的锁，不给解
       // @formatter:on

        return jedisTemplate.execute(jedis -> {
            Object r = jedis.eval(script, 2, getLockName(), getChannelName(), unlockMessage, "" + internalLockLeaseTime,
                getLockThreadName(threadId));
            return StringTools.getBool(r, null);
        });
    }

    /**
     * 强行解锁
     */
    public boolean forceUnlock() {
        cancelExpirationRenewal();
        // @formatter:off
        String script=
            "if (redis.call('del', KEYS[1]) == 1) then " +
            "   redis.call('publish', KEYS[2], ARGV[1]); " +
            "   return 1 " +
            "else " +
            "   return 0 " +
            "end";
        // @formatter:on
        return jedisTemplate.execute(jedis -> {
            Object r = jedis.eval(script, 2, getLockName(), getChannelName(), unlockMessage);
            return StringTools.getBool(r, false);
        });
    }

    /**
     * 在等待时间内如果获取到锁，则获得锁并返回true，否则获取不到锁并返回false
     *
     * @param waitTime 等待的时间
     * @param leaseTime 自动释放锁的时间,-1:手动释放
     * @param unit 等待的时间单位
     *
     * @return true:获取到锁, false: 获取不到锁
     */
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        long time = unit.toMillis(waitTime);
        long current = System.currentTimeMillis();
        final long threadId = Thread.currentThread()
                                    .getId();
        Long ttl = tryAcquire(leaseTime, unit, threadId);

        if (ttl == null) {
            // 获得锁，直接返回
            return true;
        }

        time -= (System.currentTimeMillis() - current);
        if (time <= 0) {
            return false;
        }

        current = System.currentTimeMillis();
        // 创建了另一个线程进行广播监听
        executor.execute(() -> {
            do {
                try {
                    subscribe();
                } catch (Exception ignore) {
                }
                // 持续订阅，直到取消订阅
            } while (isSubscribed.get());
        });

        // 等订阅成功后再往下走, 预防错过了消息广播
        pubSub.getSubscribeLatch()
              .tryAcquire(DEFAULT_LOCK_LEASE_MS, TimeUnit.MILLISECONDS);
        // 这里会阻塞本线程
        try {
            time -= (System.currentTimeMillis() - current);
            if (time <= 0) {
                return false;
            }

            while (true) {
                long currentTime = System.currentTimeMillis();

                // 尝试获得锁
                ttl = tryAcquire(leaseTime, unit, threadId);
                if (ttl == null) {
                    return true;
                }

                // 检测是否等待超时
                time -= (System.currentTimeMillis() - currentTime);
                if (time <= 0) {
                    return false;
                }

                // 等待获得锁
                // 等待获得锁方法一：订阅广播，等待解锁
                // 等待获得锁方法二：等待超时，自行尝试获取锁，万一取到了呢
                // 防止获得锁的线程挂了没有广播释放锁
                currentTime = System.currentTimeMillis();
                if (ttl >= 0 && ttl < time) {
                    // 按锁的超时时间等待
                    pubSub.getLatch()
                          .tryAcquire(ttl, TimeUnit.MILLISECONDS);
                } else {
                    // 按等待剩余时间等待
                    pubSub.getLatch()
                          .tryAcquire(time, TimeUnit.MILLISECONDS);
                }

                // 检测是否等待超时
                time -= (System.currentTimeMillis() - currentTime);
                if (time <= 0) {
                    return false;
                }
            }
        } finally {
            unsubscribe();
        }
    }

    /**
     * 是否已存在锁
     */
    public Boolean isExists() {
        return jedisTemplate.execute(jedis -> {
            return jedis.exists(getLockName());
        });
    }

    /**
     * 本线程是否已获得锁
     */
    public Boolean isHeldByCurrentThread() {
        return jedisTemplate.execute(jedis -> {
            return jedis.hexists(getLockName(), getLockThreadName(Thread.currentThread()
                                                                        .getId()));
        });
    }

    /**
     * 本线程获取这个锁的个数
     */
    public int getHoldCount() {

        return jedisTemplate.execute(jedis -> {
            String r = jedis.hget(getLockName(), getLockThreadName(Thread.currentThread()
                                                                         .getId()));
            return StringTools.getInt(r, 0);
        });
    }

    /**
     * 是否已锁上
     */
    public boolean isLocked() {
        return isExists();
    }

    /**
     * 等同于unlock,用于try with resource
     */
    @Override
    public void close() {
        try {
            unlock();
        } catch (Exception ignore) {
        }
    }

    /**
     * 订阅广播
     */
    private class PubSub extends JedisPubSub {

        /** 是否可以尝试获取锁的信号量，跨线程使用 */
        private final Semaphore latch = new Semaphore(0);

        /** 是否已订阅成功 */
        private final Semaphore subscribeLatch = new Semaphore(0);

        // 当接收到消息
        @Override
        public void onMessage(String channel, String message) {

            if (!unlockMessage.equals(message)) {
                // 不是释放锁消息，不管
                return;
            }
            // 接收到一个释放锁的消息，释放一个信号量，让锁的线程尝试获取
            latch.release();
        }

        @Override
        public void onSubscribe(String channel, int subscribedChannels) {
            subscribeLatch.release();
        }

        public Semaphore getLatch() {
            return latch;
        }

        public Semaphore getSubscribeLatch() {
            return subscribeLatch;
        }
    }
}
