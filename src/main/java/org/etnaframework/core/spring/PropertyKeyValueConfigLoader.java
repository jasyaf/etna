package org.etnaframework.core.spring;

import java.util.Properties;
import org.etnaframework.core.web.DispatchFilter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;

/**
 * 用于读取properties文件和指定的数据库表，提取键值对用于配置
 *
 * @author BlackCat
 * @since 2014-8-3
 */
public class PropertyKeyValueConfigLoader extends PropertyPlaceholderConfigurer implements EtnaConfigKeyValueLoader {

    @Override
    public String getSourceName() {
        return "Properties";
    }

    /** 上一次的结果，如果重新读取配置出错，就直接使用上次的 */
    private Properties lastProps;

    private Properties props;

    /** 标记是否是第一次load */
    private boolean firstLoad = true;

    /**
     * 继承自PropertyPlaceholderConfigurer时需要重写的方法，在此方法中可以自定义读取文件的方法
     */
    @Override
    protected void processProperties(ConfigurableListableBeanFactory beanFactory, Properties prop) throws BeansException {
        super.processProperties(beanFactory, prop);
        this.props = prop;
    }

    @Override
    public void reload() throws Throwable {
        lastProps = props;
        try {
            props = this.mergeProperties();
            firstLoad = false;
        } catch (Throwable e) {
            // 第一次加载是启动的时候，直接抛出阻断启动即可
            if (firstLoad) {
                throw e;
            }
            DispatchFilter.sendMail("加载配置文件出错", e);
            props = lastProps;
        }
    }

    @Override
    public String getConfig(String key) {
        Object obj = props.get(key);
        return null == obj ? null : obj.toString();
    }

    /**
     * 该实现不支持同步回写
     */
    @Override
    public boolean canSyncWriteBack() {
        return false;
    }

    @Override
    public void syncWriteBack(String key, Object value) {
    }
}
