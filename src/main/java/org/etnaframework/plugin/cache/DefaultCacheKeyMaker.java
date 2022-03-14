package org.etnaframework.plugin.cache;

import org.springframework.stereotype.Service;

/**
 * 默认的缓存key生成方法，把所有的参数直接拼起来（无参数方法直接返回空字符串）
 *
 * @author BlackCat
 * @since 2018-03-05
 */
@Service
public class DefaultCacheKeyMaker implements CacheKeyMaker {

    public static final String joiner = "::";

    @Override
    public String generate(Object[] args) {
        StringBuilder sb = new StringBuilder();
        if (null == args || args.length == 0) {
            return "";
        }
        sb.append(args[0]);
        for (int i = 1; i < args.length; i++) {
            sb.append(joiner)
              .append(args[i]);
        }
        return sb.toString();
    }
}
