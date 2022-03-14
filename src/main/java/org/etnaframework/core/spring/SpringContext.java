package org.etnaframework.core.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.spring.annotation.OnBeanInited;
import org.etnaframework.core.spring.annotation.OnContextInited;
import org.etnaframework.core.spring.annotation.OnJvmShutdown;
import org.etnaframework.core.util.ReflectionTools;
import org.etnaframework.core.web.ExceptionRender;
import org.slf4j.Logger;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.web.context.support.RequestHandledEvent;

/**
 * 用于全局管理Spring的ApplicationContext
 *
 * @author BlackCat
 * @since 2012-7-17
 */
@Service
public class SpringContext implements ApplicationContextAware {

    private static final Logger log = Log.getLogger();

    private static ApplicationContext CONTEXT;

    /** 用于Spring是否已经初始化完成 */
    static boolean contextInited = false;

    private static void _checkContextInited() {
        if (!contextInited) {
            throw new RuntimeException(
                "Spring容器尚未初始化完成，请不要使用" + SpringContext.class.getSimpleName() + ".getBeanXXX方法" + "否则会导致异常，请不要在类的构造方法或字段初始化定义中调用这些方法，如果是在标记@" + OnBeanInited.class.getSimpleName() + "的方法中遇到本异常，请改用@" + OnContextInited.class.getSimpleName());
        }
    }

    public static boolean isContextInited() {
        return contextInited;
    }

    private SpringContext() {
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        CONTEXT = applicationContext;
    }

    /**
     * 用于内部bean排序，按类全名的字典序，但org.etnaframework.框架的类优先
     */
    public static class ByNameFrameworkPriorComparator implements Comparator<Object> {

        public static final ByNameFrameworkPriorComparator INSTANCE = new ByNameFrameworkPriorComparator();

        @Override
        public int compare(Object o1, Object o2) {
            String n1 = o1.getClass().getName();
            String n2 = o2.getClass().getName();
            if (n1.startsWith("org.etnaframework.")) {
                n1 = " " + n1;
            }
            if (n2.startsWith("org.etnaframework.")) {
                n2 = " " + n2;
            }
            return n1.compareTo(n2);
        }
    }

    /**
     * 在服务器关闭时预备做的事情
     */
    private static class OnJvmShutdownMeta {

        Object bean;

        Method method;

        public OnJvmShutdownMeta(Object bean, Method method) {
            this.bean = bean;
            this.method = method;
        }
    }

    private static List<OnJvmShutdownMeta> onJvmShutdownList = new ArrayList<OnJvmShutdownMeta>();

    /**
     * 执行Spring初始化完成后的后续操作
     */
    static void onContextInited() throws Throwable {
        // 执行托管类中标记有@OnContextInited的无参方法，按照类全名的字典序来执行
        List<Object> beans = new ArrayList<Object>(CONTEXT.getBeansOfType(Object.class, false, true).values());
        beans.sort(ByNameFrameworkPriorComparator.INSTANCE);
        for (Object bean : beans) {
            Class<?> clazz = bean.getClass();
            // 加快速度，对不需要处理的类加过滤
            String name = clazz.getName();
            if (name.startsWith("java.") || name.startsWith("org.eclipse.jetty.") || name.startsWith("org.springframework.")) {
                continue;
            }
            // 部分的类的特定方法不允许加启动注解
            if (bean instanceof BootstrapModule) {
                for (Method m : BootstrapModule.NO_NEED_FOR_OnXXX) {
                    checkNoNeed(clazz.getMethod(m.getName()));
                }
            }
            Collection<Method> methods = ReflectionTools.getAllMethodsInSourceCodeOrder(clazz, new MethodFilter() {

                @Override
                public boolean matches(Method method) {
                    return null != method.getAnnotation(OnContextInited.class);
                }
            });
            for (Method m : methods) {
                long startMS = System.currentTimeMillis();
                // 为什么这里要限制protected或public方法才可以？
                // 主要是考虑对托管bean使用AOP特性时生成的类的兼容性
                // spring的AOP代理类实际是这样的，比如原始类A，代理类B
                // B是继承于A，在B的实例里面放了一个A的隐藏对象，然后把A里面所有的非private方法代理到隐藏A的对应方法去
                // private方法没有做代理，当然通过反射是能访问到，但是没有被代理到隐藏的A去，实际上调用的是B的这个方法
                // 在这个方法里面，肯定是要做一些预处理的，比如修改某个字段的值，这改的实际是B的，原始隐藏的A并没有被修改到
                // 但通过注入去实际访问的都是A，B只是一个壳而已，只有protected和public的才能代理
                if (!Modifier.isProtected(m.getModifiers()) && !Modifier.isPublic(m.getModifiers())) {
                    throw new IllegalArgumentException("类" + m.getDeclaringClass().getName() + "加@" + OnContextInited.class.getSimpleName() + "的方法" + m.getName() + "必须是protected或public的");
                }
                // 检查是不是加到有参数的方法上面去了
                if (m.getParameterTypes().length > 0) {
                    throw new IllegalArgumentException(
                        "类" + clazz.getName() + "加@" + OnContextInited.class.getSimpleName() + "的方法" + m.getName() + "不允许带有任何参数，请去掉该方法的参数或去掉@" + OnContextInited.class.getSimpleName() + "注解");
                }
                // 允许在static方法搞初始化动作，但为了防止继承带来的重复调用问题，必须要求对应的类是final的
                if (Modifier.isStatic(m.getModifiers())) {
                    if (!Modifier.isFinal(clazz.getModifiers())) {
                        throw new IllegalArgumentException(
                            "类" + clazz.getName() + "加@" + OnContextInited.class.getSimpleName() + "的方法" + m.getName() + "是static的，为了避免继承类重复调用初始化问题，请将" + clazz.getSimpleName() + "声明为final类");
                    }
                }
                m.setAccessible(true);
                try {
                    m.invoke(bean);
                } catch (Throwable e) {
                    throw new RuntimeException("类" + clazz.getName() + "加@" + OnContextInited.class.getSimpleName() + "的方法" + m.getName() + "执行过程中抛异常", e);
                } finally {
                    if (bean instanceof Advised) {
                        Advised adv = (Advised) bean;
                        name = adv.getTargetClass().getSimpleName();
                    } else {
                        name = clazz.getSimpleName();
                    }
                    log.debug("@{} -> {}.{} [{}ms]", OnContextInited.class.getSimpleName(), name, m.getName(), (System.currentTimeMillis() - startMS));
                }
            }
            methods = ReflectionTools.getDeclaredMethodsInSourceCodeOrder(clazz, new MethodFilter() {

                @Override
                public boolean matches(Method method) {
                    return null != method.getAnnotation(OnJvmShutdown.class);
                }
            });
            for (Method m : methods) {
                // 检查是不是加到有参数的方法上面去了
                if (m.getParameterTypes().length > 0) {
                    throw new IllegalArgumentException(
                        "类" + clazz.getName() + "加@" + OnJvmShutdown.class.getSimpleName() + "的方法" + m.getName() + "不允许带有任何参数，请去掉该方法的参数或去掉@" + OnJvmShutdown.class.getSimpleName() + "注解");
                }
                // 允许在static方法搞关闭动作，但为了防止继承带来的重复调用问题，必须要求对应的类是final的
                if (Modifier.isStatic(m.getModifiers())) {
                    if (!Modifier.isFinal(clazz.getModifiers())) {
                        throw new IllegalArgumentException(
                            "类" + clazz.getName() + "加@" + OnJvmShutdown.class.getSimpleName() + "的方法" + m.getName() + "是static的，为了避免继承类重复调用导致重复回收等问题，请将" + clazz.getSimpleName() + "声明为final类");
                    }
                }
                m.setAccessible(true);
                onJvmShutdownList.add(new OnJvmShutdownMeta(bean, m));
            }
        }
        // 添加jvm关闭事件（System.exit时触发）
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                for (OnJvmShutdownMeta m : onJvmShutdownList) {
                    try {
                        log.debug("@{} -> {}.{}", OnJvmShutdown.class.getSimpleName(), m.bean.getClass().getSimpleName(), m.method.getName());
                        m.method.invoke(m.bean);
                    } catch (Throwable e) { // 错误只记录，不阻断
                        log.error("类{}加@{}的方法{}执行过程中抛异常", m.bean.getClass().getName(), OnJvmShutdown.class.getSimpleName(), m.method.getName(), e);
                    }
                }
            }
        });
    }

    /**
     * 检查方法有没有添加@{@link OnBeanInited}/@{@link OnContextInited}/@{@link OnJvmShutdown}
     */
    private static void checkNoNeed(Method m) {
        if (null != m.getAnnotation(OnBeanInited.class)) {
            throw new IllegalArgumentException("类" + m.getDeclaringClass().getName() + "的方法" + m.getName() + "不允许加@" + OnBeanInited.class.getSimpleName());
        }
        if (null != m.getAnnotation(OnContextInited.class)) {
            throw new IllegalArgumentException("类" + m.getDeclaringClass().getName() + "的方法" + m.getName() + "不允许加@" + OnContextInited.class.getSimpleName());
        }
        if (null != m.getAnnotation(OnJvmShutdown.class)) {
            throw new IllegalArgumentException("类" + m.getDeclaringClass().getName() + "的方法" + m.getName() + "不允许加@" + OnJvmShutdown.class.getSimpleName());
        }
    }

    // 以下是从 ApplicationContext中代理出来的方法，加了初始化完成的判断，减少代码错误

    /**
     * 获取满足指定类型的bean的列表
     */
    public static <T> List<T> getBeansOfTypeAsList(Class<T> type) throws BeansException {
        _checkContextInited();
        return new ArrayList<T>(CONTEXT.getBeansOfType(type).values());
    }

    /**
     * 获取满足指定类型的bean，如果有多个会抛出异常，如果没有会返回null
     */
    public static <T> T getBeanOfType(Class<T> type) throws BeansException {
        _checkContextInited();
        Map<String, T> map = getBeansOfType(type);
        if (map.size() > 0) { // 如果有实现类，就注入，否则就留null
            if (map.size() > 1) {
                throw new IllegalStateException(ExceptionRender.class.getSimpleName() + "的实例只能有1个，现在有多个实例");
            }
            T v = map.values().iterator().next();
            return v;
        }
        return null;
    }

    /**
     * Return the number of beans defined in the factory.
     * <p>
     * Does not consider any hierarchy this factory may participate in, and ignores any singleton beans that have been registered by other means than bean definitions.
     *
     * @return the number of beans defined in the factory
     */
    public static int getBeanDefinitionCount() {
        _checkContextInited();
        return CONTEXT.getBeanDefinitionCount();
    }

    /**
     * Return the names of all beans defined in this factory.
     * <p>
     * Does not consider any hierarchy this factory may participate in, and ignores any singleton beans that have been registered by other means than bean definitions.
     *
     * @return the names of all beans defined in this factory, or an empty array if none defined
     */
    public static String[] getBeanDefinitionNames() {
        _checkContextInited();
        return CONTEXT.getBeanDefinitionNames();
    }

    /**
     * Return the names of beans matching the given type (including subclasses), judging from either bean definitions or the value of {@code getObjectType} in the case of FactoryBeans.
     * <p>
     * <b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i> check nested beans which might match the specified type as well.
     * <p>
     * Does consider objects created by FactoryBeans, which means that FactoryBeans will get initialized. If the object created by the FactoryBean doesn't match, the raw FactoryBean itself will be
     * matched against the type.
     * <p>
     * Does not consider any hierarchy this factory may participate in. Use BeanFactoryUtils' {@code beanNamesForTypeIncludingAncestors} to include beans in ancestor factories too.
     * <p>
     * Note: Does <i>not</i> ignore singleton beans that have been registered by other means than bean definitions.
     * <p>
     * This version of {@code getBeanNamesForType} matches all kinds of beans, be it singletons, prototypes, or FactoryBeans. In most implementations, the result will be the same as for
     * {@code getBeanNamesForType(type, true, true)}.
     * <p>
     * Bean names returned by this method should always return bean names <i>in the order of definition</i> in the backend configuration, as far as possible.
     *
     * @param type the class or interface to match, or {@code null} for all bean names
     *
     * @return the names of beans (or objects created by FactoryBeans) matching the given object type (including subclasses), or an empty array if none
     * @see FactoryBean#getObjectType
     * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors(ListableBeanFactory, Class)
     */
    public static String[] getBeanNamesForType(Class<?> type) {
        _checkContextInited();
        return CONTEXT.getBeanNamesForType(type);
    }

    /**
     * Return the names of beans matching the given type (including subclasses), judging from either bean definitions or the value of {@code getObjectType} in the case of FactoryBeans.
     * <p>
     * <b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i> check nested beans which might match the specified type as well.
     * <p>
     * Does consider objects created by FactoryBeans if the "allowEagerInit" flag is set, which means that FactoryBeans will get initialized. If the object created by the FactoryBean doesn't match,
     * the raw FactoryBean itself will be matched against the type. If "allowEagerInit" is not set, only raw FactoryBeans will be checked (which doesn't require initialization of each FactoryBean).
     * <p>
     * Does not consider any hierarchy this factory may participate in. Use BeanFactoryUtils' {@code beanNamesForTypeIncludingAncestors} to include beans in ancestor factories too.
     * <p>
     * Note: Does <i>not</i> ignore singleton beans that have been registered by other means than bean definitions.
     * <p>
     * Bean names returned by this method should always return bean names <i>in the order of definition</i> in the backend configuration, as far as possible.
     *
     * @param type the class or interface to match, or {@code null} for all bean names
     * @param includeNonSingletons whether to include prototype or scoped beans too or just singletons (also applies to FactoryBeans)
     * @param allowEagerInit whether to initialize <i>lazy-init singletons</i> and <i>objects created by FactoryBeans</i> (or by factory methods with a "factory-bean" reference) for the type check.
     * Note that FactoryBeans need to be eagerly initialized to determine their type: So be aware that passing in "true" for this flag will initialize FactoryBeans and "factory-bean"
     * references.
     *
     * @return the names of beans (or objects created by FactoryBeans) matching the given object type (including subclasses), or an empty array if none
     * @see FactoryBean#getObjectType
     * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors(ListableBeanFactory, Class, boolean, boolean)
     */
    public static String[] getBeanNamesForType(Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
        _checkContextInited();
        return CONTEXT.getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
    }

    /**
     * Return an instance, which may be shared or independent, of the specified bean.
     * <p>
     * This method allows a Spring BeanFactory to be used as a replacement for the Singleton or Prototype design pattern. Callers may retain references to returned objects in the case of Singleton
     * beans.
     * <p>
     * Translates aliases back to the corresponding canonical bean name. Will ask the parent factory if the bean cannot be found in this factory instance.
     *
     * @param name the name of the bean to retrieve
     *
     * @return an instance of the bean
     * @throws NoSuchBeanDefinitionException if there is no bean definition with the specified name
     * @throws BeansException if the bean could not be obtained
     */
    public static Object getBean(String name) throws BeansException {
        _checkContextInited();
        return CONTEXT.getBean(name);
    }

    /**
     * Return an instance, which may be shared or independent, of the specified bean.
     * <p>
     * Behaves the same as {@link #getBean(String)}, but provides a measure of type safety by throwing a BeanNotOfRequiredTypeException if the bean is not of the required type. This means that
     * ClassCastException can't be thrown on casting the result correctly, as can happen with {@link #getBean(String)}.
     * <p>
     * Translates aliases back to the corresponding canonical bean name. Will ask the parent factory if the bean cannot be found in this factory instance.
     *
     * @param name the name of the bean to retrieve
     * @param requiredType type the bean must match. Can be an interface or superclass of the actual class, or {@code null} for any match. For example, if the value is {@code Object.class}, this
     * method will succeed whatever the class of the returned instance.
     *
     * @return an instance of the bean
     * @throws NoSuchBeanDefinitionException if there is no such bean definition
     * @throws BeanNotOfRequiredTypeException if the bean is not of the required type
     * @throws BeansException if the bean could not be created
     */
    public static <T> T getBean(String name, Class<T> requiredType) throws BeansException {
        _checkContextInited();
        return CONTEXT.getBean(name, requiredType);
    }

    /**
     * Return the bean instances that match the given object type (including subclasses), judging from either bean definitions or the value of {@code getObjectType} in the case of FactoryBeans.
     * <p>
     * <b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i> check nested beans which might match the specified type as well.
     * <p>
     * Does consider objects created by FactoryBeans, which means that FactoryBeans will get initialized. If the object created by the FactoryBean doesn't match, the raw FactoryBean itself will be
     * matched against the type.
     * <p>
     * Does not consider any hierarchy this factory may participate in. Use BeanFactoryUtils' {@code beansOfTypeIncludingAncestors} to include beans in ancestor factories too.
     * <p>
     * Note: Does <i>not</i> ignore singleton beans that have been registered by other means than bean definitions.
     * <p>
     * This version of getBeansOfType matches all kinds of beans, be it singletons, prototypes, or FactoryBeans. In most implementations, the result will be the same as for
     * {@code getBeansOfType(type, true, true)}.
     * <p>
     * The Map returned by this method should always return bean names and corresponding bean instances <i>in the order of definition</i> in the backend configuration, as far as possible.
     *
     * @param type the class or interface to match, or {@code null} for all concrete beans
     *
     * @return a Map with the matching beans, containing the bean names as keys and the corresponding bean instances as values
     * @throws BeansException if a bean could not be created
     * @see FactoryBean#getObjectType
     * @see BeanFactoryUtils#beansOfTypeIncludingAncestors(ListableBeanFactory, Class)
     * @since 1.1.2
     */
    public static <T> Map<String, T> getBeansOfType(Class<T> type) throws BeansException {
        _checkContextInited();
        return CONTEXT.getBeansOfType(type);
    }

    /**
     * Return the bean instance that uniquely matches the given object type, if any.
     *
     * @param requiredType type the bean must match; can be an interface or superclass. {@code null} is disallowed.
     * <p>
     * This method goes into {@link ListableBeanFactory} by-type lookup territory but may also be translated into a conventional by-name lookup based on the name of the given type. For more
     * extensive retrieval operations across sets of beans, use {@link ListableBeanFactory} and/or {@link BeanFactoryUtils}.
     *
     * @return an instance of the single bean matching the required type
     * @throws NoSuchBeanDefinitionException if no bean of the given type was found
     * @throws NoUniqueBeanDefinitionException if more than one bean of the given type was found
     * @see ListableBeanFactory
     * @since 3.0
     */
    public static <T> T getBean(Class<T> requiredType) throws BeansException {
        _checkContextInited();
        return CONTEXT.getBean(requiredType);
    }

    /**
     * Return an instance, which may be shared or independent, of the specified bean.
     * <p>
     * Allows for specifying explicit constructor arguments / factory method arguments, overriding the specified default arguments (if any) in the bean definition.
     *
     * @param name the name of the bean to retrieve
     * @param args arguments to use when creating a bean instance using explicit arguments (only applied when creating a new instance as opposed to retrieving an existing one)
     *
     * @return an instance of the bean
     * @throws NoSuchBeanDefinitionException if there is no such bean definition
     * @throws BeanDefinitionStoreException if arguments have been given but the affected bean isn't a prototype
     * @throws BeansException if the bean could not be created
     * @since 2.5
     */
    public static Object getBean(String name, Object... args) throws BeansException {
        _checkContextInited();
        return CONTEXT.getBean(name, args);
    }

    /**
     * Return the bean instances that match the given object type (including subclasses), judging from either bean definitions or the value of {@code getObjectType} in the case of FactoryBeans.
     * <p>
     * <b>NOTE: This method introspects top-level beans only.</b> It does <i>not</i> check nested beans which might match the specified type as well.
     * <p>
     * Does consider objects created by FactoryBeans if the "allowEagerInit" flag is set, which means that FactoryBeans will get initialized. If the object created by the FactoryBean doesn't match,
     * the raw FactoryBean itself will be matched against the type. If "allowEagerInit" is not set, only raw FactoryBeans will be checked (which doesn't require initialization of each FactoryBean).
     * <p>
     * Does not consider any hierarchy this factory may participate in. Use BeanFactoryUtils' {@code beansOfTypeIncludingAncestors} to include beans in ancestor factories too.
     * <p>
     * Note: Does <i>not</i> ignore singleton beans that have been registered by other means than bean definitions.
     * <p>
     * The Map returned by this method should always return bean names and corresponding bean instances <i>in the order of definition</i> in the backend configuration, as far as possible.
     *
     * @param type the class or interface to match, or {@code null} for all concrete beans
     * @param includeNonSingletons whether to include prototype or scoped beans too or just singletons (also applies to FactoryBeans)
     * @param allowEagerInit whether to initialize <i>lazy-init singletons</i> and <i>objects created by FactoryBeans</i> (or by factory methods with a "factory-bean" reference) for the type check.
     * Note that FactoryBeans need to be eagerly initialized to determine their type: So be aware that passing in "true" for this flag will initialize FactoryBeans and "factory-bean"
     * references.
     *
     * @return a Map with the matching beans, containing the bean names as keys and the corresponding bean instances as values
     * @throws BeansException if a bean could not be created
     * @see FactoryBean#getObjectType
     * @see BeanFactoryUtils#beansOfTypeIncludingAncestors(ListableBeanFactory, Class, boolean, boolean)
     */
    public static <T> Map<String, T> getBeansOfType(Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {
        _checkContextInited();
        return CONTEXT.getBeansOfType(type, includeNonSingletons, allowEagerInit);
    }

    /**
     * Return an instance, which may be shared or independent, of the specified bean.
     * <p>
     * Allows for specifying explicit constructor arguments / factory method arguments, overriding the specified default arguments (if any) in the bean definition.
     *
     * @param requiredType type the bean must match; can be an interface or superclass. {@code null} is disallowed.
     * <p>
     * This method goes into {@link ListableBeanFactory} by-type lookup territory but may also be translated into a conventional by-name lookup based on the name of the given type. For more
     * extensive retrieval operations across sets of beans, use {@link ListableBeanFactory} and/or {@link BeanFactoryUtils}.
     * @param args arguments to use when creating a bean instance using explicit arguments (only applied when creating a new instance as opposed to retrieving an existing one)
     *
     * @return an instance of the bean
     * @throws NoSuchBeanDefinitionException if there is no such bean definition
     * @throws BeanDefinitionStoreException if arguments have been given but the affected bean isn't a prototype
     * @throws BeansException if the bean could not be created
     * @since 4.1
     */
    public static <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
        _checkContextInited();
        return CONTEXT.getBean(requiredType, args);
    }

    /**
     * Find all names of beans whose {@code Class} has the supplied {@link Annotation} type, without creating any bean instances yet.
     *
     * @param annotationType the type of annotation to look for
     *
     * @return the names of all matching beans
     * @since 4.0
     */
    public static String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
        _checkContextInited();
        return CONTEXT.getBeanNamesForAnnotation(annotationType);
    }

    /**
     * Find all beans whose {@code Class} has the supplied {@link Annotation} type, returning a Map of bean names with corresponding bean instances.
     *
     * @param annotationType the type of annotation to look for
     *
     * @return a Map with the matching beans, containing the bean names as keys and the corresponding bean instances as values
     * @throws BeansException if a bean could not be created
     * @since 3.0
     */
    public static Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) throws BeansException {
        _checkContextInited();
        return CONTEXT.getBeansWithAnnotation(annotationType);
    }

    /**
     * Notify all listeners registered with this application of an application event. Events may be framework events (such as RequestHandledEvent) or application-specific events.
     *
     * @param event the event to publish
     *
     * @see RequestHandledEvent
     */
    public static void publishEvent(ApplicationEvent event) {
        CONTEXT.publishEvent(event);
    }
}
