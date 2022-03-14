package org.etnaframework.jdbc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保存class与{@link BeanSqlMeta}的对应关系
 *
 * @author BlackCat
 * @since 2014-12-27
 */
class BeanSqlMetas {

    private static Map<Class<?>, BeanSqlMeta> map = new ConcurrentHashMap<Class<?>, BeanSqlMeta>();

    private BeanSqlMetas() {
    }

    public static BeanSqlMeta getMeta(Class<?> clazz) {
        BeanSqlMeta processor = map.get(clazz);
        if (null == processor) {
            synchronized (map) { // 如果没查到，就生成，进行双次判断，防止重复生成
                if (null == (processor = map.get(clazz))) {
                    processor = BeanSqlMeta.create(clazz);
                    map.put(clazz, processor);
                }
            }
        }
        return processor;
    }
}
