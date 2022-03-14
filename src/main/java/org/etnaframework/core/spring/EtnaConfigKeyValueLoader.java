package org.etnaframework.core.spring;

import org.etnaframework.core.spring.annotation.Config;

/**
 * 用户自行定制的配置加载器，可适用各种情形，请注意实现类中不能有@{@link Config}
 *
 * @author BlackCat
 * @since 2014-11-6
 */
public interface EtnaConfigKeyValueLoader {

    /**
     * 返回配置值的来源，用于不同的源之间区分，例如这里可以返回 Properties 表示是从配置文件里面来的
     */
    public String getSourceName();

    /**
     * 是否支持同步回写，指动态修改某个@{@link Config}项的值，能将改变同步到数据源（即重启服务后是新值不会还原成原来的），如果此处返回true需要实现syncWriteBack方法
     */
    public boolean canSyncWriteBack();

    /**
     * 触发重新读取全部的自定义配置
     */
    public void reload() throws Throwable;

    /**
     * 如果需要修改某个@{@link Config}项的值时能将改变同步到数据源，请实现该方法
     */
    public void syncWriteBack(String key, Object value);

    /**
     * 获得配置中指定key对应的字符串值，如果不存在返回null
     */
    public String getConfig(String key);
}
