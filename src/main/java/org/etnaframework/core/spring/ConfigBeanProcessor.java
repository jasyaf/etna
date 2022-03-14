package org.etnaframework.core.spring;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.spring.ConfigAnnotationBeanPostProcessor.ConfigBeanSetter;
import org.etnaframework.core.spring.ConfigAnnotationBeanPostProcessor.KeyConfigUnion;
import org.etnaframework.core.spring.annotation.Config;
import org.etnaframework.core.spring.annotation.OnBeanInited;
import org.etnaframework.core.util.ReflectionTools;
import org.etnaframework.core.util.ReflectionTools.BeanFieldValueSetter;
import org.etnaframework.core.util.StringTools;
import org.slf4j.Logger;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.FieldFilter;
import org.springframework.util.ReflectionUtils.MethodFilter;

/**
 * 指定class的托管bean中的@{@link Config}和@{@link OnBeanInited}的处理逻辑
 *
 * @author BlackCat
 * @since 2015-01-16
 */
class ConfigBeanProcessor {

    protected final Logger log = Log.getLogger();

    /** 目标bean */
    protected Object bean;

    /** 目标bean对应的class */
    protected Class<?> clazz;

    /** 对配置容器的引用 */
    protected ConfigAnnotationBeanPostProcessor beanProcessor;

    /** 指定的类下的有@{@link Config}的字段和set方法的列表 */
    protected List<ConfigBeanSetter> setters;

    /** 标记有@{@link OnBeanInited}的无参数方法列表 */
    protected Collection<Method> onBeanInitedList;

    private void checkAndAddSetter(String key, ConfigBeanSetter setter) {
        // 检查key是不是已经被用过了
        KeyConfigUnion kcu = beanProcessor.global.get(key);
        if (null == kcu) {
            beanProcessor.global.put(key, new KeyConfigUnion(key, setter));
            setters.add(setter);
        } else {
            ConfigBeanSetter s = kcu.getFirst();
            if (!kcu.valueType.equals(setter.setter.getType())) {
                throw new IllegalArgumentException(
                    "@" + Config.class.getSimpleName() + " key=" + key + "已经在类" + s.clazz.getName() + "中被声明为类型" + kcu.valueType.getName() + "，不可以在" + setter.clazz.getName() + "中再声明为类型" + setter.setter.getType().getName());
            }
            kcu.addSetter(setter);
            setters.add(setter);
        }
    }

    ConfigBeanProcessor(ConfigAnnotationBeanPostProcessor beanProcessor, Object bean) {
        this.bean = bean;
        this.beanProcessor = beanProcessor;
        this.clazz = bean.getClass();
        this.setters = new ArrayList<ConfigBeanSetter>();

        // 获得@{@link Config}中设置的key名称，默认为变量的名称，并准备赋值
        Collection<Field> fList = ReflectionTools.getAllFieldsInSourceCodeOrder(clazz, new FieldFilter() {

            @Override
            public boolean matches(Field field) {
                return null != field.getAnnotation(Config.class);
            }
        });
        for (final Field field : fList) {
            // 允许在static字段上赋值，但为了防止继承带来的重复调用问题，必须要求对应的类是final的
            if (Modifier.isFinal(field.getModifiers())) {
                throw new IllegalArgumentException("类" + field.getDeclaringClass().getName() + "加@" + Config.class.getSimpleName() + "的字段" + field.getName() + "是final的，无法准备赋值行为");
            }
            if (Modifier.isStatic(field.getModifiers())) {
                if (!Modifier.isFinal(field.getDeclaringClass().getModifiers())) {
                    throw new IllegalArgumentException(
                        "类" + field.getDeclaringClass().getName() + "加@" + Config.class.getSimpleName() + "的字段" + field.getName() + "是static的，为了避免继承类重复赋值问题，请将" + field.getDeclaringClass().getSimpleName() + "声明为final类");
                }
            }
            Config config = field.getAnnotation(Config.class);
            String key = StringTools.isEmpty(config.value()) ? field.getName() : config.value();

            field.setAccessible(true);
            BeanFieldValueSetter setter = new BeanFieldValueSetter(field.getName(), field.getType()) {

                @Override
                public void setValue(Object bean, Object value) {
                    // 如果使用了AOP技术，需要识别出代理对象，拿出原始对象才能赋值，否则就会赋值到代理对象了就无效了
                    // 这个只需要直接通过反射对字段赋值时才有必要使用，如果是方法就直接走代理对象的代理方法，这样受事务控制
                    if (bean instanceof Advised) {
                        Advised adv = (Advised) bean;
                        try {
                            Object b = adv.getTargetSource().getTarget();
                            ReflectionUtils.setField(field, b, value);
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new BeanInitializationException("从" + bean.getClass().getName() + "获取被代理的对象失败", e);
                        }
                    } else {
                        ReflectionUtils.setField(field, bean, value);
                    }
                }
            };
            checkAndAddSetter(key, new ConfigBeanSetter(clazz, config.resetable(), ConfigBeanSetter.CONFIG_TYPE_FIELD, setter));
        }

        Collection<Method> mList = ReflectionTools.getAllMethodsInSourceCodeOrder(clazz, new MethodFilter() {

            @Override
            public boolean matches(Method method) {
                return null != method.getAnnotation(Config.class);
            }
        });
        // 获得@{@link Config}中设置的key名称，默认set方法对应的字段名，并准备赋值
        for (final Method m : mList) {
            if (!m.getName().startsWith("set") || m.getParameterTypes().length != 1) {
                throw new IllegalArgumentException("类" + m.getDeclaringClass().getName() + "加@" + Config.class.getSimpleName() + "的方法" + m.getName() + "不是一个有效的set方法");
            }
            // 为什么这里要限制protected或public方法才可以？
            // 主要是考虑对托管bean使用AOP特性时生成的类的兼容性
            // spring的AOP代理类实际是这样的，比如原始类A，代理类B
            // B是继承于A，在B的实例里面放了一个A的隐藏对象，然后把A里面所有的非private方法代理到隐藏A的对应方法去
            // private方法没有做代理，当然通过反射是能访问到，但是没有被代理到隐藏的A去，实际上调用的是B的这个方法
            // 在这个方法里面，肯定是要做一些预处理的，比如修改某个字段的值，这改的实际是B的，原始隐藏的A并没有被修改到
            // 但通过注入去实际访问的都是A，B只是一个壳而已，只有protected和public的才能代理
            if (!Modifier.isProtected(m.getModifiers()) && !Modifier.isPublic(m.getModifiers())) {
                throw new IllegalArgumentException("类" + m.getDeclaringClass().getName() + "加@" + Config.class.getSimpleName() + "的方法" + m.getName() + "必须是protected或public的");
            }
            // 允许在static方法上赋值，但为了防止继承带来的重复调用问题，必须要求对应的类是final的
            if (Modifier.isStatic(m.getModifiers())) {
                if (!Modifier.isFinal(m.getDeclaringClass().getModifiers())) {
                    throw new IllegalArgumentException(
                        "类" + m.getDeclaringClass().getName() + "加@" + Config.class.getSimpleName() + "的方法" + m.getName() + "是static的，为了避免继承类重复赋值问题，请将" + m.getDeclaringClass().getSimpleName() + "声明为final类");
                }
            }
            Config cfg = m.getAnnotation(Config.class);
            String fieldName = ReflectionTools.getFieldNameFromSetMethod(m);
            if (null == fieldName) {
                throw new IllegalArgumentException("类" + m.getDeclaringClass().getName() + "加@" + Config.class.getSimpleName() + "的方法" + m.getName() + "不是一个有效的set方法");
            }
            String key = StringTools.isEmpty(cfg.value()) ? fieldName : cfg.value();

            m.setAccessible(true);
            BeanFieldValueSetter setter = new BeanFieldValueSetter(fieldName, m.getParameterTypes()[0]) {

                @Override
                public void setValue(Object bean, Object value) {
                    ReflectionUtils.invokeMethod(m, bean, value);
                }
            };
            checkAndAddSetter(key, new ConfigBeanSetter(clazz, cfg.resetable(), ConfigBeanSetter.CONFIG_TYPE_METHOD, setter));
        }

        // 提取加了@{@link OnBeanInited}的无参方法
        this.onBeanInitedList = ReflectionTools.getAllMethodsInSourceCodeOrder(clazz, new MethodFilter() {

            @Override
            public boolean matches(Method method) {
                return null != method.getAnnotation(OnBeanInited.class);
            }
        });
        for (Method m : onBeanInitedList) {
            Class<?> beanClass = m.getDeclaringClass();
            // 为什么这里要限制protected或public方法才可以？
            // 主要是考虑对托管bean使用AOP特性时生成的类的兼容性
            // spring的AOP代理类实际是这样的，比如原始类A，代理类B
            // B是继承于A，在B的实例里面放了一个A的隐藏对象，然后把A里面所有的非private方法代理到隐藏A的对应方法去
            // private方法没有做代理，当然通过反射是能访问到，但是没有被代理到隐藏的A去，实际上调用的是B的这个方法
            // 在这个方法里面，肯定是要做一些预处理的，比如修改某个字段的值，这改的实际是B的，原始隐藏的A并没有被修改到
            // 但通过注入去实际访问的都是A，B只是一个壳而已，只有protected和public的才能代理
            if (!Modifier.isProtected(m.getModifiers()) && !Modifier.isPublic(m.getModifiers())) {
                throw new IllegalArgumentException("类" + m.getDeclaringClass().getName() + "加@" + OnBeanInited.class.getSimpleName() + "的方法" + m.getName() + "必须是protected或public的");
            }
            // 检查是不是加到有参数的方法上面去了
            if (m.getParameterTypes().length > 0) {
                throw new IllegalArgumentException(
                    "类" + beanClass.getName() + "加@" + OnBeanInited.class.getSimpleName() + "的方法" + m.getName() + "不允许带有任何参数，请去掉该方法的参数或去掉@" + OnBeanInited.class.getSimpleName() + "注解");
            }
            // 允许在static方法搞初始化动作，但为了防止继承带来的重复调用问题，必须要求对应的类是final的
            if (Modifier.isStatic(m.getModifiers())) {
                if (!Modifier.isFinal(beanClass.getModifiers())) {
                    throw new IllegalArgumentException(
                        "类" + beanClass.getName() + "加@" + OnBeanInited.class.getSimpleName() + "的方法" + m.getName() + "是static的，为了避免继承类重复调用初始化问题，请将" + beanClass.getSimpleName() + "声明为final类");
                }
            }
        }
    }

    /**
     * 给指定的bean赋值，处理加了@{@link Config}的字段
     */
    void config() {
        for (ConfigBeanSetter cbs : setters) {
            if (cbs.union.checkConfigValue(true)) {
                cbs.setValue(bean, cbs.union.valueObject);
            }
        }
    }

    /**
     * 给指定的bean重新赋值，处理加了@{@link Config}的字段
     */
    void reloadConfig() {
        for (ConfigBeanSetter cbs : setters) {
            if (cbs.resetable && cbs.union.checkConfigValue(false)) {
                cbs.setValue(bean, cbs.union.valueObject);
            }
        }
    }

    /**
     * 调用bean加了@{@link OnBeanInited}的无参数方法
     */
    void onBeanInited() {
        for (Method m : onBeanInitedList) {
            m.setAccessible(true);
            try {
                m.invoke(bean);
            } catch (Throwable e) {
                throw new BeanInitializationException("类" + clazz.getName() + "加@" + OnBeanInited.class.getSimpleName() + "的方法" + m.getName() + "执行过程中抛异常", e);
            } finally {
                String name = clazz.getSimpleName();
                if (bean instanceof Advised) {
                    Advised adv = (Advised) bean;
                    name = adv.getTargetClass().getSimpleName();
                }
                log.debug("@{} -> {}.{}", OnBeanInited.class.getSimpleName(), name, m.getName());
            }
        }
    }
}
