package org.etnaframework.core.util;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.KeyValueGetter.DbMap;
import org.etnaframework.core.web.cmd.HttpCmd;
import org.etnaframework.jdbc.JdbcTemplate;
import org.etnaframework.jedis.JedisTemplate;
import org.springframework.stereotype.Service;
import com.google.common.hash.Hashing;

/**
 * 字符串处理相关工具类
 *
 * @author BlackCat
 * @since 2011-9-2
 */
@Service
public class StringTools {

    /** 定义script的正则表达式{或<script[^>]*?>[\\s\\S]*?<\\/script> */
    public static final Pattern HTML_SCRIPT = Pattern.compile("<[\\s]*?script[^>]*?>[\\s\\S]*?<[\\s]*?\\/[\\s]*?script[\\s]*?>", Pattern.CASE_INSENSITIVE);

    /** 定义style的正则表达式{或<style[^>]*?>[\\s\\S]*?<\\/style> */
    public static final Pattern HTML_STYLE = Pattern.compile("<[\\s]*?style[^>]*?>[\\s\\S]*?<[\\s]*?\\/[\\s]*?style[\\s]*?>", Pattern.CASE_INSENSITIVE);

    /** 定义HTML标签的正则表达式 */
    public static final Pattern HTML_TAG = Pattern.compile("<[^>]+>", Pattern.CASE_INSENSITIVE);

    /** 定义一些特殊字符的正则表达式 如：&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; */
    public static final Pattern HTML_SPECIAL = Pattern.compile("\\&[a-zA-Z]{1,10};", Pattern.CASE_INSENSITIVE);

    /** 异常类名的分隔符 */
    public static final String EXCEPTION_SPLIT = "@";

    private static final int caseDiff = ('a' - 'A');

    private static final char[] DIGITS_LOWER = {
        '0',
        '1',
        '2',
        '3',
        '4',
        '5',
        '6',
        '7',
        '8',
        '9',
        'a',
        'b',
        'c',
        'd',
        'e',
        'f'
    };

    /**
     * 基本型的转型方法列表
     */
    private static final Map<Type, Method> valueOfMethod = initValueOfMethod();

    private static final IllegalArgumentException classNotBasicTypeException = new IllegalArgumentException("classNotBasicType");

    private static final String[] HTML_ESCAPE_LIST;

    private static final String[] XML_ESCAPE_LIST;

    private static final String[] QUOTED_STRING_ESCAPE_LIST;

    private static final String[] WHITESPACE_ESCAPE_LIST;

    private static final String[] SHELL_ESCAPE_LIST;

    private static final String[] SQL_LIKE_PATTERN;

    private static final String[] MARKDOWN_ESCAPE_PATTERN;

    /**
     * 需要进行过滤并替换的sql字符
     */
    private static final String[][] sqlHandles = {
        {
            "'",
            "''"
        },
        {
            "\\\\",
            "\\\\\\\\"
        }
    };

    private static BitSet dontNeedEncoding;

    private static String ZEROS[] = {
        "",
        "0",
        "00",
        "000",
        "0000",
        "00000",
        "000000",
        "0000000",
        "00000000",
        "000000000",
        "0000000000",
        "00000000000",
        "000000000000",
        "0000000000000",
        "00000000000000",
        "000000000000000",
        "0000000000000000",
        "00000000000000000",
        "000000000000000000",
        "0000000000000000000",
        "00000000000000000000",
        };

    private static Method getOurStackTrace;

    static {
        dontNeedEncoding = new BitSet(256);
        int i;
        for (i = 'a'; i <= 'z'; i++) {
            dontNeedEncoding.set(i);
        }
        for (i = 'A'; i <= 'Z'; i++) {
            dontNeedEncoding.set(i);
        }
        for (i = '0'; i <= '9'; i++) {
            dontNeedEncoding.set(i);
        }
        dontNeedEncoding.set('-');
        dontNeedEncoding.set('_');
        dontNeedEncoding.set('.');
        dontNeedEncoding.set('!');
        dontNeedEncoding.set('~');
        dontNeedEncoding.set('*');
        dontNeedEncoding.set('\'');
        dontNeedEncoding.set('(');
        dontNeedEncoding.set(')');
    }

    static {
        HTML_ESCAPE_LIST = _buildEscapeArray();
        HTML_ESCAPE_LIST['<'] = "&lt;";
        HTML_ESCAPE_LIST['>'] = "&gt;";
        HTML_ESCAPE_LIST['&'] = "&amp;";
        HTML_ESCAPE_LIST['\"'] = "&quot;";
        HTML_ESCAPE_LIST['\n'] = "<br/>";
        HTML_ESCAPE_LIST['\r'] = "";
        // 2015-08-09 经测试发现，中文系统下的chrome下&nbsp;会被当作全角空格来处理，改为半角空格&ensp (0x2002)
        HTML_ESCAPE_LIST['\t'] = "&ensp;&ensp;&ensp;&ensp;";
        HTML_ESCAPE_LIST[' '] = "&ensp;";
        // 空格的ascii码值有两个：从键盘输入的空格ascii值为0x20；从网页上的&nbsp;字符表单提交而来的空格ascii值为0xa0
        HTML_ESCAPE_LIST['\u00a0'] = "&ensp;";
        HTML_ESCAPE_LIST['\u2002'] = "&ensp;";
    }

    static {
        XML_ESCAPE_LIST = _buildEscapeArray();
        XML_ESCAPE_LIST['<'] = "&lt;";
        XML_ESCAPE_LIST['>'] = "&gt;";
        XML_ESCAPE_LIST['\"'] = "&quot;";
        XML_ESCAPE_LIST['&'] = "&amp;";
        XML_ESCAPE_LIST['\''] = "&apos;";
    }

    static {
        QUOTED_STRING_ESCAPE_LIST = _buildEscapeArray();
        QUOTED_STRING_ESCAPE_LIST['\\'] = "\\\\";
        QUOTED_STRING_ESCAPE_LIST['\"'] = "\\\"";
        QUOTED_STRING_ESCAPE_LIST['\''] = "\\\'";
        QUOTED_STRING_ESCAPE_LIST['\r'] = "\\r";
        QUOTED_STRING_ESCAPE_LIST['\n'] = "\\n";
        QUOTED_STRING_ESCAPE_LIST['\f'] = "\\f";
        QUOTED_STRING_ESCAPE_LIST['\t'] = "\\t";
        QUOTED_STRING_ESCAPE_LIST['\b'] = "\\b";
        QUOTED_STRING_ESCAPE_LIST['\u00a0'] = " ";
    }

    static {
        WHITESPACE_ESCAPE_LIST = _buildEscapeArray();
        WHITESPACE_ESCAPE_LIST['\r'] = "\\r";
        WHITESPACE_ESCAPE_LIST['\n'] = "\\n";
        WHITESPACE_ESCAPE_LIST['\f'] = "\\f";
        WHITESPACE_ESCAPE_LIST['\t'] = "\\t";
        WHITESPACE_ESCAPE_LIST['\b'] = "\\b";
        WHITESPACE_ESCAPE_LIST['\u00a0'] = " ";
    }

    static {
        SHELL_ESCAPE_LIST = _buildEscapeArray();
        SHELL_ESCAPE_LIST['\\'] = "\\\\";
        SHELL_ESCAPE_LIST[' '] = "\\ ";
        SHELL_ESCAPE_LIST['"'] = "\\\"";
        SHELL_ESCAPE_LIST['\''] = "\\\'";
        SHELL_ESCAPE_LIST['&'] = "\\&";
        SHELL_ESCAPE_LIST[';'] = "\\;";
    }

    static {
        SQL_LIKE_PATTERN = _buildEscapeArray();
        SQL_LIKE_PATTERN['%'] = "\\%";
        SQL_LIKE_PATTERN['_'] = "\\_";
    }

    static {
        MARKDOWN_ESCAPE_PATTERN = _buildEscapeArray();
        MARKDOWN_ESCAPE_PATTERN['\n'] = "  \n";
        MARKDOWN_ESCAPE_PATTERN['\\'] = "\\\\";
        MARKDOWN_ESCAPE_PATTERN['`'] = "\\`";
        MARKDOWN_ESCAPE_PATTERN['*'] = "\\*";
        MARKDOWN_ESCAPE_PATTERN['_'] = "\\_";
        MARKDOWN_ESCAPE_PATTERN['{'] = "\\{";
        MARKDOWN_ESCAPE_PATTERN['}'] = "\\}";
        MARKDOWN_ESCAPE_PATTERN['['] = "\\[";
        MARKDOWN_ESCAPE_PATTERN[']'] = "\\]";
        MARKDOWN_ESCAPE_PATTERN['('] = "\\(";
        MARKDOWN_ESCAPE_PATTERN[')'] = "\\)";
        MARKDOWN_ESCAPE_PATTERN['#'] = "\\#";
        MARKDOWN_ESCAPE_PATTERN['+'] = "\\+";
        MARKDOWN_ESCAPE_PATTERN['-'] = "\\-";
        MARKDOWN_ESCAPE_PATTERN['.'] = "\\.";
        MARKDOWN_ESCAPE_PATTERN['!'] = "\\!";
        MARKDOWN_ESCAPE_PATTERN['<'] = "&lt;";
        MARKDOWN_ESCAPE_PATTERN['>'] = "&gt;";
        MARKDOWN_ESCAPE_PATTERN['&'] = "&amp;";
    }

    static {
        try {
            getOurStackTrace = Throwable.class.getDeclaredMethod("getOurStackTrace");
            getOurStackTrace.setAccessible(true);
        } catch (Exception e) {
        }
    }

    private StringTools() {
    }

    /**
     * 判断字符串是否为空
     */
    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

    /**
     * 判断传入的一系列字符串中是否有空字符串，任意一个为空都会返回true，都不为空返回false
     */
    public static boolean anyEmpty(CharSequence... strs) {
        for (CharSequence str : strs) {
            if (str == null || str.length() == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断字符串是否不为空
     */
    public static boolean isNotEmpty(CharSequence str) {
        return !isEmpty(str);
    }

    /**
     * 字符串分割并去除首尾空格
     */
    public static List<String> splitAndTrim(String str, String regex) {
        String[] array = str.split(regex);
        List<String> list = new ArrayList<>(array.length);
        for (String a : array) {
            String add = a.trim();
            if (!add.isEmpty()) {
                list.add(add);
            }
        }
        return list;
    }

    /**
     * 字符串split并trim去除空字符串
     */
    public static Collection<String> splitAndTrim(String str, String regex, Collection<String> result) {
        String[] arr = str.split(regex);
        for (String a : arr) {
            String add = a.trim();
            if (add.length() > 0) {
                result.add(add);
            }
        }
        return result;
    }

    public static Collection<Integer> splitAndTrimInteger(String str, String regex, Collection<Integer> list) {
        String[] arr = str.split(regex);
        for (String a : arr) {
            String add = a.trim();
            if (add.length() > 0) {
                try {
                    list.add(Integer.valueOf(add));
                } catch (Exception e) {
                }
            }
        }
        return list;
    }

    public static List<Integer> splitAndTrimInteger(String str, String regex) {
        if (isEmpty(str)) {
            return new ArrayList<>();
        } else {
            List<Integer> list = new ArrayList<>();
            splitAndTrimInteger(str, regex, list);
            return list;
        }
    }

    public static Collection<Long> splitAndTrimLong(String str, String regex, Collection<Long> list) {
        String[] arr = str.split(regex);
        for (String a : arr) {
            String add = a.trim();
            if (add.length() > 0) {
                try {
                    list.add(Long.valueOf(add));
                } catch (Exception e) {
                }
            }
        }
        return list;
    }

    public static List<Long> splitAndTrimLong(String str, String regex) {
        if (isEmpty(str)) {
            return new ArrayList<>();
        } else {
            List<Long> list = new ArrayList<>();
            splitAndTrimLong(str, regex, list);
            return list;
        }
    }

    public static Collection<Boolean> splitAndTrimBoolean(String str, String regex, Collection<Boolean> list) {
        String[] arr = str.split(regex);
        for (String a : arr) {
            String add = a.trim();
            if (add.length() > 0) {
                if (add.equalsIgnoreCase("false") || add.equals("0")) {
                    list.add(Boolean.FALSE);
                } else if (add.equalsIgnoreCase("true") || add.equals("1")) {
                    list.add(Boolean.TRUE);
                }
            }
        }
        return list;
    }

    public static List<Boolean> splitAndTrimBoolean(String str, String regex) {
        if (isEmpty(str)) {
            return new ArrayList<>();
        } else {
            List<Boolean> list = new ArrayList<>();
            splitAndTrimBoolean(str, regex, list);
            return list;
        }
    }

    /**
     * 对URL进行编码，抄自java.net.URLEncoder，其不同之处在于空格不会转为+而是转为%20， 与前端js的encodeURIComponent效果保持一致
     */
    public static String encodeURIComponent(String s, Charset charset) {
        boolean needToChange = false;
        StringBuilder out = new StringBuilder(s.length());
        CharArrayWriter charArrayWriter = new CharArrayWriter();
        for (int i = 0; i < s.length(); ) {
            int c = s.charAt(i);
            if (dontNeedEncoding.get(c)) {
                out.append((char) c);
                i++;
            } else {
                do {
                    charArrayWriter.write(c);
                    if (c >= 0xD800 && c <= 0xDBFF) {
                        if ((i + 1) < s.length()) {
                            int d = s.charAt(i + 1);
                            if (d >= 0xDC00 && d <= 0xDFFF) {
                                charArrayWriter.write(d);
                                i++;
                            }
                        }
                    }
                    i++;
                } while (i < s.length() && !dontNeedEncoding.get((c = s.charAt(i))));
                charArrayWriter.flush();
                String str = new String(charArrayWriter.toCharArray());
                byte[] ba = str.getBytes(charset);
                for (int j = 0; j < ba.length; j++) {
                    out.append('%');
                    char ch = Character.forDigit((ba[j] >> 4) & 0xF, 16);
                    // converting to use uppercase letter as part of
                    // the hex value if ch is a letter.
                    if (Character.isLetter(ch)) {
                        ch -= caseDiff;
                    }
                    out.append(ch);
                    ch = Character.forDigit(ba[j] & 0xF, 16);
                    if (Character.isLetter(ch)) {
                        ch -= caseDiff;
                    }
                    out.append(ch);
                }
                charArrayWriter.reset();
                needToChange = true;
            }
        }
        return (needToChange ? out.toString() : s);
    }

    /**
     * 对URL进行编码，抄自java.net.URLEncoder，其不同之处在于空格不会转为+而是转为%20， 与前端js的encodeURIComponent效果保持一致，使用UTF-8编码
     */
    public static String encodeURIComponent(String s) {
        return encodeURIComponent(s, CharsetEnum.UTF_8);
    }

    /**
     * 对URL进行解码，抄自java.net.URLDecoder，其不同之处在于+不会转为空格而是处理为%2B， 与前端js的decodeURIComponent效果保持一致
     *
     * 注意这里对原版进行了改动，解析有问题的部分，直接原样输出而不是抛出异常，尽最大可能解码
     */
    public static String decodeURIComponent(String s, Charset charset) {
        boolean needToChange = false;
        int numChars = s.length();
        StringBuilder sb = new StringBuilder(numChars > 500 ? numChars / 2 : numChars);
        int i = 0;
        char c;
        byte[] bytes = null;
        while (i < numChars) {
            c = s.charAt(i);
            int pos = 0;
            int i1 = i + 1;
            switch (c) {
            case '%':
                /*
                 * Starting with this instance of %, process all consecutive substrings of the form %xy. Each substring %xy will yield a byte. Convert all consecutive bytes obtained this way to
                 * whatever character(s) they represent in the provided encoding.
                 */
                try {
                    // (numChars-i)/3 is an upper bound for the number
                    // of remaining bytes
                    if (bytes == null) {
                        bytes = new byte[(numChars - i) / 3];
                    }
                    pos = 0;
                    while (((i + 2) < numChars) && (c == '%')) {
                        bytes[pos] = (byte) Integer.parseInt(s.substring(i + 1, i + 3), 16);
                        pos++;
                        i += 3;
                        if (i < numChars) {
                            c = s.charAt(i);
                        }
                    }
                    // A trailing, incomplete byte encoding such as
                    // "%x" will cause an exception to be thrown
                    if ((i < numChars) && (c == '%')) {
                        // throw new IllegalArgumentException("decodeURIComponent: Incomplete trailing escape (%) pattern");
                        // 2018-06-27 原版的java代码这里会报错，改为如果无法解析的部分原样返回
                        sb.append(c);
                        i = i1;
                    } else {
                        sb.append(new String(bytes, 0, pos, charset));
                    }
                } catch (NumberFormatException e) {
                    // throw new IllegalArgumentException("decodeURIComponent: Illegal hex characters in escape (%) pattern - " + e.getMessage());
                    // 2018-06-27 原版的java代码这里会报错，改为如果无法解析，就原样返回
                    sb.append(new String(bytes, 0, pos, charset)); // 此为解析成功的部分
                    sb.append(s, i, i + 3); // 此为解析失败的部分
                    i += 3;
                }
                needToChange = true;
                break;
            default:
                sb.append(c);
                i++;
                break;
            }
        }
        return (needToChange ? sb.toString() : s);
    }

    /**
     * 对URL进行解码，抄自java.net.URLDecoder，其不同之处在于+不会转为空格而是处理为%2B， 与前端js的decodeURIComponent效果保持一致，使用UTF-8解码
     */
    public static String decodeURIComponent(String s) {
        return decodeURIComponent(s, CharsetEnum.UTF_8);
    }

    /**
     * 生成格式化后的字符串，中文内容（仅限GB18030字符集中有的）一个字按2个ascii字符的宽度处理（jdk自带的String.format做不到这一点），如果包括异常参数，自动将其内容转化后输出
     */
    public static String format(String format, Object... args) {
        if (null == args) {
            return format;
        }
        String fmt = new String(format.getBytes(CharsetEnum.GB18030), CharsetEnum.ISO_8859_1);
        Object[] objs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Throwable) {
                objs[i] = printThrowable((Throwable) args[i]);
            } else if (args[i] instanceof String) {
                objs[i] = new String(((String) args[i]).getBytes(CharsetEnum.GB18030), CharsetEnum.ISO_8859_1);
            } else {
                objs[i] = args[i];
            }
        }
        return new String(String.format(fmt, objs)
                                .getBytes(CharsetEnum.ISO_8859_1), CharsetEnum.GB18030);
    }

    /**
     * 合并带?的SQL语句，用于调试时显示执行的SQL之用
     */
    public static String mergeSql(String sql, Object... args) {
        if (null == args) {
            return sql;
        }
        String[] sqls = (sql + " ").split("\\?");
        if (args.length == sqls.length - 1) {
            StringBuilder exsql = new StringBuilder();
            int i;
            for (i = 0; i < args.length; i++) {
                exsql.append(sqls[i]);
                if (args[i] instanceof String || args[i] instanceof Enum) {
                    // 参数有限制，防止显示在日志中的SQL出现换行或者太长的情况
                    String src = args[i].toString()
                                        .replace("'", "\\'")
                                        .replace("\r", "")
                                        .replace("\n", "")
                                        .trim();
                    String append = src;
                    if (src.length() > 120) {
                        append = src.substring(0, 50) + "...(" + args[i].toString()
                                                                        .length() + ")..." + src.substring(src.length() - 50, src.length());
                    }
                    exsql.append("'")
                         .append(append)
                         .append("'");
                } else if (args[i] instanceof Date) { // Date统一转为Datetime处理
                    exsql.append("'")
                         .append(new Datetime(((Date) args[i])).toString())
                         .append("'");
                } else if (args[i] instanceof byte[]) { // 二进制内容不显示，只显示长度信息
                    exsql.append("[Binary Content ")
                         .append(((byte[]) args[i]).length)
                         .append("]");
                } else {
                    exsql.append(args[i]);
                }
            }
            exsql.append(sqls[i]);
            exsql.deleteCharAt(exsql.length() - 1);
            return exsql.toString();
        }
        return "";
    }

    /**
     * 如果传入的value为null，则返回defaultValue，否则就返回value
     */
    public static String getString(Object value, String defaultValue) {
        return null == value ? defaultValue : value.toString();
    }

    /**
     * 转换为Boolean
     *
     * @return 字符串为true/y/1/是 时都返回true
     */
    public static Boolean getBool(Object value, Boolean defaultValue) {
        if (null == value) {
            return defaultValue;
        }
        String val = value.toString();
        return val.equals("true") || val.equalsIgnoreCase("y") || val.equals("1") || val.equals("是");
    }

    /**
     * 转换为Byte
     */
    public static Byte getByte(Object value, Byte defaultValue) {
        if (null == value) {
            return defaultValue;
        }
        try {
            return Byte.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 转换为Character，默认取字符串的第1个字符
     */
    public static Character getChar(Object value, Character defaultValue) {
        if (null == value) {
            return defaultValue;
        }
        String val = value.toString();
        if (val.isEmpty()) {
            return defaultValue;
        }
        return val.charAt(0);
    }

    /**
     * 转换为Short，注意不能处理小数，会导致返回defaultValue
     */
    public static Short getShort(Object value, Short defaultValue) {
        if (null == value) {
            return defaultValue;
        }
        try {
            return Short.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 转换为Integer，注意不能处理小数，会导致返回defaultValue
     */
    public static Integer getInt(Object value, Integer defaultValue) {
        if (null == value) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 转换为Long，注意不能处理小数，会导致返回defaultValue
     */
    public static Long getLong(Object value, Long defaultValue) {
        if (null == value) {
            return defaultValue;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 转换为Float
     */
    public static Float getFloat(Object value, Float defaultValue) {
        if (null == value) {
            return defaultValue;
        }
        try {
            return Float.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 字符串转换为Double
     */
    public static Double getDouble(Object value, Double defaultValue) {
        if (null == value) {
            return defaultValue;
        }
        try {
            return Double.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 转换成Character型，直接取第一个字符，如果是空字符串会返回null
     */
    public static Character getChar(Object value) {
        if (null == value) {
            return null;
        }
        String val = value.toString();
        return val.isEmpty() ? null : val.charAt(0);
    }

    /**
     * 转换成枚举类型，如果转换失败返回defaultValue
     */
    public static <T extends Enum> T getEnum(Class<T> enumClass, String value, T defaultValue) {
        try {
            return (T) Enum.valueOf(enumClass.asSubclass(Enum.class), value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 获得基本类型-基本类型转到String类型的方法的 映射列表
     */
    private static Map<Type, Method> initValueOfMethod() {
        Map<Type, Method> valueOfMethods = new HashMap<>();
        try {
            valueOfMethods.put(int.class, Integer.class.getMethod("valueOf", String.class));
            valueOfMethods.put(Integer.class, Integer.class.getMethod("valueOf", String.class));
            valueOfMethods.put(long.class, Long.class.getMethod("valueOf", String.class));
            valueOfMethods.put(Long.class, Long.class.getMethod("valueOf", String.class));
            valueOfMethods.put(float.class, Float.class.getMethod("valueOf", String.class));
            valueOfMethods.put(Float.class, Float.class.getMethod("valueOf", String.class));
            valueOfMethods.put(double.class, Double.class.getMethod("valueOf", String.class));
            valueOfMethods.put(Double.class, Double.class.getMethod("valueOf", String.class));
            valueOfMethods.put(short.class, Short.class.getMethod("valueOf", String.class));
            valueOfMethods.put(Short.class, Short.class.getMethod("valueOf", String.class));
            valueOfMethods.put(boolean.class, Boolean.class.getMethod("valueOf", String.class));
            valueOfMethods.put(Boolean.class, Boolean.class.getMethod("valueOf", String.class));
            valueOfMethods.put(byte.class, Byte.class.getMethod("valueOf", String.class));
            valueOfMethods.put(Byte.class, Byte.class.getMethod("valueOf", String.class));
            valueOfMethods.put(String.class, String.class.getMethod("valueOf", Object.class));
            valueOfMethods.put(Datetime.class, Datetime.class.getMethod("valueOf", String.class));
        } catch (Exception e) {
            throw new RuntimeException(e); // 这里有问题一定要抛出来
        }
        return valueOfMethods;
    }

    /**
     * 获取由String类型转到基本类型的方法
     */
    private static Method getValueOfMethod(Type clazz) {
        Method m = valueOfMethod.get(clazz);
        if (m == null) {
            throw classNotBasicTypeException;
        }
        return m;
    }

    /**
     * 检测是否为基本类型，如果是，可以用valueOf
     */
    public static boolean isBasicType(Type clazz) {
        Method m = valueOfMethod.get(clazz);
        return m != null;
    }

    /**
     * 将指定类型转化成目标类型
     *
     * @param <T> 泛型
     * @param value 待转换的对象
     * @param destClazz 目标类类型
     */
    @SuppressWarnings("unchecked")
    public static <T> T valueOf(String value, Type destClazz) throws Exception {
        if (char.class.equals(destClazz) || Character.class.equals(destClazz)) {
            return (T) getChar(value);
        }
        Method method = getValueOfMethod(destClazz);
        return (T) method.invoke(null, value != null ? value.trim() : value);
    }

    @SuppressWarnings("unchecked")
    public static <T> T valueOf(String value, T defaultValue, Class<T> destClazz) throws Exception {
        if (isEmpty(value)) {
            return defaultValue;
        }
        try {
            if (char.class.equals(destClazz) || Character.class.equals(destClazz)) {
                return (T) getChar(value);
            }
            Method method = getValueOfMethod(destClazz);
            return (T) method.invoke(null, value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 计算输入字符串的md5值（返回长度为32的小写字符串）
     */
    public static String md5AsHex(String str, Charset charset) {
        return Hashing.md5()
                      .hashString(str, charset)
                      .toString();
    }

    /**
     * 计算输入字符串的md5值（返回长度为32的小写字符串）
     */
    public static String md5AsHex(String str) {
        return Hashing.md5()
                      .hashString(str, CharsetEnum.UTF_8)
                      .toString();
    }

    /**
     * 计算输入字符串的sha1值（返回长度为40的小写字符串）
     */
    public static String sha1AsHex(String str, Charset charset) {
        return Hashing.sha1()
                      .hashString(str, charset)
                      .toString();
    }

    /**
     * 计算输入字符串的sha1值（返回长度为40的小写字符串）
     */
    public static String sha1AsHex(String str) {
        return Hashing.sha1()
                      .hashString(str, CharsetEnum.UTF_8)
                      .toString();
    }

    /**
     * 按照首字母大写规则进行分词，如HelloWorld，就会被分为Hello和World两个词，REST就会被分为R E S T四个词
     */
    public static List<String> splitByUppercaseLetter(String str) {
        List<String> split = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (Character.isUpperCase(ch) && sb.length() > 0) {
                split.add(sb.toString());
                sb = new StringBuilder();
            }
            sb.append(ch);
        }
        if (sb.length() > 0) {
            split.add(sb.toString());
        }
        return split;
    }

    /**
     * 将传入字符串的首字母小写，如果第2个字母也是大写首字母就不转为小写了（跟Spring的beanName规则保持一致，这也是java的get/set方法名生成规则）
     */
    public static String headLetterToLowerCase(String str) {
        if (isNotEmpty(str)) {
            if (str.length() == 1) {
                return str.toLowerCase(Locale.getDefault());
            } else if (str.charAt(1) >= 'A' && str.charAt(1) <= 'Z') {
                return str;
            }
            return Character.toLowerCase(str.charAt(0)) + str.substring(1);
        }
        return "";
    }

    /**
     * 将传入字符串的首字母大写，如果第2个字母也是大写首字母就不转为大写了（跟Spring的beanName规则保持一致，这也是java的get/set方法名生成规则）
     */
    public static String headLetterToUpperCase(String str) {
        if (isNotEmpty(str)) {
            if (str.length() == 1) {
                return str.toUpperCase(Locale.getDefault());
            } else if (str.charAt(1) >= 'A' && str.charAt(1) <= 'Z') {
                return str;
            }
            return Character.toUpperCase(str.charAt(0)) + str.substring(1);
        }
        return "";
    }

    /**
     * 统计一个字符在另一个字符串里面出现的次数
     */
    public static int count(String src, char find) {
        int c = 0;
        for (int i = 0; i < src.length(); i++) {
            if (src.charAt(i) == find) {
                c++;
            }
        }
        return c;
    }

    /**
     * 统计一个字符在另一个字符串里面出现的次数
     */
    public static int count(char[] src, char find) {
        int c = 0;
        for (int i = 0; i < src.length; i++) {
            if (src[i] == find) {
                c++;
            }
        }
        return c;
    }

    /**
     * 处理 /test///// 这样在后面加了/的情况，删除多余的/，这个例子会返回 /test，如果只传入/就返回/，传入//还是返回/
     */
    public static String removePathSlash(String pathInfo) {
        if (pathInfo.endsWith("/")) {
            int i = pathInfo.length() - 1;
            while (i > 0 && pathInfo.charAt(i) == '/') {
                i--;
            }
            return pathInfo.substring(0, i + 1);
        }
        return pathInfo;
    }

    /**
     * 删除html标签以及&转义字符，如果传入空，返回空字符串
     */
    public static String removeHtmlTag(String inputString) {
        if (isEmpty(inputString)) {
            return "";
        }
        String htmlStr = inputString; // 含html标签的字符串
        String textStr = "";
        try {
            Matcher script = HTML_SCRIPT.matcher(htmlStr);
            htmlStr = script.replaceAll(""); // 过滤script标签
            Matcher style = HTML_STYLE.matcher(htmlStr);
            htmlStr = style.replaceAll(""); // 过滤style标签
            Matcher tag = HTML_TAG.matcher(htmlStr);
            htmlStr = tag.replaceAll(""); // 过滤html标签
            Matcher special = HTML_SPECIAL.matcher(htmlStr);
            htmlStr = special.replaceAll(""); // 过滤特殊标签
            textStr = htmlStr;
        } catch (Exception ignore) {
        }
        return textStr;
    }

    /**
     * 抄自String.trim加上对全角空格的处理
     */
    public static String trim(String str) {
        int len = str.length();
        int count = str.length();
        int st = 0;
        while ((st < len) && ((str.charAt(st) <= ' ') || str.charAt(st) == '　')) {
            st++;
        }
        while ((st < len) && ((str.charAt(len - 1) <= ' ') || str.charAt(len - 1) == '　')) {
            len--;
        }
        return ((st > 0) || (len < count)) ? str.substring(st, len) : str;
    }

    /**
     * 删除空行
     */
    public static String removeEmptyLines(String str) {
        if (str == null) {
            return null;
        } else {
            return str.replaceAll("(?m)^[ \t]*\r?\n", "");
        }
    }

    /**
     * 生成预备转义的字符串数组列表
     */
    private static String[] _buildEscapeArray() {
        String[] ret = new String['\u2002' + 1]; // 最大的需要转义的就是\\u2002 （WEB提交的空格）
        for (int i = 0; i < ret.length; i++) {
            ret[i] = "" + (char) i;
        }
        return ret;
    }

    /**
     * 处理字符串的html转义字符
     */
    public static String escapeHtml(String source) {
        if (isNotEmpty(source)) {
            StringBuilder sb = new StringBuilder(source.length() + 16);
            for (int i = 0; i < source.length(); i++) {
                char ch = source.charAt(i);
                if (ch < HTML_ESCAPE_LIST.length) {
                    String append = HTML_ESCAPE_LIST[ch];
                    sb.append(append);
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }
        return "";
    }

    /**
     * 处理字符串的xml转义字符
     */
    public static String escapeXml(String source) {
        if (isNotEmpty(source)) {
            StringBuilder sb = new StringBuilder(source.length() + 16);
            for (int i = 0; i < source.length(); i++) {
                char ch = source.charAt(i);
                if (ch < XML_ESCAPE_LIST.length) {
                    String append = XML_ESCAPE_LIST[ch];
                    sb.append(append);
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }
        return "";
    }

    /**
     * 转义为""中可用的字符串，诸如\n \t等将会被转义
     */
    public static String escapeQuotedString(String source) {
        if (isNotEmpty(source)) {
            StringBuilder sb = new StringBuilder(source.length() + 16);
            for (int i = 0; i < source.length(); i++) {
                char ch = source.charAt(i);
                if (ch < QUOTED_STRING_ESCAPE_LIST.length) {
                    String append = QUOTED_STRING_ESCAPE_LIST[ch];
                    sb.append(null != append ? append : ch);
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }
        return "";
    }

    /**
     * 转义字符串中的空白字符，包括\r \n \t等
     */
    public static String escapeWhitespace(String source) {
        if (isNotEmpty(source)) {
            StringBuilder sb = new StringBuilder(source.length() + 16);
            for (int i = 0; i < source.length(); i++) {
                char ch = source.charAt(i);
                if (ch < WHITESPACE_ESCAPE_LIST.length) {
                    String append = WHITESPACE_ESCAPE_LIST[ch];
                    sb.append(null != append ? append : ch);
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }
        return "";
    }

    /**
     * 转义字符串中的空白字符，包括\r \n \t等，并将字符串简化处理，即当处理后的字符串大于指定长度时，处理为 头部...(123)...尾部 这样的字符串
     *
     * @param limit 字符串最大长度，要求>10，否则会报错
     */
    public static String escapeWhitespaceAndTruncate(String source, int limit) {
        if (limit <= 10) {
            throw new IllegalArgumentException("limit must > 10");
        }
        String str = escapeWhitespace(source);
        if (str.length() <= limit) {
            return str;
        }
        // 构造长度为limit的字符串，...()...要占8个字符，先减之
        // 假定前后各保留x字符，中间数字为y，可列出下列方程式
        // 2x+len(y)+8=limit
        // 2x+y=str.length()
        // y的值经过数次尝试即可算出
        int lenY = 1;
        int x1, x2;
        while (true) {
            int Xx2 = limit - lenY - 8;
            int Y = str.length() - Xx2;
            if (String.valueOf(Y)
                      .length() == lenY) {
                if (Xx2 % 2 == 1) {
                    x2 = Xx2 / 2;
                    x1 = Xx2 - x2;
                } else {
                    x1 = x2 = Xx2 / 2;
                }
                return str.substring(0, x1) + "...(" + Y + ")..." + str.substring(str.length() - x2, str.length());
            }
            lenY++;
        }
    }

    /**
     * 转义SHELL中输入需要转义的字符，如" 空格 等
     */
    public static String escapeShellString(String source) {
        if (isNotEmpty(source)) {
            StringBuilder sb = new StringBuilder(source.length() + 16);
            for (int i = 0; i < source.length(); i++) {
                char ch = source.charAt(i);
                if (ch < SHELL_ESCAPE_LIST.length) {
                    String append = SHELL_ESCAPE_LIST[ch];
                    sb.append(null != append ? append : ch);
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }
        return "";
    }

    /**
     * <pre>
     * 转义sql的like语句部分，防注入
     *
     * 如果想查询 john开头的name，sql写法为 where name like 'john%'
     * 使用{@link PreparedStatement}来写，就是写成 where name like ?
     * 然后?中传参john%
     *
     * 如果不是john而是包含%或_的字符串，就必须要进行转义，否则有被注入的风险
     * 本方法就用于生成?的那部分内容，写法为StringTools.escapeSqlLikePattern("john")+"%"
     * </pre>
     */
    public static String escapeSqlLikePattern(String keyword) {
        if (isEmpty(keyword)) {
            return "";
        }
        StringBuilder sb = new StringBuilder(keyword.length());
        for (int i = 0; i < keyword.length(); i++) {
            char ch = keyword.charAt(i);
            if (ch < SQL_LIKE_PATTERN.length) {
                String append = SQL_LIKE_PATTERN[ch];
                sb.append(null != append ? append : ch);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * 处理字符串的markdown转义字符
     */
    public static String escapeMarkdown(String source) {
        if (isNotEmpty(source)) {
            StringBuilder sb = new StringBuilder(source.length() + 16);
            for (int i = 0; i < source.length(); i++) {
                char ch = source.charAt(i);
                if (ch < MARKDOWN_ESCAPE_PATTERN.length) {
                    String append = MARKDOWN_ESCAPE_PATTERN[ch];
                    sb.append(append);
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }
        return "";
    }

    /**
     * 在一个已有的URL上添加参数，如果参数已经存在会被覆盖
     */
    public static String addParamsToUrl(String url, Object... keyValues) {
        return addParamsToUrl(url, new DbMap(keyValues));
    }

    /**
     * 在一个已有的URL上添加参数，如果参数已经存在会被覆盖
     */
    public static String addParamsToUrl(String url, Map<String, Object> map) {
        String urlHref = url;
        String anchor = "";

        // 如果有锚点,先取出,后补.
        int anchorPos = url.indexOf("#");
        if (anchorPos > 0) {
            urlHref = url.substring(0, anchorPos);
            anchor = url.substring(anchorPos);
        }

        // 将URL上现有的参数提取出来，默认用UTF8解码
        while (urlHref.length() > 0 && urlHref.charAt(urlHref.length() - 1) == '?') {
            // ?在最后是不符合URL规范的，强制清除纠正过来
            urlHref = urlHref.substring(0, urlHref.length() - 1);
        }
        Map<String, Object> data = new LinkedHashMap<>(); // 注意为了保证顺序，这里务必要用LinkedHashMap

        // 提取现有URL上的参数，由于上一步已经处理在结尾的情况，此时如果有?则一定在中间
        int qmark = urlHref.indexOf('?');
        if (qmark > 0) {
            String queryString = urlHref.substring(qmark + 1);
            urlHref = urlHref.substring(0, qmark);
            for (String kv : splitAndTrim(queryString, "&")) {
                List<String> kvList = splitAndTrim(kv, "=");
                if (kvList.size() == 2) {
                    data.put(kvList.get(0), decodeURIComponent(kvList.get(1)));
                }
            }
        }

        // 合并预备添加的参数后生成新的URL
        data.putAll(map);
        if (!data.isEmpty()) {
            StringBuilder sb = new StringBuilder(urlHref);
            sb.append("?");
            for (Entry<String, Object> e : data.entrySet()) {
                sb.append(e.getKey());
                sb.append("=");
                sb.append(encodeURIComponent(String.valueOf(e.getValue())));
                sb.append("&");
            }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '&') {
                sb.deleteCharAt(sb.length() - 1); // 去除最后的&
            }
            urlHref = sb.toString();
        }
        return urlHref + anchor;
    }

    /**
     * 将若干个对象存入StringBuilder中，起字符串连接作用
     *
     * @param tmp StringBuilder对象的引用
     * @param args 对象列表
     */
    public static StringBuilder append(StringBuilder tmp, Object... args) {
        for (Object s : args) {
            tmp.append(s);
        }
        return tmp;
    }

    /**
     * 将若干个字符串拼接起来
     */
    public static String concat(String... list) {
        StringBuilder sb = new StringBuilder();
        for (String str : list) {
            sb.append(str);
        }
        return sb.toString();
    }

    /**
     * 将若干个字符串拼接起来，并自动加入换行符
     */
    public static String concatln(String... list) {
        StringBuilder sb = new StringBuilder();
        for (String str : list) {
            sb.append(str)
              .append('\n');
        }
        return sb.toString();
    }

    /**
     * 将若干个字符串拼接起来
     */
    public static String concat(Object... list) {
        StringBuilder sb = new StringBuilder();
        for (Object str : list) {
            sb.append(str);
        }
        return sb.toString();
    }

    /**
     * 将若干个字符串拼接起来，并自动加入换行符
     */
    public static String concatln(Object... list) {
        StringBuilder sb = new StringBuilder();
        for (Object str : list) {
            sb.append(str)
              .append('\n');
        }
        return sb.toString();
    }

    public static String digestString(String src) {
        return digestString(src, 50);
    }

    public static String digestString(String src, int lengthThreshold) {
        if (src.length() > lengthThreshold * 2 + 20) {
            return src.substring(0, lengthThreshold) + "...(" + src.length() + ")..." + src.substring(src.length() - lengthThreshold, src.length());
        }
        return src;
    }

    public static String getAlertJs(String msg) {
        return "alert(\"" + msg + "\");";
    }

    public static String getAddCookieJs(String key, Object value, int maxAge) {
        return "document.cookie = \"" + key + " = \" + escape(\"" + value.toString() + "\") + \"; expires=\" + new Date(new Date().getTime() + maxAge * 1000).toGMTString();";
    }

    public static String getDelCookieJs(String key) {
        return "document.cookie = \"" + key + " =; expires = \" + " + "new Date(0).toGMTString();";
    }

    public static String getJumpJs(String href) {
        return "document.location.href = \"" + href + "\";";
    }

    public static String getJumpTopJs(String href) {
        return "parent.location.href = \"" + href + "\";";
    }

    public static String getBackJs() {
        return "window.history.go(-1);";
    }

    /**
     * 生成一条超链接，用于文本消息使用，参数部分会自动进行编码处理
     */
    public static String getLinkHtml(String text, String url, Object... args) {
        String href = addParamsToUrl(url, args);
        return "<a href=\"" + href + "\">" + escapeHtml(text) + "</a>";
    }

    /**
     * 随机生成一个long X, min<=X<max
     */
    public static long randomLong(long min, long max) {
        return min + (long) (Math.random() * (max - min));
    }

    /**
     * 随机生成一个int X, min<=X<max
     */
    public static int randomInt(int min, int max) {
        return min + (int) (Math.random() * (max - min));
    }

    /**
     * <pre>
     * 随机生成一个int X, min<=X<max
     *
     * 长度不足length的部分补0，例如规定长度4，生成的数为10，返回0010
     * 如果长度超过length，就按原样输出，例如规定长度1，生成的数为123，就返回123
     * </pre>
     */
    public static String randomInt(int min, int max, int length) {
        String base = String.valueOf(randomInt(min, max));
        int add = length - base.length();
        if (add > 0) {
            if (add < ZEROS.length) {
                return ZEROS[add] + base;
            }
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < add; i++) {
                sb.append('0');
            }
            sb.append(base);
            return sb.toString();
        }
        return base;
    }

    /**
     * <pre>
     * 随机生成一个long X, min<=X<max
     *
     * 长度不足length的部分补0，例如规定长度4，生成的数为10，返回0010
     * 如果长度超过length，就按原样输出，例如规定长度1，生成的数为123，就返回123
     * </pre>
     */
    public static String randomLong(long min, long max, int length) {
        String base = String.valueOf(randomLong(min, max));
        int add = length - base.length();
        if (add > 0) {
            if (add < ZEROS.length) {
                return ZEROS[add] + base;
            }
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < add; i++) {
                sb.append('0');
            }
            sb.append(base);
            return sb.toString();
        }
        return base;
    }

    /**
     * 随机生成一个字符串，内容取自于char[] range内指定的范围
     *
     * @param length 字符串的长度
     * @param range 取值范围，如果传入空就会返回空字符串
     */
    public static String randomString(int length, char[] range) {
        StringBuilder sb = new StringBuilder(length);
        if (null == range || range.length == 0) {
            return "";
        }
        Random r = new Random();
        for (int i = 0; i < length; i++) {
            char ch = range[r.nextInt(range.length)];
            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * 随机生成一个字符串，内容取自于range内指定的范围
     *
     * @param length 字符串的长度
     * @param range 取值范围，如果传入空就会返回空字符串
     */
    public static String randomString(int length, String range) {
        StringBuilder sb = new StringBuilder(length);
        if (isEmpty(range)) {
            return "";
        }
        Random r = new Random();
        for (int i = 0; i < length; i++) {
            char ch = range.charAt(r.nextInt(range.length()));
            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * 过滤非数字字符，只保留'+'和数字，可用于电话号码情形
     */
    public static String filterPhoneNumber(String str) {
        StringBuilder sb = new StringBuilder();
        if (isNotEmpty(str)) {
            String _str = str.trim();
            for (int i = 0; i < _str.length(); ++i) {
                char c = _str.charAt(i);
                if ((c == '+') || (c >= '0' && c <= '9')) {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 将字符串中可能包含有非法的sql字符进行过滤，例如过滤'。
     *
     * @param str 需要进行过滤的字符串
     *
     * @return 过滤后的安全字符串
     */
    public static String escapeSql(String str) {
        if (str == null) {
            return "";
        }
        for (String[] ss : sqlHandles) {
            str = str.replaceAll(ss[0], ss[1]);
        }
        return str;
    }

    /**
     * 当src的长度超过maxLength时，返回src的前maxLength位, 否则原样返回src
     */
    public static String truncate(String src, int maxLength) {
        if (src == null || src.length() <= maxLength || maxLength <= 0) {
            return src;
        } else {
            return src.substring(0, maxLength);
        }
    }

    /**
     * 当src的长度超过maxLength时，返回src的前maxLength位 + 指定的后缀(suffixWhenTooLong), 否则原样返回src
     *
     * 应用场景举例：只返回标题的前15位，超过15位则尾部加上...调用方法为truncate(title, 15, "...")
     */
    public static String truncate(String str, int maxLength, String suffixWhenTooLong) {
        if (StringTools.isEmpty(str)) {
            return "";
        } else if (str.length() < maxLength) {
            return str;
        } else {
            if (StringTools.isEmpty(suffixWhenTooLong)) {
                return str.substring(0, maxLength);
            } else {
                return str.substring(0, maxLength) + suffixWhenTooLong;
            }
        }
    }

    /**
     * 打印三行字符串,用不同分隔符号 把标题围起来,起强调作用
     */
    public static StringBuilder emphasizeTitle(String title, char corner, char linechar, char verticalchar) {
        return emphasizeTitle(new StringBuilder(), title, corner, linechar, verticalchar);
    }

    /**
     * 打印三行字符串,用不同分隔符号 把标题围起来,起强调作用
     */
    public static StringBuilder emphasizeTitle(StringBuilder tmp, String title, char corner, char linechar, char verticalchar) {
        StringBuilder line;
        try {
            line = printLine(title.getBytes("GBK").length, corner, linechar);
            tmp.append(line);
            tmp.append(verticalchar)
               .append(title)
               .append(verticalchar)
               .append('\n');
            tmp.append(line);
        } catch (UnsupportedEncodingException e) {
        }
        return tmp;
    }

    /**
     * 精简打印一个{@link Throwable}信息不换行，由maxTraceLen指定信息数量
     *
     * @param maxTraceLen 要打印堆栈信息的数量
     */
    public static String printThrowableSimple(Throwable ex, int maxTraceLen) {
        return printThrowableSimple(ex, maxTraceLen, 0);
    }

    /**
     * 精简打印一个{@link Throwable}信息不换行，由maxTraceLen指定信息数量
     *
     * @param maxTraceLen 要打印堆栈信息的数量
     * @param ignore 跳过多少条
     */
    public static String printThrowableSimple(Throwable ex, int maxTraceLen, int ignore) {
        if (null != ex) {
            StringBuilder s = new StringBuilder();
            s.append(ex.getClass()
                       .getSimpleName());// 这里不打印全称
            s.append(":");
            s.append(ex.getMessage());
            if (maxTraceLen > 0) {
                StackTraceElement[] trace = getOurStackTrace(ex);
                if (trace != null) {
                    int len = Math.min(trace.length, maxTraceLen);
                    for (int i = ignore; i < len; i++) {
                        try {
                            StackTraceElement t = trace[i];
                            String clazzName = t.getClassName();
                            clazzName = clazzName.substring(clazzName.lastIndexOf(".") + 1, clazzName.length());
                            s.append(EXCEPTION_SPLIT);
                            s.append(clazzName);
                            s.append(".");
                            s.append(t.getMethodName());
                            s.append(":");
                            s.append(t.getLineNumber());
                        } catch (Exception e) {
                        }
                    }
                }
            }
            return s.toString();
        }
        return "";
    }

    /**
     * 获得堆栈的所有元素
     */
    public static StackTraceElement[] getOurStackTrace(Throwable ex) {
        try {
            StackTraceElement[] ste = (StackTraceElement[]) getOurStackTrace.invoke(ex);
            return ste;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 输出精简化堆栈信息，只追溯到Cmd层，用于追溯请求来源
     */
    public static String printTrace() {
        StringBuilder s = new StringBuilder();
        StackTraceElement[] trace = Thread.currentThread()
                                          .getStackTrace();
        if (trace != null) {
            boolean meetCmd = false; // 标记是否碰到了Cmd结尾的类
            for (int i = 2; i < trace.length; i++) {
                try {
                    StackTraceElement t = trace[i];
                    String clazzName = t.getClassName();
                    // 异常多数都是在Cmd层抛出的，遍历到Cmd就可以停止了，这样可以减小输出堆栈的长度
                    // 处理办法是：当碰到Cmd结尾的类时，标记meetCmd=true
                    // 继续遍历，只要碰到非Cmd结尾的类（此时meetCmd=true）就结束遍历
                    if (clazzName.endsWith("Cmd") && !HttpCmd.class.getName()
                                                                   .equals(clazzName)) {

                        meetCmd = true;
                    } else if (meetCmd) {
                        break;
                    }
                    // 省略java和sun自身的库
                    if (clazzName.startsWith("sun.") || clazzName.startsWith("java.")) {
                        continue;
                    }
                    // 省略Spring和CGLIB生成的类
                    if (clazzName.startsWith("org.springframework.") || clazzName.contains("BySpringCGLIB$$")) {
                        continue;
                    }
                    // 省略jedis和jdbc基础库的
                    if (clazzName.startsWith("redis.") || clazzName.startsWith(JedisTemplate.class.getPackage()
                                                                                                  .getName()) || clazzName.startsWith(JdbcTemplate.class.getPackage()
                                                                                                                                                        .getName())) {
                        continue;
                    }
                    clazzName = clazzName.substring(clazzName.lastIndexOf(".") + 1);
                    s.append(clazzName);
                    s.append(".");
                    s.append(t.getMethodName());
                    s.append(":");
                    s.append(t.getLineNumber());
                    s.append(EXCEPTION_SPLIT);
                } catch (Exception e) {
                }
            }
            if (s.length() > 1) { // 去掉最后多余的||
                s.delete(s.length() - StringTools.EXCEPTION_SPLIT.length(), s.length());
            }
        }
        return s.toString();
    }

    /**
     * 输出异常的精简化堆栈信息，方便追溯源头
     */
    public static String printTrace(Throwable ex, boolean withMsg, int maxTraceLen, int ignore) {
        if (null != ex) {
            ex = ex instanceof InvocationTargetException ? ex.getCause() : ex;
            StringBuilder s = new StringBuilder();
            if (maxTraceLen > 0) {
                StackTraceElement[] trace = StringTools.getOurStackTrace(ex);
                if (trace != null) {
                    int len = Math.min(trace.length, maxTraceLen);
                    if (withMsg) {
                        String name = ex.getClass()
                                        .getSimpleName();
                        if (StringTools.isEmpty(name)) { // 如果异常是一个子类，无法获取名称就取其父类名称
                            name = ex.getClass()
                                     .getSuperclass()
                                     .getSimpleName();
                        }
                        s.append(name)
                         .append(":")
                         .append(ex.getMessage())
                         .append(StringTools.EXCEPTION_SPLIT);
                    }
                    boolean meetCmd = false; // 标记是否碰到了Cmd结尾的类
                    for (int i = ignore; i < len; i++) {
                        try {
                            StackTraceElement t = trace[i];
                            String clazzName = t.getClassName();
                            // 异常多数都是在Cmd层抛出的，遍历到Cmd就可以停止了，这样可以减小输出堆栈的长度
                            // 处理办法是：当碰到Cmd结尾的类时，标记meetCmd=true
                            // 继续遍历，只要碰到非Cmd结尾的类（此时meetCmd=true）就结束遍历
                            if (clazzName.endsWith("Cmd") && !HttpCmd.class.getName()
                                                                           .equals(clazzName)) {

                                meetCmd = true;
                            } else if (meetCmd) {
                                break;
                            }
                            // 省略java和sun自身的库
                            if (clazzName.startsWith("sun.") || clazzName.startsWith("java.")) {
                                continue;
                            }
                            // 省略Spring和CGLIB生成的类
                            if (clazzName.startsWith("org.springframework.") || clazzName.contains("BySpringCGLIB$$")) {
                                continue;
                            }
                            // 省略jedis和jdbc基础库的
                            if (clazzName.startsWith("redis.") || clazzName.startsWith(JedisTemplate.class.getPackage()
                                                                                                          .getName()) || clazzName.startsWith(JdbcTemplate.class.getPackage()
                                                                                                                                                                .getName())) {
                                continue;
                            }
                            clazzName = clazzName.substring(clazzName.lastIndexOf(".") + 1);
                            s.append(clazzName);
                            s.append(".");
                            s.append(t.getMethodName());
                            s.append(":");
                            s.append(t.getLineNumber());
                            s.append(StringTools.EXCEPTION_SPLIT);
                        } catch (Exception e) {
                        }
                    }
                    if (s.length() > 1) { // 去掉最后多余的分隔符
                        s.delete(s.length() - StringTools.EXCEPTION_SPLIT.length(), s.length());
                    }
                }
            }
            return s.toString();
        }
        return "";
    }

    /**
     * 打印一行 长度为 len 的重复lineChar字符的分隔符
     */
    public static StringBuilder printLine(int len, char linechar) {
        return printLine(new StringBuilder(), len, linechar);
    }

    /**
     * 打印一行 长度为 len 的重复lineChar字符的分隔符
     *
     * @param corner 转角处所使用字符
     * @param linechar 默认字符
     */
    public static StringBuilder printLine(int len, char corner, char linechar) {
        return printLine(new StringBuilder(), len, corner, linechar);
    }

    /**
     * 获得有数量len个lineChar组成的字符串
     *
     * @param tmp 存放结果的StringBuilder对象的引用
     * @param len 字符的个数
     * @param linechar 字符
     */
    public static StringBuilder printLine(StringBuilder tmp, int len, char linechar) {
        for (int i = 0; i < len; i++) {
            tmp.append(linechar);
        }
        tmp.append('\n');
        return tmp;
    }

    /**
     * 返回以corner结束的由len个lineChar字符组成的字符串
     *
     * @param tmp 存放结果的引用
     * @param len 字符的个数
     * @param corner 结束符号
     * @param linechar 字符
     */
    public static StringBuilder printLine(StringBuilder tmp, int len, char corner, char linechar) {
        tmp.append(corner);
        for (int i = 0; i < len; i++) {
            tmp.append(linechar);
        }
        tmp.append(corner);
        tmp.append('\n');
        return tmp;
    }

    /**
     * 打印 堆栈异常
     */
    public static StringBuilder printThrowable(StringBuilder tmp, Throwable ex) {
        if (null != ex) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter pw = new PrintWriter(stringWriter);
            ex.printStackTrace(pw);
            tmp.append(stringWriter)
               .append('\n');
        }
        return tmp;
    }

    /**
     * 打印 堆栈异常
     */
    public static StringBuilder printThrowable(Throwable ex) {
        return printThrowable(new StringBuilder(), ex);
    }

    /**
     * 简单打印Throwable信息，最多8个
     */
    public static String printThrowableSimple(Throwable ex) {
        return printThrowableSimple(ex, 8);
    }

    /**
     * 记录调用栈，直到遇到非StringTools的成员为止
     */
    public static StringBuilder printStackTraceSimple() {
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] ste = Thread.currentThread()
                                        .getStackTrace();
        if (ste != null) {
            for (int i = 0; i < ste.length; i++) {
                try {
                    StackTraceElement t = ste[i];
                    String clazzName = t.getClassName();
                    if (clazzName.equals(StringTools.class.getName()) || (clazzName.equals(Thread.class.getName())) && t.getMethodName()
                                                                                                                        .equals("getStackTrace")) {
                        continue;
                    }
                    clazzName = clazzName.substring(clazzName.lastIndexOf(".") + 1, clazzName.length());
                    sb.append(EXCEPTION_SPLIT);
                    sb.append(clazzName);
                    sb.append(".");
                    sb.append(t.getMethodName());
                    sb.append(":");
                    sb.append(t.getLineNumber());
                } catch (Exception e) {
                }
            }
        }
        return sb;
    }

    /**
     * 以16进制 打印字节数组
     */
    public static String printHexString(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder buffer = new StringBuilder(bytes.length);
        int startIndex = 0;
        int column = 0;
        for (int i = 0; i < bytes.length; i++) {
            column = i % 16;
            switch (column) {
            case 0:
                startIndex = i;
                fixHexString(buffer, Integer.toHexString(i), 8).append(": ");
                buffer.append(_toHex(bytes[i]));
                buffer.append(" ");
                break;
            case 15:
                buffer.append(_toHex(bytes[i]));
                buffer.append(" ");
                buffer.append(filterString(bytes, startIndex, column + 1));
                buffer.append("\n");
                break;
            default:
                buffer.append(_toHex(bytes[i]));
                buffer.append(" ");
            }
        }
        if (column != 15) {
            for (int i = 0; i < (15 - column); i++) {
                buffer.append("   ");
            }
            buffer.append(filterString(bytes, startIndex, column + 1));
            buffer.append("\n");
        }

        return buffer.toString();
    }

    /**
     * 过滤掉字节数组中0x0 - 0x1F的控制字符，生成字符串
     *
     * @param bytes byte[]
     * @param offset int
     * @param count int
     *
     * @return String
     */
    private static String filterString(byte[] bytes, int offset, int count) {
        byte[] buffer = new byte[count];
        System.arraycopy(bytes, offset, buffer, 0, count);
        for (int i = 0; i < count; i++) {
            if (buffer[i] >= 0x0 && buffer[i] <= 0x1F) {
                buffer[i] = 0x2e;
            }
        }
        return new String(buffer);
    }

    /**
     * 将hexStr格式化成length长度16进制数，并在后边加上h
     *
     * @param hexStr String
     *
     * @return StringBuilder
     */
    private static StringBuilder fixHexString(StringBuilder buf, String hexStr, int length) {
        if (hexStr == null || hexStr.length() == 0) {
            buf.append("00000000h");
        } else {
            int strLen = hexStr.length();
            for (int i = 0; i < length - strLen; i++) {
                buf.append("0");
            }
            buf.append(hexStr)
               .append("h");
        }
        return buf;
    }

    /**
     * 以16进制方式打印字节数组，用于调试二进制的内容，支持输入前缀后缀方便筛选日志
     */
    public static String printHexString(String prefix, byte[] bytes, String suffix) {
        if (null == bytes || bytes.length == 0) {
            return "";
        }
        StringBuilder buffer = new StringBuilder(bytes.length);
        int startIndex = 0;
        int column = 0;
        for (int i = 0; i < bytes.length; i++) {
            column = i % 16;
            switch (column) {
            case 0:
                startIndex = i;
                if (null != prefix) {
                    buffer.append(prefix)
                          .append(" ");
                }
                fixHexString(buffer, Integer.toHexString(i), 8).append(": ");
                buffer.append(_toHex(bytes[i]));
                buffer.append(" ");
                break;
            case 15:
                buffer.append(_toHex(bytes[i]));
                buffer.append(" ");
                buffer.append(filterString(bytes, startIndex, column + 1));
                if (null != suffix) {
                    buffer.append(" ")
                          .append(suffix);
                }
                buffer.append("\n");
                break;
            default:
                buffer.append(_toHex(bytes[i]));
                buffer.append(" ");
            }
        }
        if (column != 15) {
            for (int i = 0; i < (15 - column); i++) {
                buffer.append("   ");
            }
            buffer.append(filterString(bytes, startIndex, column + 1));
            for (int i = 0; i < (15 - column); i++) {
                buffer.append(" ");
            }
            if (null != suffix) {
                buffer.append(" ")
                      .append(suffix);
            }
            buffer.append("\n");
        }
        return buffer.toString();
    }

    /**
     * 将字节转换成16进制显示
     */
    private static String _toHex(byte b) {
        char[] buf = new char[2];
        byte bt = b;
        for (int i = 0; i < 2; i++) {
            buf[1 - i] = DIGITS_LOWER[bt & 0xF];
            bt = (byte) (bt >>> 4);
        }
        return new String(buf);
    }

    /**
     * 将hexStr格式化成length长度16进制数，并在后边加上h
     */
    private static StringBuilder _fixHexString(StringBuilder buf, String hexStr, int length) {
        if (hexStr == null || hexStr.length() == 0) {
            buf.append("00000000h");
        } else {
            int strLen = hexStr.length();
            for (int i = 0; i < length - strLen; i++) {
                buf.append("0");
            }
            buf.append(hexStr)
               .append("h");
        }
        return buf;
    }

    /**
     * 过滤掉字节数组中不能显示的ascii码，生成字符串
     */
    private static String _filterString(byte[] bytes, int offset, int count) {
        byte[] buffer = new byte[count];
        System.arraycopy(bytes, offset, buffer, 0, count);
        for (int i = 0; i < count; i++) {
            if (buffer[i] < 0x20 || buffer[i] > 0x7E) {
                buffer[i] = 0x2e;
            }
        }
        return new String(buffer);
    }

    /**
     * 字符编码集合（用Charsets的话和很多框架的都重复了，不利于IDE导入，故改了名字）
     */
    public static class CharsetEnum {

        public static final Charset UTF_8 = Charset.forName("UTF-8");

        public static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

        public static final Charset GBK = Charset.forName("GBK");

        public static final Charset GB18030 = Charset.forName("GB18030");

        public static final Charset BIG5 = Charset.forName("BIG5");
    }
}
