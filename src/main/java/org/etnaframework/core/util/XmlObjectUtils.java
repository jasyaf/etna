package org.etnaframework.core.util;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.util.KeyValueGetter.DbMap;
import org.slf4j.Logger;
import org.springframework.core.ResolvableType;
import org.xml.sax.SAXException;
import com.google.common.collect.Maps;

/**
 * <pre>
 * XML转换为JAVABEAN
 *
 * feature:
 * 1. 支持xml转JAVABEAN
 * 2. 支持数组类型
 * 3. 支持@XmlDecoderIgnore标注成员变量跳过解析.
 * 4. String的成员变量会默认赋值""
 *
 * bug:
 * 1. 不支持Bean里容器中的容器,最多一层容器
 * 2. 在未完全充分测试前,抓到了exception会以runtimeException方式抛出.
 * 3. 使用了递归遍历成员变量注入,收敛不好可能会死循环.只适用于简单JAVABEAN
 * 4. 成员变量是Map时,Key只能是基本类型,一般是String,否则会不处理.
 *
 * alert:
 * 1. 任何嵌套在内的javabean必须要有默认的构造方法,否则会实例化出错!
 * 2. XXE漏洞
 *     @link https://security.tencent.com/index.php/blog/msg/69
 *     @link https://blog.csdn.net/u013224189/article/details/49759845
 *
 * @author YuanHaoliang
 * @since 2014-5-28
 */
public class XmlObjectUtils {

    private static final Logger log = Log.getLogger();

    private XmlObjectUtils() { // 无需实例化,直接调用静态方法使用即可.
    }

    /**
     * 微信支付XML解析漏洞问题
     * https://pay.weixin.qq.com/wiki/doc/api/jsapi.php?chapter=23_5
     *
     * 为防止此安全问题，按照微信官方的指引，针对使用的SAXReader做了特别处理
     */
    private static SAXReader _getReader() throws SAXException {

        SAXReader reader = new SAXReader();

        // This is the PRIMARY defense. If DTDs (doctypes) are disallowed, almost all XML entity attacks are prevented
        // Xerces 2 only - http://xerces.apache.org/xerces2-j/features.html#disallow-doctype-decl
        // 不允许DTDs (doctypes) ,几乎可以阻止所有的XML实体攻击
        String FEATURE = "http://apache.org/xml/features/disallow-doctype-decl";
        reader.setFeature(FEATURE, true);

        // If you can't completely disable DTDs, then at least do the following:
        // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-general-entities
        // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-general-entities
        // JDK7+ - http://xml.org/sax/features/external-general-entities
        FEATURE = "http://xml.org/sax/features/external-general-entities";
        reader.setFeature(FEATURE, false);

        // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-parameter-entities
        // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-parameter-entities
        // JDK7+ - http://xml.org/sax/features/external-parameter-entities
        FEATURE = "http://xml.org/sax/features/external-parameter-entities";
        reader.setFeature(FEATURE, false);

        return reader;
    }

    /**
     * 解析XML并封装为{@link DbMap}，如果失败返回null
     */
    public static DbMap parse(String xmlString) {
        if (StringTools.isEmpty(xmlString)) {
            return null;
        }
        try {
            SAXReader reader = _getReader();
            Document doc = reader.read(new StringReader(xmlString));
            Element root = doc.getRootElement();

            DbMap result = new DbMap();
            for (Object o : root.elements()) {
                // 目前只做对简单bean的解析
                Element e = (Element) o;
                result.put(e.getQualifiedName(), e.getStringValue());
            }
            return result;
        } catch (DocumentException | SAXException e) {
            return null;
        }
    }

    /**
     * 解析XML并封装指定类型的javaBean
     */
    public static <T> T parse(String xmlString, Class<T> clazz) {
        if (StringTools.isEmpty(xmlString)) {
            return null;
        }

        try {
            SAXReader reader = _getReader();
            Document doc = reader.read(new StringReader(xmlString));
            Element root = doc.getRootElement();

            T instance = clazz.newInstance(); // JAVABEAN必须要有默认的构造方法
            decode(instance, root);

            return instance;
        } catch (DocumentException | InstantiationException | IllegalAccessException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private static void decode(Object instance, Element e) {

        // 获取bean的成员变量列表
        Field[] fields = instance.getClass()
                                 .getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);

            if (field.getAnnotation(XmlDecoderIgnore.class) != null) {// 带@XmlDecoderIgnore忽略
                continue;
            }

            // 查找当前节点下是否有该成员变量的值
            Element ee = e.element(field.getName());
            if (ee != null) {

                // 获取成员变量的类型
                Class<?> type = field.getType();
                if (BeanTools.isPrimitiveWrapperType(type)) { // 判断是否为基本类型

                    try {
                        String value = ee.getTextTrim();
                        if (StringTools.isNotEmpty(value)) {// 如果有值的话.
                            field.set(instance, StringTools.valueOf(value, type));
                        } else if (type.equals(String.class)) {// 如果是String,默认赋值空字符串""
                            field.set(instance, "");
                        }
                    } catch (Exception e2) {
                        throw new RuntimeException(e2);
                    }
                } else { // 非基本类型

                    if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type) || type.isArray()) {
                        // 如果是集合类型的,特别处理.

                        Object collection = decodeCollections(field.getType(), field.getGenericType(), ee);

                        try {
                            // 把集合类型的实例注入
                            field.set(instance, collection);
                        } catch (IllegalArgumentException | IllegalAccessException e1) {
                            throw new RuntimeException(e1);
                        }
                    } else {// 否则就是一个普通javabean,直接实例化之

                        try {
                            // 注意:子bean必须要有默认构造方法
                            Object elementBean = type.newInstance();
                            field.set(instance, elementBean);
                            decode(elementBean, ee);// 递归反射注入(注意收敛!!)
                        } catch (Exception e3) {
                            throw new RuntimeException(e3);
                        }
                    }
                }
            }
        }
    }

    /**
     * 解析集合类型的BEAN
     *
     * @param type 具体的集合类
     * @param genericType 集合的泛型类
     * @param e XML节点
     *
     * @return 处理好的集合实例
     */
    private static Object decodeCollections(Class<?> type, Type genericType, Element e) {

        try {

            if (Collection.class.isAssignableFrom(type) || type.isArray()) {// 数组/List/Set

                // 确定要使用的集合类
                Class<?> collectionType = type.isArray() ? ArrayList.class : (isFieldTypeAbstract(type) ? Set.class.isAssignableFrom(type) ? HashSet.class : ArrayList.class : type);

                // 集合实例化
                Object instance = collectionType.newInstance();

                // 给集合添加元素的方法
                Method addMethod = collectionType.getMethod("add", Object.class);

                Type clz;// 子类型

                if (type.isArray()) {// 如果是判断类型数组,则从 type.getComponentType() 获取子类型
                    clz = type.getComponentType();// 数组的具体类型
                } else {// 集合的子类型就是泛型指定的类,由于List/Set只有一个.所以只需取数组0

                    ResolvableType resolve = ResolvableType.forType(genericType);
                    ResolvableType t = resolve.getGeneric(0);
                    clz = t.getType();

                    // ParameterizedTypeImpl pt = (ParameterizedTypeImpl) genericType;// 获取泛型类型pt.getActualTypeArguments()[0]
                    // clz = pt.getActualTypeArguments()[0];
                }

                if (BeanTools.isPrimitiveWrapperType(clz)) { // 如果是基本类型,直接注入

                    for (Object node : e.elements()) {
                        Object o = StringTools.valueOf(((Node) node).getText(), clz);
                        addMethod.invoke(instance, o);
                    }
                } else {// 否则就更复杂了,是一个JavaBean.不支持容器中的容器!!!!

                    if (clz instanceof Class) {

                        Class<?> clzz = (Class<?>) clz;

                        for (Object node : e.elements()) { // 每一个节点为一个JAVABEAN
                            if (node instanceof Element) {

                                Element ee = (Element) node;
                                Object obj = clzz.newInstance();

                                addMethod.invoke(instance, obj);

                                decode(obj, ee);// 递归遍历属性
                            }
                        }
                    }
                }

                if (type.isArray()) {// 如果集合类型是普通数组,需要做转化才能赋值,否则会报类型不匹配

                    @SuppressWarnings("rawtypes") ArrayList list = ((ArrayList) instance);// 如果是普通数组,上面处理用的是ArrayList,安全转换

                    // 基本类型的数组单独转换.
                    if (clz.equals(int.class)) {

                        return intArrayConvert(list.toArray(), list.size());
                    } else if (clz.equals(long.class)) {

                        return longArrayConvert(list.toArray(), list.size());
                    } else if (clz.equals(byte.class)) {

                        return byteArrayConvert(list.toArray(), list.size());
                    } else if (clz.equals(short.class)) {

                        return shortArrayConvert(list.toArray(), list.size());
                    } else if (clz.equals(float.class)) {

                        return floatArrayConvert(list.toArray(), list.size());
                    } else if (clz.equals(double.class)) {

                        return doubleArrayConvert(list.toArray(), list.size());
                    } else if (clz.equals(boolean.class)) {

                        return booleanArrayConvert(list.toArray(), list.size());
                    } else if (clz.equals(char.class)) {

                        return charArrayConvert(list.toArray(), list.size());
                    }

                    // 默认是obj数组转换
                    return objectArrayConvert(list.toArray(), list.size(), type.getComponentType());
                }

                return instance;
            } else if (Map.class.isAssignableFrom(type)) {// Map, Key必须是基本类型!一般就只能是String了.

                Class<?> collectionType = isFieldTypeAbstract(type) ? HashMap.class : type;

                Object instance = collectionType.newInstance();
                Method putMethod = collectionType.getMethod("put", Object.class, Object.class); // 给集合添加元素的方法

                // ParameterizedTypeImpl pt = (ParameterizedTypeImpl) genericType;// 获取泛型类型pt.getActualTypeArguments()[0,1]
                //
                // Type keyType = pt.getActualTypeArguments()[0];
                // Type valueType = pt.getActualTypeArguments()[1];

                ResolvableType resolve = ResolvableType.forType(genericType);
                Type keyType = resolve.getGeneric(0)
                                      .getType();
                Type valueType = resolve.getGeneric(1)
                                        .getType();

                if (!BeanTools.isPrimitiveWrapperType(keyType)) {// key不是基本类型,不处理.
                    return null;
                }

                for (Object o : e.elements()) { // 每一个节点,nodeName是key,nodeValue是value
                    Node node = (Node) o;
                    if (BeanTools.isPrimitiveWrapperType(valueType)) {

                        putMethod.invoke(instance, StringTools.valueOf(node.getName(), keyType), StringTools.valueOf(node.getText(), valueType));
                    } else {// JAVABEAN,不包含容器!

                        if (valueType instanceof Class && node instanceof Element) {
                            Class<?> clazz = (Class<?>) valueType;

                            Object bean = clazz.newInstance();
                            putMethod.invoke(instance, StringTools.valueOf(node.getName(), keyType), bean);

                            decode(bean, (Element) node);
                        }
                    }
                }
                return instance;
            }
        } catch (Exception e2) {
            throw new RuntimeException(e2);
        }
        return null;
    }

    // 下面是基本类型的数组转换
    private static int[] intArrayConvert(Object[] objs, int size) {
        int[] result = new int[size];
        for (int i = 0; i < size; i++) {
            result[i] = (int) objs[i];
        }
        return result;
    }

    private static long[] longArrayConvert(Object[] objs, int size) {
        long[] result = new long[size];
        for (int i = 0; i < size; i++) {
            result[i] = (long) objs[i];
        }
        return result;
    }

    private static byte[] byteArrayConvert(Object[] objs, int size) {
        byte[] result = new byte[size];
        for (int i = 0; i < size; i++) {
            result[i] = (byte) objs[i];
        }
        return result;
    }

    private static short[] shortArrayConvert(Object[] objs, int size) {
        short[] result = new short[size];
        for (int i = 0; i < size; i++) {
            result[i] = (short) objs[i];
        }
        return result;
    }

    private static float[] floatArrayConvert(Object[] objs, int size) {
        float[] result = new float[size];
        for (int i = 0; i < size; i++) {
            result[i] = (float) objs[i];
        }
        return result;
    }

    private static double[] doubleArrayConvert(Object[] objs, int size) {
        double[] result = new double[size];
        for (int i = 0; i < size; i++) {
            result[i] = (double) objs[i];
        }
        return result;
    }

    private static boolean[] booleanArrayConvert(Object[] objs, int size) {
        boolean[] result = new boolean[size];
        for (int i = 0; i < size; i++) {
            result[i] = (boolean) objs[i];
        }
        return result;
    }

    private static char[] charArrayConvert(Object[] objs, int size) {
        char[] result = new char[size];
        for (int i = 0; i < size; i++) {
            result[i] = (char) objs[i];
        }
        return result;
    }

    /**
     * OBJ转数组
     */
    private static <T> T[] objectArrayConvert(Object[] objs, int size, Class<T> componentType) {
        @SuppressWarnings("unchecked") T[] result = (T[]) Array.newInstance(componentType, size);
        System.arraycopy(objs, 0, result, 0, size);
        return result;
    }

    /**
     * 检查类是否为抽象类,抽象类不能直接实例化.
     */
    private static boolean isFieldTypeAbstract(Class<?> type) {
        int mod = type.getModifiers();
        return Modifier.isInterface(mod) || Modifier.isAbstract(mod);
    }

    /**
     * 将对象转化成XML格式,ROOT是Result
     *
     * @param data 待转换的对象
     */
    public static String createXml(Object data) {
        return createXml(data, "Result");
    }

    /**
     * 将对象转化成XML格式,ROOT可自定义 *
     *
     * @param data 待转换的对象
     */
    public static String createXml(Object data, String root) {
        try {
            Document doc = DocumentHelper.createDocument();
            Element r = doc.addElement(root);

            print(data, r);

            OutputFormat of = new OutputFormat();
            of.setSuppressDeclaration(true);// 不打印第一行的声明<?xml version="1.0" encoding="UTF-8"?>
            StringWriter s = new StringWriter();
            XMLWriter xmlWriter = new XMLWriter(s, of);
            try {
                xmlWriter.write(doc);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return doc.asXML();
        } catch (Exception e) {
            log.error("XML_ENCODE_ERROR:", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 对象转化成XML格式的实际方法
     *
     * @param object 待转换的对象
     * @param e 存放转换结果的引用
     */
    protected static void print(Object object, Element e) throws IOException {
        if (object == null) {
            e.addCDATA("null");
        } else if (object instanceof Boolean) {
            e.addCDATA(String.valueOf(object));
        } else if (object instanceof Number) {
            e.addCDATA(String.valueOf(object));
        } else if (object instanceof Class<?>) {
            e.addCDATA(((Class<?>) object).getName());// Class 序列化容易导致死循环
        } else if (object instanceof String) {
            e.addCDATA((String) object);
        } else if (object instanceof Character) {
            e.addCDATA(String.valueOf(object));
        } else if (object instanceof Map<?, ?>) {
            print((Map<?, ?>) object, e);
        } else if (object instanceof Object[]) {
            print((Object[]) object, e);
        } else if (object instanceof char[]) {
            print((char[]) object, e);
        } else if (object instanceof int[]) {
            print((int[]) object, e);
        } else if (object instanceof double[]) {
            print((double[]) object, e);
        } else if (object instanceof float[]) {
            print((float[]) object, e);
        } else if (object instanceof short[]) {
            print((short[]) object, e);
        } else if (object instanceof byte[]) {
            print((byte[]) object, e);
        } else if (object instanceof long[]) {
            print((long[]) object, e);
        } else if (object instanceof boolean[]) {
            print((boolean[]) object, e);
        } else if (object instanceof Iterator<?>) {
            print((Iterator<?>) object, e);
        } else if (object instanceof Enumeration<?>) {
            print((Enumeration<?>) object, e);
        } else if (object instanceof Collection<?>) {
            print(((Collection<?>) object), e);
        } else {
            printBean(object, e);
        }
    }

    /**
     * 将JavaBean转化成Lua
     *
     * @param object 待转换的JavaBean
     * @param e 存放转换结果的引用
     */
    protected static void printBean(Object object, Element e) throws IOException {
        BeanInfo info;
        try {
            // 是否有必要自己实现内省功能
            info = Introspector.getBeanInfo(object.getClass());// 为了使用内省中的 缓存,不能指定filanClass
            PropertyDescriptor[] props = info.getPropertyDescriptors();

            // TODO:把Filed缓存起来.
            // 支持public字段,但没get方法的字段输出.
            // 实现方法,通过Class.getFields获取所有的Public属性,然后放入map
            Map<String, Field> fieldMap = Maps.newLinkedHashMap();

            Field[] fields = object.getClass()
                                   .getFields();

            for (Field f : fields) {
                if (!Modifier.isStatic(f.getModifiers())) {// 如果是static的不输出.只输出实例字段
                    fieldMap.put(f.getName(), f);
                }
            }

            // 先用内省获得get方法输出值,并排除对应public字段.
            for (int i = 0; i < props.length; ++i) {
                PropertyDescriptor prop = props[i];
                if (!Class.class.equals(prop.getPropertyType())) {
                    Method accessor = prop.getReadMethod();

                    if (accessor != null) {
                        if (!accessor.isAccessible()) {
                            accessor.setAccessible(true);
                        }
                        Object value = accessor.invoke(object);

                        String name = prop.getName();
                        Element newElement = e.addElement(name);
                        print(value, newElement);

                        fieldMap.remove(name);// 移除已输出的字段
                    }
                }
            }

            // 最后遍历剩下的public字段输出.
            for (Entry<String, Field> entry : fieldMap.entrySet()) {
                Element newElement = e.addElement(entry.getKey());
                Object value = entry.getValue()
                                    .get(object);
                print(value, newElement);
            }
        } catch (IllegalAccessException | InvocationTargetException | IntrospectionException iae) {
            log.error("", iae);
        }
    }

    /**
     * 将map对象转化成XML
     *
     * @param map 待转换的Map对象
     * @param e 转换后存放结果的引用
     */
    protected static void print(Map<?, ?> map, Element e) throws IOException {
        for (Map.Entry<?, ?> entry : map.entrySet()) {

            Element newElement = e.addElement(String.valueOf(entry.getKey()));
            print(entry.getValue(), newElement);
        }
    }

    /**
     * 将对象数组转换成XML
     *
     * @param object 待转换的Object数组
     * @param e 转换后存放结果的引用
     */
    protected static void print(Object[] object, Element e) throws IOException {
        String nodeName = getNodeName(e.getName());
        for (Object o : object) {
            Element nodeElement = e.addElement(nodeName);
            print(o, nodeElement);
        }
    }

    protected static void print(int[] object, Element e) throws IOException {
        String nodeName = getNodeName(e.getName());
        for (Object o : object) {
            Element nodeElement = e.addElement(nodeName);
            print(o, nodeElement);
        }
    }

    protected static void print(char[] object, Element e) throws IOException {
        String nodeName = getNodeName(e.getName());
        for (Object o : object) {
            Element nodeElement = e.addElement(nodeName);
            print(o, nodeElement);
        }
    }

    protected static void print(byte[] object, Element e) throws IOException {
        String nodeName = getNodeName(e.getName());
        for (Object o : object) {
            Element nodeElement = e.addElement(nodeName);
            print(o, nodeElement);
        }
    }

    protected static void print(long[] object, Element e) throws IOException {
        String nodeName = getNodeName(e.getName());
        for (Object o : object) {
            Element nodeElement = e.addElement(nodeName);
            print(o, nodeElement);
        }
    }

    protected static void print(short[] object, Element e) throws IOException {
        String nodeName = getNodeName(e.getName());
        for (Object o : object) {
            Element nodeElement = e.addElement(nodeName);
            print(o, nodeElement);
        }
    }

    protected static void print(float[] object, Element e) throws IOException {
        String nodeName = getNodeName(e.getName());
        for (Object o : object) {
            Element nodeElement = e.addElement(nodeName);
            print(o, nodeElement);
        }
    }

    protected static void print(double[] object, Element e) throws IOException {
        String nodeName = getNodeName(e.getName());
        for (Object o : object) {
            Element nodeElement = e.addElement(nodeName);
            print(o, nodeElement);
        }
    }

    protected static void print(boolean[] object, Element e) throws IOException {
        String nodeName = getNodeName(e.getName());
        for (Object o : object) {
            Element nodeElement = e.addElement(nodeName);
            print(o, nodeElement);
        }
    }

    /**
     * 将容器中的对象转换成xml
     *
     * @param object 待转换的容器
     * @param e 转换后存放结果的引用
     */
    protected static void print(Collection<?> object, Element e) throws IOException {
        String nodeName = getNodeName(e.getName());
        for (Object o : object) {
            Element nodeElement = e.addElement(nodeName);
            print(o, nodeElement);
        }
    }

    /**
     * 将迭代器制定的若干个对象转换成XML
     *
     * @param it 待转换的Iterator对象
     * @param e 转换后存放结果的引用
     */
    protected static void print(Iterator<?> it, Element e) throws IOException {
        String nodeName = getNodeName(e.getName());
        while (it.hasNext()) {
            Element nodeElement = e.addElement(nodeName);
            print(it.next(), nodeElement);
        }
    }

    /**
     * 将枚举对象转换成XML
     *
     * @param it 待转换的枚举对象
     * @param e 转换后存放结果的引用
     */
    protected static void print(Enumeration<?> it, Element e) throws IOException {
        String nodeName = getNodeName(e.getName());
        while (it.hasMoreElements()) {
            Element nodeElement = e.addElement(nodeName);
            print(it.nextElement(), nodeElement);
        }
    }

    private static String getNodeName(String name) {
        //        return name + "Node";
        return "item";//微信返回需要用这个字段！！
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public static @interface XmlDecoderIgnore { // 忽略注入

    }
}
