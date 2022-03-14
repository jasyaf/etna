package org.etnaframework.core.web.mapper;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import org.etnaframework.core.util.BeanTools;
import org.etnaframework.core.util.DatetimeUtils;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.EmojiCharacterUtils;
import org.etnaframework.core.util.JsonObjectUtils;
import org.etnaframework.core.util.KeyValueGetter.DbMap;
import org.etnaframework.core.util.ReflectionTools;
import org.etnaframework.core.util.ReflectionTools.BeanFieldValueSetter;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.annotation.CmdReqParam;
import org.etnaframework.core.web.exception.ParamInvalidValueException;
import org.etnaframework.jdbc.exception.BeanProcessException;
import com.alibaba.fastjson.JSONObject;

/**
 * 保存class与{@link ValidatorMappers}的对应关系
 *
 * @author dragon
 * @since 2015-07-14 11:11
 */
public class ValidatorMappers {

    private ValidatorMappers() {
    }

    private final static String NotEmoji = "不能包含表情字符";

    public static <T> T createFormObj(Class<T> clazz, HttpEvent he, boolean isCheck) throws Throwable {
        if (Collection.class.isAssignableFrom(clazz) || Map.class.isAssignableFrom(clazz)) {
            throw new BeanProcessException("不支持集合类型" + clazz.getName());
        }
        // 空类（即没有任何字段的类）是不允许的，会抛出异常
        T instance = clazz.newInstance();
        Collection<Field> fields = ReflectionTools.getAllFieldsInSourceCodeOrder(clazz, null);
        if (fields.isEmpty()) {
            throw new BeanProcessException("类" + clazz.getName() + "不能是空的，必须至少要有一个field");
        }
        DbMap requestDbMap = he.getRequestDbMap();

        String contentType = he.getContentType();
        if (contentType.startsWith("application/json")) {
            String jsonString = he.getContentString();
            if (EmojiCharacterUtils.containsEmoji(jsonString)) {
                // 由于现在的emoji处理，fastjson尚不能完全有效地转义emoji，现在采取过滤策略，出现就抛异常
                throw new ParamInvalidValueException("", NotEmoji);
            }
            JSONObject jsonObject = JsonObjectUtils.parseJson(jsonString);

            if (jsonObject != null) {
                requestDbMap.append(jsonObject);
            }
        }
        parseObject(instance, clazz, requestDbMap, fields, isCheck);
        return instance;
    }

    private static <T> void parseObject(T instance, Class<T> clazz, Map<String, Object> map, Collection<Field> fields, boolean isCheck) {
        for (Field f : fields) {
            BeanFieldValueSetter setter = BeanFieldValueSetter.create(clazz, f.getName(), f.getType());
            CmdReqParam p = f.getAnnotation(CmdReqParam.class);
            //当没有添加注解时,直接赋值即可
            if (p == null) {
                Object o = map.get(f.getName());
                if (o != null) {
                    setValue(instance, setter, o);
                }
                continue;
            }
            String key = f.getName();
            if (StringTools.isNotEmpty(p.alias())) {
                key = p.alias();
            }
            Object value = map.get(key);
            boolean isEmpty = (value == null || StringTools.isEmpty(value.toString())) && p.required() && isCheck;
            if (isEmpty) {
                throw new ParamInvalidValueException(key, p.errmsg());
            }
            if (value == null && StringTools.isNotEmpty(p.defaultValue())) {
                value = p.defaultValue();
            }
            if (value == null) {
                continue;
            }
            if (isCheck) {
                checkReqParam(key, setter, p.errmsg(), value, p.minLength(), p.maxLength());
            }
            setValue(instance, setter, value);
        }
    }

    private static void checkReqParam(String key, BeanFieldValueSetter setter, String errmsg, Object value, int minlength, int maxLength) {
        double length;

        if (BeanTools.isInteger(setter.getType())) {
            length = Double.valueOf(value.toString());
        } else if (BeanTools.isLong(setter.getType())) {
            length = Double.valueOf(value.toString());
        } else if (BeanTools.isDouble(setter.getType())) {
            length = Double.valueOf(value.toString());
        } else if (BeanTools.isFloat(setter.getType())) {
            length = Double.valueOf(value.toString());
        } else if (BeanTools.isShort(setter.getType())) {
            length = Double.valueOf(value.toString());
        } else if (BeanTools.isDateWrapperType(setter.getType())) {
            Datetime datetime = DatetimeUtils.parse(value.toString());

            length = datetime.toString().length();
        } else {
            String valueStr = value.toString();
            length = valueStr.length();
        }
        if (length < minlength || length > maxLength) {
            throw new ParamInvalidValueException(key, errmsg);
        }
    }

    private static <T> void setValue(T bean, BeanFieldValueSetter setter, Object value) {
        if (StringTools.isEmpty(value.toString())) {
                return;
        }
        if (BeanTools.isBoolean(setter.getType())) {
            String valueStr = value.toString();
            if (valueStr.equalsIgnoreCase("on") || valueStr.equalsIgnoreCase("1")) {
                setter.setValue(bean, true);
            } else {
                setter.setValue(bean, false);
            }
        } else if (BeanTools.isInteger(setter.getType())) {
            setter.setValue(bean, Integer.parseInt(value.toString()));
        } else if (BeanTools.isLong(setter.getType())) {
            setter.setValue(bean, Long.valueOf(value.toString()));
        } else if (BeanTools.isByte(setter.getType())) {
            setter.setValue(bean, Byte.valueOf(value.toString()));
        } else if (BeanTools.isDouble(setter.getType())) {
            setter.setValue(bean, Double.valueOf(value.toString()));
        } else if (BeanTools.isFloat(setter.getType())) {
            setter.setValue(bean, Float.valueOf(value.toString()));
        } else if (BeanTools.isShort(setter.getType())) {
            setter.setValue(bean, Short.valueOf(value.toString()));
        } else if (BeanTools.isDateWrapperType(setter.getType())) {
            setter.setValue(bean, DatetimeUtils.parse(value.toString()));
        } else {
            setter.setValue(bean, value.toString());
        }
    }
}
