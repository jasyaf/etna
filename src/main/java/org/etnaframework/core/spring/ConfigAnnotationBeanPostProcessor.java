package org.etnaframework.core.spring;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.spring.annotation.Config;
import org.etnaframework.core.spring.annotation.OnContextInited;
import org.etnaframework.core.util.BeanTools;
import org.etnaframework.core.util.CollectionTools;
import org.etnaframework.core.util.DatetimeUtils;
import org.etnaframework.core.util.JsonObjectUtils;
import org.etnaframework.core.util.ReflectionTools.BeanFieldValueSetter;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.util.ThreadUtils;
import org.slf4j.Logger;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import com.google.common.collect.Lists;

/**
 * 自定义的Spring组件，用于给在Spring托管bean加了@Config注解的字段或set方法进行赋值
 *
 * @author BlackCat
 * @since 2014-8-3
 */
public class ConfigAnnotationBeanPostProcessor extends InstantiationAwareBeanPostProcessorAdapter {

    private static Logger log = Log.getLogger();

    private static ConfigAnnotationBeanPostProcessor INSTANCE;

    /**
     * 全局的有@{@link Config}标注的托管bean集合
     */
    Map<String, KeyConfigUnion> global = new LinkedHashMap<String, KeyConfigUnion>();

    /** 赋值组件重新加载的时间间隔，单位秒，<=0表示不进行重新加载（效果就跟Spring自带的@{@link Value}一样了，本实现最大的特点是支持热加载） */
    private int configReloadPeriodSec = -1;

    private Map<Class<?>, ConfigBeanProcessor> map = new ConcurrentHashMap<Class<?>, ConfigBeanProcessor>();

    /** 配置项的值的来源，可以有多个，优先级按配置的顺序 */
    private EtnaConfigKeyValueLoader[] keyValueLoaders = new EtnaConfigKeyValueLoader[0];

    private List<ConfigMeta> allConfig;

    private ConfigAnnotationBeanPostProcessor() {
        INSTANCE = this;
    }

    public int getConfigReloadPeriodSec() {
        return configReloadPeriodSec;
    }

    public void setConfigReloadPeriodSec(int configReloadPeriodSec) {
        this.configReloadPeriodSec = configReloadPeriodSec;
    }

    public ConfigBeanProcessor getProcessor(Object bean) {
        Class<?> clazz = bean.getClass();
        ConfigBeanProcessor processor = map.get(clazz);
        if (null == processor) {
            synchronized (map) { // 如果没查到，就生成，进行双次判断，防止重复生成
                if (null == (processor = map.get(clazz))) {
                    processor = new ConfigBeanProcessor(this, bean);
                    map.put(clazz, processor);
                }
            }
        }
        return processor;
    }

    public void setKeyValueLoaders(EtnaConfigKeyValueLoader[] keyValueLoaders) throws Throwable {
        this.keyValueLoaders = keyValueLoaders;
        for (EtnaConfigKeyValueLoader loader : keyValueLoaders) {
            loader.reload();
        }
    }

    /**
     * 从配置列表中获取指定key的值
     */
    String getConfigValue(String key) {
        for (EtnaConfigKeyValueLoader loader : keyValueLoaders) {
            String value = loader.getConfig(key);
            if (null != value) {
                return value;
            }
        }
        return null;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        ConfigBeanProcessor p = getProcessor(bean);
        p.config();
        p.onBeanInited();
        return bean;
    }

    /**
     * 获取所有的config的列表
     */
    public List<ConfigMeta> getAllConfig() {
        if (allConfig == null) {
            List<ConfigMeta> tmp = Lists.newLinkedList();
            _buildAllConfig(tmp, map);
            allConfig = tmp;
        }
        return allConfig;
    }

    private void _buildAllConfig(List<ConfigMeta> tmp, Map<Class<?>, ConfigBeanProcessor> ori) {
        for (Entry<Class<?>, ConfigBeanProcessor> e : ori.entrySet()) {
            Class<?> clazz1 = e.getKey();
            ConfigBeanProcessor p = e.getValue();
            p.setters.forEach(setter -> {
                ConfigMeta meta = new ConfigMeta();
                meta.setClassName(clazz1.getSimpleName());
                meta.setConfigName(setter.union.key);
                meta.setResetable(setter.resetable ? "RESETABLE" : "");
                meta.setConfigValue(setter.union.valueString);
                meta.setValueType(setter.union.valueType.getSimpleName());
                tmp.add(meta);
            });
        }
    }

    /**
     * 定时扫描器启动方法
     */
    @OnContextInited
    protected void start() {
        if (configReloadPeriodSec > 0) {
            ThreadUtils.getCron().scheduleAtFixedRate((Runnable) () -> reloadAllConfig(), configReloadPeriodSec, configReloadPeriodSec, TimeUnit.SECONDS);
        }
    }

    /**
     * 全部重新加载
     */
    public void reloadAllConfig() {
        try {
            setKeyValueLoaders(keyValueLoaders); // 重新load配置文件
        } catch (Throwable e) {
        }
        for (ConfigBeanProcessor p : map.values()) {
            try {
                p.reloadConfig(); // 重新扫描赋值
            } catch (Throwable e) {
            }
        }
        allConfig = null; // 清空CONFIG缓存
    }

    /**
     * 给spring托管bean字段和set方法赋值的辅助类
     */
    static class ConfigBeanSetter {

        static final String CONFIG_TYPE_METHOD = "METHOD";

        static final String CONFIG_TYPE_FIELD = "FIELD";

        /** 目标clazz */
        Class<?> clazz;

        /** config所处的类型是 FIELD和METHOD */
        String setterType;

        /** 是否允许重设值 */
        boolean resetable;

        /** 对应的字段或set方法赋值工具类 */
        BeanFieldValueSetter setter;

        /** 只保存最新的托管bean实例 */
        Object latestBean;

        /** 保留对上一级的引用 */
        KeyConfigUnion union;

        ConfigBeanSetter(Class<?> clazz, boolean resetable, String setterType, BeanFieldValueSetter setter) {
            this.clazz = clazz;
            this.resetable = resetable;
            this.setterType = setterType;
            this.setter = setter;
        }

        void setValue(Object targetBean, Object value) {
            try {
                setter.setValue(targetBean, value);
            } finally {
                String name = targetBean.getClass().getSimpleName();
                if (targetBean instanceof Advised) {
                    Advised adv = (Advised) targetBean;
                    name = adv.getTargetClass().getSimpleName();
                }
                log.debug("@" + Config.class.getSimpleName() + " SET " + name + "->" + union.key + "=" + union.valueString);
            }
        }
    }

    static class KeyConfigUnion {

        /** 在取值列表中的对应的key名称，默认是字段的名称，也可以在注解另外指定 */
        String key;

        /** 对应值的字符串表示 */
        String valueString;

        /** 对应值的类型 */
        Class<?> valueType;

        /** 对应值的对象引用 */
        Object valueObject;

        /** 所有的有该key的spring托管bean信息集合，这个列表里面至少有1个元素 */
        List<ConfigBeanSetter> list;

        KeyConfigUnion(String key, ConfigBeanSetter setter) {
            this.key = key;
            setter.union = this;
            this.valueType = setter.setter.getType(); // 这个type确定下来就不能更改了，要求后面用到的必须完全一样
            this.list = CollectionTools.buildList(setter);
        }

        void addSetter(ConfigBeanSetter setter) {
            setter.union = this;
            list.add(setter);
        }

        ConfigBeanSetter getFirst() {
            return list.get(0);
        }

        @Override
        public String toString() {
            return "KeyConfigUnion [key=" + key + ", valueString=" + valueString + ", valueType=" + valueType + ", valueObject=" + valueObject + ", list=" + list + "]";
        }

        /**
         * 从配置列表中获取指定key的值
         *
         * @param init 是否是第一次加载，如果是第一次加载，不会去判断是否跟以前的相同
         *
         * @return 如果未能获取到指定key的值，返回false。如果获取到，但跟保存的valueString相比有变化，返回true否则返回false
         */
        boolean checkConfigValue(boolean init) {
            String vs = INSTANCE.getConfigValue(key);

            if (null == vs) {
                return false;
            }
            if (!init && vs.equals(valueString)) {
                return false;
            }

            if (BeanTools.isPrimitiveWrapperType(valueType) && !BeanTools.isDateWrapperType(valueType)) {
                try {
                    valueObject = StringTools.valueOf(vs, valueType);
                } catch (Exception e) {
                    valueObject = null;
                }
            } else if (Date.class.isAssignableFrom(valueType)) {
                valueObject = DatetimeUtils.parse(vs);
            } else {
                valueObject = JsonObjectUtils.parseJson(vs, valueType);
            }
            if (null == valueObject) {
                throw new IllegalArgumentException(getFirst().clazz.getName() + "->" + key + "赋值失败，需要类型" + valueType.getName() + "，待转换字符串" + vs);
            }
            valueString = vs;
            return true;
        }
    }

    /**
     * config元数据对象
     */
    public static class ConfigMeta {

        private String className;

        private String configName;

        private String resetable;

        private String configValue;

        private String valueType;

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getConfigName() {
            return configName;
        }

        public void setConfigName(String configName) {
            this.configName = configName;
        }

        public String getResetable() {
            return resetable;
        }

        public void setResetable(String resetable) {
            this.resetable = resetable;
        }

        public String getConfigValue() {
            return configValue;
        }

        public void setConfigValue(String configValue) {
            this.configValue = configValue;
        }

        public String getValueType() {
            return valueType;
        }

        public void setValueType(String valueType) {
            this.valueType = valueType;
        }
    }
}
