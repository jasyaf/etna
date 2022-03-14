package org.etnaframework.core.util;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import org.etnaframework.core.spring.annotation.Config;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.deserializer.AbstractDateDeserializer;

/**
 * <pre>
 * 日期时间相关处理工具类，参考Joda-Time和jdk8的java.time包下相关类，具有更好的通用性
 *
 * 建议老系统使用{@link DatetimeUtils}中的工具方法，对{@link Date}对象进行操作
 * 新系统直接用{@link Datetime}替代{@link Date}
 *
 * 相关工具方法两套都有，分以下几个部分（其中的XXX为时间单位，例如Days/Weeks），都可以指定时区进行处理
 * {@link Datetime}只保存了毫秒时间按戳，无时区信息，不指定就使用系统默认的
 *
 * now/today            系统变量，表示当前时间的时间戳和当前日期的00:00:00，获取昨天的时间戳可使用DatetimeUtil.today().minusDays(1)
 *
 * isBefore/isAfter     时间先后比较
 *
 * parse                从表示时间的字符串解析出{@link Datetime}对象，在{@link Datetime}中则是通过构造方法进行初始化
 *
 * getXXX               提取日期时间中的值，如获取年份/月/时分秒等
 *
 * retainXXX            时间修剪，例如获取传入时刻所在月的第一天的00:00:00，获取所在时刻的那个小时的开始等
 *
 * format               格式化输出，一部分通用的格式可使用{@link Datetime}.DF_，在{@link Datetime}则是通过toString方法输出
 *
 * plusXXX/minusXXX     时间加减，返回加减后的新实例（不会更改原对象的值，就像{@link String}那样的机制），能自动判断并处理大小月，平年闰年的情况
 *
 * getXXXBetween        获取时间差，支持精确比对或按日期进行比对，例如两个时刻 2015-01-01 12:00:00和2015-02-01 11:00:00
 *                      精确比对的话，后者必须要>=2015-02-01 12:00:00才能算是和前者相差一个月
 *                      按日期比对的话，后者只需要>=>=2015-02-01 00:00:00就可以算是一个月了，适用一些只需要按日期比对判断的场景
 *                      大小月、平年闰年情况能自动判断处理
 *
 * getXXXAfter          功能和getXXXBetween一样，专用于{@link Datetime}类型
 * </pre>
 *
 * @author BlackCat
 * @since 2015-03-07
 */
@Service
public final class DatetimeUtils {

    /** 日期格式->DateFormat映射 */
    private static final Map<String, ThreadLocal<DateFormat>> _dateFormatFactory = new ConcurrentHashMap<String, ThreadLocal<DateFormat>>();

    /** 默认时间格式 */
    private static String defaultDateTimeFormat = Datetime.DF_yyyy_MM_dd_HHmmss_SSSZ;

    /** 默认日期格式 */
    private static String defaultDateFormat = Datetime.DF_yyyy_MM_dd;

    private static Datetime testNow;

    private static Datetime testToday;

    private DatetimeUtils() {
    }

    @Config("etna.defaultDatetimeFormat")
    protected static void setDateTimeFormat(String format) {
        defaultDateTimeFormat = format;
    }

    @Config("etna.defaultDateFormat")
    protected static void setDateFormat(String format) {
        defaultDateFormat = format;
    }

    /**
     * 设置在单元测试中{@link DatetimeUtils#now()}的返回值，用于在测试中变造测试时间
     */
    public static void setTestNow(Datetime testNow) {
        if (SystemInfo.IN_TEST) {
            DatetimeUtils.testNow = testNow;
        } else {
            throw new IllegalAccessError("本方法只能在JUnit测试用例中使用");
        }
    }

    /**
     * 设置在单元测试中{@link DatetimeUtils#today()}的返回值，用于在测试中变造测试时间
     */
    public static void setTestToday(Datetime testToday) {
        if (SystemInfo.IN_TEST) {
            DatetimeUtils.testToday = testToday;
        } else {
            throw new IllegalAccessError("本方法只能在JUnit测试用例中使用");
        }
    }

    /**
     * 默认时间格式
     */
    public static String getDefaultDatetimeFormat() {
        return defaultDateTimeFormat;
    }

    /**
     * 默认日期格式
     */
    public static String getDefaultDateFormat() {
        return defaultDateFormat;
    }

    /**
     * 注意{@link DateFormat}不是线程安全的，在多线程环境下需要做同步处理，这里使用{@link ThreadLocal}让每个线程一个实例避免并发问题
     */
    private static ThreadLocal<DateFormat> _prepareDateFormatPerThread(final String format) {
        return new ThreadLocal<DateFormat>() {

            @Override
            protected synchronized DateFormat initialValue() {
                try {
                    DateFormat df = new SimpleDateFormat(format, Locale.getDefault());
                    df.setLenient(false);
                    return df;
                } catch (Exception e) {
                    return null;
                }
            }
        };
    }

    /**
     * 获得线程安全的{@link DateFormat}（注意由于设置了时区，为了保证不出问题，请每次都要重新获取之）
     *
     * @param tz 如果为null表示使用当前系统默认的时区
     */
    private static DateFormat _getDateFormat(String format, TimeZone tz) {
        ThreadLocal<DateFormat> tdf = _dateFormatFactory.get(format);
        if (null == tdf) {
            tdf = _prepareDateFormatPerThread(format);
            _dateFormatFactory.put(format, tdf);
        }
        DateFormat df = tdf.get();
        if (null == df) { // 如果获取到null，就新建之
            df = new SimpleDateFormat(format, Locale.getDefault());
        }
        if (tz != null) {
            df.setTimeZone(tz);
        } else {
            df.setTimeZone(TimeZone.getDefault());
        }
        return df;
    }

    /**
     * <pre>
     * 获取当前时刻的{@link Datetime}，包含时分秒部分
     *
     * 请注意，Date对象中只保存了GMT时间戳，即1970-01-01T00:00:00+00:00到当前的毫秒数
     * 本身不包含时区信息，时区需要转出来的时候再才能指定，如果toString()会使用系统当前的时区自动转
     * </pre>
     */
    public static Datetime now() {
        if (null != testNow) {
            return testNow;
        }
        return new Datetime();
    }

    /**
     * 获取当前系统默认的时区的当天日期{@link Datetime}，时分秒部分为当天的00:00:00
     */
    public static Datetime today() {
        if (null != testToday) {
            return testToday;
        }
        return today(TimeZone.getDefault());
    }

    /**
     * 获取当前系统默认的时区的昨天日期{@link Datetime}，时分秒部分为当天的00:00:00
     */
    public static Datetime yesterday() {
        return today().minusDays(1);
    }

    /**
     * 获取当前系统默认的时区的明天日期{@link Datetime}，时分秒部分为当天的00:00:00
     */
    public static Datetime tomorrow() {
        return today().plusDays(1);
    }

    // 常用的时间变量

    /**
     * 获取指定时区的当天日期{@link Datetime}，时分秒部分为当天的00:00:00
     */
    public static Datetime today(TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new Datetime(cal.getTime());
    }

    /**
     * 判断before代表时刻是否在after之前
     */
    public static boolean isBefore(Date before, Date after) {
        return before.getTime() < after.getTime();
    }

    /**
     * 判断date代表时刻是否在当前时刻之前
     */
    public static boolean isBeforeNow(Date date) {
        return date.getTime() < System.currentTimeMillis();
    }

    // 时间比较

    /**
     * 判断before代表时刻是否在after之后
     */
    public static boolean isAfter(Date before, Date after) {
        return before.getTime() > after.getTime();
    }

    /**
     * 判断date代表时刻是否在当前时刻之后
     */
    public static boolean isAfterNow(Date date) {
        return date.getTime() > System.currentTimeMillis();
    }

    /**
     * 判断before代表时刻是否在after之前
     *
     * @param before/after 如果解析失败,直接返回false
     */
    public static boolean isBefore(String before, String after) {
        Datetime _before = parse(before);
        Datetime _after = parse(after);
        return !(null == _before || null == _after) && _before.getTime() < _after.getTime();
    }

    /**
     * 判断date代表时刻是否在当前时刻之前
     *
     * @param date 如果解析失败,直接返回false
     */
    public static boolean isBeforeNow(String date) {
        Datetime _date = parse(date);
        return null != _date && _date.getTime() < System.currentTimeMillis();
    }

    /**
     * 判断before代表时刻是否在after之后
     *
     * @param before/after 如果解析失败,直接返回false
     */
    public static boolean isAfter(String before, String after) {
        Datetime _before = parse(before);
        Datetime _after = parse(after);
        return !(null == _before || null == _after) && _before.getTime() > _after.getTime();
    }

    /**
     * 判断date代表时刻是否在当前时刻之后
     *
     * @param date 如果解析失败,直接返回false
     */
    public static boolean isAfterNow(String date) {
        Datetime _date = parse(date);
        return null != _date && _date.getTime() > System.currentTimeMillis();
    }

    /**
     * 将字符串的时间转换为{@link Date}，默认格式为yyyy-MM-dd HH:mm:ss.SSSZ，默认输出格式可在{@link DatetimeUtils}中指定，使用当前系统默认的时区
     * 支持智能判断时间格式，提高兼容性
     *
     * @return 如果转换过程中出现异常将返回null
     */
    public static Datetime parse(String datetime) {
        return parse(datetime, (TimeZone) null);
    }

    /**
     * 将字符串的时间转换为{@link Date}，使用当前系统默认的时区
     *
     * @return 如果转换过程中出现异常将返回null
     */
    public static Datetime parse(String date, String format) {
        try {
            return null != date ? new Datetime(_getDateFormat(format, null).parse(date)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // 解析字符串，获取时间

    /**
     * 将字符串的时间转换为{@link Date}，默认格式为yyyy-MM-dd HH:mm:ss.SSSZ，默认输出格式可在{@link DatetimeUtils}中指定，使用指定的时区
     * 支持智能判断时间格式，提高兼容性
     *
     * @return 如果转换过程中出现异常将返回null
     */
    public static Datetime parse(String date, TimeZone tz) {
        if (null == date || date.isEmpty()) {
            return null;
        }
        Datetime result = null;
        try {
            result = null != date ? new Datetime(_getDateFormat(defaultDateTimeFormat, tz).parse(date)) : null;
        } catch (Exception e) {
        }
        if (null == result) {
            // 如果传入的是date的格式，则按date格式解析，增强兼容性
            if (date.length() == defaultDateFormat.length()) {
                try {
                    return new Datetime(_getDateFormat(defaultDateFormat, tz).parse(date));
                } catch (Exception ex) {
                }
            }
            if (date.length() == defaultDateTimeFormat.length()) {
                try {
                    return new Datetime(_getDateFormat(defaultDateTimeFormat, tz).parse(date));
                } catch (Exception ex) {
                }
            }
            // 特殊判断
            if (date.length() == Datetime.DF_yyyyMMdd.length()) {
                try {
                    return new Datetime(_getDateFormat(Datetime.DF_yyyyMMdd, tz).parse(date));
                } catch (Exception ex) {
                }
            }
            if (date.contains("-")) {
                if (date.length() == Datetime.DF_yyyy_MM_dd_HHmmss.length()) {
                    try {
                        return new Datetime(_getDateFormat(Datetime.DF_yyyy_MM_dd_HHmmss, tz).parse(date));
                    } catch (Exception ex) {
                    }
                }
                if (date.length() == Datetime.DF_yyyy_MM_dd.length()) {
                    try {
                        return new Datetime(_getDateFormat(Datetime.DF_yyyy_MM_dd, tz).parse(date));
                    } catch (Exception ex) {
                    }
                }
            }
            if (date.contains("/")) {
                try {
                    return new Datetime(_getDateFormat("yyyy/MM/dd HH:mm:ss", tz).parse(date));
                } catch (Exception ex) {
                }
                try {
                    return new Datetime(_getDateFormat("yyyy/MM/dd", tz).parse(date));
                } catch (Exception ex) {
                }
            }
            if (date.contains("年")) {
                try {
                    return new Datetime(_getDateFormat("yyyy年MM月dd日HH:mm:ss", tz).parse(date));
                } catch (Exception ex) {
                }
                try {
                    return new Datetime(_getDateFormat("yyyy年MM月dd日", tz).parse(date));
                } catch (Exception ex) {
                }
            }
            if (date.contains("T")) {
                try {
                    return new Datetime(_getDateFormat(Datetime.DF_yyyy_MM_dd_T_HHmmss_SSSZ, tz).parse(date));
                } catch (Exception ex) {
                }
            }
            if (date.contains("UTC")) {
                try {
                    return new Datetime(_getDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", tz).parse(date));
                } catch (Exception ex) {
                }
            }
        }
        return result;
    }

    /**
     * 将字符串的时间转换为{@link Date}，如果转换过程中出现异常将返回null，使用指定的格式和时区
     *
     * @return 如果转换过程中出现异常将返回null
     */
    public static Datetime parse(String date, String format, TimeZone tz) {
        try {
            return null != date ? new Datetime(_getDateFormat(format, tz).parse(date)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将字符串的日期转换为{@link Date}，默认格式为yyyy-MM-dd，默认输出格式可在{@link DatetimeUtils}中指定，使用当前系统默认的时区
     *
     * @return 如果转换过程中出现异常将返回null
     */
    public static Datetime parseDate(String date) {
        try {
            return null != date ? new Datetime(_getDateFormat(defaultDateFormat, null).parse(date)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将字符串的日期转换为{@link Date}，默认格式为yyyy-MM-dd，默认输出格式可在{@link DatetimeUtils}中指定，使用指定的时区
     *
     * @return 如果转换过程中出现异常将返回null
     */
    public static Datetime parseDate(String date, TimeZone tz) {
        try {
            return null != date ? new Datetime(_getDateFormat(defaultDateFormat, tz).parse(date)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取指定毫秒时间戳对应时刻的【毫秒】，如12:30:45.123，返回123
     *
     * @return 返回范围0-999
     */
    public static int getMillisOfSecond(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        return cal.get(Calendar.MILLISECOND);
    }

    /**
     * 获取指定毫秒时间戳对应时刻的【秒】，如12:30:45，返回45
     *
     * @return 返回范围0-59
     */
    public static int getSecondOfMinute(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        return cal.get(Calendar.SECOND);
    }

    // 提取日期时间中的值

    /**
     * 获取指定毫秒时间戳对应时刻的【分】，如12:30:45，返回30，使用系统默认的时区
     *
     * @return 返回范围0-59
     */
    public static int getMinuteOfHour(long timestamp) {
        return getMinuteOfHour(timestamp, TimeZone.getDefault());
    }

    /**
     * 获取指定毫秒时间戳对应时刻的【分】，如12:30:45，返回30，使用指定的时区
     *
     * @return 返回范围0-23
     */
    public static int getMinuteOfHour(long timestamp, TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        return cal.get(Calendar.MINUTE);
    }

    /**
     * 获取指定毫秒时间戳对应时刻的【时】，如12:30:45，返回12，使用系统默认的时区
     *
     * @return 返回范围0-23
     */
    public static int getHourOfDay(long timestamp) {
        return getHourOfDay(timestamp, TimeZone.getDefault());
    }

    /**
     * 获取指定毫秒时间戳对应时刻的【时】，如12:30:45，返回12，使用指定的时区
     *
     * @return 返回范围0-59
     */
    public static int getHourOfDay(long timestamp, TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        return cal.get(Calendar.HOUR_OF_DAY);
    }

    /**
     * 获取指定毫秒时间戳对应日期是星期几，星期天为0，星期一到六为1-6，使用系统默认的时区
     *
     * @return 返回范围0-6 对应星期天到星期六，可使用{@link Datetime}的SUNDAY - SATURSDAY来指代
     */
    public static int getDayOfWeek(long timestamp) {
        return getDayOfWeek(timestamp, TimeZone.getDefault());
    }

    /**
     * 获取指定毫秒时间戳对应日期是星期几，星期天为0，星期一到六为1-6，使用指定的时区
     *
     * @return 返回范围0-6 对应星期天到星期六，可使用{@link Datetime}的SUNDAY - SATURSDAY来指代
     */
    public static int getDayOfWeek(long timestamp, TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        return cal.get(Calendar.DAY_OF_WEEK) - 1;
    }

    /**
     * 获取指定毫秒时间戳对应的月份的日期，如2月1日，就返回1，使用系统默认的时区
     *
     * @return 返回范围1-31，最大为当月的最后一天的数值
     */
    public static int getDayOfMonth(long timestamp) {
        return getDayOfMonth(timestamp, TimeZone.getDefault());
    }

    /**
     * 获取指定毫秒时间戳对应的月份的日期，如2月1日，就返回1，使用指定的时区
     *
     * @return 返回范围1-31，最大为当月的最后一天的数值
     */
    public static int getDayOfMonth(long timestamp, TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        return cal.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 获取指定毫秒时间戳对应日期是这一年的第几天，使用系统默认的时区
     *
     * @return 返回范围平年1-365，闰年1-366
     */
    public static int getDayOfYear(long timestamp) {
        return getDayOfYear(timestamp, TimeZone.getDefault());
    }

    /**
     * 获取指定毫秒时间戳对应日期是这一年的第几天，使用指定的时区
     *
     * @return 返回范围平年1-365，闰年1-366
     */
    public static int getDayOfYear(long timestamp, TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        return cal.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * 获取指定毫秒时间戳对应日期的【月】，使用系统默认的时区
     *
     * @return 返回范围1-12，可使用{@link Datetime}的JANUARY - DECEMBER来指代
     */
    public static int getMonthOfYear(long timestamp) {
        return getMonthOfYear(timestamp, TimeZone.getDefault());
    }

    /**
     * 获取指定毫秒时间戳对应日期的【月】，使用指定的时区
     *
     * @return 返回范围1-12，可使用{@link Datetime}的JANUARY - DECEMBER来指代
     */
    public static int getMonthOfYear(long timestamp, TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        return cal.get(Calendar.MONTH) + 1;
    }

    /**
     * 获取指定毫秒时间戳对应日期的【年】，公元前的时间，例如221BC将表示为-221，使用系统默认的时区
     */
    public static int getYear(long timestamp) {
        return getYear(timestamp, TimeZone.getDefault());
    }

    /**
     * 获取指定毫秒时间戳对应日期的【年】，公元前的时间，例如221BC将表示为-221，使用指定的时区
     */
    public static int getYear(long timestamp, TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        if (cal.get(Calendar.ERA) == GregorianCalendar.BC) {
            return -cal.get(Calendar.YEAR);
        }
        return cal.get(Calendar.YEAR);
    }

    /**
     * 获取指定{@link Date}对应时刻的【毫秒】，如12:30:45.123，返回123
     *
     * @return 返回范围0-999
     */
    public static int getMillisOfSecond(Date date) {
        return getMillisOfSecond(date.getTime());
    }

    /**
     * 获取指定{@link Date}对应时刻的【秒】，如12:30:45，返回45
     *
     * @return 返回范围0-59
     */
    public static int getSecondOfMinute(Date date) {
        return getSecondOfMinute(date.getTime());
    }

    /**
     * 获取指定{@link Date}对应时刻的【分】，如12:30:45，返回30，使用系统默认的时区
     *
     * @return 返回范围0-59
     */
    public static int getMinuteOfHour(Date date) {
        return getMinuteOfHour(date.getTime());
    }

    /**
     * 获取指定{@link Date}对应时刻的【分】，如12:30:45，返回30，使用指定的时区
     *
     * @return 返回范围0-23
     */
    public static int getMinuteOfHour(Date date, TimeZone tz) {
        return getMinuteOfHour(date.getTime(), tz);
    }

    /**
     * 获取指定{@link Date}对应时刻的【时】，如12:30:45，返回12，使用系统默认的时区
     *
     * @return 返回范围0-23
     */
    public static int getHourOfDay(Date date) {
        return getHourOfDay(date.getTime());
    }

    /**
     * 获取指定{@link Date}对应时刻的【时】，如12:30:45，返回12，使用指定的时区
     *
     * @return 返回范围0-59
     */
    public static int getHourOfDay(Date date, TimeZone tz) {
        return getHourOfDay(date.getTime(), tz);
    }

    /**
     * 获取指定{@link Date}对应日期是星期几，星期天为0，星期一到六为1-6，使用系统默认的时区
     *
     * @return 返回范围0-6 对应星期天到星期六，可使用{@link Datetime}的SUNDAY - SATURSDAY来指代
     */
    public static int getDayOfWeek(Date date) {
        return getDayOfWeek(date.getTime());
    }

    /**
     * 获取指定{@link Date}对应日期是星期几，星期天为0，星期一到六为1-6，使用指定的时区
     *
     * @return 返回范围0-6 对应星期天到星期六，可使用{@link Datetime}的SUNDAY - SATURSDAY来指代
     */
    public static int getDayOfWeek(Date date, TimeZone tz) {
        return getDayOfWeek(date.getTime(), tz);
    }

    /**
     * 获取指定{@link Date}对应的月份的日期，如2月1日，就返回1，使用系统默认的时区
     *
     * @return 返回范围1-31，最大为当月的最后一天的数值
     */
    public static int getDayOfMonth(Date date) {
        return getDayOfMonth(date.getTime());
    }

    /**
     * 获取指定{@link Date}对应的月份的日期，如2月1日，就返回1，使用指定的时区
     *
     * @return 返回范围1-31，最大为当月的最后一天的数值
     */
    public static int getDayOfMonth(Date date, TimeZone tz) {
        return getDayOfMonth(date.getTime(), tz);
    }

    /**
     * 获取指定{@link Date}对应日期是这一年的第几天，使用系统默认的时区
     *
     * @return 返回范围平年1-365，闰年1-366
     */
    public static int getDayOfYear(Date date) {
        return getDayOfYear(date.getTime());
    }

    /**
     * 获取指定{@link Date}对应日期是这一年的第几天，使用指定的时区
     *
     * @return 返回范围平年1-365，闰年1-366
     */
    public static int getDayOfYear(Date date, TimeZone tz) {
        return getDayOfYear(date.getTime(), tz);
    }

    /**
     * 获取指定{@link Date}对应日期的【月】，使用系统默认的时区
     *
     * @return 返回范围1-12，可使用{@link Datetime}的JANUARY - DECEMBER来指代
     */
    public static int getMonthOfYear(Date date) {
        return getMonthOfYear(date.getTime());
    }

    /**
     * 获取指定{@link Date}对应日期的【月】，使用指定的时区
     *
     * @return 返回范围1-12，可使用{@link Datetime}的JANUARY - DECEMBER来指代
     */
    public static int getMonthOfYear(Date date, TimeZone tz) {
        return getMonthOfYear(date.getTime(), tz);
    }

    /**
     * 获取指定{@link Date}对应日期的【年】，公元前的时间，例如221BC将表示为-221，使用系统默认的时区
     */
    public static int getYear(Date date) {
        return getYear(date.getTime());
    }

    /**
     * 获取指定{@link Date}对应日期的【年】，公元前的时间，例如221BC将表示为-221，使用指定的时区
     */
    public static int getYear(Date date, TimeZone tz) {
        return getYear(date.getTime(), tz);
    }

    /**
     * 获取指定{@link Date}对应时刻的【毫秒】，如12:30:45.123，返回123
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回{@link Integer#MIN_VALUE}
     *
     * @return 返回范围0-999
     */
    public static int getMillisOfSecond(String date) {
        Datetime _date = parse(date);
        return _date == null ? Integer.MIN_VALUE : getMillisOfSecond(_date.getTime());
    }

    /**
     * 获取指定{@link Date}对应时刻的【秒】，如12:30:45，返回45
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回{@link Integer#MIN_VALUE}
     *
     * @return 返回范围0-59
     */
    public static int getSecondOfMinute(String date) {
        Datetime _date = parse(date);
        return _date == null ? Integer.MIN_VALUE : getSecondOfMinute(_date.getTime());
    }

    /**
     * 获取指定{@link Date}对应时刻的【分】，如12:30:45，返回30，使用系统默认的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回{@link Integer#MIN_VALUE}
     *
     * @return 返回范围0-59
     */
    public static int getMinuteOfHour(String date) {
        Datetime _date = parse(date);
        return _date == null ? Integer.MIN_VALUE : getMinuteOfHour(_date.getTime());
    }

    /**
     * 获取指定{@link Date}对应时刻的【分】，如12:30:45，返回30，使用指定的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回{@link Integer#MIN_VALUE}
     *
     * @return 返回范围0-23
     */
    public static int getMinuteOfHour(String date, TimeZone tz) {
        Datetime _date = parse(date);
        return _date == null ? Integer.MIN_VALUE : getMinuteOfHour(_date.getTime(), tz);
    }

    /**
     * 获取指定{@link Date}对应时刻的【时】，如12:30:45，返回12，使用系统默认的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回{@link Integer#MIN_VALUE}
     *
     * @return 返回范围0-23
     */
    public static int getHourOfDay(String date) {
        Datetime _date = parse(date);
        return _date == null ? Integer.MIN_VALUE : getHourOfDay(_date.getTime());
    }

    /**
     * 获取指定{@link Date}对应时刻的【时】，如12:30:45，返回12，使用指定的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回{@link Integer#MIN_VALUE}
     *
     * @return 返回范围0-59
     */
    public static int getHourOfDay(String date, TimeZone tz) {
        Datetime _date = parse(date);
        return _date == null ? Integer.MIN_VALUE : getHourOfDay(_date.getTime(), tz);
    }

    /**
     * 获取指定{@link Date}对应日期是星期几，星期天为0，星期一到六为1-6，使用系统默认的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回{@link Integer#MIN_VALUE}
     *
     * @return 返回范围0-6 对应星期天到星期六，可使用{@link Datetime}的SUNDAY - SATURSDAY来指代
     */
    public static int getDayOfWeek(String date) {
        Datetime _date = parse(date);
        return _date == null ? Integer.MIN_VALUE : getDayOfWeek(_date.getTime());
    }

    /**
     * 获取指定{@link Date}对应日期是星期几，星期天为0，星期一到六为1-6，使用指定的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回{@link Integer#MIN_VALUE}
     *
     * @return 返回范围0-6 对应星期天到星期六，可使用{@link Datetime}的SUNDAY - SATURSDAY来指代
     */
    public static int getDayOfWeek(String date, TimeZone tz) {
        Datetime _date = parse(date);
        return _date == null ? Integer.MIN_VALUE : getDayOfWeek(_date.getTime(), tz);
    }

    /**
     * 获取指定{@link Date}对应的月份的日期，如2月1日，就返回1，使用系统默认的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回{@link Integer#MIN_VALUE}
     *
     * @return 返回范围1-31，最大为当月的最后一天的数值
     */
    public static int getDayOfMonth(String date) {
        Datetime _date = parse(date);
        return _date == null ? Integer.MIN_VALUE : getDayOfMonth(_date.getTime());
    }

    /**
     * 获取指定{@link Date}对应的月份的日期，如2月1日，就返回1，使用指定的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回{@link Integer#MIN_VALUE}
     *
     * @return 返回范围1-31，最大为当月的最后一天的数值
     */
    public static int getDayOfMonth(String date, TimeZone tz) {
        Datetime _date = parse(date);
        return _date == null ? Integer.MIN_VALUE : getDayOfMonth(_date.getTime(), tz);
    }

    /**
     * 获取指定{@link Date}对应日期是这一年的第几天，使用系统默认的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回{@link Integer#MIN_VALUE}
     *
     * @return 返回范围平年1-365，闰年1-366
     */
    public static int getDayOfYear(String date) {
        Datetime _date = parse(date);
        return _date == null ? Integer.MIN_VALUE : getDayOfYear(_date.getTime());
    }

    /**
     * 获取指定{@link Date}对应日期是这一年的第几天，使用指定的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回{@link Integer#MIN_VALUE}
     *
     * @return 返回范围平年1-365，闰年1-366
     */
    public static int getDayOfYear(String date, TimeZone tz) {
        Datetime _date = parse(date);
        return _date == null ? Integer.MIN_VALUE : getDayOfYear(_date.getTime(), tz);
    }

    /**
     * 获取指定{@link Date}对应日期的【月】，使用系统默认的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回{@link Integer#MIN_VALUE}
     *
     * @return 返回范围1-12，可使用{@link Datetime}的JANUARY - DECEMBER来指代
     */
    public static int getMonthOfYear(String date) {
        Datetime _date = parse(date);
        return _date == null ? Integer.MIN_VALUE : getMonthOfYear(_date.getTime());
    }

    /**
     * 获取指定{@link Date}对应日期的【月】，使用指定的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回{@link Integer#MIN_VALUE}
     *
     * @return 返回范围1-12，可使用{@link Datetime}的JANUARY - DECEMBER来指代
     */
    public static int getMonthOfYear(String date, TimeZone tz) {
        Datetime _date = parse(date);
        return _date == null ? Integer.MIN_VALUE : getMonthOfYear(_date.getTime(), tz);
    }

    /**
     * 获取指定{@link Date}对应日期的【年】，公元前的时间，例如221BC将表示为-221，使用系统默认的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回{@link Integer#MIN_VALUE}
     */
    public static int getYear(String date) {
        Datetime _date = parse(date);
        return _date == null ? Integer.MIN_VALUE : getYear(_date.getTime());
    }

    /**
     * 获取指定{@link Date}对应日期的【年】，公元前的时间，例如221BC将表示为-221，使用指定的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回{@link Integer#MIN_VALUE}
     */
    public static int getYear(String date, TimeZone tz) {
        Datetime _date = parse(date);
        return _date == null ? Integer.MIN_VALUE : getYear(_date.getTime(), tz);
    }

    /**
     * 修剪获取时刻所在的那一【秒】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 12:34.56.000
     */
    public static Datetime retainSecond(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.MILLISECOND, 0);
        return new Datetime(cal.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【分】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 12:34.00.000
     */
    public static Datetime retainMinute(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new Datetime(cal.getTime());
    }

    // 时间修剪

    /**
     * 修剪获取时刻所在的那一【小时】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 12:00.00.000，使用系统默认的时区
     */
    public static Datetime retainHour(long timestamp) {
        return retainHour(timestamp, TimeZone.getDefault());
    }

    /**
     * 修剪获取时刻所在的那一【小时】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 12:00.00.000，使用指定的时区
     */
    public static Datetime retainHour(long timestamp, TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new Datetime(cal.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【天】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用系统默认的时区
     */
    public static Datetime retainDay(long timestamp) {
        return retainDay(timestamp, TimeZone.getDefault());
    }

    /**
     * 修剪获取时刻所在的那一【天】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用指定的时区
     */
    public static Datetime retainDay(long timestamp, TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new Datetime(cal.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【周的第一天（星期天为每周的第一天）】，例如2015-02-28 12:34:56.789，修剪到星期天将返回2015-02-22 00:00.00.000，使用系统默认的时区
     */
    public static Datetime retainWeek(long timestamp) {
        return retainWeek(timestamp, TimeZone.getDefault());
    }

    /**
     * 修剪获取时刻所在的那一【周的第一天（星期天为每周的第一天）】，例如2015-02-28 12:34:56.789，修剪到星期天将返回2015-02-22 00:00.00.000，使用指定的时区
     */
    public static Datetime retainWeek(long timestamp, TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.DAY_OF_WEEK, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new Datetime(cal.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【月的第一天】，例如2015-01-02 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用系统默认的时区
     */
    public static Datetime retainMonth(long timestamp) {
        return retainMonth(timestamp, TimeZone.getDefault());
    }

    /**
     * 修剪获取时刻所在的那一【月的第一天】，例如2015-01-02 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用指定的时区
     */
    public static Datetime retainMonth(long timestamp, TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new Datetime(cal.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【年的第一天】，例如2015-01-02 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用系统默认的时区
     */
    public static Datetime retainYear(long timestamp) {
        return retainYear(timestamp, TimeZone.getDefault());
    }

    /**
     * 修剪获取时刻所在的那一【年的第一天】，例如2015-01-02 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用指定的时区
     */
    public static Datetime retainYear(long timestamp, TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.MONTH, 0);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new Datetime(cal.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【秒】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 12:34.56.000
     */
    public static Datetime retainSecond(Date date) {
        return retainSecond(date.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【分】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 12:34.00.000
     */
    public static Datetime retainMinute(Date date) {
        return retainMinute(date.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【小时】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 12:00.00.000，使用系统默认的时区
     */
    public static Datetime retainHour(Date date) {
        return retainHour(date.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【小时】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 12:00.00.000，使用指定的时区
     */
    public static Datetime retainHour(Date date, TimeZone tz) {
        return retainHour(date.getTime(), tz);
    }

    /**
     * 修剪获取时刻所在的那一【天】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用系统默认的时区
     */
    public static Datetime retainDay(Date date) {
        return retainDay(date.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【天】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用指定的时区
     */
    public static Datetime retainDay(Date date, TimeZone tz) {
        return retainDay(date.getTime(), tz);
    }

    /**
     * 修剪获取时刻所在的那一【周的第一天（星期天为每周的第一天）】，例如2015-02-28 12:34:56.789，修剪到星期天将返回2015-02-22 00:00.00.000，使用系统默认的时区
     */
    public static Datetime retainWeek(Date date) {
        return retainWeek(date.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【周的第一天（星期天为每周的第一天）】，例如2015-02-28 12:34:56.789，修剪到星期天将返回2015-02-22 00:00.00.000，使用指定的时区
     */
    public static Datetime retainWeek(Date date, TimeZone tz) {
        return retainWeek(date.getTime(), tz);
    }

    /**
     * 修剪获取时刻所在的那一【月的第一天】，例如2015-01-02 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用系统默认的时区
     */
    public static Datetime retainMonth(Date date) {
        return retainMonth(date.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【月的第一天】，例如2015-01-02 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用指定的时区
     */
    public static Datetime retainMonth(Date date, TimeZone tz) {
        return retainMonth(date.getTime(), tz);
    }

    /**
     * 修剪获取时刻所在的那一【年的第一天】，例如2015-01-02 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用系统默认的时区
     */
    public static Datetime retainYear(Date date) {
        return retainYear(date.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【年的第一天】，例如2015-01-02 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用指定的时区
     */
    public static Datetime retainYear(Date date, TimeZone tz) {
        return retainYear(date.getTime(), tz);
    }

    /**
     * 修剪获取时刻所在的那一【秒】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 12:34.56.000
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     */
    public static Datetime retainSecond(String date) {
        Datetime _date = parse(date);
        return _date == null ? null : retainSecond(_date.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【分】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 12:34.00.000
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     */
    public static Datetime retainMinute(String date) {
        Datetime _date = parse(date);
        return _date == null ? null : retainMinute(_date.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【小时】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 12:00.00.000，使用系统默认的时区
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     */
    public static Datetime retainHour(String date) {
        Datetime _date = parse(date);
        return _date == null ? null : retainHour(_date.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【小时】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 12:00.00.000，使用指定的时区
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     */
    public static Datetime retainHour(String date, TimeZone tz) {
        Datetime _date = parse(date);
        return _date == null ? null : retainHour(_date.getTime(), tz);
    }

    /**
     * 修剪获取时刻所在的那一【天】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用系统默认的时区
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     */
    public static Datetime retainDay(String date) {
        Datetime _date = parse(date);
        return _date == null ? null : retainDay(_date.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【天】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用指定的时区
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     */
    public static Datetime retainDay(String date, TimeZone tz) {
        Datetime _date = parse(date);
        return _date == null ? null : retainDay(_date.getTime(), tz);
    }

    /**
     * 修剪获取时刻所在的那一【周的第一天（星期天为每周的第一天）】，例如2015-02-28 12:34:56.789，修剪到星期天将返回2015-02-22 00:00.00.000，使用系统默认的时区
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     */
    public static Datetime retainWeek(String date) {
        Datetime _date = parse(date);
        return _date == null ? null : retainWeek(_date.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【周的第一天（星期天为每周的第一天）】，例如2015-02-28 12:34:56.789，修剪到星期天将返回2015-02-22 00:00.00.000，使用指定的时区
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     */
    public static Datetime retainWeek(String date, TimeZone tz) {
        Datetime _date = parse(date);
        return _date == null ? null : retainWeek(_date.getTime(), tz);
    }

    /**
     * 修剪获取时刻所在的那一【月的第一天】，例如2015-01-02 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用系统默认的时区
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     */
    public static Datetime retainMonth(String date) {
        Datetime _date = parse(date);
        return _date == null ? null : retainMonth(_date.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【月的第一天】，例如2015-01-02 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用指定的时区
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     */
    public static Datetime retainMonth(String date, TimeZone tz) {
        Datetime _date = parse(date);
        return _date == null ? null : retainMonth(_date.getTime(), tz);
    }

    /**
     * 修剪获取时刻所在的那一【年的第一天】，例如2015-01-02 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用系统默认的时区
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     */
    public static Datetime retainYear(String date) {
        Datetime _date = parse(date);
        return _date == null ? null : retainYear(_date.getTime());
    }

    /**
     * 修剪获取时刻所在的那一【年的第一天】，例如2015-01-02 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用指定的时区
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     */
    public static Datetime retainYear(String date, TimeZone tz) {
        Datetime _date = parse(date);
        return _date == null ? null : retainYear(_date.getTime(), tz);
    }

    /**
     * 将毫秒时间戳（自1970-01-01T00:00:00+00:00到所代表时刻的毫秒数）转换为对应格式的时间字符串，默认格式为yyyy-MM-dd HH:mm:ss.SSSZ，默认输出格式可在{@link DatetimeUtils}中指定，使用当前系统默认的时区
     */
    public static String format(long timestamp) {
        return _getDateFormat(defaultDateTimeFormat, null).format(timestamp);
    }

    /**
     * 将毫秒时间戳（自1970-01-01T00:00:00+00:00到所代表时刻的毫秒数）转换为对应格式的时间字符串，使用指定的格式，使用当前系统默认的时区
     */
    public static String format(long timestamp, String format) {
        return _getDateFormat(format, null).format(timestamp);
    }

    // 格式化输出

    /**
     * 将毫秒时间戳（自1970-01-01T00:00:00+00:00到所代表时刻的毫秒数）转换为对应格式的时间字符串，默认格式为yyyy-MM-dd HH:mm:ss.SSSZ，默认输出格式可在{@link DatetimeUtils}中指定，使用指定的时区
     */
    public static String format(long timestamp, TimeZone tz) {
        return _getDateFormat(defaultDateTimeFormat, tz).format(timestamp);
    }

    /**
     * 将毫秒时间戳（自1970-01-01T00:00:00+00:00到所代表时刻的毫秒数）转换为对应格式的时间字符串，使用指定的格式和时区
     */
    public static String format(long timestamp, String format, TimeZone tz) {
        return _getDateFormat(format, tz).format(timestamp);
    }

    /**
     * 将毫秒时间戳（自1970-01-01T00:00:00+00:00到所代表时刻的毫秒数）转换为对应格式的时间字符串，默认格式为yyyy-MM-dd，默认输出格式可在{@link DatetimeUtils}中指定，使用当前系统默认的时区
     */
    public static String formatDate(long timestamp) {
        return _getDateFormat(defaultDateFormat, null).format(timestamp);
    }

    /**
     * 将毫秒时间戳（自1970-01-01T00:00:00+00:00到所代表时刻的毫秒数）转换为对应格式的时间字符串，默认格式为yyyy-MM-dd，默认输出格式可在{@link DatetimeUtils}中指定，使用指定的时区
     */
    public static String formatDate(long timestamp, TimeZone tz) {
        return _getDateFormat(defaultDateFormat, tz).format(timestamp);
    }

    /**
     * 将{@link Date}转换为对应格式的时间字符串，默认格式为yyyy-MM-dd HH:mm:ss.SSSZ，默认输出格式可在{@link DatetimeUtils}中指定，使用当前系统默认的时区
     */
    public static String format(Date date) {
        return _getDateFormat(defaultDateTimeFormat, null).format(date);
    }

    /**
     * 将{@link Date}转换为对应格式的时间字符串，使用指定的格式，使用当前系统默认的时区
     */
    public static String format(Date date, String format) {
        return _getDateFormat(format, null).format(date);
    }

    /**
     * 将{@link Date}转换为对应格式的时间字符串，默认格式为yyyy-MM-dd HH:mm:ss.SSSZ，默认输出格式可在{@link DatetimeUtils}中指定，使用指定的时区
     */
    public static String format(Date date, TimeZone tz) {
        return _getDateFormat(defaultDateTimeFormat, tz).format(date);
    }

    /**
     * 将{@link Date}转换为对应格式的时间字符串，使用指定的格式和时区
     */
    public static String format(Date date, String format, TimeZone tz) {
        return _getDateFormat(format, tz).format(date);
    }

    /**
     * 将{@link Date}转换为对应格式的时间字符串，默认格式为yyyy-MM-dd，默认输出格式可在{@link DatetimeUtils}中指定，使用当前系统默认的时区
     */
    public static String formatDate(Date date) {
        return _getDateFormat(defaultDateFormat, null).format(date);
    }

    /**
     * 将{@link Date}转换为对应格式的时间字符串，默认格式为yyyy-MM-dd，默认输出格式可在{@link DatetimeUtils}中指定，使用指定的时区
     */
    public static String formatDate(Date date, TimeZone tz) {
        return _getDateFormat(defaultDateFormat, tz).format(date);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【毫秒】数后的结果
     *
     * @param millis 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusMillis(long timestamp, long millis) {
        return new Datetime(timestamp + millis);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【秒】数后的结果
     *
     * @param seconds 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusSeconds(long timestamp, long seconds) {
        return new Datetime(timestamp + Datetime.MILLIS_PER_SECOND * seconds);
    }

    // 时间操作

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【分】数后的结果
     *
     * @param minutes 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusMinutes(long timestamp, long minutes) {
        return new Datetime(timestamp + Datetime.MILLIS_PER_MINUTE * minutes);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【时】数后的结果
     *
     * @param hours 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusHours(long timestamp, long hours) {
        return new Datetime(timestamp + Datetime.MILLIS_PER_HOUR * hours);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【天】数后的结果
     *
     * @param days 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusDays(long timestamp, long days) {
        return new Datetime(timestamp + Datetime.MILLIS_PER_DAY * days);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【周】数后的结果
     *
     * @param weeks 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusWeeks(long timestamp, long weeks) {
        return new Datetime(timestamp + Datetime.MILLIS_PER_WEEK * weeks);
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【月】数后的结果，使用当前系统默认的时区
     *
     * 默认情况是对应月份的这一天，如果这一天不存在，返回的将是这个月的最后一天
     * 如今天是3月31日，加1个月，将返回4月30日
     *
     * @param months 如果传入0，并且是{@link Datetime}将直接返回原对象
     * </pre>
     */
    public static Datetime plusMonths(long timestamp, int months) {
        return plusMonths(timestamp, months, TimeZone.getDefault());
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【月】数后的结果，使用指定的时区
     *
     * 默认情况是对应月份的这一天，如果这一天不存在，返回的将是这个月的最后一天
     * 如今天是3月31日，加1个月，将返回4月30日
     *
     * @param months 如果传入0，并且是{@link Datetime}将直接返回原对象
     * </pre>
     */
    public static Datetime plusMonths(long timestamp, int months, TimeZone tz) {
        if (months == 0) {
            return new Datetime(timestamp);
        }
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        cal.add(Calendar.MONTH, months);
        return new Datetime(cal.getTime());
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【年】数后的结果，使用当前系统默认的时区
     *
     * 如果当年是闰年2月29日，加上一年后是平年，返回的将是2月28日
     *
     * @param years 如果传入0，并且是{@link Datetime}将直接返回原对象
     * </pre>
     */
    public static Datetime plusYears(long timestamp, int years) {
        return plusYears(timestamp, years, TimeZone.getDefault());
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【年】数后的结果，使用指定的时区
     *
     * 如果当年是闰年2月29日，加上一年后是平年，返回的将是2月28日
     *
     * @param years 如果传入0，并且是{@link Datetime}将直接返回原对象
     * </pre>
     */
    public static Datetime plusYears(long timestamp, int years, TimeZone tz) {
        if (years == 0) {
            return new Datetime(timestamp);
        }
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTimeInMillis(timestamp);
        cal.add(Calendar.YEAR, years);
        return new Datetime(cal.getTime());
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【毫秒】数后的结果
     *
     * @param millis 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusMillis(long timestamp, long millis) {
        return plusMillis(timestamp, -millis);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【秒】数后的结果
     *
     * @param seconds 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusSeconds(long timestamp, long seconds) {
        return plusSeconds(timestamp, -seconds);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【分】数后的结果
     *
     * @param minutes 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusMinutes(long timestamp, long minutes) {
        return plusMinutes(timestamp, -minutes);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【时】数后的结果
     *
     * @param hours 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusHours(long timestamp, long hours) {
        return plusHours(timestamp, -hours);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【天】数后的结果
     *
     * @param days 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusDays(long timestamp, long days) {
        return plusDays(timestamp, -days);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【周】数后的结果
     *
     * @param weeks 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusWeeks(long timestamp, long weeks) {
        return plusWeeks(timestamp, -weeks);
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【月】数后的结果，使用当前系统默认的时区
     *
     * 默认情况是对应月份的这一天，如果这一天不存在，返回的将是这个月的最后一天
     * 如今天是5月31日，减1个月，将返回4月30日
     *
     * @param months 如果传入0，并且是{@link Datetime}将直接返回原对象
     * </pre>
     */
    public static Datetime minusMonths(long timestamp, int months) {
        return plusMonths(timestamp, -months);
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【月】数后的结果，使用指定的时区
     *
     * 默认情况是对应月份的这一天，如果这一天不存在，返回的将是这个月的最后一天
     * 如今天是5月31日，减1个月，将返回4月30日
     *
     * @param months 如果传入0，并且是{@link Datetime}将直接返回原对象
     * </pre>
     */
    public static Datetime minusMonths(long timestamp, int months, TimeZone tz) {
        return plusMonths(timestamp, -months, tz);
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【年】数后的结果，使用当前系统默认的时区
     *
     * 如果当年是闰年2月29日，减去一年后是平年，返回的将是2月28日
     *
     * @param years 如果传入0，并且是{@link Datetime}将直接返回原对象
     * </pre>
     */
    public static Datetime minusYears(long timestamp, int years) {
        return plusYears(timestamp, -years);
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【年】数后的结果，使用指定的时区
     *
     * 如果当年是闰年2月29日，减去一年后是平年，返回的将是2月28日
     *
     * @param years 如果传入0，并且是{@link Datetime}将直接返回原对象
     * </pre>
     */
    public static Datetime minusYears(long timestamp, int years, TimeZone tz) {
        return plusYears(timestamp, -years, tz);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【毫秒】数后的结果
     *
     * @param millis 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusMillis(Date date, long millis) {
        if (millis == 0 && date instanceof Datetime) {
            return (Datetime) date;
        }
        return new Datetime(date.getTime() + millis);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【秒】数后的结果
     *
     * @param seconds 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusSeconds(Date date, long seconds) {
        if (seconds == 0 && date instanceof Datetime) {
            return (Datetime) date;
        }
        return new Datetime(date.getTime() + Datetime.MILLIS_PER_SECOND * seconds);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【分】数后的结果
     *
     * @param minutes 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusMinutes(Date date, long minutes) {
        if (minutes == 0 && date instanceof Datetime) {
            return (Datetime) date;
        }
        return new Datetime(date.getTime() + Datetime.MILLIS_PER_MINUTE * minutes);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【时】数后的结果
     *
     * @param hours 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusHours(Date date, long hours) {
        if (hours == 0 && date instanceof Datetime) {
            return (Datetime) date;
        }
        return new Datetime(date.getTime() + Datetime.MILLIS_PER_HOUR * hours);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【天】数后的结果
     *
     * @param days 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusDays(Date date, long days) {
        if (days == 0 && date instanceof Datetime) {
            return (Datetime) date;
        }
        return new Datetime(date.getTime() + Datetime.MILLIS_PER_DAY * days);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【周】数后的结果
     *
     * @param weeks 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusWeeks(Date date, long weeks) {
        if (weeks == 0 && date instanceof Datetime) {
            return (Datetime) date;
        }
        return new Datetime(date.getTime() + Datetime.MILLIS_PER_WEEK * weeks);
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【月】数后的结果，使用当前系统默认的时区
     *
     * 默认情况是对应月份的这一天，如果这一天不存在，返回的将是这个月的最后一天
     * 如今天是3月31日，加1个月，将返回4月30日
     *
     * @param months 如果传入0，并且是{@link Datetime}将直接返回原对象
     * </pre>
     */
    public static Datetime plusMonths(Date date, int months) {
        return plusMonths(date, months, TimeZone.getDefault());
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【月】数后的结果，使用指定的时区
     *
     * 默认情况是对应月份的这一天，如果这一天不存在，返回的将是这个月的最后一天
     * 如今天是3月31日，加1个月，将返回4月30日
     *
     * @param months 如果传入0，并且是{@link Datetime}将直接返回原对象
     * </pre>
     */
    public static Datetime plusMonths(Date date, int months, TimeZone tz) {
        if (months == 0 && date instanceof Datetime) {
            return (Datetime) date;
        }
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTime(date);
        cal.add(Calendar.MONTH, months);
        return new Datetime(cal.getTime());
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【年】数后的结果，使用当前系统默认的时区
     *
     * 如果当年是闰年2月29日，加上一年后是平年，返回的将是2月28日
     *
     * @param years 如果传入0，并且是{@link Datetime}将直接返回原对象
     * </pre>
     */
    public static Datetime plusYears(Date date, int years) {
        return plusYears(date, years, TimeZone.getDefault());
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【年】数后的结果，使用指定的时区
     *
     * 如果当年是闰年2月29日，加上一年后是平年，返回的将是2月28日
     *
     * @param years 如果传入0，并且是{@link Datetime}将直接返回原对象
     * </pre>
     */
    public static Datetime plusYears(Date date, int years, TimeZone tz) {
        if (years == 0 && date instanceof Datetime) {
            return (Datetime) date;
        }
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTime(date);
        cal.add(Calendar.YEAR, years);
        return new Datetime(cal.getTime());
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【毫秒】数后的结果
     *
     * @param millis 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusMillis(Date date, long millis) {
        return plusMillis(date, -millis);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【秒】数后的结果
     *
     * @param seconds 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusSeconds(Date date, long seconds) {
        return plusSeconds(date, -seconds);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【分】数后的结果
     *
     * @param minutes 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusMinutes(Date date, long minutes) {
        return plusMinutes(date, -minutes);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【时】数后的结果
     *
     * @param hours 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusHours(Date date, long hours) {
        return plusHours(date, -hours);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【天】数后的结果
     *
     * @param days 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusDays(Date date, long days) {
        return plusDays(date, -days);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【周】数后的结果
     *
     * @param weeks 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusWeeks(Date date, long weeks) {
        return plusWeeks(date, -weeks);
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【月】数后的结果，使用当前系统默认的时区
     *
     * 默认情况是对应月份的这一天，如果这一天不存在，返回的将是这个月的最后一天
     * 如今天是5月31日，减1个月，将返回4月30日
     * </pre>
     *
     * @param months 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusMonths(Date date, int months) {
        return plusMonths(date, -months);
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【月】数后的结果，使用指定的时区
     *
     * 默认情况是对应月份的这一天，如果这一天不存在，返回的将是这个月的最后一天
     * 如今天是5月31日，减1个月，将返回4月30日
     * </pre>
     *
     * @param months 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusMonths(Date date, int months, TimeZone tz) {
        return plusMonths(date, -months, tz);
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【年】数后的结果，使用当前系统默认的时区
     *
     * 如果当年是闰年2月29日，减去一年后是平年，返回的将是2月28日
     * </pre>
     *
     * @param years 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusYears(Date date, int years) {
        return plusYears(date, -years);
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【年】数后的结果，使用指定的时区
     *
     * 如果当年是闰年2月29日，减去一年后是平年，返回的将是2月28日
     * </pre>
     *
     * @param years 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusYears(Date date, int years, TimeZone tz) {
        return plusYears(date, -years, tz);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【毫秒】数后的结果
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param millis 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusMillis(String date, long millis) {
        if (millis == 0) {
            return parse(date);
        }
        Datetime _date = parse(date);
        return _date == null ? null : new Datetime(_date.getTime() + millis);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【秒】数后的结果
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param seconds 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusSeconds(String date, long seconds) {
        if (seconds == 0) {
            return parse(date);
        }
        Datetime _date = parse(date);
        return _date == null ? null : new Datetime(_date.getTime() + Datetime.MILLIS_PER_SECOND * seconds);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【分】数后的结果
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param minutes 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusMinutes(String date, long minutes) {
        if (minutes == 0) {
            return parse(date);
        }
        Datetime _date = parse(date);
        return _date == null ? null : new Datetime(_date.getTime() + Datetime.MILLIS_PER_MINUTE * minutes);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【时】数后的结果
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param hours 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusHours(String date, long hours) {
        if (hours == 0) {
            return parse(date);
        }
        Datetime _date = parse(date);
        return _date == null ? null : new Datetime(parse(date).getTime() + Datetime.MILLIS_PER_HOUR * hours);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【天】数后的结果
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param days 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusDays(String date, long days) {
        if (days == 0) {
            return parse(date);
        }
        Datetime _date = parse(date);
        return _date == null ? null : new Datetime(_date.getTime() + Datetime.MILLIS_PER_DAY * days);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【周】数后的结果
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param weeks 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusWeeks(String date, long weeks) {
        if (weeks == 0) {
            return parse(date);
        }
        Datetime _date = parse(date);
        return _date == null ? null : new Datetime(_date.getTime() + Datetime.MILLIS_PER_WEEK * weeks);
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【月】数后的结果，使用当前系统默认的时区
     *
     * 默认情况是对应月份的这一天，如果这一天不存在，返回的将是这个月的最后一天
     * 如今天是3月31日，加1个月，将返回4月30日
     * </pre>
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param months 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusMonths(String date, int months) {
        return plusMonths(date, months, TimeZone.getDefault());
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【月】数后的结果，使用指定的时区
     *
     * 默认情况是对应月份的这一天，如果这一天不存在，返回的将是这个月的最后一天
     * 如今天是3月31日，加1个月，将返回4月30日
     * </pre>
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param months 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusMonths(String date, int months, TimeZone tz) {
        if (months == 0) {
            return parse(date);
        }
        Datetime _date = parse(date);
        if (_date == null) {
            return null;
        }

        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTime(_date);
        cal.add(Calendar.MONTH, months);
        return new Datetime(cal.getTime());
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【年】数后的结果，使用当前系统默认的时区
     *
     * 如果当年是闰年2月29日，加上一年后是平年，返回的将是2月28日
     * </pre>
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param years 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusYears(String date, int years) {
        return plusYears(date, years, TimeZone.getDefault());
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【年】数后的结果，使用指定的时区
     *
     * 如果当年是闰年2月29日，加上一年后是平年，返回的将是2月28日
     * </pre>
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param years 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime plusYears(String date, int years, TimeZone tz) {
        if (years == 0) {
            return parse(date);
        }
        Datetime _date = parse(date);
        if (_date == null) {
            return null;
        }
        Calendar cal = Calendar.getInstance(tz);
        cal.setLenient(false);
        cal.setTime(_date);
        cal.add(Calendar.YEAR, years);
        return new Datetime(cal.getTime());
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【毫秒】数后的结果
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param millis 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusMillis(String date, long millis) {
        Datetime _date = parse(date);
        return _date == null ? null : plusMillis(_date, -millis);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【秒】数后的结果
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param seconds 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusSeconds(String date, long seconds) {
        Datetime _date = parse(date);
        return _date == null ? null : plusSeconds(_date, -seconds);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【分】数后的结果
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param minutes 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusMinutes(String date, long minutes) {
        Datetime _date = parse(date);
        return _date == null ? null : plusMinutes(_date, -minutes);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【时】数后的结果
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param hours 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusHours(String date, long hours) {
        Datetime _date = parse(date);
        return _date == null ? null : plusHours(_date, -hours);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【天】数后的结果
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param days 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusDays(String date, long days) {
        Datetime _date = parse(date);
        return _date == null ? null : plusDays(_date, -days);
    }

    /**
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【周】数后的结果
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param weeks 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusWeeks(String date, long weeks) {
        Datetime _date = parse(date);
        return _date == null ? null : plusWeeks(_date, -weeks);
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【月】数后的结果，使用当前系统默认的时区
     *
     * 默认情况是对应月份的这一天，如果这一天不存在，返回的将是这个月的最后一天
     * 如今天是5月31日，减1个月，将返回4月30日
     * </pre>
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param months 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusMonths(String date, int months) {
        Datetime _date = parse(date);
        return _date == null ? null : plusMonths(_date, -months);
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【月】数后的结果，使用指定的时区
     *
     * 默认情况是对应月份的这一天，如果这一天不存在，返回的将是这个月的最后一天
     * 如今天是5月31日，减1个月，将返回4月30日
     * </pre>
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param months 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusMonths(String date, int months, TimeZone tz) {
        Datetime _date = parse(date);
        return _date == null ? null : plusMonths(_date, -months, tz);
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【年】数后的结果，使用当前系统默认的时区
     *
     * 如果当年是闰年2月29日，减去一年后是平年，返回的将是2月28日
     * </pre>
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param years 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusYears(String date, int years) {
        Datetime _date = parse(date);
        return _date == null ? null : plusYears(_date, -years);
    }

    /**
     * <pre>
     * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【年】数后的结果，使用指定的时区
     *
     * 如果当年是闰年2月29日，减去一年后是平年，返回的将是2月28日
     * </pre>
     *
     * @param date 使用defaultDateTimeFormat解析时间,如果解析失败返回null
     * @param years 如果传入0，并且是{@link Datetime}将直接返回原对象
     */
    public static Datetime minusYears(String date, int years, TimeZone tz) {
        Datetime _date = parse(date);
        return _date == null ? null : plusYears(_date, -years, tz);
    }

    /**
     * 获取两时刻相减的【毫秒】数差值 end-start
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getMillisBetween(long start, long end) {
        return end - start;
    }

    /**
     * 获取两时刻相减的【秒】数差值 end-start，将直接比较二者的时间差，>=1000ms才算是1s
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getSecondsBetween(long start, long end) {
        return (end - start) / Datetime.MILLIS_PER_SECOND;
    }

    // 时间差

    /**
     * 获取两时刻相减的【分】数差值 end-start，将直接比较二者的时间差，>=60s才算是1min
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getMinutesBetween(long start, long end) {
        return (end - start) / Datetime.MILLIS_PER_MINUTE;
    }

    /**
     * 获取两时刻相减的【小时】数差值 end-start，将直接比较二者的时间差，>=60min才算是1h
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getHoursBetween(long start, long end) {
        return (end - start) / Datetime.MILLIS_PER_HOUR;
    }

    /**
     * 获取两时刻相减的【天】数差值 end-start，将直接比较二者的时间差，>=24h才算是1day
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getDaysBetween(long start, long end) {
        return (end - start) / Datetime.MILLIS_PER_DAY;
    }

    /**
     * 获取两时刻相减的【天】数差值 end-start，将比较二者的日期差，以自然天计算，日期相差1但差距不满24h的也可以算是1day，使用系统默认的时区
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getDaysInCalendarBetween(long start, long end) {
        return getDaysInCalendarBetween(start, end, TimeZone.getDefault());
    }

    /**
     * 获取两时刻相减的【天】数差值 end-start，将比较二者的日期差，以自然天计算，日期相差1但差距不满24h的也可以算是1day，使用指定的时区
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getDaysInCalendarBetween(long start, long end, TimeZone tz) {
        long s = retainDay(start, tz).getTime();
        long e = retainDay(end, tz).getTime();
        return (e - s) / Datetime.MILLIS_PER_DAY;
    }

    /**
     * 获取两时刻相减的【周】数差值 end-start，必须要满足7*24h才能算是一周
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getWeeksBetween(long start, long end) {
        return (end - start) / Datetime.MILLIS_PER_WEEK;
    }

    /**
     * 获取两时刻相减的【周】数差值 end-start，将比较二者的日期差，以自然天7天计算，日期相差1但差距不满24h的也可以算是1day，使用系统默认的时区
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getWeeksInCalendarBetween(long start, long end) {
        return getWeeksInCalendarBetween(start, end, TimeZone.getDefault());
    }

    /**
     * 获取两时刻相减的【周】数差值 end-start，将比较二者的日期差，以自然天7天计算，日期相差1但差距不满24h的也可以算是1day，使用指定的时区
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getWeeksInCalendarBetween(long start, long end, TimeZone tz) {
        long s = retainDay(start, tz).getTime();
        long e = retainDay(end, tz).getTime();
        return (e - s) / Datetime.MILLIS_PER_WEEK;
    }

    /**
     * <pre>
     * 获取两时刻相减的【月】数差值 end-start，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），要求【必须满24h才能算1day】，使用系统默认的时区
     * 例如start为2015-01-01 12:00:00则end必须要>=2015-02-01 12:00:00才能算是一个月
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getMonthsBetween(long start, long end) {
        return getMonthsBetween(start, end, TimeZone.getDefault());
    }

    /**
     * <pre>
     * 获取两时刻相减的【月】数差值 end-start，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），要求【必须满24h才能算1day】，使用指定的时区
     * 例如start为2015-01-01 12:00:00则end必须要>=2015-02-01 12:00:00才能算是一个月
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getMonthsBetween(long start, long end, TimeZone tz) {
        int s = getYear(start, tz) * 12 + getMonthOfYear(start, tz);
        int e = getYear(end) * 12 + getMonthOfYear(end, tz);
        if (e == s) {
            return 0;
        } else if (e > s) {
            if (plusMonths(start, e - s, tz).isAfter(end)) { // 说明零头不满一个月，不算一个月，要在结果中要减去
                return e - s - 1;
            }
            return e - s;
        }
        if (plusMonths(end, s - e, tz).isAfter(start)) { // 说明零头不满一个月，不算一个月，要在结果中要减去
            return s - e - 1;
        }
        return e - s;
    }

    /**
     * <pre>
     * 获取两时刻相减的【月】数差值 end-start，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），不要求一定要满24h，使用系统默认的时区
     * 例如start为2015-01-01 12:00:00则end只需>=2015-02-01 00:00:00就可以算是一个月
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getMonthsInCalendarBetween(long start, long end) {
        return getMonthsInCalendarBetween(start, end, TimeZone.getDefault());
    }

    /**
     * <pre>
     * 获取两时刻相减的【月】数差值 end-start，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），不要求一定要满24h，使用指定的时区
     * 例如start为2015-01-01 12:00:00则end只需>=2015-02-01 00:00:00就可以算是一个月
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getMonthsInCalendarBetween(long start, long end, TimeZone tz) {
        int s = getYear(start, tz) * 12 + getMonthOfYear(start, tz);
        int e = getYear(end, tz) * 12 + getMonthOfYear(end, tz);
        if (e == s) {
            return 0;
        } else if (e > s) {
            // 由于是InCalendar不精确到时分秒毫秒，直接算到天就行了
            if (plusMonths(start, e - s, tz).retainDay(tz)
                                            .isAfter(end)) { // 说明零头不满一个月，不算一个月，要在结果中要减去
                return e - s - 1;
            }
            return e - s;
        }
        if (plusMonths(end, s - e, tz).retainDay(tz)
                                      .isAfter(start)) { // 说明零头不满一个月，不算一个月，要在结果中要减去
            return s - e - 1;
        }
        return e - s;
    }

    /**
     * <pre>
     * 获取两时刻相减的【年】数差值 end-start，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），要求【必须满24h才能算1day】，使用系统默认的时区
     * 例如start为2015-01-01 12:00:00则end必须要>=2016-01-01 12:00:00才能算是一年
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getYearsBetween(long start, long end) {
        return getMonthsBetween(start, end) / 12;
    }

    /**
     * <pre>
     * 获取两时刻相减的【年】数差值 end-start，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），要求【必须满24h才能算1day】，使用指定的时区
     * 例如start为2015-01-01 12:00:00则end必须要>=2016-01-01 12:00:00才能算是一年
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getYearsBetween(long start, long end, TimeZone tz) {
        return getMonthsBetween(start, end, tz) / 12;
    }

    /**
     * <pre>
     * 获取两时刻相减的【年】数差值 end-start，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），不要求一定要满24h，使用系统默认的时区
     * 例如start为2015-01-01 12:00:00则end只需>=2016-01-01 00:00:00就可以算是一年
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getYearsInCalendarBetween(long start, long end) {
        return getMonthsInCalendarBetween(start, end) / 12;
    }

    /**
     * <pre>
     * 获取两时刻相减的【年】数差值 end-start，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），不要求一定要满24h，使用指定的时区
     * 例如start为2015-01-01 12:00:00则end只需>=2016-01-01 00:00:00就可以算是一年
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getYearsInCalendarBetween(long start, long end, TimeZone tz) {
        return getMonthsInCalendarBetween(start, end, tz) / 12;
    }

    /**
     * 获取两时刻相减的【毫秒】数差值 end-start
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getMillisBetween(Date start, Date end) {
        return getMillisBetween(start.getTime(), end.getTime());
    }

    /**
     * 获取两时刻相减的【秒】数差值 end-start，将直接比较二者的时间差，>=1000ms才算是1s
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getSecondsBetween(Date start, Date end) {
        return getSecondsBetween(start.getTime(), end.getTime());
    }

    /**
     * 获取两时刻相减的【分】数差值 end-start，将直接比较二者的时间差，>=60s才算是1min
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getMinutesBetween(Date start, Date end) {
        return getMinutesBetween(start.getTime(), end.getTime());
    }

    /**
     * 获取两时刻相减的【小时】数差值 end-start，将直接比较二者的时间差，>=60min才算是1h
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getHoursBetween(Date start, Date end) {
        return getHoursBetween(start.getTime(), end.getTime());
    }

    /**
     * 获取两时刻相减的【天】数差值 end-start，将直接比较二者的时间差，>=24h才算是1day
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getDaysBetween(Date start, Date end) {
        return getDaysBetween(start.getTime(), end.getTime());
    }

    /**
     * 获取两时刻相减的【天】数差值 end-start，将比较二者的日期差，以自然天计算，日期相差1但差距不满24h的也可以算是1day，使用系统默认的时区
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getDaysInCalendarBetween(Date start, Date end) {
        return getDaysInCalendarBetween(start.getTime(), end.getTime());
    }

    /**
     * 获取两时刻相减的【天】数差值 end-start，将比较二者的日期差，以自然天计算，日期相差1但差距不满24h的也可以算是1day，使用指定的时区
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getDaysInCalendarBetween(Date start, Date end, TimeZone tz) {
        return getDaysInCalendarBetween(start.getTime(), end.getTime(), tz);
    }

    /**
     * 获取两时刻相减的【周】数差值 end-start，必须要满足7*24h才能算是一周
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getWeeksBetween(Date start, Date end) {
        return getWeeksBetween(start.getTime(), end.getTime());
    }

    /**
     * 获取两时刻相减的【周】数差值 end-start，将比较二者的日期差，以自然天7天计算，日期相差1但差距不满24h的也可以算是1day，使用系统默认的时区
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getWeeksInCalendarBetween(Date start, Date end) {
        return getWeeksInCalendarBetween(start.getTime(), end.getTime());
    }

    /**
     * 获取两时刻相减的【周】数差值 end-start，将比较二者的日期差，以自然天7天计算，日期相差1但差距不满24h的也可以算是1day，使用指定的时区
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getWeeksInCalendarBetween(Date start, Date end, TimeZone tz) {
        return getWeeksInCalendarBetween(start.getTime(), end.getTime(), tz);
    }

    /**
     * <pre>
     * 获取两时刻相减的【月】数差值 end-start，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），要求【必须满24h才能算1day】，使用系统默认的时区
     * 例如start为2015-01-01 12:00:00则end必须要>=2015-02-01 12:00:00才能算是一个月
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getMonthsBetween(Date start, Date end) {
        return getMonthsBetween(start.getTime(), end.getTime());
    }

    /**
     * <pre>
     * 获取两时刻相减的【月】数差值 end-start，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），要求【必须满24h才能算1day】，使用指定的时区
     * 例如start为2015-01-01 12:00:00则end必须要>=2015-02-01 12:00:00才能算是一个月
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getMonthsBetween(Date start, Date end, TimeZone tz) {
        return getMonthsBetween(start.getTime(), end.getTime(), tz);
    }

    /**
     * <pre>
     * 获取两时刻相减的【月】数差值 end-start，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），不要求一定要满24h，使用系统默认的时区
     * 例如start为2015-01-01 12:00:00则end只需>=2015-02-01 00:00:00就可以算是一个月
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getMonthsInCalendarBetween(Date start, Date end) {
        return getMonthsInCalendarBetween(start.getTime(), end.getTime());
    }

    /**
     * <pre>
     * 获取两时刻相减的【月】数差值 end-start，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），不要求一定要满24h，使用指定的时区
     * 例如start为2015-01-01 12:00:00则end只需>=2015-02-01 00:00:00就可以算是一个月
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getMonthsInCalendarBetween(Date start, Date end, TimeZone tz) {
        return getMonthsInCalendarBetween(start.getTime(), end.getTime(), tz);
    }

    /**
     * <pre>
     * 获取两时刻相减的【年】数差值 end-start，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），要求【必须满24h才能算1day】，使用系统默认的时区
     * 例如start为2015-01-01 12:00:00则end必须要>=2016-01-01 12:00:00才能算是一年
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getYearsBetween(Date start, Date end) {
        return getYearsBetween(start.getTime(), end.getTime());
    }

    /**
     * <pre>
     * 获取两时刻相减的【年】数差值 end-start，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），要求【必须满24h才能算1day】，使用指定的时区
     * 例如start为2015-01-01 12:00:00则end必须要>=2016-01-01 12:00:00才能算是一年
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getYearsBetween(Date start, Date end, TimeZone tz) {
        return getYearsBetween(start.getTime(), end.getTime(), tz);
    }

    /**
     * <pre>
     * 获取两时刻相减的【年】数差值 end-start，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），不要求一定要满24h，使用系统默认的时区
     * 例如start为2015-01-01 12:00:00则end只需>=2016-01-01 00:00:00就可以算是一年
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getYearsInCalendarBetween(Date start, Date end) {
        return getYearsInCalendarBetween(start.getTime(), end.getTime());
    }

    /**
     * <pre>
     * 获取两时刻相减的【年】数差值 end-start，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），不要求一定要满24h，使用指定的时区
     * 例如start为2015-01-01 12:00:00则end只需>=2016-01-01 00:00:00就可以算是一年
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getYearsInCalendarBetween(Date start, Date end, TimeZone tz) {
        return getYearsInCalendarBetween(start.getTime(), end.getTime(), tz);
    }

    /**
     * 获取两时刻相减的【毫秒】数差值 end-start
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getMillisBetween(String start, String end) {
        return getMillisBetween(parse(start), parse(end));
    }

    /**
     * 获取两时刻相减的【秒】数差值 end-start，将直接比较二者的时间差，>=1000ms才算是1s
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getSecondsBetween(String start, String end) {
        return getSecondsBetween(parse(start), parse(end));
    }

    /**
     * 获取两时刻相减的【分】数差值 end-start，将直接比较二者的时间差，>=60s才算是1min
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getMinutesBetween(String start, String end) {
        return getMinutesBetween(parse(start), parse(end));
    }

    /**
     * 获取两时刻相减的【小时】数差值 end-start，将直接比较二者的时间差，>=60min才算是1h
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getHoursBetween(String start, String end) {
        return getHoursBetween(parse(start), parse(end));
    }

    /**
     * 获取两时刻相减的【天】数差值 end-start，将直接比较二者的时间差，>=24h才算是1day
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getDaysBetween(String start, String end) {
        return getDaysBetween(parse(start), parse(end));
    }

    /**
     * 获取两时刻相减的【天】数差值 end-start，将比较二者的日期差，以自然天计算，日期相差1但差距不满24h的也可以算是1day，使用系统默认的时区
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getDaysInCalendarBetween(String start, String end) {
        return getDaysInCalendarBetween(parse(start), parse(end));
    }

    /**
     * 获取两时刻相减的【天】数差值 end-start，将比较二者的日期差，以自然天计算，日期相差1但差距不满24h的也可以算是1day，使用指定的时区
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getDaysInCalendarBetween(String start, String end, TimeZone tz) {
        return getDaysInCalendarBetween(parse(start), parse(end), tz);
    }

    /**
     * 获取两时刻相减的【周】数差值 end-start，必须要满足7*24h才能算是一周
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getWeeksBetween(String start, String end) {
        return getWeeksBetween(parse(start), parse(end));
    }

    /**
     * 获取两时刻相减的【周】数差值 end-start，将比较二者的日期差，以自然天7天计算，日期相差1但差距不满24h的也可以算是1day，使用系统默认的时区
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getWeeksInCalendarBetween(String start, String end) {
        return getWeeksInCalendarBetween(parse(start), parse(end));
    }

    /**
     * 获取两时刻相减的【周】数差值 end-start，将比较二者的日期差，以自然天7天计算，日期相差1但差距不满24h的也可以算是1day，使用指定的时区
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static long getWeeksInCalendarBetween(String start, String end, TimeZone tz) {
        return getWeeksInCalendarBetween(parse(start), parse(end), tz);
    }

    /**
     * <pre>
     * 获取两时刻相减的【月】数差值 end-start，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），要求【必须满24h才能算1day】，使用系统默认的时区
     * 例如start为2015-01-01 12:00:00则end必须要>=2015-02-01 12:00:00才能算是一个月
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getMonthsBetween(String start, String end) {
        return getMonthsBetween(parse(start), parse(end));
    }

    /**
     * <pre>
     * 获取两时刻相减的【月】数差值 end-start，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），要求【必须满24h才能算1day】，使用指定的时区
     * 例如start为2015-01-01 12:00:00则end必须要>=2015-02-01 12:00:00才能算是一个月
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getMonthsBetween(String start, String end, TimeZone tz) {
        return getMonthsBetween(parse(start), parse(end), tz);
    }

    /**
     * <pre>
     * 获取两时刻相减的【月】数差值 end-start，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），不要求一定要满24h，使用系统默认的时区
     * 例如start为2015-01-01 12:00:00则end只需>=2015-02-01 00:00:00就可以算是一个月
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getMonthsInCalendarBetween(String start, String end) {
        return getMonthsInCalendarBetween(parse(start), parse(end));
    }

    /**
     * <pre>
     * 获取两时刻相减的【月】数差值 end-start，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），不要求一定要满24h，使用指定的时区
     * 例如start为2015-01-01 12:00:00则end只需>=2015-02-01 00:00:00就可以算是一个月
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getMonthsInCalendarBetween(String start, String end, TimeZone tz) {
        return getMonthsInCalendarBetween(parse(start), parse(end), tz);
    }

    /**
     * <pre>
     * 获取两时刻相减的【年】数差值 end-start，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），要求【必须满24h才能算1day】，使用系统默认的时区
     * 例如start为2015-01-01 12:00:00则end必须要>=2016-01-01 12:00:00才能算是一年
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getYearsBetween(String start, String end) {
        return getYearsBetween(parse(start), parse(end));
    }

    /**
     * <pre>
     * 获取两时刻相减的【年】数差值 end-start，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），要求【必须满24h才能算1day】，使用指定的时区
     * 例如start为2015-01-01 12:00:00则end必须要>=2016-01-01 12:00:00才能算是一年
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getYearsBetween(String start, String end, TimeZone tz) {
        return getYearsBetween(parse(start), parse(end), tz);
    }

    /**
     * <pre>
     * 获取两时刻相减的【年】数差值 end-start，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），不要求一定要满24h，使用系统默认的时区
     * 例如start为2015-01-01 12:00:00则end只需>=2016-01-01 00:00:00就可以算是一年
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getYearsInCalendarBetween(String start, String end) {
        return getYearsInCalendarBetween(parse(start), parse(end));
    }

    /**
     * <pre>
     * 获取两时刻相减的【年】数差值 end-start，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），不要求一定要满24h，使用指定的时区
     * 例如start为2015-01-01 12:00:00则end只需>=2016-01-01 00:00:00就可以算是一年
     * </pre>
     *
     * @return 如果end时刻在start之后，将返回正数的结果，如果end时刻在start之前则会返回负数的结果
     */
    public static int getYearsInCalendarBetween(String start, String end, TimeZone tz) {
        return getYearsInCalendarBetween(parse(start), parse(end), tz);
    }

    private static int _getDayCountOfMonth(int year, int month) {
        int days = Datetime.DAYS_OF_MONTH[month];
        if (month != Datetime.FEBRUARY) {
            return days;
        }
        // 如果是2月，判断该年是否为闰年
        if (_isLeapYear(year)) {
            return days + 1;
        }
        return days;
    }

    /**
     * 获取时间戳所在的月份有多少天，例如1月返回31天，使用系统默认时区
     */
    public static int getDayCountOfMonth(long timestamp) {
        return getDayCountOfMonth(timestamp, TimeZone.getDefault());
    }

    /**
     * 获取时间戳所在的月份有多少天，例如1月返回31天，使用指定的时区
     */
    public static int getDayCountOfMonth(long timestamp, TimeZone tz) {
        int year = DatetimeUtils.getYear(timestamp, tz);
        int month = DatetimeUtils.getMonthOfYear(timestamp, tz);
        return _getDayCountOfMonth(year, month);
    }

    /**
     * 获取时间戳所在的月份有多少天，例如1月返回31天，使用系统默认时区
     */
    public static int getDayCountOfMonth(Date date) {
        return getDayCountOfMonth(date, TimeZone.getDefault());
    }

    /**
     * 获取时间戳所在的月份有多少天，例如1月返回31天，使用指定的时区
     */
    public static int getDayCountOfMonth(Date date, TimeZone tz) {
        int year = DatetimeUtils.getYear(date, tz);
        int month = DatetimeUtils.getMonthOfYear(date, tz);
        return _getDayCountOfMonth(year, month);
    }

    /**
     * 获取时间戳所在的月份有多少天，例如1月返回31天，使用系统默认时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回0
     */
    public static int getDayCountOfMonth(String date) {
        return getDayCountOfMonth(date, TimeZone.getDefault());
    }

    /**
     * 获取时间戳所在的月份有多少天，例如1月返回31天，使用指定的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回0
     */
    public static int getDayCountOfMonth(String date, TimeZone tz) {
        Datetime _date = parse(date);
        return null == _date ? 0 : DatetimeUtils.getDayCountOfMonth(_date, tz);
    }

    /**
     * 判断时间戳所属的年份是否为闰年，使用系统默认的时区
     */
    public static boolean isLeapYear(long timestamp) {
        return isLeapYear(timestamp, TimeZone.getDefault());
    }

    private static boolean _isLeapYear(int year) {
        return (year % 100 != 0 && year % 4 == 0) || (year % 400 == 0);
    }

    /**
     * 判断时间戳所属的年份是否为闰年，使用指定的时区
     */
    public static boolean isLeapYear(long timestamp, TimeZone tz) {
        int year = DatetimeUtils.getYear(timestamp, tz);
        return _isLeapYear(year);
    }

    /**
     * 判断时间戳所属的年份是否为闰年，使用系统默认的时区
     */
    public static boolean isLeapYear(Date date) {
        return isLeapYear(date, TimeZone.getDefault());
    }

    /**
     * 判断时间戳所属的年份是否为闰年，使用指定的时区
     */
    public static boolean isLeapYear(Date date, TimeZone tz) {
        int year = DatetimeUtils.getYear(date, tz);
        return _isLeapYear(year);
    }

    /**
     * 判断时间戳所属的年份是否为闰年，使用系统默认的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回false
     */
    public static boolean isLeapYear(String date) {
        return isLeapYear(date, TimeZone.getDefault());
    }

    /**
     * 判断时间戳所属的年份是否为闰年，使用指定的时区
     *
     * @param date 使用{@link #parse(String)}智能解析时间，如果解析失败返回false
     */
    public static boolean isLeapYear(String date, TimeZone tz) {
        Datetime _date = parse(date);
        if (null == _date) {
            return false;
        }
        int year = DatetimeUtils.getYear(_date, tz);
        return _isLeapYear(year);
    }

    /**
     * <pre>
     * 封装的加强版{@link Date}，参考了Joda-Time和jdk8的java.time包下相关类
     * 注意内部只保存了GMT时间戳，本身没有存储时区和时间格式，需要在输出时指定
     * 未指定的话就取的{@link DatetimeUtils}和系统的默认配置
     *
     * 请参考{@link DatetimeUtils}
     * </pre>
     */
    public static class Datetime extends Date {

        public static final int MILLIS_PER_SECOND = 1000;

        // 时间单位

        public static final int MILLIS_PER_MINUTE = MILLIS_PER_SECOND * 60;

        public static final int MILLIS_PER_HOUR = MILLIS_PER_MINUTE * 60;

        public static final int MILLIS_PER_DAY = MILLIS_PER_HOUR * 24;

        public static final int MILLIS_PER_WEEK = MILLIS_PER_DAY * 7;

        /** 时区GMT-12 */
        public static final TimeZone GMT_W12 = TimeZone.getTimeZone("GMT-12");

        // 时区

        /** 时区GMT-11 */
        public static final TimeZone GMT_W11 = TimeZone.getTimeZone("GMT-11");

        /** 时区GMT-10 */
        public static final TimeZone GMT_W10 = TimeZone.getTimeZone("GMT-10");

        /** 时区GMT-9 */
        public static final TimeZone GMT_W09 = TimeZone.getTimeZone("GMT-9");

        /** 时区GMT-8 */
        public static final TimeZone GMT_W08 = TimeZone.getTimeZone("GMT-8");

        /** 时区GMT-7 */
        public static final TimeZone GMT_W07 = TimeZone.getTimeZone("GMT-7");

        /** 时区GMT-6 */
        public static final TimeZone GMT_W06 = TimeZone.getTimeZone("GMT-6");

        /** 时区GMT-5 */
        public static final TimeZone GMT_W05 = TimeZone.getTimeZone("GMT-5");

        /** 时区GMT-4 */
        public static final TimeZone GMT_W04 = TimeZone.getTimeZone("GMT-4");

        /** 时区GMT-3 */
        public static final TimeZone GMT_W03 = TimeZone.getTimeZone("GMT-3");

        /** 时区GMT-2 */
        public static final TimeZone GMT_W02 = TimeZone.getTimeZone("GMT-2");

        /** 时区GMT-1 */
        public static final TimeZone GMT_W01 = TimeZone.getTimeZone("GMT-1");

        /** 时区GMT+0 */
        public static final TimeZone GMT = TimeZone.getTimeZone("GMT+0");

        /** 时区GMT+1 */
        public static final TimeZone GMT_E01 = TimeZone.getTimeZone("GMT+1");

        /** 时区GMT+2 */
        public static final TimeZone GMT_E02 = TimeZone.getTimeZone("GMT+2");

        /** 时区GMT+3 */
        public static final TimeZone GMT_E03 = TimeZone.getTimeZone("GMT+3");

        /** 时区GMT+4 */
        public static final TimeZone GMT_E04 = TimeZone.getTimeZone("GMT+4");

        /** 时区GMT+5 */
        public static final TimeZone GMT_E05 = TimeZone.getTimeZone("GMT+5");

        /** 时区GMT+6 */
        public static final TimeZone GMT_E06 = TimeZone.getTimeZone("GMT+6");

        /** 时区GMT+7 */
        public static final TimeZone GMT_E07 = TimeZone.getTimeZone("GMT+7");

        /** 时区GMT+8 */
        public static final TimeZone GMT_E08 = TimeZone.getTimeZone("GMT+8");

        /** 时区GMT+9 */
        public static final TimeZone GMT_E09 = TimeZone.getTimeZone("GMT+9");

        /** 时区GMT+10 */
        public static final TimeZone GMT_E10 = TimeZone.getTimeZone("GMT+10");

        /** 时区GMT+11 */
        public static final TimeZone GMT_E11 = TimeZone.getTimeZone("GMT+11");

        /** 时区GMT+12 */
        public static final TimeZone GMT_E12 = TimeZone.getTimeZone("GMT+12");

        /** yyyy-MM-dd'T'HH:mm:ss.SSS'Z' 例如:2017-06-01T06:13:22.717Z */
        public static final String DF_yyyy_MM_dd_T_HHmmss_SSSZ = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

        /** yyyy-MM-dd'T'HH:mm:ss'Z' */
        public static final String DF_yyyy_MM_dd_T_HHmmssZ = "yyyy-MM-dd'T'HH:mm:ss'Z'";

        /** yyyy-MM-dd HH:mm:ss.SSSZ */
        public static final String DF_yyyy_MM_dd_HHmmss_SSSZ = "yyyy-MM-dd HH:mm:ss.SSSZ";

        // 常用的日期格式

        /** yyyy-MM-dd HH:mm:ss.SSS */
        public static final String DF_yyyy_MM_dd_HHmmss_SSS = "yyyy-MM-dd HH:mm:ss.SSS";

        /** yyyy-MM-dd HH:mm:ss */
        public static final String DF_yyyy_MM_dd_HHmmss = "yyyy-MM-dd HH:mm:ss";

        /** yy-MM-dd HH:mm:ss */
        public static final String DF_yy_MM_dd_HHmmss = "yy-MM-dd HH:mm:ss";

        /** yyyy-MM-dd HH:mm */
        public static final String DF_yyyy_MM_dd_HHmm = "yyyy-MM-dd HH:mm";

        /** yyyyMMddHHmmssSSS */
        public static final String DF_yyyyMMddHHmmssSSS = "yyyyMMddHHmmssSSS";

        /** yyyyMMddHHmmss */
        public static final String DF_yyyyMMddHHmmss = "yyyyMMddHHmmss";

        /** yyyyMMddHHmm */
        public static final String DF_yyyyMMddHHmm = "yyyyMMddHHmm";

        /** yyMMddHHmmss */
        public static final String DF_yyMMddHHmmss = "yyMMddHHmmss";

        /** yyMMddHHmm */
        public static final String DF_yyMMddHHmm = "yyMMddHHmm";

        /** yyyyMMdd */
        public static final String DF_yyyyMMdd = "yyyyMMdd";

        /** yyMMdd */
        public static final String DF_yyMMdd = "yyMMdd";

        /** yyMM */
        public static final String DF_yyMM = "yyMM";

        /** yyyyMM */
        public static final String DF_yyyyMM = "yyyyMM";

        /** yyyy-MM */
        public static final String DF_yyyy_MM = "yyyy-MM";

        /** yyyy-MM-dd */
        public static final String DF_yyyy_MM_dd = "yyyy-MM-dd";

        /** yy-MM-dd */
        public static final String DF_yy_MM_dd = "yy-MM-dd";

        /** HH:mm:ss.S */
        public static final String DF_HH_mm_ss_S = "HH:mm:ss.S";

        /** HH:mm:ss */
        public static final String DF_HH_mm_ss = "HH:mm:ss";

        /** HHmmss */
        public static final String DF_HHmmss = "HHmmss";

        /** HH:mm */
        public static final String DF_HH_mm = "HH:mm";

        /** HHmm */
        public static final String DF_HHmm = "HHmm";

        // 星期

        /** 0 星期天 */
        public final static int SUNDAY = 0;

        /** 1 星期一 */
        public final static int MONDAY = 1;

        /** 2 星期二 */
        public final static int TUESDAY = 2;

        /** 3 星期三 */
        public final static int WEDNESDAY = 3;

        /** 4 星期四 */
        public final static int THURSDAY = 4;

        /** 5 星期五 */
        public final static int FRIDAY = 5;

        /** 6 星期六 */
        public final static int SATURDAY = 6;

        // 月份

        /** 1 一月 */
        public final static int JANUARY = 1;

        /** 2 二月 */
        public final static int FEBRUARY = 2;

        /** 3 三月 */
        public final static int MARCH = 3;

        /** 4 四月 */
        public final static int APRIL = 4;

        /** 5 五月 */
        public final static int MAY = 5;

        /** 6 六月 */
        public final static int JUNE = 6;

        /** 7 七月 */
        public final static int JULY = 7;

        /** 8 八月 */
        public final static int AUGUST = 8;

        /** 9 九月 */
        public final static int SEPTEMBER = 9;

        /** 10 十月 */
        public final static int OCTOBER = 10;

        /** 11 十一月 */
        public final static int NOVEMBER = 11;

        /** 12 十二月 */
        public final static int DECEMBER = 12;

        /** 每个月的天数（平年），下标说明：1月份的下标是1，2月份的下标是2 */
        public static final int[] DAYS_OF_MONTH = {
            0,
            31,
            28,
            31,
            30,
            31,
            30,
            31,
            31,
            30,
            31,
            30,
            31
        };

        private static final long serialVersionUID = 4990495169914196127L;

        public Datetime() {
            super(System.currentTimeMillis());
        }

        public Datetime(Date date) {
            super(date.getTime());
        }

        public Datetime(long time) {
            super(time);
        }

        /**
         * 解析传入的字符串时间，默认格式为yyyy-MM-dd HH:mm:ss.SSSZ，默认输出格式可在{@link DatetimeUtils}中指定，使用当前系统默认的时区
         */
        public Datetime(String date) throws ParseException {
            super(_getDateFormat(defaultDateTimeFormat, null).parse(date)
                                                             .getTime());
        }

        /**
         * 解析传入的字符串时间，使用传入的时间格式解析，使用当前系统默认的时区
         *
         * @param format 传入的字符串时间的时间格式，注意该格式仅用于解析传入的时间，该对象再次输出时并不会默认使用该格式
         */
        public Datetime(String date, String format) throws ParseException {
            super(_getDateFormat(format, null).parse(date)
                                              .getTime());
        }

        /**
         * 解析传入的字符串时间，默认格式为yyyy-MM-dd HH:mm:ss.SSSZ，默认输出格式可在{@link DatetimeUtils}中指定，使用传入的时区
         *
         * @param tz 传入的字符串时间的时区，注意该时区仅用于解析传入的时间，该对象再次输出时并不会默认使用该时区
         */
        public Datetime(String date, TimeZone tz) throws ParseException {
            super(_getDateFormat(defaultDateTimeFormat, tz).parse(date)
                                                           .getTime());
        }

        /**
         * 解析传入的字符串时间，使用传入的时间格式和时区解析
         *
         * @param format 传入的字符串时间的时间格式，注意该格式仅用于解析传入的时间，该对象再次输出时并不会默认使用该格式
         * @param tz 传入的字符串时间的时区，注意该时区仅用于解析传入的时间，该对象再次输出时并不会默认使用该时区
         */
        public Datetime(String date, String format, TimeZone tz) throws ParseException {
            super(_getDateFormat(format, tz).parse(date)
                                            .getTime());
        }

        /**
         * 根据传入的时间构造{@link Datetime}，使用系统默认的时区
         */
        public Datetime(int year, int monthOfYear, int dayOfMonth) {
            this(year, monthOfYear, dayOfMonth, 0, 0, 0, 0);
        }

        /**
         * 根据传入的时间构造{@link Datetime}，使用指定的时区
         */
        public Datetime(int year, int monthOfYear, int dayOfMonth, TimeZone tz) {
            this(year, monthOfYear, dayOfMonth, 0, 0, 0, 0, tz);
        }

        /**
         * 根据传入的时间构造{@link Datetime}，使用系统默认的时区
         */
        public Datetime(int year, int monthOfYear, int dayOfMonth, int hourOfDay) {
            this(year, monthOfYear, dayOfMonth, hourOfDay, 0, 0, 0);
        }

        /**
         * 根据传入的时间构造{@link Datetime}，使用指定的时区
         */
        public Datetime(int year, int monthOfYear, int dayOfMonth, int hourOfDay, TimeZone tz) {
            this(year, monthOfYear, dayOfMonth, hourOfDay, 0, 0, 0, tz);
        }

        /**
         * 根据传入的时间构造{@link Datetime}，使用系统默认的时区
         */
        public Datetime(int year, int monthOfYear, int dayOfMonth, int hourOfDay, int minuteOfHour) {
            this(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour, 0, 0);
        }

        /**
         * 根据传入的时间构造{@link Datetime}，使用指定的时区
         */
        public Datetime(int year, int monthOfYear, int dayOfMonth, int hourOfDay, int minuteOfHour, TimeZone tz) {
            this(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour, 0, 0, tz);
        }

        /**
         * 根据传入的时间构造{@link Datetime}，使用系统默认的时区
         */
        public Datetime(int year, int monthOfYear, int dayOfMonth, int hourOfDay, int minuteOfHour, int secondOfMinute) {
            this(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour, secondOfMinute, 0);
        }

        /**
         * 根据传入的时间构造{@link Datetime}，使用指定的时区
         */
        public Datetime(int year, int monthOfYear, int dayOfMonth, int hourOfDay, int minuteOfHour, int secondOfMinute, TimeZone tz) {
            this(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour, secondOfMinute, 0, tz);
        }

        /**
         * 根据传入的时间构造{@link Datetime}，使用系统默认的时区
         */
        public Datetime(int year, int monthOfYear, int dayOfMonth, int hourOfDay, int minuteOfHour, int secondOfMinute, int millisOfSecond) {
            this(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour, secondOfMinute, millisOfSecond, TimeZone.getDefault());
        }

        /**
         * 根据传入的时间构造{@link Datetime}，使用指定的时区
         */
        public Datetime(int year, int monthOfYear, int dayOfMonth, int hourOfDay, int minuteOfHour, int secondOfMinute, int millisOfSecond, TimeZone tz) {
            Calendar cal = Calendar.getInstance(tz);
            cal.setLenient(false);
            if (year < 0) {
                cal.set(Calendar.ERA, GregorianCalendar.BC);
                cal.set(Calendar.YEAR, -year);
            } else {
                cal.set(Calendar.YEAR, year);
            }
            cal.set(Calendar.MONTH, monthOfYear - 1);
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
            cal.set(Calendar.MINUTE, minuteOfHour);
            cal.set(Calendar.SECOND, secondOfMinute);
            cal.set(Calendar.MILLISECOND, millisOfSecond);
            setTime(cal.getTime()
                       .getTime());
        }

        /**
         * 字符串转时间
         */
        public static Datetime valueOf(String s) {
            return DatetimeUtils.parse(s);
        }
        // 已废弃的方法不允许再调用

        /**
         * 获取所在月份有多少天，例如1月返回31天，使用系统默认的时区
         */
        public int getDayCountOfMonth() {
            return DatetimeUtils.getDayCountOfMonth(this);
        }

        /**
         * 获取所在月份有多少天，例如1月返回31天，使用指定的时区
         */
        public int getDayCountOfMonth(TimeZone tz) {
            return DatetimeUtils.getDayCountOfMonth(this, tz);
        }

        /**
         * 判断今年是否为闰年，使用系统默认的时区
         */
        public boolean isLeapYear() {
            return DatetimeUtils.isLeapYear(this);
        }

        /**
         * 判断今年是否为闰年，使用指定的时区
         */
        public boolean isLeapYear(TimeZone tz) {
            return DatetimeUtils.isLeapYear(this, tz);
        }

        /**
         * 获取UNIX时间戳
         */
        public int getUnixTime() {
            return (int) (getTime() / Datetime.MILLIS_PER_SECOND);
        }

        /**
         * 判断代表时刻是否在date之前
         */
        public boolean isBefore(Date date) {
            return DatetimeUtils.isBefore(this, date);
        }

        /**
         * 判断代表时刻是否在date之前
         *
         * @param date 如果解析失败,直接返回false
         */
        public boolean isBefore(String date) {
            Datetime _date = DatetimeUtils.parse(date);
            return _date != null && DatetimeUtils.isBefore(this, _date);
        }

        /**
         * 判断代表时刻是否在指定的毫秒时间戳之前
         */
        public boolean isBefore(long timestamp) {
            return getTime() < timestamp;
        }

        /**
         * 判断代表时刻是否在当前时刻之前
         */
        public boolean isBeforeNow() {
            return DatetimeUtils.isBeforeNow(this);
        }

        /**
         * 判断代表时刻是否在date之后
         */
        public boolean isAfter(Date date) {
            return DatetimeUtils.isAfter(this, date);
        }

        /**
         * 判断代表时刻是否在date之后
         *
         * @param date 如果解析失败,直接返回false
         */
        public boolean isAfter(String date) {
            Datetime _date = DatetimeUtils.parse(date);
            return _date != null && DatetimeUtils.isAfter(this, _date);
        }

        /**
         * 判断代表时刻是否在指定的毫秒时间戳之后
         */
        public boolean isAfter(long timestamp) {
            return getTime() > timestamp;
        }

        /**
         * 判断代表时刻是否在当前时刻之后
         */
        public boolean isAfterNow() {
            return DatetimeUtils.isAfterNow(this);
        }

        /**
         * 获取对应时刻的【毫秒】，如12:30:45.123，返回123
         *
         * @return 返回范围0-999
         */
        public int getMillisOfSecond() {
            return DatetimeUtils.getMillisOfSecond(this);
        }

        /**
         * 获取对应时刻的【秒】，如12:30:45，返回45
         *
         * @return 返回范围0-59
         */
        public int getSecondOfMinute() {
            return DatetimeUtils.getSecondOfMinute(this);
        }

        /**
         * 获取对应时刻的【分】，如12:30:45，返回30，使用系统默认的时区
         *
         * @return 返回范围0-59
         */
        public int getMinuteOfHour() {
            return DatetimeUtils.getMinuteOfHour(this);
        }

        /**
         * 获取对应时刻的【分】，如12:30:45，返回30，使用指定的时区
         *
         * @return 返回范围0-59
         */
        public int getMinuteOfHour(TimeZone tz) {
            return DatetimeUtils.getMinuteOfHour(this, tz);
        }

        /**
         * 获取对应时刻的【时】，如12:30:45，返回12，使用系统默认的时区
         *
         * @return 返回范围0-23
         */
        public int getHourOfDay() {
            return DatetimeUtils.getHourOfDay(this);
        }

        // 时间比较

        /**
         * 获取对应时刻的【时】，如12:30:45，返回12，使用指定的时区
         *
         * @return 返回范围0-23
         */
        public int getHourOfDay(TimeZone tz) {
            return DatetimeUtils.getHourOfDay(this, tz);
        }

        /**
         * 获取对应日期是星期几，星期天为0，星期一到六为1-6，使用系统默认的时区
         *
         * @return 返回范围0-6 对应星期天到星期六，可使用{@link Datetime}的SUNDAY - SATURSDAY来指代
         */
        public int getDayOfWeek() {
            return DatetimeUtils.getDayOfWeek(this);
        }

        /**
         * 获取对应日期是星期几，星期天为0，星期一到六为1-6，使用指定的时区
         *
         * @return 返回范围0-6 对应星期天到星期六，可使用{@link Datetime}的SUNDAY - SATURSDAY来指代
         */
        public int getDayOfWeek(TimeZone tz) {
            return DatetimeUtils.getDayOfWeek(this, tz);
        }

        /**
         * 获取对应的月份的日期，如2月1日，就返回1，使用系统默认的时区
         *
         * @return 返回范围1-31，最大为当月的最后一天的数值
         */
        public int getDayOfMonth() {
            return DatetimeUtils.getDayOfMonth(this);
        }

        /**
         * 获取对应的月份的日期，如2月1日，就返回1，使用指定的时区
         *
         * @return 返回范围1-31，最大为当月的最后一天的数值
         */
        public int getDayOfMonth(TimeZone tz) {
            return DatetimeUtils.getDayOfMonth(this, tz);
        }

        /**
         * 获取对应日期是这一年的第几天，使用系统默认的时区
         *
         * @return 返回范围平年1-365，闰年1-366
         */
        public int getDayOfYear() {
            return DatetimeUtils.getDayOfYear(this);
        }

        /**
         * 获取对应日期是这一年的第几天，使用指定的时区
         *
         * @return 返回范围平年1-365，闰年1-366
         */
        public int getDayOfYear(TimeZone tz) {
            return DatetimeUtils.getDayOfYear(this, tz);
        }

        /**
         * 获取对应日期的【月】，使用系统默认的时区
         *
         * @return 返回范围1-12，可使用{@link Datetime}的JANUARY - DECEMBER来指代
         */
        public int getMonthOfYear() {
            return DatetimeUtils.getMonthOfYear(this);
        }

        // 提取日期时间中的值

        /**
         * 获取对应日期的【月】，使用指定的时区
         *
         * @return 返回范围1-12，可使用{@link Datetime}的JANUARY - DECEMBER来指代
         */
        public int getMonthOfYear(TimeZone tz) {
            return DatetimeUtils.getMonthOfYear(this, tz);
        }

        /**
         * 获取对应日期的【年】，公元前的时间，例如221BC将表示为-221，使用系统默认的时区
         */
        @Override
        @SuppressWarnings("deprecation")
        public int getYear() {
            return DatetimeUtils.getYear(this);
        }

        @Override
        @Deprecated
        @SuppressWarnings("deprecation")
        public void setYear(int year) {
            throw new UnsupportedOperationException(Deprecated.class.getSimpleName());
        }

        @Override
        @Deprecated
        @SuppressWarnings("deprecation")
        public int getMonth() {
            throw new UnsupportedOperationException(Deprecated.class.getSimpleName());
        }

        @Override
        @Deprecated
        @SuppressWarnings("deprecation")
        public void setMonth(int month) {
            throw new UnsupportedOperationException(Deprecated.class.getSimpleName());
        }

        @Override
        @Deprecated
        @SuppressWarnings("deprecation")
        public int getDate() {
            throw new UnsupportedOperationException(Deprecated.class.getSimpleName());
        }

        @Override
        @Deprecated
        @SuppressWarnings("deprecation")
        public void setDate(int date) {
            throw new UnsupportedOperationException(Deprecated.class.getSimpleName());
        }

        @Override
        @Deprecated
        @SuppressWarnings("deprecation")
        public int getDay() {
            throw new UnsupportedOperationException(Deprecated.class.getSimpleName());
        }

        @Override
        @Deprecated
        @SuppressWarnings("deprecation")
        public int getHours() {
            throw new UnsupportedOperationException(Deprecated.class.getSimpleName());
        }

        @Override
        @Deprecated
        @SuppressWarnings("deprecation")
        public void setHours(int hours) {
            throw new UnsupportedOperationException(Deprecated.class.getSimpleName());
        }

        @Override
        @Deprecated
        @SuppressWarnings("deprecation")
        public int getMinutes() {
            throw new UnsupportedOperationException(Deprecated.class.getSimpleName());
        }

        @Override
        @Deprecated
        @SuppressWarnings("deprecation")
        public void setMinutes(int minutes) {
            throw new UnsupportedOperationException(Deprecated.class.getSimpleName());
        }

        @Override
        @Deprecated
        @SuppressWarnings("deprecation")
        public int getSeconds() {
            throw new UnsupportedOperationException(Deprecated.class.getSimpleName());
        }

        @Override
        @Deprecated
        @SuppressWarnings("deprecation")
        public void setSeconds(int seconds) {
            throw new UnsupportedOperationException(Deprecated.class.getSimpleName());
        }

        /**
         * 获取自1970-01-01T00:00:00+00:00到当前对象代表时刻的毫秒数，如果当前对象代表时刻在此之前，将返回负数
         */
        @Override
        public long getTime() {
            return super.getTime();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Datetime && getTime() == ((Datetime) obj).getTime();
        }

        @Override
        public int hashCode() {
            long ht = this.getTime();
            return (int) ht ^ (int) (ht >> 32);
        }

        // 时间修剪

        /**
         * 转换为对应格式的时间字符串，默认格式为{@link #defaultDateTimeFormat}，使用当前系统默认的时区
         */
        @Override
        public String toString() {
            return DatetimeUtils.format(this);
        }

        @Override
        @Deprecated
        @SuppressWarnings("deprecation")
        public int getTimezoneOffset() {
            throw new UnsupportedOperationException(Deprecated.class.getSimpleName());
        }

        /**
         * 获取对应日期的【年】，公元前的时间，例如221BC将表示为-221，使用指定的时区
         */
        public int getYear(TimeZone tz) {
            return DatetimeUtils.getYear(this, tz);
        }

        /**
         * 修剪获取时间所在的那一【秒】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 12:34.56.000
         */
        public Datetime retainSecond() {
            return DatetimeUtils.retainSecond(this);
        }

        /**
         * 修剪获取时间所在的那一【分】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 12:34.00.000
         */
        public Datetime retainMinute() {
            return DatetimeUtils.retainMinute(this);
        }

        /**
         * 修剪获取时间所在的那一【小时】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 12:00.00.000，使用系统默认的时区
         */
        public Datetime retainHour() {
            return DatetimeUtils.retainHour(this);
        }

        /**
         * 修剪获取时间所在的那一【小时】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 12:00.00.000，使用指定的时区
         */
        public Datetime retainHour(TimeZone tz) {
            return DatetimeUtils.retainHour(this, tz);
        }

        /**
         * 修剪获取时间所在的那一【天】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用系统默认的时区
         */
        public Datetime retainDay() {
            return DatetimeUtils.retainDay(this);
        }

        /**
         * 修剪获取时间所在的那一【天】，例如2015-01-01 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用指定的时区
         */
        public Datetime retainDay(TimeZone tz) {
            return DatetimeUtils.retainDay(this, tz);
        }

        /**
         * 修剪获取时间所在的那一【月的第一天】，例如2015-01-02 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用系统默认的时区
         */
        public Datetime retainMonth() {
            return DatetimeUtils.retainMonth(this);
        }

        /**
         * 修剪获取时间所在的那一【月的第一天】，例如2015-01-02 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用指定的时区
         */
        public Datetime retainMonth(TimeZone tz) {
            return DatetimeUtils.retainMonth(this, tz);
        }

        /**
         * 修剪获取时间所在的那一【周的第一天（星期天为每周的第一天）】，例如2015-02-28 12:34:56.789，修剪到星期天将返回2015-02-22 00:00.00.000，使用系统默认的时区
         */
        public Datetime retainWeek() {
            return DatetimeUtils.retainWeek(this);
        }

        // 格式化输出

        /**
         * 修剪获取时间所在的那一【周的第一天（星期天为每周的第一天）】，例如2015-02-28 12:34:56.789，修剪到星期天将返回2015-02-22 00:00.00.000，使用指定的时区
         */
        public Datetime retainWeek(TimeZone tz) {
            return DatetimeUtils.retainWeek(this, tz);
        }

        /**
         * 修剪获取时间所在的那一【年的第一天】，例如2015-01-02 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用系统默认的时区
         */
        public Datetime retainYear() {
            return DatetimeUtils.retainYear(this);
        }

        /**
         * 修剪获取时间所在的那一【年的第一天】，例如2015-01-02 12:34:56.789修剪后将返回2015-01-01 00:00.00.000，使用指定的时区
         */
        public Datetime retainYear(TimeZone tz) {
            return DatetimeUtils.retainYear(this, tz);
        }

        /**
         * 转换为对应格式的时间字符串，默认格式为yyyy-MM-dd HH:mm:ss.SSSZ，默认输出格式可在{@link DatetimeUtils}中指定，使用指定的时区
         */
        public String toString(TimeZone tz) {
            return DatetimeUtils.format(this, tz);
        }

        /**
         * 转换为对应格式的时间字符串，使用当前系统默认的时区
         */
        public String toString(String format) {
            return DatetimeUtils.format(this, format);
        }

        /**
         * 转换为对应格式的时间字符串，使用指定的时区
         */
        public String toString(String format, TimeZone tz) {
            return DatetimeUtils.format(this, format, tz);
        }

        // 时间操作

        /**
         * 转换为对应格式的时间字符串，默认格式为yyyy-MM-dd，默认输出格式可在DatetimeUtil中指定，使用当前系统默认的时区
         */
        public String toDateString() {
            return DatetimeUtils.formatDate(this);
        }

        /**
         * 转换为对应格式的时间字符串，默认格式为yyyy-MM-dd，默认输出格式可在DatetimeUtil中指定，使用指定的时区
         */
        public String toDateString(TimeZone tz) {
            return DatetimeUtils.formatDate(this, tz);
        }

        /**
         * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【毫秒】数后的结果
         *
         * @param millis 如果传入0将直接返回原对象
         */
        public Datetime plusMillis(long millis) {
            return DatetimeUtils.plusMillis(this, millis);
        }

        /**
         * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【秒】数后的结果
         *
         * @param seconds 如果传入0将直接返回原对象
         */
        public Datetime plusSeconds(long seconds) {
            return DatetimeUtils.plusSeconds(this, seconds);
        }

        /**
         * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【分】数后的结果
         *
         * @param minutes 如果传入0将直接返回原对象
         */
        public Datetime plusMinutes(long minutes) {
            return DatetimeUtils.plusMinutes(this, minutes);
        }

        /**
         * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【时】数后的结果
         *
         * @param hours 如果传入0将直接返回原对象
         */
        public Datetime plusHours(long hours) {
            return DatetimeUtils.plusHours(this, hours);
        }

        /**
         * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【天】数后的结果
         *
         * @param days 如果传入0将直接返回原对象
         */
        public Datetime plusDays(long days) {
            return DatetimeUtils.plusDays(this, days);
        }

        /**
         * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【周】数后的结果
         *
         * @param weeks 如果传入0将直接返回原对象
         */
        public Datetime plusWeeks(long weeks) {
            return DatetimeUtils.plusWeeks(this, weeks);
        }

        /**
         * <pre>
         * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【月】数后的结果，使用系统默认的时区
         *
         * 默认情况是对应月份的这一天，如果这一天不存在，返回的将是这个月的最后一天
         * 如今天是3月31日，加1个月，将返回4月30日
         * </pre>
         *
         * @param months 如果传入0将直接返回原对象
         */
        public Datetime plusMonths(int months) {
            return DatetimeUtils.plusMonths(this, months);
        }

        /**
         * <pre>
         * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【月】数后的结果，使用指定的时区
         *
         * 默认情况是对应月份的这一天，如果这一天不存在，返回的将是这个月的最后一天
         * 如今天是3月31日，加1个月，将返回4月30日
         * </pre>
         *
         * @param months 如果传入0将直接返回原对象
         */
        public Datetime plusMonths(int months, TimeZone tz) {
            return DatetimeUtils.plusMonths(this, months, tz);
        }

        /**
         * <pre>
         * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【年】数后的结果，使用系统默认的时区
         *
         * 如果当年是闰年2月29日，加上一年后是平年，返回的将是2月28日
         * </pre>
         *
         * @param years 如果传入0将直接返回原对象
         */
        public Datetime plusYears(int years) {
            return DatetimeUtils.plusYears(this, years);
        }

        /**
         * <pre>
         * 返回一个新生成的{@link Datetime}对象，为当前时刻加上指定的【年】数后的结果，使用指定的时区
         *
         * 如果当年是闰年2月29日，加上一年后是平年，返回的将是2月28日
         * </pre>
         *
         * @param years 如果传入0将直接返回原对象
         */
        public Datetime plusYears(int years, TimeZone tz) {
            return DatetimeUtils.plusYears(this, years, tz);
        }

        /**
         * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【毫秒】数后的结果
         *
         * @param millis 如果传入0将直接返回原对象
         */
        public Datetime minusMillis(long millis) {
            return DatetimeUtils.minusMillis(this, millis);
        }

        /**
         * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【秒】数后的结果
         *
         * @param seconds 如果传入0将直接返回原对象
         */
        public Datetime minusSeconds(long seconds) {
            return DatetimeUtils.minusSeconds(this, seconds);
        }

        /**
         * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【分】数后的结果
         *
         * @param minutes 如果传入0将直接返回原对象
         */
        public Datetime minusMinutes(long minutes) {
            return DatetimeUtils.minusMinutes(this, minutes);
        }

        /**
         * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【时】数后的结果
         *
         * @param hours 如果传入0将直接返回原对象
         */
        public Datetime minusHours(long hours) {
            return DatetimeUtils.minusHours(this, hours);
        }

        /**
         * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【天】数后的结果
         *
         * @param days 如果传入0将直接返回原对象
         */
        public Datetime minusDays(long days) {
            return DatetimeUtils.minusDays(this, days);
        }

        /**
         * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【周】数后的结果
         *
         * @param weeks 如果传入0将直接返回原对象
         */
        public Datetime minusWeeks(long weeks) {
            return DatetimeUtils.minusWeeks(this, weeks);
        }

        /**
         * <pre>
         * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【月】数后的结果，使用系统默认的时区
         *
         * 默认情况是对应月份的这一天，如果这一天不存在，返回的将是这个月的最后一天
         * 如今天是5月31日，减1个月，将返回4月30日
         * </pre>
         *
         * @param months 如果传入0将直接返回原对象
         */
        public Datetime minusMonths(int months) {
            return DatetimeUtils.minusMonths(this, months);
        }

        /**
         * <pre>
         * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【月】数后的结果，使用指定的时区
         *
         * 默认情况是对应月份的这一天，如果这一天不存在，返回的将是这个月的最后一天
         * 如今天是5月31日，减1个月，将返回4月30日
         * </pre>
         *
         * @param months 如果传入0将直接返回原对象
         */
        public Datetime minusMonths(int months, TimeZone tz) {
            return DatetimeUtils.minusMonths(this, months, tz);
        }

        // 时间差

        /**
         * <pre>
         * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【年】数后的结果，使用系统默认的时区
         *
         * 如果当年是闰年2月29日，减去一年后是平年，返回的将是2月28日
         * </pre>
         *
         * @param years 如果传入0将直接返回原对象
         */
        public Datetime minusYears(int years) {
            return DatetimeUtils.minusYears(this, years);
        }

        /**
         * <pre>
         * 返回一个新生成的{@link Datetime}对象，为当前时刻减去指定的【年】数后的结果，使用指定的时区
         *
         * 如果当年是闰年2月29日，减去一年后是平年，返回的将是2月28日
         * </pre>
         *
         * @param years 如果传入0将直接返回原对象
         */
        public Datetime minusYears(int years, TimeZone tz) {
            return DatetimeUtils.minusYears(this, years, tz);
        }

        /**
         * 获取从start到当前时刻所经过的【毫秒】数
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getMillisAfter(long start) {
            return DatetimeUtils.getMillisBetween(start, getTime());
        }

        /**
         * 获取从start到当前时刻所经过的【秒】数，将直接比较二者的时间差，>=1000ms才算是1s
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getSecondsAfter(long start) {
            return DatetimeUtils.getSecondsBetween(start, getTime());
        }

        /**
         * 获取从start到当前时刻所经过的【分】数，将直接比较二者的时间差，>=60s才算是1min
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getMinutesAfter(long start) {
            return DatetimeUtils.getMinutesBetween(start, getTime());
        }

        /**
         * 获取从start到当前时刻所经过的【小时】数，将直接比较二者的时间差，>=60min才算是1h
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getHoursAfter(long start) {
            return DatetimeUtils.getHoursBetween(start, getTime());
        }

        /**
         * 获取从start到当前时刻所经过的【天】数，将直接比较二者的时间差，>=24h才算是1day
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getDaysAfter(long start) {
            return DatetimeUtils.getDaysBetween(start, getTime());
        }

        /**
         * 获取从start到当前时刻所经过的【天】数，将比较二者的日期差，以自然天计算，日期相差1但差距不满24h的也可以算是1day，使用系统默认的时区
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getDaysInCalendarAfter(long start) {
            return DatetimeUtils.getDaysInCalendarBetween(start, getTime());
        }

        /**
         * 获取从start到当前时刻所经过的【天】数，将比较二者的日期差，以自然天计算，日期相差1但差距不满24h的也可以算是1day，使用指定的时区
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getDaysInCalendarAfter(long start, TimeZone tz) {
            return DatetimeUtils.getDaysInCalendarBetween(start, getTime(), tz);
        }

        /**
         * 获取从start到当前时刻所经过的【周】数，必须要满足7*24h才能算是一周
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getWeeksAfter(long start) {
            return DatetimeUtils.getWeeksBetween(start, getTime());
        }

        /**
         * 获取从start到当前时刻所经过的【周】数，将比较二者的日期差，以自然天7天计算，日期相差1但差距不满24h的也可以算是1day，使用系统默认的时区
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getWeeksInCalendarAfter(long start) {
            return DatetimeUtils.getWeeksInCalendarBetween(start, getTime());
        }

        /**
         * 获取从start到当前时刻所经过的【周】数，将比较二者的日期差，以自然天7天计算，日期相差1但差距不满24h的也可以算是1day，使用指定的时区
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getWeeksInCalendarAfter(long start, TimeZone tz) {
            return DatetimeUtils.getWeeksInCalendarBetween(start, getTime(), tz);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【月】数，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），要求【必须满24h才能算1day】，使用系统默认的时区
         * 例如start为2015-01-01 12:00:00则end必须要>=2015-02-01 12:00:00才能算是一个月
         * </pre>
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getMonthsAfter(long start) {
            return DatetimeUtils.getMonthsBetween(start, getTime());
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【月】数，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），要求【必须满24h才能算1day】，使用指定的时区
         * 例如start为2015-01-01 12:00:00则end必须要>=2015-02-01 12:00:00才能算是一个月
         * </pre>
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getMonthsAfter(long start, TimeZone tz) {
            return DatetimeUtils.getMonthsBetween(start, getTime(), tz);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【月】数，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），不要求一定要满24h，使用系统默认的时区
         * 例如start为2015-01-01 12:00:00则end只需>=2015-02-01 00:00:00就可以算是一个月
         * </pre>
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getMonthsInCalendarAfter(long start) {
            return DatetimeUtils.getMonthsInCalendarBetween(start, getTime());
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【月】数，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），不要求一定要满24h，使用指定的时区
         * 例如start为2015-01-01 12:00:00则end只需>=2015-02-01 00:00:00就可以算是一个月
         * </pre>
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getMonthsInCalendarAfter(long start, TimeZone tz) {
            return DatetimeUtils.getMonthsInCalendarBetween(start, getTime(), tz);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【年】数，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），要求【必须满24h才能算1day】，使用系统默认的时区
         * 例如start为2015-01-01 12:00:00则end必须要>=2016-01-01 12:00:00才能算是一年
         * </pre>
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getYearsAfter(long start) {
            return DatetimeUtils.getYearsBetween(start, getTime());
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【年】数，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），要求【必须满24h才能算1day】，使用指定的时区
         * 例如start为2015-01-01 12:00:00则end必须要>=2016-01-01 12:00:00才能算是一年
         * </pre>
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getYearsAfter(long start, TimeZone tz) {
            return DatetimeUtils.getYearsBetween(start, getTime(), tz);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【年】数，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），不要求一定要满24h，使用系统默认的时区
         * 例如start为2015-01-01 12:00:00则end只需>=2016-01-01 00:00:00就可以算是一年
         * </pre>
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getYearsInCalendarAfter(long start) {
            return DatetimeUtils.getYearsInCalendarBetween(start, getTime());
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【年】数，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），不要求一定要满24h，使用指定的时区
         * 例如start为2015-01-01 12:00:00则end只需>=2016-01-01 00:00:00就可以算是一年
         * </pre>
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getYearsInCalendarAfter(long start, TimeZone tz) {
            return DatetimeUtils.getYearsInCalendarBetween(start, getTime(), tz);
        }

        /**
         * 获取从start到当前时刻所经过的【毫秒】数
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getMillisAfter(Date start) {
            return DatetimeUtils.getMillisBetween(start, this);
        }

        /**
         * 获取从start到当前时刻所经过的【毫秒】数
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getMillisAfter(String start) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getMillisBetween(_start, this);
        }

        /**
         * 获取从start到当前时刻所经过的【秒】数，将直接比较二者的时间差，>=1000ms才算是1s
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getSecondsAfter(Date start) {
            return DatetimeUtils.getSecondsBetween(start, this);
        }

        /**
         * 获取从start到当前时刻所经过的【秒】数，将直接比较二者的时间差，>=1000ms才算是1s
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getSecondsAfter(String start) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getSecondsBetween(_start, this);
        }

        /**
         * 获取从start到当前时刻所经过的【分】数，将直接比较二者的时间差，>=60s才算是1min
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getMinutesAfter(Date start) {
            return DatetimeUtils.getMinutesBetween(start, this);
        }

        /**
         * 获取从start到当前时刻所经过的【分】数，将直接比较二者的时间差，>=60s才算是1min
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getMinutesAfter(String start) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getMinutesBetween(_start, this);
        }

        /**
         * 获取从start到当前时刻所经过的【小时】数，将直接比较二者的时间差，>=60min才算是1h
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getHoursAfter(Date start) {
            return DatetimeUtils.getHoursBetween(start, this);
        }

        /**
         * 获取从start到当前时刻所经过的【小时】数，将直接比较二者的时间差，>=60min才算是1h
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getHoursAfter(String start) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getHoursBetween(_start, this);
        }

        /**
         * 获取从start到当前时刻所经过的【天】数，将直接比较二者的时间差，>=24h才算是1day
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getDaysAfter(Date start) {
            return DatetimeUtils.getDaysBetween(start, this);
        }

        /**
         * 获取从start到当前时刻所经过的【天】数，将直接比较二者的时间差，>=24h才算是1day
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getDaysAfter(String start) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getDaysBetween(_start, this);
        }

        /**
         * 获取从start到当前时刻所经过的【天】数，将比较二者的日期差，以自然天计算，日期相差1但差距不满24h的也可以算是1day，使用系统默认的时区
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getDaysInCalendarAfter(Date start) {
            return DatetimeUtils.getDaysInCalendarBetween(start, this);
        }

        /**
         * 获取从start到当前时刻所经过的【天】数，将比较二者的日期差，以自然天计算，日期相差1但差距不满24h的也可以算是1day，使用系统默认的时区
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getDaysInCalendarAfter(String start) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getDaysInCalendarBetween(_start, this);
        }

        /**
         * 获取从start到当前时刻所经过的【天】数，将比较二者的日期差，以自然天计算，日期相差1但差距不满24h的也可以算是1day，使用指定的时区
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getDaysInCalendarAfter(Date start, TimeZone tz) {
            return DatetimeUtils.getDaysInCalendarBetween(start, this, tz);
        }

        /**
         * 获取从start到当前时刻所经过的【天】数，将比较二者的日期差，以自然天计算，日期相差1但差距不满24h的也可以算是1day，使用指定的时区
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getDaysInCalendarAfter(String start, TimeZone tz) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getDaysInCalendarBetween(_start, this, tz);
        }

        /**
         * 获取从start到当前时刻所经过的【周】数，必须要满足7*24h才能算是一周
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getWeeksAfter(Date start) {
            return DatetimeUtils.getWeeksBetween(start, this);
        }

        /**
         * 获取从start到当前时刻所经过的【周】数，必须要满足7*24h才能算是一周
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getWeeksAfter(String start) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getWeeksBetween(_start, this);
        }

        /**
         * 获取从start到当前时刻所经过的【周】数，将比较二者的日期差，以自然天7天计算，日期相差1但差距不满24h的也可以算是1day，使用系统默认的时区
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getWeeksInCalendarAfter(Date start) {
            return DatetimeUtils.getWeeksInCalendarBetween(start, this);
        }

        /**
         * 获取从start到当前时刻所经过的【周】数，将比较二者的日期差，以自然天7天计算，日期相差1但差距不满24h的也可以算是1day，使用系统默认的时区
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getWeeksInCalendarAfter(String start) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getWeeksInCalendarBetween(_start, this);
        }

        /**
         * 获取从start到当前时刻所经过的【周】数，将比较二者的日期差，以自然天7天计算，日期相差1但差距不满24h的也可以算是1day，使用指定的时区
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getWeeksInCalendarAfter(Date start, TimeZone tz) {
            return DatetimeUtils.getWeeksInCalendarBetween(start, this, tz);
        }

        /**
         * 获取从start到当前时刻所经过的【周】数，将比较二者的日期差，以自然天7天计算，日期相差1但差距不满24h的也可以算是1day，使用指定的时区
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public long getWeeksInCalendarAfter(String start, TimeZone tz) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getWeeksInCalendarBetween(_start, this, tz);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【月】数，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），要求【必须满24h才能算1day】，使用系统默认的时区
         * 例如start为2015-01-01 12:00:00则end必须要>=2015-02-01 12:00:00才能算是一个月
         * </pre>
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getMonthsAfter(Date start) {
            return DatetimeUtils.getMonthsBetween(start, this);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【月】数，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），要求【必须满24h才能算1day】，使用系统默认的时区
         * 例如start为2015-01-01 12:00:00则end必须要>=2015-02-01 12:00:00才能算是一个月
         * </pre>
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getMonthsAfter(String start) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getMonthsBetween(_start, this);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【月】数，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），要求【必须满24h才能算1day】，使用指定的时区
         * 例如start为2015-01-01 12:00:00则end必须要>=2015-02-01 12:00:00才能算是一个月
         * </pre>
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getMonthsAfter(Date start, TimeZone tz) {
            return DatetimeUtils.getMonthsBetween(start, this, tz);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【月】数，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），要求【必须满24h才能算1day】，使用指定的时区
         * 例如start为2015-01-01 12:00:00则end必须要>=2015-02-01 12:00:00才能算是一个月
         * </pre>
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getMonthsAfter(String start, TimeZone tz) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getMonthsBetween(_start, this, tz);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【月】数，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），不要求一定要满24h，使用系统默认的时区
         * 例如start为2015-01-01 12:00:00则end只需>=2015-02-01 00:00:00就可以算是一个月
         * </pre>
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getMonthsInCalendarAfter(Date start) {
            return DatetimeUtils.getMonthsInCalendarBetween(start, this);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【月】数，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），不要求一定要满24h，使用系统默认的时区
         * 例如start为2015-01-01 12:00:00则end只需>=2015-02-01 00:00:00就可以算是一个月
         * </pre>
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getMonthsInCalendarAfter(String start) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getMonthsInCalendarBetween(_start, this);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【月】数，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），不要求一定要满24h，使用指定的时区
         * 例如start为2015-01-01 12:00:00则end只需>=2015-02-01 00:00:00就可以算是一个月
         * </pre>
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getMonthsInCalendarAfter(Date start, TimeZone tz) {
            return DatetimeUtils.getMonthsInCalendarBetween(start, this, tz);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【月】数，要求end的日期必须要>=start的才能算是一个月（一个月天数可不固定，分大小月），不要求一定要满24h，使用指定的时区
         * 例如start为2015-01-01 12:00:00则end只需>=2015-02-01 00:00:00就可以算是一个月
         * </pre>
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getMonthsInCalendarAfter(String start, TimeZone tz) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getMonthsInCalendarBetween(_start, this, tz);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【年】数，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），要求【必须满24h才能算1day】，使用系统默认的时区
         * 例如start为2015-01-01 12:00:00则end必须要>=2016-01-01 12:00:00才能算是一年
         * </pre>
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getYearsAfter(Date start) {
            return DatetimeUtils.getYearsBetween(start, this);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【年】数，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），要求【必须满24h才能算1day】，使用系统默认的时区
         * 例如start为2015-01-01 12:00:00则end必须要>=2016-01-01 12:00:00才能算是一年
         * </pre>
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getYearsAfter(String start) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getYearsBetween(_start, this);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【年】数，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），要求【必须满24h才能算1day】，使用指定的时区
         * 例如start为2015-01-01 12:00:00则end必须要>=2016-01-01 12:00:00才能算是一年
         * </pre>
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getYearsAfter(Date start, TimeZone tz) {
            return DatetimeUtils.getYearsBetween(start, this, tz);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【年】数，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），要求【必须满24h才能算1day】，使用指定的时区
         * 例如start为2015-01-01 12:00:00则end必须要>=2016-01-01 12:00:00才能算是一年
         * </pre>
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getYearsAfter(String start, TimeZone tz) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getYearsBetween(_start, this, tz);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【年】数，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），不要求一定要满24h，使用系统默认的时区
         * 例如start为2015-01-01 12:00:00则end只需>=2016-01-01 00:00:00就可以算是一年
         * </pre>
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getYearsInCalendarAfter(Date start) {
            return DatetimeUtils.getYearsInCalendarBetween(start, this);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【年】数，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），不要求一定要满24h，使用系统默认的时区
         * 例如start为2015-01-01 12:00:00则end只需>=2016-01-01 00:00:00就可以算是一年
         * </pre>
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getYearsInCalendarAfter(String start) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getYearsInCalendarBetween(_start, this);
        }

        // 由于内部只保存时间戳，进行比对时只需要对时间戳即可，相关方法抄自java.util.Date

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【年】数，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），不要求一定要满24h，使用指定的时区
         * 例如start为2015-01-01 12:00:00则end只需>=2016-01-01 00:00:00就可以算是一年
         * </pre>
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getYearsInCalendarAfter(Date start, TimeZone tz) {
            return DatetimeUtils.getYearsInCalendarBetween(start, this, tz);
        }

        /**
         * <pre>
         * 获取从start到当前时刻所经过的【年】数，要求end的日期必须要>=start的才能算是一年（支持平年/闰年的处理），不要求一定要满24h，使用指定的时区
         * 例如start为2015-01-01 12:00:00则end只需>=2016-01-01 00:00:00就可以算是一年
         * </pre>
         *
         * @param start 如果解析失败,直接返回{@link Integer#MIN_VALUE}
         *
         * @return 如果start时刻在当前所代表时刻之前，将返回正数的结果，如果start时刻在当前所代表时刻之后则会返回负数的结果
         */
        public int getYearsInCalendarAfter(String start, TimeZone tz) {
            Datetime _start = DatetimeUtils.parse(start);
            return _start == null ? Integer.MIN_VALUE : DatetimeUtils.getYearsInCalendarBetween(_start, this, tz);
        }
    }

    /**
     * 在fastjson中解析{@link Datetime}的辅助类
     */
    public static class DatetimeDeserializer extends AbstractDateDeserializer {

        public final static DatetimeDeserializer instance = new DatetimeDeserializer();

        @Override
        @SuppressWarnings("unchecked")
        protected <T> T cast(DefaultJSONParser parser, Type clazz, Object fieldName, Object val) {
            if (val == null) {
                return null;
            }
            if (val instanceof Number) {
                return (T) new Datetime(((Number) val).longValue());
            }
            if (val instanceof String) {
                String strVal = (String) val;
                if (strVal.length() == 0) {
                    return null;
                }
                DateFormat dateFormat = parser.getDateFormat();
                try {
                    Date date = dateFormat.parse(strVal);
                    return (T) new Datetime(date);
                } catch (ParseException e) {
                    // 默认格式解不了，就智能解析一下
                    Datetime date = DatetimeUtils.parse(strVal);
                    if (null != date) {
                        return (T) date;
                    }
                }
                long longVal = Long.parseLong(strVal);
                return (T) new Datetime(longVal);
            }
            if (val instanceof java.util.Date) {
                return (T) new Datetime((Date) val);
            }
            throw new JSONException("parse error");
        }

        @Override
        public int getFastMatchToken() {
            return JSONToken.LITERAL_INT;
        }
    }
}
