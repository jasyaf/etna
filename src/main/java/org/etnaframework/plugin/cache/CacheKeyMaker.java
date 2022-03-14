package org.etnaframework.plugin.cache;

/**
 * 缓存的key生成规则
 *
 * @author BlackCat
 * @since 2018-03-05
 */
public interface CacheKeyMaker {

    String generate(Object[] args);
}

