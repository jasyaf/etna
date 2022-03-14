package org.etnaframework.jdbc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.etnaframework.jdbc.annotation.DbField;

/**
 * 数据库查询sql生成器
 *
 * @author BlackCat
 * @since 2014-12-27
 */
public class SqlBuilder {

    private static final String[] formats;

    static {
        formats = new String[20];
        formats[0] = null; // 特此提醒，不要传长度为0的列表！
        formats[1] = "(?)";
        for (int i = 2; i < formats.length; i++) {
            StringBuilder tmp = new StringBuilder("(");
            for (int j = 1; j < i; j++) {
                tmp.append("?, ");
            }
            tmp.append("?)");
            formats[i] = tmp.toString();
        }
    }

    StringBuilder sql;

    List<Object> args;

    /** 标记是否自动添加过where */
    boolean addWhere = false;

    // sql的保留字，使用时必须圈起来，否则有可能会解析出错，此处可根据不同的sql方言设置不同的圈起方式
    private String prefix = "`";

    private String suffix = "`";

    public SqlBuilder() {
        sql = new StringBuilder();
        args = new ArrayList<>();
    }

    public SqlBuilder(String sql) {
        this.sql = new StringBuilder(sql);
        args = new ArrayList<>();
    }

    public SqlBuilder(String sql, Object... args) {
        this.sql = new StringBuilder(sql);
        this.args = new ArrayList<>(args.length);
        Collections.addAll(this.args, args);
    }

    public SqlBuilder(String sql, Collection<Object> args) {
        this.sql = new StringBuilder(sql);
        this.args = new ArrayList<>(args.size());
        this.args.addAll(args);
    }

    public static SqlBuilder build() {
        return new SqlBuilder();
    }

    public static SqlBuilder build(String sql) {
        return new SqlBuilder(sql);
    }

    public static SqlBuilder build(String sql, Object... args) {
        return new SqlBuilder(sql, args);
    }

    public static SqlBuilder build(String sql, Collection<Object> args) {
        return new SqlBuilder(sql, args);
    }

    /**
     * 使用的数据库方言，用于生成sql语句时生成符合规则的语句，默认是mysql，另外支持postgresql
     */
    public SqlBuilder setDialect(String dialect) {
        if (JdbcTemplate.POSTGRESQL.equals(dialect)) {
            prefix = "";
            suffix = "";
        }
        return this;
    }

    /**
     * 添加sql语句片段和参数
     */
    public SqlBuilder add(Object sqlPart, Object... objs) {
        if (sql.charAt(sql.length() - 1) != ' ') {
            sql.append(' ');
        }
        sql.append(sqlPart);
        Collections.addAll(this.args, objs);
        return this;
    }

    /**
     * 添加sql语句片段和参数
     */
    public SqlBuilder add(Object sqlPart, List<Object> objs) {
        if (sql.charAt(sql.length() - 1) != ' ') {
            sql.append(' ');
        }
        sql.append(sqlPart);
        Collections.addAll(this.args, objs);
        return this;
    }

    /**
     * 添加sql语句参数
     */
    public SqlBuilder addArg(Object arg) {
        args.add(arg);
        return this;
    }

    /**
     * <pre>
     * 本方法用于生成多条件查询的sql，自动添加where/and等条件，减少相关判断的代码量
     *
     * 条件个数不会完全确定，要根据前端是否传入对应的条件才能确定是否参与生成sql语句
     * 如果之前没有通过本方法添加where就会自动添加，参考代码如下：
     *
     * SqlBuilder sql = new SqlBuilder("select * from test_table");
     * if (null != name) {
     *     sql.addWhereAnd("name like ?", "'%" + StringTools.escapeSqlLikePattern(name) + "%'");
     * }
     * if (null != minAge) {
     *     sql.addWhereAnd("age>?", minAge);
     * }
     * if (null != maxAge) {
     *     sql.addWhereAnd("age<=?", maxAge);
     * }
     * if (CollectionTools.isNotEmpty(list)) {
     *     sql.addWhereOr("current_status in").addInArgs(list);
     * }
     * </pre>
     */
    public SqlBuilder addWhereAnd(Object sqlPart, Object... objs) {
        if (addWhere) {
            add("and").add(sqlPart, objs);
        } else {
            add("where").add(sqlPart, objs);
            addWhere = true;
        }
        return this;
    }

    /**
     * <pre>
     * 本方法用于生成多条件查询的sql，自动添加where/or等条件，减少相关判断的代码量
     *
     * 条件个数不会完全确定，要根据前端是否传入对应的条件才能确定是否参与生成sql语句
     * 如果之前没有通过本方法添加where就会自动添加，参考代码如下：
     *
     * SqlBuilder sql = new SqlBuilder("select * from test_table");
     * if (null != name) {
     *     sql.addWhereAnd("name like ?", "'%" + StringTools.escapeSqlLikePattern(name) + "%'");
     * }
     * if (null != minAge) {
     *     sql.addWhereAnd("age>?", minAge);
     * }
     * if (null != maxAge) {
     *     sql.addWhereAnd("age<=?", maxAge);
     * }
     * if (CollectionTools.isNotEmpty(list)) {
     *     sql.addWhereOr("current_status in").addInArgs(list);
     * }
     * </pre>
     */
    public SqlBuilder addWhereOr(Object sqlPart, Object... objs) {
        if (addWhere) {
            add("or").add(sqlPart, objs);
        } else {
            add("where").add(sqlPart, objs);
            addWhere = true;
        }
        return this;
    }

    /**
     * 生成指定数量的?符号，用,分隔（带缓存）
     *
     * @param argsLen 生成的数量
     */
    String getMarks(int argsLen) {
        if (argsLen == 0) {
            throw new IllegalArgumentException("argsLen must >0");
        }
        if (formats.length > argsLen) {
            return formats[argsLen];
        }
        // 超过一定数量就动态生成，不再缓存
        StringBuilder tmp = new StringBuilder("(");
        for (int i = 1; i < argsLen; i++) {
            tmp.append("?, ");
        }
        tmp.append("?)");
        return tmp.toString();
    }

    /**
     * 禁止null传入，否则sql生成肯定不正确，到了jdbc层抛出的异常很难看出问题来
     */
    private void _checkNull(Object obj) {
        if (null == obj) {
            throw new NullPointerException();
        }
    }

    /**
     * 添加展开XX in (...) 中的列表参数的sql，前缀sql写成where xxx in即可，程序展开时会自动加()
     *
     * @param list 待展开的参数列表
     */
    public SqlBuilder addInArgs(int[] list) {
        _checkNull(list);
        add(getMarks(list.length));
        for (Object obj : list) {
            args.add(obj);
        }
        return this;
    }

    /**
     * 添加展开XX in (...) 中的列表参数的sql，前缀sql写成where xxx in即可，程序展开时会自动加()
     *
     * @param list 待展开的参数列表
     */
    public SqlBuilder addInArgs(long[] list) {
        _checkNull(list);
        add(getMarks(list.length));
        for (Object obj : list) {
            args.add(obj);
        }
        return this;
    }

    /**
     * 添加展开XX in (...) 中的列表参数的sql，前缀sql写成where xxx in即可，程序展开时会自动加()
     *
     * @param list 待展开的参数列表
     */
    public SqlBuilder addInArgs(char[] list) {
        _checkNull(list);
        add(getMarks(list.length));
        for (Object obj : list) {
            args.add(obj);
        }
        return this;
    }

    /**
     * 添加展开XX in (...) 中的列表参数的sql，前缀sql写成where xxx in即可，程序展开时会自动加()
     *
     * @param list 待展开的参数列表
     */
    public SqlBuilder addInArgs(boolean[] list) {
        _checkNull(list);
        add(getMarks(list.length));
        for (Object obj : list) {
            args.add(obj);
        }
        return this;
    }

    /**
     * 添加展开XX in (...) 中的列表参数的sql，前缀sql写成where xxx in即可，程序展开时会自动加()
     *
     * @param list 待展开的参数列表
     */
    public SqlBuilder addInArgs(byte[] list) {
        _checkNull(list);
        add(getMarks(list.length));
        for (Object obj : list) {
            args.add(obj);
        }
        return this;
    }

    /**
     * 添加展开XX in (...) 中的列表参数的sql，前缀sql写成where xxx in即可，程序展开时会自动加()
     *
     * @param list 待展开的参数列表
     */
    public SqlBuilder addInArgs(float[] list) {
        _checkNull(list);
        add(getMarks(list.length));
        for (Object obj : list) {
            args.add(obj);
        }
        return this;
    }

    /**
     * 添加展开XX in (...) 中的列表参数的sql，前缀sql写成where xxx in即可，程序展开时会自动加()
     *
     * @param list 待展开的参数列表
     */
    public SqlBuilder addInArgs(double[] list) {
        _checkNull(list);
        add(getMarks(list.length));
        for (Object obj : list) {
            args.add(obj);
        }
        return this;
    }

    /**
     * 添加展开XX in (...) 中的列表参数的sql，前缀sql写成where xxx in即可，程序展开时会自动加()
     *
     * @param list 待展开的参数列表
     */
    public SqlBuilder addInArgs(short[] list) {
        _checkNull(list);
        add(getMarks(list.length));
        for (Object obj : list) {
            args.add(obj);
        }
        return this;
    }

    /**
     * 添加展开XX in (...) 中的列表参数的sql，前缀sql写成where xxx in即可，程序展开时会自动加()
     *
     * @param list 待展开的参数列表
     */
    public SqlBuilder addInArgs(Object[] list) {
        _checkNull(list);
        add(getMarks(list.length));
        for (Object obj : list) {
            args.add(obj);
        }
        return this;
    }

    /**
     * 添加展开XX in (...) 中的列表参数的sql，前缀sql写成where xxx in即可，程序展开时会自动加()
     *
     * @param list 待展开的参数列表
     */
    public <T> SqlBuilder addInArgs(Collection<T> list) {
        _checkNull(list);
        add(getMarks(list.size()));
        for (Object obj : list) {
            args.add(obj);
        }
        return this;
    }

    /**
     * 添加展开XX in (...) 中的列表参数的sql，前缀sql写成where xxx in即可，程序展开时会自动加()
     *
     * @param list 待展开的参数列表
     */
    public <T> SqlBuilder addInArgs(T[] list, ValueGetter<T> adapter) {
        _checkNull(list);
        add(getMarks(list.length));
        for (T e : list) {
            args.add(adapter.getValue(e));
        }
        return this;
    }

    /**
     * 添加展开XX in (...) 中的列表参数的sql，前缀sql写成where xxx in即可，程序展开时会自动加()
     *
     * @param list 待展开的参数列表
     */
    public <T> SqlBuilder addInArgs(Collection<T> list, ValueGetter<T> adapter) {
        _checkNull(list);
        add(getMarks(list.size()));
        for (T e : list) {
            args.add(adapter.getValue(e));
        }
        return this;
    }

    /**
     * <pre>
     * 将传入bean中的非空基本型字段按照XX=aa, YY=bb这样的方式添加到sql中去
     *
     * null值的字段默认不参与sql生成，如果想让某个字段在null时也参与生成sql
     * 请在该字段加注解@{@link DbField}并设置@{@link DbField#writeNullToDb()}=true
     * </pre>
     */
    public SqlBuilder addEqualsBean(Object bean) {
        _checkNull(bean);
        BeanSqlMeta meta = BeanSqlMetas.getMeta(bean.getClass());
        meta.addEqualsBeanSql(prefix, suffix, this, bean);
        return this;
    }

    /**
     * <pre>
     *
     * 将传入bean中的非空基本型字段按照XX=aa and YY=bb这样的方式添加到sql中去,where 子句适用
     * 默认后面会加入一个1=1的子句
     * null值的字段默认不参与sql生成，如果想让某个字段在null时也参与生成sql
     * 请在该字段加注解@{@link DbField}并设置@{@link DbField#writeNullToDb()}=true
     * </pre>
     */
    public SqlBuilder addWhereBeanSql(Object bean) {
        _checkNull(bean);
        BeanSqlMeta meta = BeanSqlMetas.getMeta(bean.getClass());
        meta.addWhereBeanSql(prefix, suffix, this, bean);
        return this;
    }

    /**
     * <pre>
     * 将传入bean中的非空基本型字段按照(XX, YY, ZZ) values (aa, bb, cc)的方式添加到sql中去
     *
     * null值的字段默认不参与sql生成，如果想让某个字段在null时也参与生成sql
     * 请在该字段加注解@{@link DbField}并设置@{@link DbField#writeNullToDb()}=true
     * </pre>
     */
    public SqlBuilder addInsertBean(Object bean) {
        _checkNull(bean);
        BeanSqlMeta meta = BeanSqlMetas.getMeta(bean.getClass());
        meta.addInsertBeanSql(prefix, suffix, this, bean);
        return this;
    }

    /**
     * 禁止null传入，否则sql生成肯定不正确，到了jdbc层抛出的异常很难看出问题来
     */
    private void _checkNull(Map<String, ? extends Object> map) {
        if (null == map) {
            throw new NullPointerException();
        } else if (map.isEmpty()) {
            throw new IllegalArgumentException("empty map");
        }
    }

    /**
     * 将传入Map<String,Object>中的非空基本型字段按照XX=aa, YY=bb这样的方式添加到sql中去
     */
    public SqlBuilder addEqualsBean(Map<String, ? extends Object> map) {
        _checkNull(map);
        BeanSqlMeta.addEqualsBeanSql(prefix, suffix, this, map);
        return this;
    }

    /**
     * 将传入Map<String,Object>中的非空基本型字段按照(XX, YY, ZZ) values (aa, bb, cc)的方式添加到sql中去
     */
    public SqlBuilder addInsertBean(Map<String, ? extends Object> map) {
        _checkNull(map);
        BeanSqlMeta.addInsertBeanSql(prefix, suffix, this, map);
        return this;
    }

    /**
     * 用于删除sql最后的,等符号，防止生成的sql有语法错误
     */
    void removeTail() {
        if (sql.length() > 0) {
            sql.deleteCharAt(sql.length() - 1);
        }
    }

    @Override
    public String toString() {
        return "SqlBuilder [sql=" + sql + " args=" + args + "]";
    }

    /**
     * 生成countSql
     * select XXXX替换成select count(1)
     * 去掉后面的order部分子句
     */
    public SqlBuilder toCountSqlBuilder() {
        String countSql = sql.toString();
        String sqlToLowcase = countSql.toLowerCase();
        int groupby_index = sqlToLowcase.indexOf("group by");
        //当语句中包含group by 子句的时候,计算sql的行数,应该用子查询,因为count也是分类的聚合函数
        if (groupby_index > 0) {
            return SqlBuilder.build("select count(1) from (" + sqlToLowcase + ") t1 ", args.toArray());
        }
        int from = sqlToLowcase.indexOf(" from ");
        int orderby = sqlToLowcase.indexOf("order by ");
        if (from > 0) {
            if (orderby > 0) {
                return SqlBuilder.build("select count(1)" + countSql.substring(from, orderby), args.toArray());
            }
            return SqlBuilder.build("select count(1)" + countSql.substring(from), args.toArray());
        }
        throw new RuntimeException("why no 'from' keyword?");
    }

    /**
     * 用于提取集合中的元素的辅助类
     */
    interface ValueGetter<T> {

        Object getValue(T t);
    }
}
