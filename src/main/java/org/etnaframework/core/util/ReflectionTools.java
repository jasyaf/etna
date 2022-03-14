package org.etnaframework.core.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.FieldFilter;
import org.springframework.util.ReflectionUtils.MethodFilter;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;

/**
 * 提供Java反射相关的工具类
 *
 * @author BlackCat
 * @since 2013-12-8
 */
public class ReflectionTools {

    /**
     * 获取方法签名字符串，可用于唯一表示一个方法，包括类名、方法名、参数类型（由于java不允许仅返回值不同的，故不包含返回类型信息）
     */
    public static String getMethodSingature(Method m) {
        StringBuilder sb = new StringBuilder(m.getDeclaringClass().getName());
        sb.append(".").append(m.getName()).append('(');
        Class<?>[] params = m.getParameterTypes();
        for (int j = 0; j < params.length; j++) {
            sb.append(params[j].getName());
            if (j < (params.length - 1)) {
                sb.append(',');
            }
        }
        sb.append(')');
        return sb.toString();
    }

    /**
     * 列举指定类的所有的接口类列表，会进行去重处理
     */
    public static Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        Set<Class<?>> list = new LinkedHashSet<Class<?>>();
        for (Class<?> intf : clazz.getInterfaces()) {
            list.add(intf);
            if (intf.getInterfaces().length > 0) {
                list.addAll(getAllInterfaces(intf));
            }
        }
        return list;
    }

    /**
     * 根据传入的field获取其set方法名（布尔类型是is）
     */
    public static String getSetMethodNameOfField(Field field) {
        return "set" + StringTools.headLetterToUpperCase(field.getName());
    }

    /**
     * 根据传入的field获取其get方法名
     */
    public static String getGetMethodNameOfField(Field field) {
        if (boolean.class.equals(field.getType()) || Boolean.class.equals(field.getType())) {
            return "is" + StringTools.headLetterToUpperCase(field.getName());
        }
        return "get" + StringTools.headLetterToUpperCase(field.getName());
    }

    /**
     * 根据传入的getMethod获取其对应的field名，如果不能取得就会返回null
     */
    public static String getFieldNameFromGetMethod(Method method) {
        if (void.class.equals(method.getReturnType())) { // get方法必须要有返回值
            return null;
        }
        if (method.getParameterTypes().length > 0) { // get方法必须无参
            return null;
        }
        String name = method.getName();
        if (boolean.class.equals(method.getReturnType()) || Boolean.class.equals(method.getReturnType())) {
            if (name.length() > 2 && name.startsWith("is")) {
                return StringTools.headLetterToLowerCase(name.substring(2));
            }
        }
        if (name.length() > 3 && name.startsWith("get")) {
            return StringTools.headLetterToLowerCase(name.substring(3));
        }
        return null;
    }

    /**
     * 根据传入的setMethod获取其对应的field名，如果不能取得就会返回null
     */
    public static String getFieldNameFromSetMethod(Method method) {
        if (method.getParameterTypes().length != 1) { // set方法只能有一个参数
            return null;
        }
        String name = method.getName();
        if (name.length() > 3 && name.startsWith("set")) {
            return StringTools.headLetterToLowerCase(name.substring(3));
        }
        return null;
    }

    /**
     * 获取javassist对应的CtClass，如果获取失败返回null
     */
    public static CtClass getCtClass(Class<?> clazz) {
        try {
            ClassPool pool = ClassPool.getDefault();
            CtClass cc = pool.getOrNull(clazz.getName());
            if (null == cc) { // 按正常的流程来走，不应当会有获取不到的情况，但在某些容器中启动时由于是SystemClassLoader并不是应用的，导致加载不到这个class，所以需要手工添加一下
                pool.insertClassPath(new ClassClassPath(clazz));
                cc = pool.get(clazz.getName());
            }
            return cc;
        } catch (NotFoundException e) {
        }
        return null;
    }

    /**
     * 按源码中的顺序（注意：java的反射机制无法保证返回的顺序）返回类中的所有符合筛选条件的字段，包含所有非public的字段但不包括被继承的字段（返回内容和java反射的getDeclaredFields一样）
     *
     * @param clazz 需要被解析的类
     */
    public static Collection<Field> getDeclaredFieldsInSourceCodeOrder(Class<?> clazz) {
        return getDeclaredFieldsInSourceCodeOrder(clazz, null);
    }

    /**
     * 按源码中的顺序（注意：java的反射机制无法保证返回的顺序）返回类中的所有符合筛选条件的字段，包含所有非public的字段但不包括被继承的字段（返回内容和java反射的getDeclaredFields一样）
     *
     * @param clazz 需要被解析的类
     * @param filter 如果需要进行筛选，可指定筛选条件
     */
    public static Collection<Field> getDeclaredFieldsInSourceCodeOrder(Class<?> clazz, FieldFilter filter) {
        Map<String, Field> map = new LinkedHashMap<String, Field>();
        for (Field f : clazz.getDeclaredFields()) {
            if (null == filter || filter.matches(f)) {
                map.put(f.getName(), f); // 建立字段名和字段的映射
            }
        }
        Set<Field> result = new LinkedHashSet<Field>(map.size());
        try {
            CtClass cc = getCtClass(clazz);
            if (null != cc) {
                for (CtField cf : cc.getDeclaredFields()) {
                    Field me = map.get(cf.getName());
                    if (null != me) {
                        result.add(me);
                    }
                }
            } else {
                result.addAll(map.values());
            }
        } catch (Throwable ex) { // 上面的流程都走过了，此处应当不可能抛此异常的，实在有问题，就用默认的方法列表
            result.addAll(map.values());
        }
        return result;
    }

    /**
     * 按源码中的顺序（注意：java的反射机制无法保证返回的顺序）返回类中的所有符合筛选条件的字段，包含所有被继承的public字段（非public不会返回，返回内容和java反射的getFields一样）
     *
     * @param clazz 需要被解析的类
     */
    public static Collection<Field> getFieldsInSourceCodeOrder(Class<?> clazz) {
        return getFieldsInSourceCodeOrder(clazz, null);
    }

    /**
     * 按源码中的顺序（注意：java的反射机制无法保证返回的顺序）返回类中的所有符合筛选条件的字段，包含所有被继承的public字段（非public不会返回，返回内容和java反射的getFields一样）
     *
     * @param clazz 需要被解析的类
     * @param filter 如果需要进行筛选，可指定筛选条件
     */
    public static Collection<Field> getFieldsInSourceCodeOrder(Class<?> clazz, FieldFilter filter) {
        Map<String, Field> map = new LinkedHashMap<String, Field>();
        for (Field f : clazz.getFields()) {
            if (null == filter || filter.matches(f)) {
                map.put(f.getName(), f); // 建立字段名和字段的映射
            }
        }
        Set<Field> result = new LinkedHashSet<Field>(map.size());
        try {
            CtClass cc = getCtClass(clazz);
            if (null != cc) {
                for (CtField cf : cc.getFields()) {
                    Field me = map.get(cf.getName());
                    if (null != me) {
                        result.add(me);
                    }
                }
            } else {
                result.addAll(map.values());
            }
        } catch (Throwable ex) { // 上面的流程都走过了，此处应当不可能抛此异常的，实在有问题，就用默认的方法列表
            result.addAll(map.values());
        }
        return result;
    }

    /**
     * 按源码中的顺序（注意：java的反射机制无法保证返回的顺序）返回类中的所有符合筛选条件的字段，包含本类所有的字段以及父类中的所有字段，父类字段优先显示
     *
     * @param clazz 需要被解析的类
     */
    public static Collection<Field> getAllFieldsInSourceCodeOrder(Class<?> clazz) {
        return getAllFieldsInSourceCodeOrder(clazz, null);
    }

    /**
     * 按源码中的顺序（注意：java的反射机制无法保证返回的顺序）返回类中的所有符合筛选条件的字段，包含本类所有的字段以及父类中的所有字段，父类字段优先显示
     *
     * @param clazz 需要被解析的类
     * @param filter 如果需要进行筛选，可指定筛选条件
     */
    public static Collection<Field> getAllFieldsInSourceCodeOrder(Class<?> clazz, FieldFilter filter) {
        Map<String, Field> map = new LinkedHashMap<String, Field>();
        Class<?> targetClass = clazz;
        do {
            Field[] fields = targetClass.getDeclaredFields();
            for (Field f : fields) {
                if (null == filter || filter.matches(f)) {
                    map.put(f.getName(), f); // 建立字段名和字段的映射
                }
            }
            targetClass = targetClass.getSuperclass();
        } while (targetClass != null && targetClass != Object.class);
        Set<Field> result = new LinkedHashSet<Field>(map.size());
        try {
            CtClass cc = getCtClass(clazz);
            if (null != cc) {
                LinkedList<CtClass> list = new LinkedList<CtClass>();
                CtClass targetCtClass = cc;
                do {
                    list.addFirst(targetCtClass); // 把父类提到前面去处理，实现父类字段优先显示
                    targetCtClass = targetCtClass.getSuperclass();
                } while (targetCtClass != null && !targetCtClass.getName().equals(Object.class.getName()));
                for (CtClass c : list) {
                    CtField[] ctFields = c.getDeclaredFields();
                    for (CtField cf : ctFields) {
                        Field me = map.get(cf.getName());
                        if (null != me) {
                            result.add(me);
                        }
                    }
                }
            } else {
                result.addAll(map.values());
            }
        } catch (Throwable ex) { // 上面的流程都走过了，此处应当不可能抛此异常的，实在有问题，就用默认的方法列表
            result.addAll(map.values());
        }
        return result;
    }

    /**
     * 按源码中的顺序（注意：java的反射机制无法保证返回的顺序）返回类中的所有符合筛选条件的方法，包含所有非public方法但不包括被继承的方法
     *
     * @param clazz 需要被解析的类
     */
    public static Collection<Method> getDeclaredMethodsInSourceCodeOrder(Class<?> clazz) {
        return getDeclaredMethodsInSourceCodeOrder(clazz, null);
    }

    /**
     * 按源码中的顺序（注意：java的反射机制无法保证返回的顺序）返回类中的所有符合筛选条件的方法，包含所有非public方法但不包括被继承的方法
     *
     * @param clazz 需要被解析的类
     * @param filter 如果需要进行筛选，可指定筛选条件
     */
    public static Collection<Method> getDeclaredMethodsInSourceCodeOrder(Class<?> clazz, MethodFilter filter) {
        Map<String, Method> map = new LinkedHashMap<String, Method>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (null == filter || filter.matches(m)) {
                // 建立方法名和方法的映射，改编自Method.toString()
                StringBuilder sb = new StringBuilder();
                sb.append(m.getName()).append('(');
                Class<?>[] params = m.getParameterTypes();
                for (int j = 0; j < params.length; j++) {
                    sb.append(params[j].getName());
                    if (j < (params.length - 1)) {
                        sb.append(',');
                    }
                }
                sb.append(')');
                map.put(sb.toString(), m);
            }
        }
        Set<Method> result = new LinkedHashSet<Method>(map.size());
        try {
            CtClass cc = getCtClass(clazz);
            if (null != cc) {
                for (CtMethod cm : cc.getDeclaredMethods()) {
                    String key = cm.getName() + Descriptor.toString(cm.getSignature());
                    Method me = map.get(key);
                    if (null != me) {
                        result.add(me);
                    }
                }
            } else {
                result.addAll(map.values());
            }
        } catch (Throwable ex) { // 上面的流程都走过了，此处应当不可能抛此异常的，实在有问题，就用默认的方法列表
            result.addAll(map.values());
        }
        return result;
    }

    /**
     * 按源码中的顺序（注意：java的反射机制无法保证返回的顺序）返回类中的所有符合筛选条件的方法，包含所有被继承的public方法（非public不会返回）
     *
     * @param clazz 需要被解析的类
     */
    public static Collection<Method> getMethodsInSourceCodeOrder(Class<?> clazz) {
        return getMethodsInSourceCodeOrder(clazz, null);
    }

    /**
     * 按源码中的顺序（注意：java的反射机制无法保证返回的顺序）返回类中的所有符合筛选条件的方法，包含所有被继承的public方法（非public不会返回）
     *
     * @param clazz 需要被解析的类
     * @param filter 如果需要进行筛选，可指定筛选条件
     */
    public static Collection<Method> getMethodsInSourceCodeOrder(Class<?> clazz, MethodFilter filter) {
        Map<String, Method> map = new LinkedHashMap<String, Method>();
        for (Method m : clazz.getMethods()) {
            if (null == filter || filter.matches(m)) {
                // 建立方法名和方法的映射，改编自Method.toString()
                StringBuilder sb = new StringBuilder();
                sb.append(m.getName()).append('(');
                Class<?>[] params = m.getParameterTypes();
                for (int j = 0; j < params.length; j++) {
                    sb.append(params[j].getName());
                    if (j < (params.length - 1)) {
                        sb.append(',');
                    }
                }
                sb.append(')');
                map.put(sb.toString(), m);
            }
        }
        Set<Method> result = new LinkedHashSet<Method>(map.size());
        try {
            CtClass cc = getCtClass(clazz);
            if (null != cc) {
                for (CtMethod cm : cc.getMethods()) {
                    String key = cm.getName() + Descriptor.toString(cm.getSignature());
                    Method me = map.get(key);
                    if (null != me) {
                        result.add(me);
                    }
                }
            } else {
                result.addAll(map.values());
            }
        } catch (Throwable ex) { // 上面的流程都走过了，此处应当不可能抛此异常的，实在有问题，就用默认的方法列表
            result.addAll(map.values());
        }
        return result;
    }

    /**
     * 按源码中的顺序（注意：java的反射机制无法保证返回的顺序）返回类中的所有符合筛选条件的方法，包含本类所有的方法以及父类/接口中的所有方法，显示优先级 接口>父类>子类
     *
     * @param clazz 需要被解析的类
     */
    public static Collection<Method> getAllMethodsInSourceCodeOrder(Class<?> clazz) {
        return getAllMethodsInSourceCodeOrder(clazz, null);
    }

    /**
     * 按源码中的顺序（注意：java的反射机制无法保证返回的顺序）返回类中的所有符合筛选条件的方法，包含本类所有的方法以及父类/接口中的所有方法，显示父类和接口的显示优先级高于子类
     *
     * @param clazz 需要被解析的类
     * @param filter 如果需要进行筛选，可指定筛选条件
     */
    public static Collection<Method> getAllMethodsInSourceCodeOrder(Class<?> clazz, MethodFilter filter) {
        Set<Method> result = new LinkedHashSet<Method>();
        // 优先处理父类和子类的方法
        if (clazz.getSuperclass() != null) {
            result.addAll(getAllMethodsInSourceCodeOrder(clazz.getSuperclass(), filter));
        } else if (clazz.isInterface()) {
            for (Class<?> superIfc : clazz.getInterfaces()) {
                result.addAll(getAllMethodsInSourceCodeOrder(superIfc, filter));
            }
        }
        Map<String, Method> map = new LinkedHashMap<String, Method>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (null == filter || filter.matches(m)) {
                // 建立方法名和方法的映射，改编自Method.toString()
                StringBuilder sb = new StringBuilder();
                sb.append(m.getName()).append('(');
                Class<?>[] params = m.getParameterTypes();
                for (int j = 0; j < params.length; j++) {
                    sb.append(params[j].getName());
                    if (j < (params.length - 1)) {
                        sb.append(',');
                    }
                }
                sb.append(')');
                map.put(sb.toString(), m);
            }
        }
        try {
            CtClass cc = getCtClass(clazz);
            if (null != cc) {
                for (CtMethod cm : cc.getDeclaredMethods()) {
                    String key = cm.getName() + Descriptor.toString(cm.getSignature());
                    Method me = map.get(key);
                    if (null != me) {
                        result.add(me);
                    }
                }
            } else {
                result.addAll(map.values());
            }
        } catch (Throwable ex) { // 上面的流程都走过了，此处应当不可能抛此异常的，实在有问题，就用默认的方法列表
            result.addAll(map.values());
        }
        return result;
    }

    /**
     * 用于给bean的指定字段赋值，规则是如果有set方法就调用set方法，否则直接用反射赋值
     */
    public static abstract class BeanFieldValueSetter {

        private String fieldName;

        private Class<?> fieldClass;

        public BeanFieldValueSetter(String fieldName, Class<?> fieldClass) {
            this.fieldName = fieldName;
            this.fieldClass = fieldClass;
        }

        /**
         * 生成一个赋值工具类，返回setter方法
         */
        public static BeanFieldValueSetter createBySetter(Class<?> beanClass, final String fieldName, final Class<?> fieldClass) {
            String methodName = "set" + StringTools.headLetterToUpperCase(fieldName);

            final Method m = ReflectionUtils.findMethod(beanClass, methodName, fieldClass);
            if (null != m) {
                m.setAccessible(true);
                return new BeanFieldValueSetter(fieldName, fieldClass) {

                    @Override
                    public void setValue(Object bean, Object value) {
                        ReflectionUtils.invokeMethod(m, bean, value);
                    }
                };
            }
            return null;
        }

        /**
         * 生成一个赋值工具类，规则是如果有set方法就调用set方法，否则直接用反射赋值，出现任何异常将返回null
         */
        public static BeanFieldValueSetter create(Class<?> beanClass, final String fieldName, final Class<?> fieldClass) {
            // 判断有无set方法，有就允许直接使用set方法

            BeanFieldValueSetter setter = createBySetter(beanClass, fieldName, fieldClass);
            if (setter != null) {
                return setter;
            }

            // 没有set方法就直接通过反射赋值
            final Field f = ReflectionUtils.findField(beanClass, fieldName);
            if (null != f) {
                f.setAccessible(true);
                return new BeanFieldValueSetter(fieldName, fieldClass) {

                    @Override
                    public void setValue(Object bean, Object value) {
                        // 如果使用了AOP技术，需要识别出代理对象，拿出原始对象才能赋值，否则就会赋值到代理对象了就无效了
                        // 这个只需要直接通过反射对字段赋值时才有必要使用，如果是方法就直接走代理对象的代理方法，这样受事务控制
                        if (bean instanceof Advised) {
                            Advised adv = (Advised) bean;
                            try {
                                Object b = adv.getTargetSource().getTarget();
                                ReflectionUtils.setField(f, b, value);
                            } catch (RuntimeException e) {
                                throw e;
                            } catch (Exception e) {
                                throw new BeanInitializationException("从" + bean.getClass().getName() + "获取被代理的对象失败", e);
                            }
                        } else {
                            ReflectionUtils.setField(f, bean, value);
                        }
                    }
                };
            }
            return null;
        }

        /**
         * 获取字段的类型
         */
        public Class<?> getType() {
            return fieldClass;
        }

        /**
         * 获取字段的名称
         */
        public String getName() {
            return fieldName;
        }

        /**
         * 给指定对象的字段赋值
         *
         * @param bean 目标对象
         * @param value 赋值内容
         */
        public abstract void setValue(Object bean, Object value);

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((fieldName == null) ? 0 : fieldName.hashCode());
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
            BeanFieldValueSetter other = (BeanFieldValueSetter) obj;
            if (fieldName == null) {
                return other.fieldName == null;
            } else
                return fieldName.equals(other.fieldName);
        }

        @Override
        public String toString() {
            return "BeanFieldValueSetter [fieldName=" + fieldName + ", fieldClass=" + fieldClass + "]";
        }
    }

    /**
     * 用于从给定的bean获取指定字段的值，规则是如果有get方法就调用get方法获取，否则直接用反射获取
     */
    public static abstract class BeanFieldValueGetter {

        private String fieldName;

        private Class<?> fieldClass;

        public BeanFieldValueGetter(String fieldName, Class<?> fieldClass) {
            this.fieldName = fieldName;
            this.fieldClass = fieldClass;
        }

        /**
         * 生成一个赋值工具类，规则是如果有get方法就调用get方法，出现任何异常将返回null
         */
        public static BeanFieldValueGetter createByGetter(Class<?> beanClass, final String fieldName, final Class<?> fieldClass) {
            // 判断有无get方法，有就允许直接使用get方法，布尔类型要特殊处理
            String methodName = ((boolean.class.equals(fieldClass) || Boolean.class.equals(fieldClass)) ? "is" : "get") + StringTools.headLetterToUpperCase(fieldName);
            final Method m = ReflectionUtils.findMethod(beanClass, methodName);
            if (null != m) {
                if (!void.class.equals(m.getReturnType())) { // 返回类型不能是void
                    m.setAccessible(true);
                    return new BeanFieldValueGetter(fieldName, fieldClass) {

                        @Override
                        public Object getValue(Object bean) {
                            return ReflectionUtils.invokeMethod(m, bean);
                        }
                    };
                }
            }
            return null;
        }

        /**
         * 生成一个赋值工具类，规则是如果有get方法就调用get方法，否则直接用反射取值，出现任何异常将返回null
         */
        public static BeanFieldValueGetter create(Class<?> beanClass, final String fieldName, final Class<?> fieldClass) {

            BeanFieldValueGetter getter = createByGetter(beanClass, fieldName, fieldClass);
            if (getter != null) {
                return getter;
            }

            // 没有get方法就直接通过反射取值
            final Field f = ReflectionUtils.findField(beanClass, fieldName);
            if (null != f) {
                f.setAccessible(true);
                return new BeanFieldValueGetter(fieldName, fieldClass) {

                    @Override
                    public Object getValue(Object bean) {
                        return ReflectionUtils.getField(f, bean);
                    }
                };
            }
            return null;
        }

        /**
         * 获取字段的类型
         */
        public Class<?> getType() {
            return fieldClass;
        }

        /**
         * 获取字段的名称
         */
        public String getName() {
            return fieldName;
        }

        /**
         * 获取指定对象的字段的值，获取失败或出现异常将返回null
         *
         * @param bean 目标对象
         */
        public abstract Object getValue(Object bean);

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((fieldName == null) ? 0 : fieldName.hashCode());
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
            BeanFieldValueGetter other = (BeanFieldValueGetter) obj;
            if (fieldName == null) {
                return other.fieldName == null;
            } else
                return fieldName.equals(other.fieldName);
        }

        @Override
        public String toString() {
            return "BeanFieldValueGetter [fieldName=" + fieldName + ", fieldClass=" + fieldClass + "]";
        }
    }
}
