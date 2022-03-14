package org.etnaframework.core.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;

/**
 * 利用java反射机制动态创建对象
 *
 * @author dragon
 * @since 2015.07.07 10:02
 */
public class ModelReflector {

    public static Object setProperty(Object bean, String propertyName, Object value) {
        Class<?> clazz = bean.getClass();
        try {
            Field field = clazz.getDeclaredField(propertyName);
            Method method = clazz.getDeclaredMethod(getSetterName(field.getName()), new Class[] {
                field.getType()
            });
            return method.invoke(bean, new Object[] {
                value
            });
        } catch (Exception e) {
        }
        return null;
    }

    public static String getGetterName(String propertyName) {
        String method = "get" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        return method;
    }

    public static String getSetterName(String propertyName) {
        String method = "set" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        return method;
    }

    public static <T> T bindModel(Map<String, Object> map, Class<T> cls) {
        try {
            JSONObject mjson = new JSONObject(map);
            try {
                T instance = cls.newInstance();

                Set<Map.Entry<String, Object>> set = mjson.entrySet();
                for (Map.Entry<String, Object> entry : set) {
                    String next = entry.getKey();
                    setProperty(instance, next, mjson.get(next).toString());
                }
                return instance;
            } catch (Exception e) {

            }
        } catch (JSONException e) {
            System.out.println(" {} {}JSONException");
        }
        return null;
    }

    class Test {

    }

    public static void main(String[] args) {
        Map<String, Object> map = new HashMap<String, Object>();
        List<Test> data = new ArrayList<Test>();

        map.put("name", "dragontest");
        map.put("age", new Integer(23));

        map.put("data", data);

        Test t = bindModel(map, Test.class);

        System.out.println(" {} {} " + t);
    }
}
