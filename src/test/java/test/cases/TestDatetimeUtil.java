package test.cases;

import java.text.ParseException;
import org.etnaframework.core.test.EtnaTestCase;
import org.etnaframework.core.test.annotation.TestDescr;
import org.etnaframework.core.test.annotation.TestLauncherClass;
import org.etnaframework.core.util.DatetimeUtils;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.junit.Test;
import org.springframework.stereotype.Service;
import test.TestEtnaLaunch;

/**
 * {@link Datetime}的测试
 *
 * @author BlackCat
 * @since 2015-03-10
 */
@Service
@TestLauncherClass(TestEtnaLaunch.class)
public class TestDatetimeUtil extends EtnaTestCase {

    @Override
    protected void cleanup() throws Throwable {
        // 默认的时间格式 配置etna.defaultDatetimeFormat
        // 默认的日期格式 配置etna.defaultDateFormat
        log.error("默认的日期时间格式 " + DatetimeUtils.getDefaultDatetimeFormat());
        log.error("默认的日期格式     " + DatetimeUtils.getDefaultDateFormat());

        log.error("当前时间           " + DatetimeUtils.now());
        log.error("当前日期           " + DatetimeUtils.today());
        log.error("昨天这个时候       " + DatetimeUtils.now().minusDays(1));
        log.error("昨天               " + DatetimeUtils.today().minusDays(1));
    }

    /**
     * 从字符串转换{@link Datetime}并格式化输出
     */
    @Test
    @TestDescr("从字符串转换{@link Datetime}并格式化输出")
    public void test001_parse_and_format() {
        String s1 = "2015-03-10 12:34:56.789+0800";
        String s2 = "2015-03-10 12:34:56";
        String s3 = "2015-03-10";

        // 直接通过parse方法来转，如果转失败了将返回null，需要做好空指针判断
        Datetime d1 = DatetimeUtils.parse(s1);
        Datetime d2 = DatetimeUtils.parse(s1);
        Datetime d3 = DatetimeUtils.parse(s3); // 针对日期的转换有一个专门的
        assertNotNull(d1);
        assertNotNull(d2);
        assertNotNull(d3);

        // 通过Datetime的构造方法来转，如果转换失败会抛出异常，需要捕获处理！
        try {
            d1 = new Datetime(s1);
            d2 = new Datetime(s2, Datetime.DF_yyyy_MM_dd_HHmmss);
            d3 = new Datetime(s3, Datetime.DF_yyyy_MM_dd);
        } catch (ParseException e) {
            fail(e.getMessage());
        }
        assertNotNull(d1);
        assertNotNull(d2);
        assertNotNull(d3);

        // Datetime类型的格式化输出可用toString，另外还可指定输出格式
        assertEquals(s1, d1.toString());
        assertEquals(s2, d2.toString(Datetime.DF_yyyy_MM_dd_HHmmss));
        assertEquals(s3, d3.toDateString());
        // 如果是普通的Date类型可以使用format方法
        assertEquals(s1, DatetimeUtils.format(d1));
        assertEquals(s2, DatetimeUtils.format(d2, Datetime.DF_yyyy_MM_dd_HHmmss));
        assertEquals(s3, DatetimeUtils.formatDate(d3));

        // 自定义时间格式
        d1 = new Datetime(2015, 3, 1, 12, 34, 56, 789);
        d2 = new Datetime(2015, 2, 1, 12, 34, 56);
        d3 = new Datetime(2015, 1, 1);
        log.error("{}", d1);
        log.error("{}", d2);
        log.error("{}", d3);

        assertEquals(2015, d1.getYear());
        assertEquals(3, d1.getMonthOfYear());
        assertEquals(1, d1.getDayOfMonth());
        assertEquals(12, d1.getHourOfDay());
        assertEquals(34, d1.getMinuteOfHour());
        assertEquals(56, d1.getSecondOfMinute());
        assertEquals(789, d1.getMillisOfSecond());

        assertEquals(2015, d2.getYear());
        assertEquals(2, d2.getMonthOfYear());
        assertEquals(1, d2.getDayOfMonth());
        assertEquals(12, d2.getHourOfDay());
        assertEquals(34, d2.getMinuteOfHour());
        assertEquals(56, d2.getSecondOfMinute());

        assertEquals(2015, d3.getYear());
        assertEquals(1, d3.getMonthOfYear());
        assertEquals(1, d3.getDayOfMonth());
    }

    /**
     * 增加/减少时间
     */
    @Test
    @TestDescr("增加/减少时间")
    public void test002_plus_and_minus() {
        Datetime d = new Datetime(2015, 3, 31, 12, 34, 56, 789);

        assertEquals(new Datetime(2015, 3, 31, 12, 34, 56, 790), d.plusMillis(1));
        assertEquals(new Datetime(2015, 3, 31, 12, 34, 57, 789), d.plusSeconds(1));
        assertEquals(new Datetime(2015, 3, 31, 12, 35, 56, 789), d.plusMinutes(1));
        assertEquals(new Datetime(2015, 3, 31, 13, 34, 56, 789), d.plusHours(1));
        assertEquals(new Datetime(2015, 4, 1, 12, 34, 56, 789), d.plusDays(1));
        assertEquals(new Datetime(2015, 4, 7, 12, 34, 56, 789), d.plusWeeks(1));
        assertEquals(new Datetime(2015, 4, 30, 12, 34, 56, 789), d.plusMonths(1));
        assertEquals(new Datetime(2016, 3, 31, 12, 34, 56, 789), d.plusYears(1));

        assertEquals(new Datetime(2015, 3, 31, 12, 34, 56, 788), d.minusMillis(1));
        assertEquals(new Datetime(2015, 3, 31, 12, 34, 55, 789), d.minusSeconds(1));
        assertEquals(new Datetime(2015, 3, 31, 12, 33, 56, 789), d.minusMinutes(1));
        assertEquals(new Datetime(2015, 3, 31, 11, 34, 56, 789), d.minusHours(1));
        assertEquals(new Datetime(2015, 3, 30, 12, 34, 56, 789), d.minusDays(1));
        assertEquals(new Datetime(2015, 3, 24, 12, 34, 56, 789), d.minusWeeks(1));
        assertEquals(new Datetime(2015, 2, 28, 12, 34, 56, 789), d.minusMonths(1));
        assertEquals(new Datetime(2014, 3, 31, 12, 34, 56, 789), d.minusYears(1));
    }

    /**
     * 定位到某个时间所在的那一天/那一个月的1号/那一年的1月1号的00:00:00，可用于判断是否是同一天，同一个月等
     */
    @Test
    @TestDescr("定位到某个时间所在的那一天/那一个月的1号/那一年的1月1号的00:00:00，可用于判断是否是同一天，同一个月等")
    public void test003_retain() {
        Datetime d = new Datetime(2015, 3, 31, 12, 34, 56, 789);

        assertEquals(new Datetime(2015, 3, 31, 12, 34, 56), d.retainSecond());
        assertEquals(new Datetime(2015, 3, 31, 12, 34), d.retainMinute());
        assertEquals(new Datetime(2015, 3, 31, 12), d.retainHour());
        assertEquals(new Datetime(2015, 3, 31), d.retainDay());
        assertEquals(new Datetime(2015, 3, 29), d.retainWeek());
        assertEquals(new Datetime(2015, 3, 1), d.retainMonth());
        assertEquals(new Datetime(2015, 1, 1), d.retainYear());
    }

    /**
     * 计算两时间的时间差，以下所有带InCalendar的方法，计算时间差时将只看日期不看具体的时分秒毫秒，否则必须要精确毫秒满24h才能算是一天
     */
    @Test
    @TestDescr("计算两时间的时间差，以下所有带InCalendar的方法，计算时间差时将只看日期不看具体的时分秒毫秒，否则必须要精确毫秒满24h才能算是一天")
    public void test004_between() {
        Datetime d1 = new Datetime(2015, 3, 1, 12, 34, 56, 789);

        assertEquals(1, d1.getSecondsAfter(new Datetime(2015, 3, 1, 12, 34, 55)));
        assertEquals(0, d1.getSecondsAfter(new Datetime(2015, 3, 1, 12, 34, 57)));
        assertEquals(-1, d1.getSecondsAfter(new Datetime(2015, 3, 1, 12, 34, 58)));

        assertEquals(1, d1.getMinutesAfter(new Datetime(2015, 3, 1, 12, 33)));
        assertEquals(0, d1.getMinutesAfter(new Datetime(2015, 3, 1, 12, 34)));
        assertEquals(0, d1.getMinutesAfter(new Datetime(2015, 3, 1, 12, 35)));
        assertEquals(-1, d1.getMinutesAfter(new Datetime(2015, 3, 1, 12, 35, 56, 789)));

        assertEquals(1, d1.getHoursAfter(new Datetime(2015, 3, 1, 11, 34)));
        assertEquals(0, d1.getHoursAfter(new Datetime(2015, 3, 1, 12, 34)));
        assertEquals(0, d1.getHoursAfter(new Datetime(2015, 3, 1, 13, 34)));
        assertEquals(-1, d1.getHoursAfter(new Datetime(2015, 3, 1, 13, 34, 56, 789)));

        assertEquals(1, d1.getDaysAfter(new Datetime(2015, 2, 28, 12, 34)));
        assertEquals(0, d1.getDaysAfter(new Datetime(2015, 2, 28, 12, 35)));
        assertEquals(-1, d1.getDaysInCalendarAfter(new Datetime(2015, 3, 2, 12, 34)));
        assertEquals(-1, d1.getDaysAfter(new Datetime(2015, 3, 2, 12, 34, 56, 789)));

        assertEquals(1, d1.getWeeksAfter(new Datetime(2015, 2, 22, 12, 34)));
        // 满24h和不满24h的区别
        assertEquals(0, d1.getWeeksAfter(new Datetime(2015, 3, 8, 12, 34)));
        assertEquals(-1, d1.getWeeksInCalendarAfter(new Datetime(2015, 3, 8, 12, 34)));
        assertEquals(-1, d1.getWeeksAfter(new Datetime(2015, 3, 8, 12, 34, 56, 789)));

        assertEquals(0, d1.getMonthsAfter(new Datetime(2015, 2, 1, 12, 35)));
        assertEquals(1, d1.getMonthsAfter(new Datetime(2015, 2, 1, 12, 34)));
        assertEquals(1, d1.getMonthsAfter(new Datetime(2015, 2, 1, 12, 34, 56, 789)));
        assertEquals(0, d1.getMonthsAfter(new Datetime(2015, 2, 1, 12, 34, 56, 790)));
        assertEquals(-1, d1.getMonthsInCalendarAfter(new Datetime(2015, 4, 1, 12, 34)));
        assertEquals(-1, d1.getMonthsAfter(new Datetime(2015, 4, 1, 12, 34, 56, 789)));
        assertEquals(-1, d1.getMonthsAfter(new Datetime(2015, 4, 1, 12, 34, 56, 790)));
        assertEquals(0, d1.getMonthsAfter(new Datetime(2015, 4, 1, 12, 34))); // 不满1天，不算1个月

        // 公元前月数计算
        Datetime d2 = new Datetime(-1, 3, 1, 12, 34, 56, 789);
        assertEquals(0, d2.getMonthsAfter(new Datetime(-1, 2, 1, 12, 35)));
        assertEquals(1, d2.getMonthsAfter(new Datetime(-1, 2, 1, 12, 34)));
        assertEquals(1, d2.getMonthsAfter(new Datetime(-1, 2, 1, 12, 34, 56, 789)));
        assertEquals(0, d2.getMonthsAfter(new Datetime(-1, 2, 1, 12, 34, 56, 790)));
        assertEquals(-1, d2.getMonthsInCalendarAfter(new Datetime(-1, 4, 1, 12, 34)));
        assertEquals(-1, d2.getMonthsAfter(new Datetime(-1, 4, 1, 12, 34, 56, 789)));
        assertEquals(-1, d2.getMonthsAfter(new Datetime(-1, 4, 1, 12, 34, 56, 790)));
        assertEquals(0, d2.getMonthsAfter(new Datetime(-1, 4, 1, 12, 34))); // 不满1天，不算1个月

        assertEquals(1, d1.getYearsInCalendarAfter(new Datetime(2014, 3, 1, 12, 34)));
        assertEquals(0, d1.getYearsAfter(new Datetime(2016, 3, 1, 12, 34)));
        assertEquals(0, d1.getYearsAfter(new Datetime(2016, 3, 1, 12, 34, 56, 788)));
        assertEquals(-1, d1.getYearsAfter(new Datetime(2016, 3, 1, 12, 34, 56, 789)));
        assertEquals(-1, d1.getYearsAfter(new Datetime(2016, 3, 1, 12, 34, 56, 790)));
    }

    /**
     * 计算两时间的时间差，以下所有带InCalendar的方法，计算时间差时将只看日期不看具体的时分秒毫秒，否则必须要精确毫秒满24h才能算是一天
     */
    @Test
    @TestDescr("计算两时间的时间差，String重载")
    public void test004_between_string_overload() {
        Datetime d1 = new Datetime(2015, 3, 1, 12, 34, 56, 789);

        assertEquals(1, d1.getSecondsAfter(new Datetime(2015, 3, 1, 12, 34, 55).toString()));
        assertEquals(0, d1.getSecondsAfter(new Datetime(2015, 3, 1, 12, 34, 57).toString()));
        assertEquals(-1, d1.getSecondsAfter(new Datetime(2015, 3, 1, 12, 34, 58).toString()));

        assertEquals(1, d1.getMinutesAfter(new Datetime(2015, 3, 1, 12, 33).toString()));
        assertEquals(0, d1.getMinutesAfter(new Datetime(2015, 3, 1, 12, 34).toString()));
        assertEquals(0, d1.getMinutesAfter(new Datetime(2015, 3, 1, 12, 35).toString()));
        assertEquals(-1, d1.getMinutesAfter(new Datetime(2015, 3, 1, 12, 35, 56, 789).toString()));

        assertEquals(1, d1.getHoursAfter(new Datetime(2015, 3, 1, 11, 34).toString()));
        assertEquals(0, d1.getHoursAfter(new Datetime(2015, 3, 1, 12, 34).toString()));
        assertEquals(0, d1.getHoursAfter(new Datetime(2015, 3, 1, 13, 34).toString()));
        assertEquals(-1, d1.getHoursAfter(new Datetime(2015, 3, 1, 13, 34, 56, 789).toString()));

        assertEquals(1, d1.getDaysAfter(new Datetime(2015, 2, 28, 12, 34).toString()));
        assertEquals(0, d1.getDaysAfter(new Datetime(2015, 2, 28, 12, 35).toString()));
        assertEquals(-1, d1.getDaysInCalendarAfter(new Datetime(2015, 3, 2, 12, 34).toString()));
        assertEquals(-1, d1.getDaysAfter(new Datetime(2015, 3, 2, 12, 34, 56, 789).toString()));

        assertEquals(1, d1.getWeeksAfter(new Datetime(2015, 2, 22, 12, 34).toString()));
        // 满24h和不满24h的区别
        assertEquals(0, d1.getWeeksAfter(new Datetime(2015, 3, 8, 12, 34).toString()));
        assertEquals(-1, d1.getWeeksInCalendarAfter(new Datetime(2015, 3, 8, 12, 34).toString()));
        assertEquals(-1, d1.getWeeksAfter(new Datetime(2015, 3, 8, 12, 34, 56, 789).toString()));

        assertEquals(0, d1.getMonthsAfter(new Datetime(2015, 2, 1, 12, 35).toString()));
        assertEquals(1, d1.getMonthsAfter(new Datetime(2015, 2, 1, 12, 34).toString()));
        assertEquals(1, d1.getMonthsAfter(new Datetime(2015, 2, 1, 12, 34, 56, 789).toString()));
        assertEquals(0, d1.getMonthsAfter(new Datetime(2015, 2, 1, 12, 34, 56, 790).toString()));
        assertEquals(-1, d1.getMonthsInCalendarAfter(new Datetime(2015, 4, 1, 12, 34).toString()));
        assertEquals(-1, d1.getMonthsAfter(new Datetime(2015, 4, 1, 12, 34, 56, 789).toString()));
        assertEquals(-1, d1.getMonthsAfter(new Datetime(2015, 4, 1, 12, 34, 56, 790).toString()));
        assertEquals(0, d1.getMonthsAfter(new Datetime(2015, 4, 1, 12, 34).toString())); // 不满1天，不算1个月

        // 公元前月数计算
        // XXX 公元前toString不行.格式化出来的字符串体现不出公元前的时间

        // Datetime d2 = new Datetime(-1, 3, 1, 12, 34, 56, 789);
        // assertEquals(0, d2.getMonthsAfter(new Datetime(-1, 2, 1, 12, 35).toString()));
        // assertEquals(1, d2.getMonthsAfter(new Datetime(-1, 2, 1, 12, 34).toString()));
        // assertEquals(1, d2.getMonthsAfter(new Datetime(-1, 2, 1, 12, 34, 56, 789).toString()));
        // assertEquals(0, d2.getMonthsAfter(new Datetime(-1, 2, 1, 12, 34, 56, 790).toString()));
        // assertEquals(-1, d2.getMonthsInCalendarAfter(new Datetime(-1, 4, 1, 12, 34).toString()));
        // assertEquals(-1, d2.getMonthsAfter(new Datetime(-1, 4, 1, 12, 34, 56, 789).toString()));
        // assertEquals(-1, d2.getMonthsAfter(new Datetime(-1, 4, 1, 12, 34, 56, 790).toString()));
        // assertEquals(0, d2.getMonthsAfter(new Datetime(-1, 4, 1, 12, 34).toString())); // 不满1天，不算1个月

        assertEquals(1, d1.getYearsInCalendarAfter(new Datetime(2014, 3, 1, 12, 34).toString()));
        assertEquals(0, d1.getYearsAfter(new Datetime(2016, 3, 1, 12, 34).toString()));
        assertEquals(0, d1.getYearsAfter(new Datetime(2016, 3, 1, 12, 34, 56, 788).toString()));
        assertEquals(-1, d1.getYearsAfter(new Datetime(2016, 3, 1, 12, 34, 56, 789).toString()));
        assertEquals(-1, d1.getYearsAfter(new Datetime(2016, 3, 1, 12, 34, 56, 790).toString()));
    }

    @Test
    @TestDescr("模拟微信聊天界面的时间显示")
    public void test005_im() {
        log.error(getTimeStamp(new Datetime(), null));

        Datetime day = DatetimeUtils.today();
        assertEquals("凌晨00:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 0, 30, 1), null));
        assertEquals("凌晨01:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 1, 30, 1), null));
        assertEquals("凌晨03:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 3, 30, 1), null));
        assertEquals("中午06:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 6, 30, 1), null));
        assertEquals("早上09:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 9, 30, 1), null));
        assertEquals("早上11:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 11, 30, 1), null));
        assertEquals("中午12:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 12, 30, 1), null));
        assertEquals("下午13:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 13, 30, 1), null));
        assertEquals("中午18:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 18, 30, 1), null));
        assertEquals("晚上22:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 22, 30, 1), null));

        day = DatetimeUtils.today().minusDays(1);
        assertEquals("昨天 凌晨00:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 0, 30, 1), null));
        assertEquals("昨天 凌晨01:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 1, 30, 1), null));
        assertEquals("昨天 凌晨03:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 3, 30, 1), null));
        assertEquals("昨天 中午06:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 6, 30, 1), null));
        assertEquals("昨天 早上09:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 9, 30, 1), null));
        assertEquals("昨天 早上11:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 11, 30, 1), null));
        assertEquals("昨天 中午12:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 12, 30, 1), null));
        assertEquals("昨天 下午13:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 13, 30, 1), null));
        assertEquals("昨天 中午18:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 18, 30, 1), null));
        assertEquals("昨天 晚上22:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 22, 30, 1), null));

        day = new Datetime(DatetimeUtils.now().getYear(), 3, 1);
        assertEquals("3月1日 凌晨00:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 0, 30, 1), null));
        assertEquals("3月1日 凌晨01:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 1, 30, 1), null));
        assertEquals("3月1日 凌晨03:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 3, 30, 1), null));
        assertEquals("3月1日 中午06:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 6, 30, 1), null));
        assertEquals("3月1日 早上09:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 9, 30, 1), null));
        assertEquals("3月1日 早上11:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 11, 30, 1), null));
        assertEquals("3月1日 中午12:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 12, 30, 1), null));
        assertEquals("3月1日 下午13:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 13, 30, 1), null));
        assertEquals("3月1日 中午18:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 18, 30, 1), null));
        assertEquals("3月1日 晚上22:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 22, 30, 1), null));

        day = new Datetime(2014, 12, 1);
        assertEquals("2014年12月1日 凌晨00:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 0, 30, 1), null));
        assertEquals("2014年12月1日 凌晨01:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 1, 30, 1), null));
        assertEquals("2014年12月1日 凌晨03:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 3, 30, 1), null));
        assertEquals("2014年12月1日 中午06:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 6, 30, 1), null));
        assertEquals("2014年12月1日 早上09:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 9, 30, 1), null));
        assertEquals("2014年12月1日 早上11:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 11, 30, 1), null));
        assertEquals("2014年12月1日 中午12:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 12, 30, 1), null));
        assertEquals("2014年12月1日 下午13:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 13, 30, 1), null));
        assertEquals("2014年12月1日 中午18:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 18, 30, 1), null));
        assertEquals("2014年12月1日 晚上22:30", getTimeStamp(new Datetime(day.getYear(), day.getMonthOfYear(), day.getDayOfMonth(), 22, 30, 1), null));
    }

    /**
     * 模拟微信聊天界面的时间显示
     *
     * @param thisOne 本条消息的时间
     * @param lastOne 上一条消息的时间，如果没有上条消息请传入null
     */
    public static String getTimeStamp(Datetime thisOne, Datetime lastOne) {
        if (null != lastOne && thisOne.getMinutesAfter(lastOne) < 1) {
            return "";
        }
        String time = thisOne.toString(Datetime.DF_HH_mm);
        String seg = "中午";
        int hour = thisOne.getHourOfDay();
        if (hour >= 0 && hour < 6) {
            seg = "凌晨";
        } else if (hour > 6 && hour < 12) {
            seg = "早上";
        } else if (hour > 12 && hour < 18) {
            seg = "下午";
        } else if (hour > 18) {
            seg = "晚上";
        }
        Datetime today = DatetimeUtils.today();
        Datetime thisOneDay = thisOne.retainDay();
        Datetime thisOneYear = thisOne.retainYear();
        if (thisOneDay.equals(today)) {
            return seg + time;
        } else if (thisOneDay.equals(today.minusDays(1))) {
            return "昨天 " + seg + time;
        } else if (thisOneYear.equals(today.retainYear())) {
            return thisOne.toString("M月d日") + " " + seg + time;
        }
        return thisOne.toString("yyyy年M月d日") + " " + seg + time;
    }
}
