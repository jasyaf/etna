package org.etnaframework.jdbc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.etnaframework.core.util.BeanTools;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.KeyValueGetter.DbMap;
import org.etnaframework.core.util.ReflectionTools;
import org.etnaframework.core.util.ReflectionTools.BeanFieldValueSetter;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.jdbc.annotation.DbField;
import org.etnaframework.jdbc.exception.BeanProcessException;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;

/**
 * 根据bean的字段生成查询结果包装辅助类{@link RowMapper}
 *
 * @author BlackCat
 * @since 2014-12-27
 */
abstract class JdbcRowMapper<T> implements RowMapper<T> {

    /** 字段的setter方法过滤器，setter只有一个参数，非static，方法名以set开头 */
    static MethodFilter setterMethodFilter = method -> method.getParameterTypes().length == 1 && !Modifier.isStatic(
        method.getModifiers()) && method.getName().length() > "set".length() && method.getName().startsWith("set");

    /** 目标bean对应的class */
    protected Class<T> clazz;

    /** 从结果集构造对象时需要的字段（表字段名->类字段） */
    protected Map<String, BeanFieldValueSetter> setters;

    private JdbcRowMapper() {
    }

    static <T> RowMapper<T> create(Class<T> clazz) {
        if (Collection.class.isAssignableFrom(clazz) || Map.class.isAssignableFrom(clazz)) {
            throw new BeanProcessException(
                "不支持集合类型" + clazz.getName() + "，如果希望返回" + Map.class.getSimpleName() + "可使用" + JdbcTemplate.class.getSimpleName() + ".query*的返回" + DbMap.class
                    .getSimpleName() + "的重载方法");
        }
        if (BeanTools.isPrimitiveWrapperType(clazz)) { // 如果是简单封装类型就只生成简单的rowMapper
            return new OneColumnRowMapper<T>(clazz);
        }
        Collection<Field> fields = ReflectionTools.getAllFieldsInSourceCodeOrder(clazz, null);
        if (fields.isEmpty()) { // 空类（即没有任何字段的类）是不允许的，会抛出异常
            throw new BeanProcessException("类" + clazz.getName() + "不能是空的，必须至少要有一个field");
        }
        Map<String, BeanFieldValueSetter> setters = new HashMap<>(fields.size());
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue; // 不考虑static的或final的
            }
            DbField cc = field.getAnnotation(DbField.class);
            boolean readFromDb = true;
            String name = field.getName();
            Class<?> fieldClass = field.getType();
            if (null != cc) { // 如果没有加注解就直接使用类字段名作为数据库字段名
                name = StringTools.isEmpty(cc.name()) ? field.getName() : cc.name();
                readFromDb = cc.readFromDb();
                fieldClass = Object.class.equals(cc.clazz()) ? field.getType() : cc.clazz();
            }
            if (!readFromDb) {
                continue;
            }
            if (BeanTools.isNotPrimitiveWrapperType(field.getType()) && fieldClass.equals(field.getType())) {
                // 字段类型不是基本类型而且没有自定义setter方法
                // 如果自定义setter方法需要自定义fieldClass则fieldClass与字段class会不同。
                throw new BeanProcessException(
                    "类" + clazz.getName() + "的字段" + field.getName() + "只能是在" + BeanTools.class.getName() + "中定义的简单包装类型，如果不想为其赋值，请加@" + DbField.class
                        .getSimpleName() + "并设置readFromDb=false,或者填写clazz并提供setter方法");
            }

            BeanFieldValueSetter setter;
            if (!fieldClass.equals(field.getType())) {
                // 如果使用了自定义setter,字段类型跟数据库储存类型不一致，在setter里做预处理的,直接获取setter注入
                setter = BeanFieldValueSetter.createBySetter(clazz, field.getName(), fieldClass);
            } else {
                setter = BeanFieldValueSetter.create(clazz, field.getName(), field.getType());
            }
            setters.put(name, setter);
        }

        // 把所有setter方法当字段注入
        Collection<Method> methods = ReflectionTools.getAllMethodsInSourceCodeOrder(clazz, setterMethodFilter);

        for (Method method : methods) {
            String name = StringTools.headLetterToLowerCase(method.getName().substring("set".length()));
            if (setters.containsKey(name)) {
                // 如果存在就不创建了。
                continue;
            }

            // setter只有一个参数，setterMethodFilter已做过滤，保证只有一个参数
            Class<?> fieldClass = method.getParameterTypes()[0];

            method.setAccessible(true);
            BeanFieldValueSetter setter = new BeanFieldValueSetter(name, fieldClass) {

                @Override
                public void setValue(Object bean, Object value) {
                    ReflectionUtils.invokeMethod(method, bean, value);
                }
            };
            setters.put(name, setter);
        }

        return new SimpleBeanRowMapper<T>(clazz, setters);
    }

    /**
     * 当结果集只有一列时，可用于包装的简单类型
     */
    static class OneColumnRowMapper<T> extends JdbcRowMapper<T> {

        public OneColumnRowMapper(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public T mapRow(ResultSet rs, int rowNum) throws SQLException {
            Object value = null;
            try {
                value = JdbcUtils.getResultSetValue(rs, 1, clazz);
            } catch (SQLException ex) { // 取值出现问题时，不处理，赋给null
            }
            if (null != value) {
                if (Datetime.class.equals(clazz)) { // 对新的Datetime封装类型的支持
                    value = new Datetime((Date) value);
                }
            }
            return clazz.cast(value);
        }
    }

    /**
     * 一个简单的从结果集包装javaBean的包装器类
     */
    static class SimpleBeanRowMapper<T> extends JdbcRowMapper<T> {

        public SimpleBeanRowMapper(Class<T> clazz, Map<String, BeanFieldValueSetter> setters) {
            this.clazz = clazz;
            this.setters = setters;
        }

        @Override
        public T mapRow(ResultSet rs, int rowNum) throws SQLException {
            T result = BeanUtils.instantiate(clazz);
            ResultSetMetaData rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = JdbcUtils.lookupColumnName(rsmd, i);
                BeanFieldValueSetter setter = setters.get(columnName);
                if (null != setter) { // 由于是从结果集反推到JavaBean的，为了提高性能，建议如果不是完全表对应的话就没有必要用select *来取结果
                    Object value = null;
                    try {
                        value = JdbcUtils.getResultSetValue(rs, i, setter.getType());
                    } catch (SQLException ex) { // 取值出现问题时，不处理，赋给null
                    }
                    if (null != value) {
                        if (Datetime.class.equals(setter.getType())) { // 对新的Datetime封装类型的支持
                            value = new Datetime((Date) value);
                        }
                        setter.setValue(result, value);
                    }
                }
            }
            return result;
        }
    }

    /**
     * 将SQL查询的结果封装成{@link DbMap}，参考自org.springframework.jdbc.core.ColumnMapRowMapper
     */
    static class ColumnDbMapRowMapper extends JdbcRowMapper<DbMap> {

        @Override
        public DbMap mapRow(ResultSet rs, int rowNum) throws SQLException {
            ResultSetMetaData rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();
            DbMap dbMapOfColValues = new DbMap();
            for (int i = 1; i <= columnCount; i++) {
                String key = JdbcUtils.lookupColumnName(rsmd, i);
                try {
                    Object obj = JdbcUtils.getResultSetValue(rs, i);
                    if (obj instanceof Date) {
                        obj = new Datetime((Date) obj);
                    }
                    dbMapOfColValues.put(key, obj);
                } catch (SQLException ex) { // 如果转换失败就不赋值，如0000-00-00 00:00:00的转换失败问题
                }
            }
            return dbMapOfColValues;
        }
    }
}
