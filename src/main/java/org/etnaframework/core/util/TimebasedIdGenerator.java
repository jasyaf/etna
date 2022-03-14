package org.etnaframework.core.util;

import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.slf4j.Logger;

/**
 * <pre>
 * 基于时间戳的Long型主键生成器
 *
 * 生成有带时间戳(起码精确到秒)的long型唯一序列号,最短长度为12位,最长长度为19位
 *  Long.MAX_VALUE = 9223372036854775807 [19位]
 *
 *  yyyyMMddHHmmss   建议的时间格式
 *  accuracyLen      表示精度表达位数
 *  incrLen          表示内部自增器位数
 *
 *  整体序列号 = [yy]yyMMddHHmmss + accuracyLen(包含incrLen)
 *  accuracyLen-incrLen=0 表示精确到秒
 *  accuracyLen-incrLen=3 表示精确到毫秒
 *  accuracyLen-incrLen=6 表示精确到微秒
 *  accuracyLen-incrLen=9 表示精确到纳秒(因为12+9 > 19 已经超出long型,所以精确不到这里)
 *
 * 使用     yyMMddHHmmss表示时,表达到秒已经占用了12位,因此还有7位可用,也就是其最高精度可达到 1/ 10 000 000 (7个零) 秒,即:千万分之一秒
 * 使用yyyyMMddHHmmss表示时,表达到秒已经占用了14位,因此还有5位可用,也就是其最高精度可达到 1/ 100 000 (5个零) 秒,   即:十万分之一秒
 *
 * 注意:yyMMddHHmmss只能表达到2092年,到了2093年就long溢出了
 *
 * 考虑以下场景：
 * 1.如果使用yyMMddHHmmss,而accuracyLen=0,incrLen=0,也就是只精确到1s,这样在nextLongId() 1秒内只能生成1个
 * 2.提高精度：增加accuracyLen=2 这时精确到1/100 分之一秒,nextLongId()可以在1秒内最多生成100个
 * 3.精度不变：修改incrLen=2     这时是只精确到1秒,nextLongId()也可以在1秒内最多生成100个
 *   在高并发情况下,3跟2不同的是：
 *      2是1秒内生成的100个,每一个请求都是平均分布在1秒内的
 *      3是在1秒的前段时间(几乎不耗时,这里假定耗时为k )生成100个,第101请求进来时会卡住一段时间(1s - k)
 * 4.最高精度：accuracyLen=7 这时,nextLongId()每秒最多生成 10 000 000个id
 *
 * 所以结论是：
 * 精度越高(accuracyLen)在高并发情况下,其生成id内部解决冲突情况就越少,生成速度就越快
 * 内部自增器占用位数incrLen>0时,可以让并发请求生成id尽量挤在 前段时间 生成
 * </pre>
 *
 * <pre>
 * 优点：
 * 该Long型主键生成器不依赖于数据库，相比依赖数据表的PrimaryKey生成器，速度快，而且值可读
 * 缺点：
 * 1.该 PrimaryKey生成器 虽然依赖于时间，但是在运行过程中改变时间不会影响,但是如果将系统时间后调，然后重新启动有可能造成PrimaryKey重复。
 * 2.不同于UUID,不可用于集群
 * </pre>
 *
 * @author BlackCat
 * @since 2014-12-30
 */
public class TimebasedIdGenerator {

    public static final long[] DECIMAL_SHIFT_BASE = new long[19]; // 注意long型最长19位

    private static final Logger log = Log.getLogger();

    public static final int LONG_MAX_LEN = ("" + Long.MAX_VALUE).length(); // 注意long型最长19位

    private static final DecimalFormat percentageFormat = new DecimalFormat("#0.00%");

    /**
     * 初始化时的毫秒数，因为该时间会随系统时间的改变而改变， 所以计算方法为该时间加上通过 nanoTime 计算出来的时间差
     */
    private static final Long startMilli;

    /**
     * 初始化时的纳秒数，用来计量时间差，nanoTime不会随着系统时间的修改而改变
     */
    private static final long startNano;

    static {
        // 初始化 DECIMAL_SHIFT_BASE,也就是把long型范围内的所有 10的i次方提前计算出来
        for (int i = 0; i < DECIMAL_SHIFT_BASE.length; i++) {
            DECIMAL_SHIFT_BASE[i] = (long) Math.pow(10, i);
        }
        startNano = System.nanoTime();
        startMilli = System.currentTimeMillis();
    }

    /**
     * 十进制移位
     *
     * @param num 原始值
     * @param len len>0 效果同于 << 左移,也就是后面多len个零,len<0 是除以 10的len次方
     *
     * @return 左移时超过Long范围会报IndexOutOfBoundsException
     */
    public static long decimalShift(long num, int len) {
        if (num == 0) {
            return 0;
        }
        if (len > 18) {
            throw new IndexOutOfBoundsException("decimalShift overflow,num:" + num + ",len:" + len);
        }
        if (len >= 0) {
            long r = num * DECIMAL_SHIFT_BASE[len];
            if (num > 0 ^ r > 0) {
                throw new IndexOutOfBoundsException("decimalShift overflow,num:" + num + ",len:" + len + "result:" + r);
            }
            return r;
        }
        return num / DECIMAL_SHIFT_BASE[len];
    }

    private long _accuracyDividend;

    private long _accuracyMod;

    private long _incrMod = 1;

    /** 精确秒数后n位 */
    private int accuracyLen;

    /** 可以扩展使用的长度 */
    private int canExpandLen = -1;

    /** 生成id时发生冲撞次数 */
    private long collides;

    /** 生成id时发生冲撞后,重试总次数 */
    private long collideTries;

    private final String pattern;

    /**
     * 内部自增器
     */
    private long gens;

    private int incrLen;

    /**
     * 记录上一次生成 的 id，如果新生成的id和上次相等，则需要再次生成
     */
    private volatile long lastId = -1;

    /**
     * 生成锁，用来限定同一时刻只有一个线程进入生成计算
     */
    private final Lock LOCK = new ReentrantLock();

    private long sleepWhenCollide = 0;

    /**
     * <pre>
     *
     * 在accuracyLen < 6的情况下,为了 避免 高并发请求时,大量主键生成请求,把 accuracyLen后incrLen位,用 自增器来生成,默认2位
     * accuracyLen == 4 ,则  秒数精确到 百分之一秒,最后2位自增
     * accuracyLen == 3 ,则  秒数精确到 十分之一秒,最后2位自增
     * accuracyLen == 2 ,则  秒数精确到 秒,最后2位自增
     * accuracyLen == 2 ,精确度太低  >_<,自增器也无能为力
     *
     * 也就是说  1 < accuracyLen < 6时,会默认开启incrLen = 2的自增器
     * </pre>
     *
     * @param accuracyLen>0 精确秒数后n位(最好不要小于3,否则在高并发下有很大冲突)
     * @param dateFormat 时间表示格式，例如yyyyMMddHHmmss
     */
    public TimebasedIdGenerator(int accuracyLen, String dateFormat) {
        this(accuracyLen, accuracyLen < 6 ? 2 : 0, dateFormat);
    }

    /**
     * @param accuracyLen>0 精确秒数后n位(最好不要小于3,否则在高并发下有很大冲突)
     * @param incrLen>0 指定内部使用自增器生成的数字长度(最好是2,这样请求100次时,不会有冲突)
     * @param dateFormat 时间表示格式，例如yyyyMMddHHmmss
     */
    public TimebasedIdGenerator(int accuracyLen, int incrLen, String dateFormat) {
        int accuracyLen_l = accuracyLen;
        int incrLen_l = incrLen;
        if (incrLen_l < 0) {
            incrLen_l = 0;
        }
        if (accuracyLen_l < 0) {
            accuracyLen_l = 0;
        }
        if (incrLen_l > accuracyLen_l) {
            incrLen_l = 0;
        }
        this.pattern = dateFormat;
        try {
            this.canExpandLen = LONG_MAX_LEN - pattern.length() - accuracyLen_l;
            this.accuracyLen = accuracyLen_l;
            this.incrLen = incrLen_l;

            this._accuracyDividend = DECIMAL_SHIFT_BASE[9 - (accuracyLen_l - incrLen_l)];
            this._accuracyMod = DECIMAL_SHIFT_BASE[accuracyLen_l - incrLen_l];

            if (_accuracyMod < 1000) { // 精度度比较低,在发生冲撞时,为了节省cpu,这种情况下 sleep()一下
                sleepWhenCollide = 1000 / _accuracyMod / 4; // 可以去掉这行,跟使用这行,来测试 collide times的区别
                // 1000 / _accuracyMod / 10;
                // 第三个参数 可以调的范围是 1~ 10
                // 1时sleep最长,collide较少,cpu占用较少
                // 10时sleep最短,collide较多,cpu占用较多
                // 如果不sleep, cpu占用最多
            }
            this._incrMod = DECIMAL_SHIFT_BASE[incrLen_l];
        } catch (Exception e) {
        } finally {
            if (canExpandLen < 0) {
                log.error("CREATE ERROR:{}", this);
                throw new IllegalArgumentException("accuracyLen和dateFormat长度之和不能超过" + LONG_MAX_LEN);
            }
            log.debug("CREATE:{}", this);
        }
    }

    public long currentTimeMillis() {
        long pastNano = System.nanoTime() - startNano; // 纳秒时间差
        long pastMilli = pastNano / 1000000; // 取得毫秒差
        return startMilli + pastMilli;
    }

    public int getAccuracyLen() {
        return accuracyLen;
    }

    public int getCanExpandLen() {
        return canExpandLen;
    }

    public long getCollides() {
        return collides;
    }

    public long getCollideTries() {
        return collideTries;
    }

    public long getGens() {
        return gens;
    }

    public int getIncrLen() {
        return incrLen;
    }

    public long getLastId() {
        return lastId;
    }

    public long getSleepWhenCollide() {
        return sleepWhenCollide;
    }

    public String nextId() {
        return nextLongId() + "";
    }

    public Date parse(String id) {
        int patternLen = pattern.length();
        if (id.length() < patternLen) {
            log.warn("id:{},pattern:{},length err", id, pattern);
            return new Date(0);
        }
        String date = id.substring(0, patternLen);
        return DatetimeUtils.parse(date, pattern);
    }

    public Date parse(long id) {
        return parse(id + "");
    }

    public long nextLongId() {
        LOCK.lock();
        // long incr = incrCounter.incrementAndGet();// 不处理溢出的情况,外部已经有锁,这里不用 acomicLong
        gens++;
        long incr = incrLen > 0 ? gens % _incrMod : 0; // 计算出这一次请求使用的计数

        boolean collide = incr == 0; // 在使用自增器的情况下,计算这一次请求是否有冲突
        int collideTimes = 0;
        long newId = 0;
        try {
            while (true) {
                long pastNano = System.nanoTime() - startNano; // 纳秒时间差
                long pastMilli = pastNano / 1000000; // 取得毫秒差
                long pastAccuracyTime = (pastNano / _accuracyDividend) % _accuracyMod;
                newId = Long.parseLong(DatetimeUtils.format(startMilli + pastMilli, Datetime.DF_yyyyMMddHHmmss)) * _accuracyMod * _incrMod + pastAccuracyTime * _incrMod;
                if (incrLen > 0) {
                    if (collide && lastId == newId) {
                        sleepWhenCollide();
                        collideTimes++;
                        continue;
                    }
                    lastId = newId; // 设置 lastPK
                    return newId + incr;
                }
                if (lastId != newId) {
                    lastId = newId; // 设置 lastPK
                    return newId;
                }
                sleepWhenCollide();
                collideTimes++;
            }
        } finally {
            LOCK.unlock();
            if (collideTimes > 0) {
                collides++;
                collideTries += collideTimes;
            }
        }
    }

    private void sleepWhenCollide() {
        try {
            Thread.sleep(sleepWhenCollide);// sleep(0) 方法内部应该有处理0的情况
        } catch (InterruptedException e) {
        }
    }

    /**
     * 计算 当前系统时间 跟 {@link TimebasedIdGenerator}内部维护的时间偏差
     *
     * @return 正数表明 内部时间跑得慢/较旧,负数表明 内部时间路得快/较新(这种情况可能会有 出现 重复id的危险)
     */
    public long timeBias() {
        return System.currentTimeMillis() - currentTimeMillis();
    }

    @Override
    public String toString() {
        String statInfo = "";
        if (gens > 0) {
            long collides_l = this.collides == 0 ? 1 : this.collides;
            statInfo = MessageFormat.format("[gens:{0},collide(tries/num):{2}/{1}->avg:{3},probability:{4},now:{5},bias:{6}] ", gens, collides_l, collideTries, collideTries / collides_l,
                percentageFormat.format((double) this.collides / gens), DatetimeUtils.format(currentTimeMillis(), Datetime.DF_yyyy_MM_dd_HHmmss), timeBias());
        }
        return MessageFormat.format("TimebasedIdGenerator {0}[dateFormat={1}, accuracyLen={2}, incrLen={3}, canExpandLen={4}, sleepWhenCollide={5}]", statInfo, pattern, accuracyLen, incrLen,
            canExpandLen, sleepWhenCollide);
    }
}
