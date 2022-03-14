package org.etnaframework.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import com.alibaba.fastjson.JSONObject;

/**
 * 根据key获取对应的基本型的value，支持默认值和异常处理
 *
 * @author BlackCat
 * @since 2015-02-13
 */
public interface KeyValueGetter {

    /**
     * 获取key对应的value字符串
     */
    String getString(String key);

    /**
     * 获取key对应的value字符串，当value不存在时返回defaultValue
     */
    String getString(String key, String defaultValue);

    /**
     * 获取key对应的value并转换为{@link Boolean}
     *
     * @return 字符串为true/y/1时都返回true
     */
    Boolean getBool(String key);

    /**
     * 获取key对应的value并转换为{@link Boolean}，当value不存在或转换失败时返回defaultValue
     *
     * @return 字符串为true/y/1时都返回true
     */
    Boolean getBool(String key, Boolean defaultValue);

    /**
     * 获取key对应的value并转换为{@link Byte}
     */
    Byte getByte(String key);

    /**
     * 获取key对应的value并转换为{@link Byte}，当value不存在或转换失败时返回defaultValue
     */
    Byte getByte(String key, Byte defaultValue);

    /**
     * 获取key对应的value并转换为{@link Character}
     */
    Character getChar(String key);

    /**
     * 获取key对应的value并转换为{@link Character}，默认取value字符串的第1个字符，当value不存在或转换失败时返回defaultValue
     */
    Character getChar(String key, Character defaultValue);

    /**
     * 获取key对应的value并转换为{@link Short}
     */
    Short getShort(String key);

    /**
     * 获取key对应的value并转换为{@link Short}，当value不存在或转换失败时返回defaultValue
     */
    Short getShort(String key, Short defaultValue);

    /**
     * 获取key对应的value并转换为{@link Integer}
     */
    Integer getInt(String key);

    /**
     * 获取key对应的value并转换为{@link Integer}，当value不存在或转换失败时返回defaultValue
     */
    Integer getInt(String key, Integer defaultValue);

    /**
     * 获取key对应的value并转换为{@link Long}
     */
    Long getLong(String key);

    /**
     * 获取key对应的value并转换为{@link Long}，当value不存在或转换失败时返回defaultValue
     */
    Long getLong(String key, Long defaultValue);

    /**
     * 获取key对应的value并转换为{@link Float}
     */
    Float getFloat(String key);

    /**
     * 获取key对应的value并转换为{@link Float}，当value不存在或转换失败时返回defaultValue
     */
    Float getFloat(String key, Float defaultValue);

    /**
     * 获取key对应的value并转换为{@link Double}
     */
    Double getDouble(String key);

    /**
     * 获取key对应的value并转换为{@link Double}，当value不存在或转换失败时返回defaultValue
     */
    Double getDouble(String key, Double defaultValue);

    /**
     * <pre>
     * 如果key对应的value是{@link Date}的实例或其派生类的实例，就将其转化为{@link Datetime}返回
     * 如果对应的是字符串，就使用{@link DatetimeUtils}配置的默认时间格式将其转化返回
     * </pre>
     */
    Datetime getDate(String key);

    /**
     * <pre>
     * 如果key对应的value是{@link Date}的实例或其派生类的实例，就将其转化为{@link Datetime}返回
     * 如果对应的是字符串，就使用{@link DatetimeUtils}配置的默认时间格式将其转化返回
     *
     * 如果value为null或转换失败，返回defaultValue
     * </pre>
     */
    Datetime getDate(String key, Datetime defaultValue);

    /**
     * 获取key对应的value并按照给定的格式解析并转换为{@link Datetime}
     */
    Datetime getDate(String key, String dateFormat);

    /**
     * 获取key对应的value并按照给定的格式解析并转换为{@link Datetime}，当value不存在或转换失败时返回defaultValue
     */
    Datetime getDate(String key, String dateFormat, Datetime defaultValue);

    /**
     * 被包装增强的Map，可以用指定的类型来get其中的值
     */
    abstract class WrappedMap<K, V> implements Map<K, V>, KeyValueGetter {

        private static final WrappedMap<Object, Object> EMPTY_MAP = new WrappedMap<Object, Object>() {

            @Override
            public int size() {
                return 0;
            }

            @Override
            public boolean isEmpty() {
                return true;
            }

            @Override
            public boolean containsKey(Object key) {
                return false;
            }

            @Override
            public boolean containsValue(Object value) {
                return false;
            }

            @Override
            public Object get(Object key) {
                return null;
            }

            @Override
            public Object put(Object key, Object value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Object remove(Object key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void putAll(Map<? extends Object, ? extends Object> m) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<Object> keySet() {
                return Collections.emptySet();
            }

            @Override
            public Collection<Object> values() {
                return Collections.emptySet();
            }

            @Override
            public Set<Map.Entry<Object, Object>> entrySet() {
                return Collections.emptySet();
            }

            @Override
            public int hashCode() {
                return 0;
            }

            @Override
            public boolean equals(Object o) {
                return (o instanceof Map) && ((Map<?, ?>) o).isEmpty();
            }

            @Override
            public String getString(String key) {
                return null;
            }
        };

        protected Map<K, V> inner;

        public WrappedMap() {
            inner = new LinkedHashMap<>();
        }

        public WrappedMap(int size) {
            inner = new LinkedHashMap<>(size);
        }

        /**
         * 返回一个空的没有数据的WrappedMap，且不允许往里面写入内容
         */
        @SuppressWarnings("unchecked")
        public static final <K, V> WrappedMap<K, V> emptyMap() {
            return (WrappedMap<K, V>) EMPTY_MAP;
        }

        @Override
        public int size() {
            return inner.size();
        }

        @Override
        public boolean isEmpty() {
            return inner.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return inner.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return inner.containsValue(value);
        }

        @Override
        public V get(Object key) {
            return inner.get(key);
        }

        @Override
        public V put(K key, V value) {
            return inner.put(key, value);
        }

        @Override
        public V remove(Object key) {
            return inner.remove(key);
        }

        @Override
        public void putAll(Map<? extends K, ? extends V> m) {
            inner.putAll(m);
        }

        @Override
        public void clear() {
            inner.clear();
        }

        @Override
        public Set<K> keySet() {
            return inner.keySet();
        }

        @Override
        public Collection<V> values() {
            return inner.values();
        }

        @Override
        public Set<java.util.Map.Entry<K, V>> entrySet() {
            return inner.entrySet();
        }

        @Override
        public int hashCode() {
            return inner.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return inner.equals(o);
        }

        @Override
        public String toString() {
            return inner.toString();
        }

        /**
         * 转换为对应的类型是通过值字符串进行的，需要指定这个值字符串是如何获取到的
         */
        @Override
        public abstract String getString(String key);

        @Override
        public String getString(String key, String defaultValue) {
            String value = getString(key);
            return null == value ? defaultValue : value;
        }

        @Override
        public Boolean getBool(String key) {
            return StringTools.getBool(getString(key), null);
        }

        @Override
        public Boolean getBool(String key, Boolean defaultValue) {
            return StringTools.getBool(getString(key), defaultValue);
        }

        @Override
        public Byte getByte(String key) {
            return StringTools.getByte(getString(key), null);
        }

        @Override
        public Byte getByte(String key, Byte defaultValue) {
            return StringTools.getByte(getString(key), defaultValue);
        }

        @Override
        public Character getChar(String key) {
            return StringTools.getChar(getString(key), null);
        }

        @Override
        public Character getChar(String key, Character defaultValue) {
            return StringTools.getChar(getString(key), defaultValue);
        }

        @Override
        public Short getShort(String key) {
            return StringTools.getShort(getString(key), null);
        }

        @Override
        public Short getShort(String key, Short defaultValue) {
            return StringTools.getShort(getString(key), defaultValue);
        }

        @Override
        public Integer getInt(String key) {
            return StringTools.getInt(getString(key), null);
        }

        @Override
        public Integer getInt(String key, Integer defaultValue) {
            return StringTools.getInt(getString(key), defaultValue);
        }

        @Override
        public Long getLong(String key) {
            return StringTools.getLong(getString(key), null);
        }

        @Override
        public Long getLong(String key, Long defaultValue) {
            return StringTools.getLong(getString(key), defaultValue);
        }

        @Override
        public Float getFloat(String key) {
            return StringTools.getFloat(getString(key), null);
        }

        @Override
        public Float getFloat(String key, Float defaultValue) {
            return StringTools.getFloat(getString(key), defaultValue);
        }

        @Override
        public Double getDouble(String key) {
            return StringTools.getDouble(getString(key), null);
        }

        @Override
        public Double getDouble(String key, Double defaultValue) {
            return StringTools.getDouble(getString(key), defaultValue);
        }

        @Override
        public Datetime getDate(String key) {
            V obj = get(key);
            if (obj instanceof Datetime) {
                return (Datetime) obj;
            } else if (obj instanceof Date) {
                return new Datetime((Date) obj);
            }
            return getDate(key, DatetimeUtils.getDefaultDatetimeFormat());
        }

        @Override
        public Datetime getDate(String key, Datetime defaultValue) {
            Datetime date = getDate(key);
            return null == date ? defaultValue : date;
        }

        @Override
        public Datetime getDate(String key, String dateFormat) {
            String value = getString(key);
            if (StringTools.isEmpty(value)) {
                return null;
            }
            Datetime date = DatetimeUtils.parse(value, dateFormat);
            return null == date ? null : date;
        }

        @Override
        public Datetime getDate(String key, String dateFormat, Datetime defaultValue) {
            String value = getString(key);
            if (StringTools.isEmpty(value)) {
                return defaultValue;
            }
            Datetime date = DatetimeUtils.parse(value, dateFormat);
            return null == date ? defaultValue : date;
        }

        /**
         * 获取二进制内容，如果对应对象不是byte[]将返回null
         */
        public byte[] getBin(String key) {
            V obj = get(key);
            if (obj instanceof byte[]) {
                return (byte[]) obj;
            }
            return null;
        }

        public byte[] getBin(String key, byte[] defaultValue) {
            byte[] bin = getBin(key);
            return null == bin ? defaultValue : bin;
        }
    }

    /**
     * 一般的<String,Object>类型的WrappedMap实现，用于数据绑定（DataBind）
     */
    class DbMap extends WrappedMap<String, Object> {

        public DbMap() {
        }

        public DbMap(int size) {
            super(size);
        }

        public DbMap(Map<?, ?> map) {
            super(map.size());
            for (Entry<?, ?> e : map.entrySet()) {
                inner.put(String.valueOf(e.getKey()), e.getValue());
            }
        }

        /**
         * 初始化时，按key1,value1,key2,value2的顺序传入构造Map
         */
        public DbMap(Object... keyValue) {
            super(keyValue.length);
            if (keyValue.length % 2 != 0) {
                throw new IllegalArgumentException("keyValue.length is invalid: " + keyValue.length);
            }
            for (int i = 0; i < keyValue.length; i++) {
                put(String.valueOf(keyValue[i++]), keyValue[i]);
            }
        }

        /**
         * 另一种初始化方式，和调用构造方法效果相同
         */
        public static DbMap build(Object... keyValue) {
            return new DbMap(keyValue);
        }

        /**
         * 构造器方式添加内容
         */
        public DbMap append(String key, Object value) {
            put(key, value);
            return this;
        }

        /**
         * 构造器方式添加内容
         */
        public DbMap append(Map<String, Object> data) {
            putAll(data);
            return this;
        }

        public <T> T get(String key, Class<T> clazz) {
            if (BeanTools.isPrimitiveWrapperType(clazz)) {
                throw new IllegalArgumentException("简单包装类型" + clazz.getName() + "请直接使用getXXX方法获取值");
            }
            Object value = get(key);
            if (null == value) {
                return null;
            }
            if (clazz == value.getClass() || clazz.isAssignableFrom(value.getClass())) {
                return clazz.cast(value);
            }
            if (value instanceof Map) {
                return JsonObjectUtils.parseJson((Map<?, ?>) value, clazz);
            }
            return JsonObjectUtils.parseJson(JsonObjectUtils.createJson(value), clazz);
        }

        public <T> List<T> getList(String key, Class<T> clazz) {
            Object value = get(key);
            if (null == value) {
                return null;
            }
            return JsonObjectUtils.parseJsonArray(JsonObjectUtils.createJson(value), clazz);
        }

        public DbMap getDbMap(String key) {
            Object value = get(key);
            if (null == value) {
                return null;
            }
            if (value instanceof DbMap) {
                return (DbMap) value;
            }
            if (value instanceof Map) {
                return new DbMap((Map<?, ?>) value);
            }
            JSONObject jso = JsonObjectUtils.parseJson(JsonObjectUtils.createJson(value));
            return null == jso ? null : new DbMap(jso);
        }

        public List<DbMap> getDbMapList(String key) {
            Object value = get(key);
            if (null == value) {
                return null;
            }
            if (value instanceof Collection) {
                Collection<?> list = (Collection<?>) value;
                List<DbMap> result = new ArrayList<>(list.size());
                for (Object o : list) {
                    if (o instanceof Map) {
                        result.add(new DbMap((Map<?, ?>) o));
                    } else {
                        JSONObject jso = JsonObjectUtils.parseJson(value.toString());
                        if (null != jso) {
                            result.add(new DbMap(jso));
                        }
                    }
                }
                return result;
            }
            return null;
        }

        @Override
        public String getString(String key) {
            Object value = get(key);
            return null == value ? null : value.toString();
        }
    }
}
