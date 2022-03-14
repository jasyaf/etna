package org.etnaframework.jdbc;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Date;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.StringTools;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.JdbcUtils;

/**
 * SQL 获取两列数据，封装成Map，第一列为key，第二列为Value,
 * Created by yuanhaoliang on 2016-09-21.
 */
public class KeyValueMultimapResultSetExtractor<T> implements ResultSetExtractor<Multimap<String, T>> {


    private Class<T> type;

    public KeyValueMultimapResultSetExtractor(Class<T> type) {
        this.type = type;
    }

    @Override
    public Multimap<String, T> extractData(ResultSet rs) throws SQLException, DataAccessException {
        Multimap<String, T> map = ArrayListMultimap.create();

        boolean columnCountCheck = false;
        while (rs.next()) {
            if (!columnCountCheck) { // 列数检测一次就可以了。
                ResultSetMetaData rsmd = rs.getMetaData();
                int columnCount = rsmd.getColumnCount();
                if (columnCount != 2) {
                    throw new SQLException("数据集列数必须为2，目前获取到的数据集列数：" + columnCount);
                }
                columnCountCheck = true;
            }
            try {
                Object _key = JdbcUtils.getResultSetValue(rs, 1);
                String key = _key == null ? "null" : _key.toString();
                Object obj = JdbcUtils.getResultSetValue(rs, 2);

                if (obj == null) {
                    map.put(key, null);
                } else {
                    if (obj instanceof Date) {
                        obj = new Datetime((Date) obj);
                    }
                    if (type.isInstance(obj)) {
                        map.put(key, (T) obj);
                    } else {
                        map.put(key, StringTools.valueOf(obj.toString(), type));
                    }
                }
            } catch (SQLException ex) { // 如果转换失败就不赋值，如0000-00-00 00:00:00的转换失败问题
            } catch (Exception e) {
            }
        }
        return map;
    }
}
