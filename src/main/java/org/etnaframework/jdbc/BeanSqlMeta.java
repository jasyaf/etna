package org.etnaframework.jdbc;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.etnaframework.core.util.BeanTools;
import org.etnaframework.core.util.ReflectionTools;
import org.etnaframework.core.util.ReflectionTools.BeanFieldValueGetter;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.jdbc.annotation.DbField;
import org.etnaframework.jdbc.exception.BeanProcessException;

/**
 * 根据bean的字段生成SQL辅助类
 *
 * @author BlackCat
 * @since 2014-12-27
 */
class BeanSqlMeta {

    /** 目标bean对应的class */
    protected Class<?> clazz;

    /** 目标bean中的字段的获取值方法 */
    List<Getter> getters;

    private BeanSqlMeta(Class<?> clazz, List<Getter> getters) {
        this.clazz = clazz;
        this.getters = getters;
    }

    /**
     * 将传入Map<String,Object>中的非空基本型字段按照XX=aa, YY=bb这样的方式添加到sql中去
     */
    static void addEqualsBeanSql(String prefix, String suffix, SqlBuilder sql, Map<String, ? extends Object> map) {
        for (Entry<String, ? extends Object> e : map.entrySet()) {
            Object value = e.getValue();
            if (null != value) { // 只要非空就添加sql
                sql.add(prefix + e.getKey() + suffix + "=?,", value);
            }
        }
        sql.removeTail();
    }

    /**
     * 将传入Map<String,Object>中的非空基本型字段按照(XX, YY, ZZ) values (aa, bb, cc)的方式添加到sql中去
     */
    static void addInsertBeanSql(String prefix, String suffix, SqlBuilder sql, Map<String, ? extends Object> map) {
        int count = 0;
        StringBuilder sb = new StringBuilder();
        for (Entry<String, ? extends Object> e : map.entrySet()) {
            Object value = e.getValue();
            if (null != value) { // 只要非空就添加到sql
                sb.append(prefix).append(e.getKey()).append(suffix).append(", ");
                sql.addArg(value);
                count++;
            }
        }
        if (count > 0) {
            sb.deleteCharAt(sb.length() - 1).deleteCharAt(sb.length() - 1);
            sql.add("(" + sb + ")");
            sql.add("values").add(sql.getMarks(count));
        }
    }

    static BeanSqlMeta create(Class<?> clazz) {
        if (Collection.class.isAssignableFrom(clazz) || Map.class.isAssignableFrom(clazz)) {
            throw new BeanProcessException("不支持集合类型" + clazz.getName() + "，请检查传入的bean的类型");
        }
        if (BeanTools.isPrimitiveWrapperType(clazz)) {
            throw new BeanProcessException("不支持简单封装类型" + clazz.getName() + "，请检查传入的bean的类型");
        }
        // 如果有这个field，首先找其get方法，如果找不到，就将直接通过反射取值
        Collection<Field> fields = ReflectionTools.getAllFieldsInSourceCodeOrder(clazz, null);

        List<Getter> list = new ArrayList<>(fields.size());
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue; // 不考虑static的或final的
            }
            DbField cc = field.getAnnotation(DbField.class);
            boolean writeToDb = true;
            String name = field.getName();
            boolean writeNullToDb = false;
            boolean preparedStatementArg = true;
            Class<?> fieldClass = field.getType();
            if (null != cc) { // 如果没有加注解就直接使用类字段名作为数据库字段名
                name = StringTools.isEmpty(cc.name()) ? field.getName() : cc.name();
                writeToDb = cc.writeToDb();
                preparedStatementArg = cc.preparedStatementArg();
                writeNullToDb = cc.writeNullToDb();
                fieldClass = Object.class.equals(cc.clazz()) ? field.getType() : cc.clazz();
            }
            if (!writeToDb) {
                continue;
            }
            if (BeanTools.isNotPrimitiveWrapperType(field.getType()) && fieldClass.equals(field.getType())) {
                // 字段类型不是基本类型而且没有自定义setter方法
                // 如果自定义getter方法需要自定义fieldClass则fieldClass与字段class会不同。
                throw new BeanProcessException(
                    "类" + clazz.getName() + "的字段" + field.getName() + "只能是在" + BeanTools.class.getName() + "中定义的简单包装类型，如果不想为其赋值，请加@" + DbField.class.getSimpleName() + "并设置writeToDb=false");
            }
            BeanFieldValueGetter getter ;

            if (!fieldClass.equals(field.getType())) {
                // 如果使用了自定义getter,字段类型跟数据库储存类型不一致，在getter里做预处理的,直接获取getter注入
                getter = BeanFieldValueGetter.createByGetter(clazz, field.getName(), fieldClass);
            } else {
                getter = BeanFieldValueGetter.create(clazz, field.getName(), field.getType());
            }

            list.add(new Getter(name, preparedStatementArg, writeNullToDb, getter));
        }
        if (list.isEmpty()) { // 空类（即没有任何字段的类）是不允许的，会抛出异常
            throw new BeanProcessException("类" + clazz.getName() + "不能是空的，必须至少要有一个field或get方法");
        }
        return new BeanSqlMeta(clazz, list);
    }

    /**
     * 将传入bean中的非空基本型字段按照XX=aa, YY=bb这样的方式添加到sql中去
     */
    void addEqualsBeanSql(String prefix, String suffix, SqlBuilder sql, Object bean) {
        boolean add = false;
        for (Getter g : getters) {
            Object value = g.get(bean);
            if (null != value || g.writeNullToDb) { // 只要非空就添加sql，或者是设置了允许null添加
                if (g.preparedStatementArg) {
                    sql.add(prefix + g.name + suffix + "=?,", value);

                } else {
                    sql.add(StringTools.mergeSql(prefix + g.name + suffix + "=?,", value));
                }
                add = true;
            }
        }
        if (add) {
            sql.removeTail();
        }
    }

    /**
     *
     * 将传入bean中的非空基本型字段按照XX=aa and  YY=bb这样的方式添加到sql中去
     */
    void addWhereBeanSql(String prefix, String suffix, SqlBuilder sql, Object bean) {
        boolean add = false;
        for (Getter g : getters) {
            Object value = g.get(bean);
            if (null != value) { // 只要非空就添加sql，或者是设置了允许null添加
                if (g.preparedStatementArg) {
                    sql.add(prefix + g.name + suffix + "=? and ", value);
                } else {
                    sql.add(StringTools.mergeSql(prefix + g.name + suffix + "=? and ", value));
                }
                add = true;
            }
        }
        if (add) {
            sql.add(" 1=1 ");
        }
    }

    /**
     * 将传入bean中的非空基本型字段按照(XX, YY, ZZ) values (aa, bb, cc)的方式添加到sql中去
     */
    void addInsertBeanSql(String prefix, String suffix, SqlBuilder sql, Object bean) {
        int count = 0;
        StringBuilder mid = new StringBuilder();
        StringBuilder tail = new StringBuilder();
        for (Getter g : getters) {
            Object value = g.get(bean);
            if (null != value || g.writeNullToDb) { // 只要非空就添加sql，或者是设置了允许null添加
                mid.append(prefix).append(g.name).append(suffix).append(", ");
                if (g.preparedStatementArg) {
                    tail.append("?, ");
                    sql.addArg(value);
                } else {
                    tail.append(StringTools.mergeSql("?, ", value));
                }
                count++;
            }
        }
        if (count > 0) {
            mid.deleteCharAt(mid.length() - 1).deleteCharAt(mid.length() - 1);
            tail.deleteCharAt(tail.length() - 1).deleteCharAt(tail.length() - 1);
            sql.add("(" + mid + ")");
            sql.add("values(" + tail + ")");
        }
    }

    /**
     * 从对象构造sql时预先提取的信息
     */
    static class Getter {

        /** 对应的数据库表字段名 */
        String name;

        /** 在生成sql语句时，为true表示将通过{@link PreparedStatement}来设置值，为false表示将值直接生成到sql语句里面，用于适应一些特殊情况，默认为true */
        boolean preparedStatementArg;

        /** 当字段值为null时，是否参与sql生成 */
        boolean writeNullToDb;

        /** 对应的bean字段值的提取工具类 */
        private BeanFieldValueGetter valueGetter;

        Getter(String name, boolean preparedStatementArg, boolean writeNullToDb, BeanFieldValueGetter valueGetter) {
            this.name = name;
            this.preparedStatementArg = preparedStatementArg;
            this.writeNullToDb = writeNullToDb;
            this.valueGetter = valueGetter;
        }

        Object get(Object bean) {
            return valueGetter.getValue(bean);
        }

        @Override
        public String toString() {
            return "Getter [name=" + name + ", preparedStatementArg=" + preparedStatementArg + ", valueGetter=" + valueGetter + "]";
        }
    }
}
