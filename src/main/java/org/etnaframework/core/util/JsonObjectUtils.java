package org.etnaframework.core.util;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.spring.annotation.Config;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.DatetimeUtils.DatetimeDeserializer;
import org.etnaframework.core.util.KeyValueGetter.DbMap;
import org.etnaframework.core.util.StringTools.CharsetEnum;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.serializer.ValueFilter;
import com.alibaba.fastjson.util.TypeUtils;

/**
 * 序列号/反序列化JSON的工具，基于FastJson
 *
 * @author BlackCat
 * @since 2012-10-3
 */
@Service
public final class JsonObjectUtils {

    private static final Logger log = Log.getLogger();

    static {
        // 配置生成json的规则
        int features = 0;
        features |= SerializerFeature.QuoteFieldNames.getMask();
        features |= SerializerFeature.SkipTransientField.getMask();
        features |= SerializerFeature.WriteEnumUsingToString.getMask();
        features |= SerializerFeature.WriteDateUseDateFormat.getMask();
        features |= SerializerFeature.DisableCircularReferenceDetect.getMask();
        JSON.DEFAULT_GENERATE_FEATURE = features;

        // 配置解析json的规则
        features = 0;
        features |= Feature.AutoCloseSource.getMask();
        features |= Feature.InternFieldNames.getMask();
        features |= Feature.UseBigDecimal.getMask();
        features |= Feature.AllowUnQuotedFieldNames.getMask();
        features |= Feature.AllowSingleQuotes.getMask();
        features |= Feature.AllowArbitraryCommas.getMask();
        features |= Feature.IgnoreNotMatch.getMask();
        features |= Feature.OrderedField.getMask();
        JSON.DEFAULT_PARSER_FEATURE = features;

        // 配置日期时间生成/解析格式
        JSON.DEFFAULT_DATE_FORMAT = DatetimeUtils.getDefaultDatetimeFormat();

        // 支持封装的Datetime类型
        ParserConfig.getGlobalInstance().putDeserializer(Datetime.class, DatetimeDeserializer.instance);
    }

    @Config("etna.defaultDatetimeFormat")
    protected static void setDateFormat(String format) {
        JSON.DEFFAULT_DATE_FORMAT = format;
    }

    /**
     * 将传入的对象生成JSON字符串，如果转换失败，或者传入对象空，返回{}
     */
    public static String createJson(Object obj) {
        try {
            return JSON.toJSONString(obj, LargeNumberFixToStringValueFilter.instance);
        } catch (Exception e) {
            log.debug("createJson failed, type={}", null == obj ? null : obj.getClass(), e);
        }
        return "{}";
    }

    /**
     * 将传入的对象生成格式化后的JSON字符串，如果转换失败，或者传入对象空，返回{}
     */
    public static String createJsonPretty(Object obj) {
        try {
            return JSON.toJSONString(obj, LargeNumberFixToStringValueFilter.instance, SerializerFeature.PrettyFormat);
        } catch (Exception e) {
            log.debug("createJson failed, type={}", null == obj ? null : obj.getClass(), e);
        }
        return "{}";
    }

    /**
     * 本方法可用于生成代码中的返回结构的注释，方便开发使用
     */
    public static String createJsonPrettyForDevComment(Object obj) {
        try {
            String json = JSON.toJSONString(obj, LargeNumberFixToStringValueFilter.instance, SerializerFeature.UseSingleQuotes, SerializerFeature.PrettyFormat).replace("\t", "    ");
            StringBuilder sb = new StringBuilder("\"");
            int c = 0;
            int maxL = 59;
            for (int i = 0; i < json.length(); i++) {
                char ch = json.charAt(i);
                if (ch == '\n') {
                    maxL = Math.max(c, maxL);
                    c = 0;
                } else {
                    if (("" + ch).getBytes(CharsetEnum.UTF_8).length > 1) { // 非ascii字符算2个字符进行占位
                        c += 2;
                    } else {
                        c++;
                    }
                }
            }
            boolean ignore = false;
            int max = maxL + 1;
            c = 0;
            for (int i = 0; i < json.length(); i++) {
                char ch = json.charAt(i);
                if (ch == '\n') {
                    if (c < max && !ignore) {
                        sb.append(StringTools.printLine(max - c, ' '));
                        sb.deleteCharAt(sb.length() - 1);
                        sb.append("// ");
                    }
                    sb.append("\",\n\"");
                    c = 0;
                    ignore = false;
                } else {
                    sb.append(ch);
                    if (("" + ch).getBytes(CharsetEnum.UTF_8).length > 1) { // 非ascii字符算2个字符进行占位
                        c += 2;
                    } else {
                        c++;
                    }
                    if (ch == '{' || ch == '}') {
                        ignore = true;
                    }
                }
            }
            sb.append("\",");
            return sb.toString();
        } catch (Exception e) {
            log.debug("createJson failed, type={}", null == obj ? null : obj.getClass(), e);
        }
        return "{}";
    }

    /**
     * 将JSON字符串转化为指定的对象，如果转换失败，返回null
     */
    public static <T> T parseJson(String jsonString, Class<T> requiredClass) {
        try {
            return JSON.parseObject(jsonString, requiredClass);
        } catch (Exception e) {
            log.debug("parseJson Failed, requiredClass={}, jsonString={}", requiredClass, jsonString, e);
        }
        return null;
    }

    /**
     * 将JSON字符串转化为指定的对象，如果转换失败，返回null
     */
    public static <T> T parseJson(String jsonString, TypeReference<T> requiredType) {
        try {
            return JSON.parseObject(jsonString, requiredType);
        } catch (Exception e) {
            log.debug("parseJson Failed, requiredClass={}, jsonString={}", requiredType, jsonString, e);
        }
        return null;
    }

    /**
     * 将JSONObject转化为指定的对象，如果转换失败，返回null
     */
    public static <T> T parseJson(JSONObject jsonObject, Class<T> requiredClass) {
        try {
            return JSON.toJavaObject(jsonObject, requiredClass);
        } catch (Exception e) {
            log.debug("parseJson Failed, requiredClass={}, jsonString={}", requiredClass, jsonObject, e);
        }
        return null;
    }

    /**
     * 将Object转化为指定的对象，如果转换失败，返回null
     */
    public static <T> T parseJson(Object jsonObject, Class<T> requiredClass) {
        try {
            return TypeUtils.castToJavaBean(jsonObject, requiredClass);
        } catch (Exception e) {
            log.debug("parseJson Failed, requiredClass={}, jsonString={}", requiredClass, createJson(jsonObject), e);
        }
        return null;
    }

    /**
     * 将Map<String,Object>转化为指定的对象，如果转换失败，返回null
     */
    public static <T> T parseJson(Map<String, ? extends Object> map, Class<T> requiredClass) {
        try {
            if (map instanceof DbMap) {
                DbMap dm = (DbMap) map;
                return JSON.toJavaObject(new JSONObject(dm), requiredClass);
            }
            return JSON.toJavaObject(new JSONObject(new DbMap(map)), requiredClass);
        } catch (Exception e) {
            log.debug("parseJson Failed, requiredClass={}, jsonString={}", requiredClass, createJson(map), e);
        }
        return null;
    }

    /**
     * 将json字符串转换为JSONObject（内部是Map<String,Object>），如果转换失败，返回null
     */
    public static JSONObject parseJson(String jsonString) {
        try {
            return JSON.parseObject(jsonString);
        } catch (Exception e) {
            log.debug("parseJson Failed, jsonString={}", jsonString, e);
        }
        return null;
    }

    /**
     * 将json字符串转换为List，如果转换失败，返回null
     */
    public static <T> List<T> parseJsonArray(String jsonString, Class<T> requiredClass) {
        try {
            return JSON.parseArray(jsonString, requiredClass);
        } catch (Exception e) {
            log.debug("parseJsonArray Failed, requiredClass={}, jsonString={}", requiredClass, jsonString, e);
        }
        return null;
    }

    /**
     * 将json字符串转换为List，如果转换失败，返回null
     */
    public static List<Object> parseJsonArray(String jsonString, Type[] types) {
        try {
            return JSON.parseArray(jsonString, types);
        } catch (Exception e) {
            log.debug("parseJsonArray Failed, requiredTypes={}, jsonString={}", Arrays.asList(types), jsonString, e);
        }
        return null;
    }

    /**
     * 将json字符串转换为JSONArray（内部是List<JSONObject>），如果转换失败，返回null
     */
    public static JSONArray parseJsonArray(String jsonString) {
        try {
            return JSON.parseArray(jsonString);
        } catch (Exception e) {
            log.debug("parseJsonArray Failed, jsonString={}", jsonString, e);
        }
        return null;
    }

    /**
     * 按key1,value1,key2,value2的顺序传入构造Map
     */
    public static Map<String, Object> buildMap(Object... keyValue) {
        return new DbMap(keyValue);
    }

    /**
     * fastjson序列化long型数值时，为了防止在web端丢失精度，如果数值大于js的Number.MAX_SAFE_INTEGER或小于Number.MIN_SAFE_INTEGER，把数值序列化成字符串
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER">https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER</a>
     */
    public static class LargeNumberFixToStringValueFilter implements ValueFilter {

        public final static LargeNumberFixToStringValueFilter instance = new LargeNumberFixToStringValueFilter();

        @Override
        public Object process(Object object, String name, Object value) {
            if (value instanceof Long && (((Long) value) > 0x1fffffffffffffL || ((Long) value) < -0x1fffffffffffffL)) {
                // 为防止long型过长在web端丢失精度，只要大于js的Number.MAX_SAFE_INTEGER或小于Number.MIN_SAFE_INTEGER，都用引号括起来。
                return String.valueOf(value);
            }
            return value;
        }
    }
}
