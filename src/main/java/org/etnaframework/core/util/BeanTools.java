package org.etnaframework.core.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.KeyValueGetter.DbMap;
import org.etnaframework.core.util.ReflectionTools.BeanFieldValueGetter;
import org.etnaframework.jdbc.exception.BeanProcessException;
import org.springframework.util.ReflectionUtils;

/**
 * bean的基本操作
 *
 * @author BlackCat
 * @since 2011-9-30
 */
public class BeanTools {

    /** 简单封装类型，模仿org.springframework.util.ClassUtils，但加入了额外内容 */
    private static final Set<Type> primitiveWrapperTypes = new HashSet<>();
    private static final Set<Type> dateWrapperTypes = new HashSet<>();

    private static final Map<ClassPair, BeanCopier> copier = new ConcurrentHashMap<>();

    static {
        // java基本类型
        primitiveWrapperTypes.add(boolean.class);
        primitiveWrapperTypes.add(Boolean.class);
        primitiveWrapperTypes.add(byte.class);
        primitiveWrapperTypes.add(Byte.class);
        primitiveWrapperTypes.add(char.class);
        primitiveWrapperTypes.add(Character.class);
        primitiveWrapperTypes.add(short.class);
        primitiveWrapperTypes.add(Short.class);
        primitiveWrapperTypes.add(int.class);
        primitiveWrapperTypes.add(Integer.class);
        primitiveWrapperTypes.add(long.class);
        primitiveWrapperTypes.add(Long.class);
        primitiveWrapperTypes.add(float.class);
        primitiveWrapperTypes.add(Float.class);
        primitiveWrapperTypes.add(double.class);
        primitiveWrapperTypes.add(Double.class);

        // 字符串
        primitiveWrapperTypes.add(String.class);

        // 大数值类型
        primitiveWrapperTypes.add(BigInteger.class);
        primitiveWrapperTypes.add(BigDecimal.class);

        // 二进制内容
        primitiveWrapperTypes.add(byte[].class);
        primitiveWrapperTypes.add(Byte[].class);

        // 时间日期类型
        dateWrapperTypes.add(Datetime.class);
        dateWrapperTypes.add(java.util.Date.class);
        dateWrapperTypes.add(java.sql.Date.class);
        dateWrapperTypes.add(java.sql.Time.class);
        dateWrapperTypes.add(java.sql.Timestamp.class);
        primitiveWrapperTypes.addAll(dateWrapperTypes);
    }

    /**
     * 判断当前Class是否是简单封装类型（包括String）
     */
    public static boolean isPrimitiveWrapperType(Type clazz) {
        return primitiveWrapperTypes.contains(clazz);
    }


    /**
     * 判断当前Class是否是日期封装类型
     */
    public static boolean isDateWrapperType(Type clazz) {
        return dateWrapperTypes.contains(clazz);
    }

    /**
     * 判断当前Class是否不是简单封装类型（包括String）
     */
    public static boolean isNotPrimitiveWrapperType(Type clazz) {
        return !isPrimitiveWrapperType(clazz);
    }

    /**
     * 判断当前Class是否为boolean
     */
    public static boolean isBoolean(Class<?> clazz) {
        return boolean.class.equals(clazz) || Boolean.class.equals(clazz);
    }

    /**
     * 判断当前Class是否为byte
     */
    public static boolean isByte(Class<?> clazz) {
        return byte.class.equals(clazz) || Byte.class.equals(clazz);
    }

    /**
     * 判断当前Class是否为char
     */
    public static boolean isCharacter(Class<?> clazz) {
        return char.class.equals(clazz) || Character.class.equals(clazz);
    }

    /**
     * 判断当前Class是否为short
     */
    public static boolean isShort(Class<?> clazz) {
        return short.class.equals(clazz) || Short.class.equals(clazz);
    }

    /**
     * 判断当前Class是否为int
     */
    public static boolean isInteger(Class<?> clazz) {
        return int.class.equals(clazz) || Integer.class.equals(clazz);
    }

    /**
     * 判断当前Class是否为long
     */
    public static boolean isLong(Class<?> clazz) {
        return long.class.equals(clazz) || Long.class.equals(clazz);
    }

    /**
     * 判断当前Class是否为float
     */
    public static boolean isFloat(Class<?> clazz) {
        return float.class.equals(clazz) || Float.class.equals(clazz);
    }

    /**
     * 判断当前Class是否为double
     */
    public static boolean isDouble(Class<?> clazz) {
        return double.class.equals(clazz) || Double.class.equals(clazz);
    }

    /**
     * 将sourceBean中的字段的值复制到targetBean中的对应字段去，用于从前端提交的数据回写到数据库
     */
    public static void copyBean(Object sourceBean, Object targetBean) {
        if (null == sourceBean || null == targetBean) {
            return;
        }
        ClassPair cp = new ClassPair(sourceBean.getClass(), targetBean.getClass());
        BeanCopier bc = copier.get(cp);
        if (null == bc) {
            bc = new BeanCopier(sourceBean.getClass(), targetBean.getClass());
            copier.put(cp, bc);
        }
        bc.copy(sourceBean, targetBean);
    }

    /**
     * 将JavaBean转化为Map，只能支持基本类型元素
     */
    public static DbMap convertToMap(Object o) {
        if (null == o) {
            return null;
        }
        DbMap dbMap = new DbMap();
        Map<String, BeanFieldValueGetter> getter = BeanReflectionCache.mapperGeterr(o.getClass());
        for (Map.Entry<String, BeanFieldValueGetter> entry : getter.entrySet()) {
            dbMap.put(entry.getKey(), entry.getValue().getValue(o));
        }
        return dbMap;
    }

    private static class ClassPair {

        private Class<?> sourceClass;

        private Class<?> targetClass;

        public ClassPair(Class<?> sourceClass, Class<?> targetClass) {
            this.sourceClass = sourceClass;
            this.targetClass = targetClass;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((sourceClass == null) ? 0 : sourceClass.hashCode());
            result = prime * result + ((targetClass == null) ? 0 : targetClass.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ClassPair other = (ClassPair) obj;
            if (sourceClass == null) {
                if (other.sourceClass != null) {
                    return false;
                }
            } else if (!sourceClass.equals(other.sourceClass)) {
                return false;
            }
            if (targetClass == null) {
                if (other.targetClass != null) {
                    return false;
                }
            } else if (!targetClass.equals(other.targetClass)) {
                return false;
            }
            return true;
        }
    }

    private static class BeanCopier {

        /** source->target的field对应关系 */
        private Map<Field, Field> relation = new HashMap<Field, Field>();

        public BeanCopier(Class<?> sourceClass, Class<?> targetClass) {
            Map<String, Field> sourceMap = new HashMap<String, Field>();
            for (Field f : sourceClass.getDeclaredFields()) {
                sourceMap.put(f.getName(), f);
            }
            for (Field tf : targetClass.getDeclaredFields()) {
                // 匹配的条件是：名称完全一样，类型完全一样
                Field sf = sourceMap.get(tf.getName());
                if (null != sf && sf.getType().equals(tf.getType())) {
                    relation.put(sf, tf);
                }
            }
        }

        public void copy(Object sourceBean, Object targetBean) {
            for (Entry<Field, Field> e : relation.entrySet()) {
                ReflectionUtils.makeAccessible(e.getKey());
                ReflectionUtils.makeAccessible(e.getValue());
                Object val = ReflectionUtils.getField(e.getKey(), sourceBean);
                if (null != val) { // null值不复制
                    ReflectionUtils.setField(e.getValue(), targetBean, val);
                }
            }
        }
    }

    /**
     * 缓存class与JavaBean的get方法的对应关系
     */
    static class BeanReflectionCache {

        private static ConcurrentHashMap<Class<?>, Map<String, BeanFieldValueGetter>> map = new ConcurrentHashMap<>();

        private BeanReflectionCache() {
        }

        public static Map<String, BeanFieldValueGetter> mapperGeterr(Class<?> clazz) {
            Map<String, BeanFieldValueGetter> getter = map.get(clazz);
            if (null == getter) {
                synchronized (map) { // 如果没查到，就生成，进行双次判断，防止重复生成
                    if (null == (getter = map.get(clazz))) {
                        getter = createMapper(clazz);
                        map.put(clazz, getter);
                    }
                }
            }
            return getter;
        }

        private static Map<String, BeanFieldValueGetter> createMapper(Class<?> clazz) {
            if (Collection.class.isAssignableFrom(clazz) || Map.class.isAssignableFrom(clazz)) {
                throw new BeanProcessException("不支持集合类型" + clazz.getName() + "，请检查传入的bean的类型");
            }
            if (BeanTools.isPrimitiveWrapperType(clazz)) {
                throw new BeanProcessException("不支持简单包装类型" + clazz.getName() + "，请检查传入的bean的类型");
            }
            Collection<Field> fields = ReflectionTools.getAllFieldsInSourceCodeOrder(clazz, null);
            Map<String, BeanFieldValueGetter> setters = new HashMap<>(fields.size());
            if (fields.isEmpty()) { // 空类（即没有任何字段的类）是不允许的，会抛出异常
                throw new BeanProcessException("类" + clazz.getName() + "不能是空的，必须至少要有一个field");
            }
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    continue; // 不考虑static的或final的
                }
                String name = field.getName();
                if (BeanTools.isNotPrimitiveWrapperType(field.getType())) {
                    throw new BeanProcessException("类" + clazz.getName() + "的字段" + field.getName() + "只能是在" + BeanTools.class.getName() + "中定义的简单包装类型");
                }
                setters.put(name, BeanFieldValueGetter.create(clazz, name, field.getClass()));
            }
            return setters;
        }
    }
}
