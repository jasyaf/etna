package org.etnaframework.core.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 集合操作工具类
 *
 * @author BlackCat
 * @since 2012-10-11
 */
@SuppressWarnings({
    "rawtypes",
    "unchecked"
})
public class CollectionTools {

    private static Random r = new Random();

    private static byte[] byteArray = new byte[0];

    private static char[] charArray = new char[0];

    private static boolean[] booleanArray = new boolean[0];

    private static short[] shortArray = new short[0];

    private static int[] intArray = new int[0];

    private static long[] longArray = new long[0];

    private static float[] floatArray = new float[0];

    private static double[] doubleArray = new double[0];

    private static String[] stringArray = new String[0];

    /**
     * 批量添加元素到集合中，不检查添加的具体类型，相比Collections.addAll方法不会输出警告，如果传入的有null将不会添加到结果中
     */
    public static <T> boolean addAll(Collection c, Object... objs) {
        boolean result = false;
        for (Object obj : objs) {
            if (null != obj) {
                result |= c.add(obj);
            }
        }
        return result;
    }

    /**
     * 生成一个由指定元素组成的List，如果传入null将会返回空的List（此List允许用户后续自行添加元素）
     */
    public static <T> List<T> buildList(T... ts) {
        if (null == ts) {
            return new ArrayList<>(0);
        }
        List<T> list = new ArrayList<>(ts.length);
        addAll(list, ts);
        return list;
    }

    /**
     * 生成一个由指定元素组成的Set，如果传入null将会返回空的Set（此Set允许用户后续自行添加元素）
     */
    public static <T> Set<T> buildSet(T... ts) {
        if (null == ts) {
            return new HashSet<>(0);
        }
        Set<T> set = new HashSet<>(ts.length * 2);
        addAll(set, ts);
        return set;
    }

    /**
     * 从传入的列表中随机抽取1个返回，如果传入空列表将会返回null
     */
    public static <T> T getRandomOne(List<T> source) {
        if (isEmpty(source)) {
            return null;
        }
        int index = r.nextInt(source.size());
        return source.get(index);
    }

    /**
     * 从传入的数组中随机抽取1个返回，如果传入空列表将会返回null
     */
    public static <T> T getRandomOne(T[] source) {
        if (isEmpty(source)) {
            return null;
        }
        int index = r.nextInt(source.length);
        return source[index];
    }

    /**
     * 从传入的列表中随机抽取limit个返回，如果传入的列表小于limit则直接返回原列表的数据（顺序打乱）
     *
     * @param limit 随机抽取的数量
     *
     * @return 不会返回null，没有数据时会返回一个空列表
     */
    public static <T> List<T> getRandomList(List<T> source, int limit) {
        if (isEmpty(source)) {
            return new ArrayList<>(0);
        }
        if (source.size() <= limit) {
            List<T> result = new ArrayList<>(source);
            Collections.shuffle(result);
            return result;
        }
        List<T> result = new ArrayList<>(limit);
        BitSet bs = new BitSet(source.size());
        for (int i = 0; i < limit; i++) {
            int index;
            do {
                index = r.nextInt(source.size());
            } while (bs.get(index));
            bs.set(index);
            result.add(source.get(index));
        }
        return result;
    }

    /**
     * 从传入的数组中随机抽取limit个返回，如果传入的列表小于limit则直接返回原数组的数据（顺序打乱）
     *
     * @param limit 随机抽取的数量
     *
     * @return 不会返回null，没有数据时会返回一个空列表
     */
    public static <T> List<T> getRandomList(T[] source, int limit) {
        if (isEmpty(source)) {
            return new ArrayList<>(0);
        }
        if (source.length <= limit) {
            List<T> result = buildList(source);
            Collections.shuffle(result);
            return result;
        }
        List<T> result = new ArrayList<>(limit);
        BitSet bs = new BitSet(source.length);
        for (int i = 0; i < limit; i++) {
            int index;
            do {
                index = r.nextInt(source.length);
            } while (bs.get(index));
            bs.set(index);
            result.add(source[index]);
        }
        return result;
    }

    /**
     * 获取空的数组引用
     */
    public static byte[] emptyByteArray() {
        return byteArray;
    }

    /**
     * 获取空的数组引用
     */
    public static char[] emptyCharArray() {
        return charArray;
    }

    /**
     * 获取空的数组引用
     */
    public static boolean[] emptyBooleanArray() {
        return booleanArray;
    }

    /**
     * 获取空的数组引用
     */
    public static short[] emptyShortArray() {
        return shortArray;
    }

    /**
     * 获取空的数组引用
     */
    public static int[] emptyIntArray() {
        return intArray;
    }

    /**
     * 获取空的数组引用
     */
    public static long[] emptyLongArray() {
        return longArray;
    }

    /**
     * 获取空的数组引用
     */
    public static float[] emptyFloatArray() {
        return floatArray;
    }

    /**
     * 获取空的数组引用
     */
    public static double[] emptyDoubleArray() {
        return doubleArray;
    }

    /**
     * 获取空的数组引用
     */
    public static String[] emptyStringArray() {
        return stringArray;
    }

    /**
     * 判断boolean类型的数组是否为空
     */
    public static boolean isEmpty(boolean[] array) {
        return null == array || array.length == 0;
    }

    /**
     * 判断byte类型的数组是否为空
     */
    public static boolean isEmpty(byte[] array) {
        return null == array || array.length == 0;
    }

    /**
     * 判断char类型的数组是否为空
     */
    public static boolean isEmpty(char[] array) {
        return null == array || array.length == 0;
    }

    /**
     * short类型的数组是否为空
     */
    public static boolean isEmpty(short[] array) {
        return null == array || array.length == 0;
    }

    /**
     * 判断int类型的数组是否为空
     */
    public static boolean isEmpty(int[] array) {
        return null == array || array.length == 0;
    }

    /**
     * 判断long类型的数组是否为空
     */
    public static boolean isEmpty(long[] array) {
        return null == array || array.length == 0;
    }

    /**
     * 判断float类型的数组是否为空
     */
    public static boolean isEmpty(float[] array) {
        return null == array || array.length == 0;
    }

    /**
     * 判断double类型的数组是否为空
     */
    public static boolean isEmpty(double[] array) {
        return null == array || array.length == 0;
    }

    /**
     * 判断数组类型的容器是否为空
     */
    public static <T> boolean isEmpty(T[] array) {
        return null == array || array.length == 0;
    }

    /**
     * 判断{@link Collection}类型的容器是否为空
     */
    public static <T> boolean isEmpty(Collection<T> coll) {
        return null == coll || coll.isEmpty();
    }

    /**
     * 判断{@link Map}类型的容器是否为空
     */
    public static <K, V> boolean isEmpty(Map<K, V> map) {
        return null == map || map.isEmpty();
    }

    /**
     * 判断{@link Iterator}类型的容器是否为空
     */
    public static <T> boolean isEmpty(Iterator<T> it) {
        return null == it || it.hasNext();
    }

    /**
     * 判断{@link Enumeration}类型的容器是否为空
     */
    public static <T> boolean isEmpty(Enumeration<T> e) {
        return null == e || e.hasMoreElements();
    }

    /**
     * 判断boolean类型的数组是否不为空
     */
    public static boolean isNotEmpty(boolean[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断byte类型的数组是否不为空
     */
    public static boolean isNotEmpty(byte[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断char类型的数组是否不为空
     */
    public static boolean isNotEmpty(char[] array) {
        return !isEmpty(array);
    }

    /**
     * short类型的数组是否不为空
     */
    public static boolean isNotEmpty(short[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断int类型的数组是否不为空
     */
    public static boolean isNotEmpty(int[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断long类型的数组是否不为空
     */
    public static boolean isNotEmpty(long[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断float类型的数组是否不为空
     */
    public static boolean isNotEmpty(float[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断double类型的数组是否不为空
     */
    public static boolean isNotEmpty(double[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断数组类型的容器是否不为空
     */
    public static <T> boolean isNotEmpty(T[] array) {
        return !isEmpty(array);
    }

    /**
     * 判断{@link Collection}类型的容器是否不为空
     */
    public static <T> boolean isNotEmpty(Collection<T> coll) {
        return !isEmpty(coll);
    }

    /**
     * 判断{@link Map}类型的容器是否不为空
     */
    public static <K, V> boolean isNotEmpty(Map<K, V> map) {
        return !isEmpty(map);
    }

    /**
     * 判断{@link Iterator}类型的容器是否不为空
     */
    public static <T> boolean isNotEmpty(Iterator<T> it) {
        return !isEmpty(it);
    }

    /**
     * 判断{@link Enumeration}类型的容器是否不为空
     */
    public static <T> boolean isNotEmpty(Enumeration<T> e) {
        return !isEmpty(e);
    }

    /**
     * 获取{@link Collection}的大小，如果是null则返回0
     */
    public static int size(Collection collection) {
        return null == collection ? 0 : collection.size();
    }
}
