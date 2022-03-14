package org.etnaframework.jdbc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.RowMapper;

/**
 * 保存class与{@link JdbcRowMapper}的对应关系
 *
 * @author BlackCat
 * @since 2013-3-6
 */
public class JdbcRowMappers {

    private static Map<Class<?>, RowMapper<?>> map = new ConcurrentHashMap<Class<?>, RowMapper<?>>();

    private JdbcRowMappers() {
    }

    @SuppressWarnings("unchecked")
    public static <T> RowMapper<T> getMapper(Class<T> clazz) {
        RowMapper<T> processor = (RowMapper<T>) map.get(clazz);
        if (null == processor) {
            synchronized (map) { // 如果没查到，就生成，进行双次判断，防止重复生成
                if (null == (processor = (RowMapper<T>) map.get(clazz))) {
                    processor = JdbcRowMapper.create(clazz);
                    map.put(clazz, processor);
                }
            }
        }
        return processor;
    }
}
