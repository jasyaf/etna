package org.etnaframework.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.util.BeanTools;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.KeyValueGetter.DbMap;
import org.etnaframework.core.util.KeyValueGetter.WrappedMap;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.jdbc.JdbcRowMapper.ColumnDbMapRowMapper;
import org.etnaframework.jdbc.annotation.DbField;
import org.etnaframework.jdbc.exception.SqlExecuteException;
import org.etnaframework.plugin.stat.jdbc.StatJdbcUtils;
import org.slf4j.Logger;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;
import com.google.common.collect.Multimap;

/**
 * 数据库对象基本操作模板，基于Spring的JdbcTemplate根据业务需要改造而来
 *
 * @author BlackCat
 * @since 2013-3-6
 */
public class JdbcTemplate {

    public static final String MYSQL = "mysql";

    public static final String POSTGRESQL = "postgresql";

    protected DataSource dataSource;

    protected Logger log = Log.getLogger();

    protected org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** 使用的数据库方言，用于生成sql语句时生成符合规则的语句，默认是mysql，另外支持postgresql */
    String dialect = MYSQL;

    /** 当前数据库方言是否是mysql */
    boolean isMySql = true;

    /** 当网络不稳定时，执行重试的次数 */
    private int retryTimes = 1;

    public JdbcTemplate(DataSource dataSource) {
        this.jdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        this.dataSource = dataSource;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setDialect(String dialect) {
        // 仅支持mysql和postgresql
        if (!MYSQL.equals(dialect) && !POSTGRESQL.equals(dialect)) {
            throw new IllegalArgumentException("不支持的数据库方言 " + dialect);
        }
        this.dialect = dialect;
        this.isMySql = MYSQL.equals(dialect);
    }

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    /**
     * 执行insert操作，并获取到自增主键的值（如果想返回影响的行数，请使用update）
     *
     * @param sql 插入sql
     * @param args 插入sql语句中的?参数的值
     *
     * @return 如果能获取到，将返回自增主键的值，否则返回-1
     */
    public long insert(String sql, Object... args) {
        long id = -1L;
        Throwable ex = null;
        String logString = null;
        long start = System.currentTimeMillis();
        try {
            for (int i = 0; i <= retryTimes; i++) { // 为了应对网络不稳定的情况，当执行SQL失败时，重试一下
                try {
                    id = jdbcTemplate.execute(new SqlCreatorForInsert(sql, args), new PreparedStatementCallback<Long>() {

                        @Override
                        public Long doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
                            ps.execute();
                            try { // 没有自增主键的表，下面的执行会抛出异常，返回-1，此时就不能根据返回值来判断是否添加成功了
                                ResultSet rs = ps.getGeneratedKeys(); // 获取自增主键
                                if (rs.next()) {
                                    return rs.getLong(1);
                                }
                            } catch (SQLException e) {
                            }
                            return -1L;
                        }
                    });
                    break;
                } catch (CannotGetJdbcConnectionException | TransientDataAccessResourceException | ConcurrencyFailureException ext) {
                    if (i == retryTimes) {
                        throw ext;
                    }
                }
            }
            return id;
        } catch (Throwable e) {
            ex = e;
            logString = StringTools.mergeSql(sql, args);
            throw new SqlExecuteException(logString, e);
        } finally {
            if (log.isDebugEnabled()) {
                if (null == ex) {
                    logString = StringTools.mergeSql(sql, args) + " -- " + (System.currentTimeMillis() - start) + "ms|result: " + id;
                    log.debug(logString);
                } else {
                    logString += " -- " + StringTools.printThrowable(ex);
                    log.error(logString);
                }
            }
            StatJdbcUtils.record(sql, 1, start);
        }
    }

    /**
     * 执行insert操作，并获取到自增主键的值（如果想返回影响的行数，请使用update）
     *
     * @param sql 插入sql
     * @param args 插入sql语句中的?参数的值
     *
     * @return 如果能获取到，将返回自增主键的值，否则返回-1
     */
    public long insert(String sql, Collection<Object> args) {
        return insert(sql, args.toArray());
    }

    /**
     * 执行insert操作，并获取到自增主键的值（如果想返回影响的行数，请使用update）
     *
     * @param sql 插入sql以及其参数
     *
     * @return 如果能获取到，将返回自增主键的值，否则返回-1
     */
    public long insert(SqlBuilder sql) {
        return insert(sql.sql.toString(), sql.args);
    }

    /**
     * 将一个javabean生成sql插入到数据库，通过反射获取到bean的所有非空字段自动生成sql
     *
     * @param prefixSql sql的前半部分，如insert into tablename，后面的values一段将由程序自动生成（mysql将使用set xx=aa, yy=bb的语法）
     * @param bean 预备插入的javabean，其中只要是非null的字段，都会参与到sql的生成，如果不想某个字段参与生成，请在字段上加@{@link DbField}设置
     *
     * @return 如果能获取到，将返回自增主键的值，否则返回-1
     */
    public long insertOne(String prefixSql, Object bean) {
        SqlBuilder sql = new SqlBuilder(prefixSql).setDialect(dialect);
        // 如果是mysql就使用set xx=aa, yy=bb这样的方式来insert，这样在日志中显得更直观
        if (isMySql) {
            sql.add("set").addEqualsBean(bean);
        } else {
            sql.addInsertBean(bean);
        }
        return insert(sql);
    }

    /**
     * 将一个Map<String,Object>生成sql插入到数据库，所有非空value自动生成sql
     *
     * @param prefixSql sql的前半部分，如insert into tablename，后面的values一段将由程序自动生成（mysql将使用set xx=aa, yy=bb的语法）
     * @param map 预备插入的Map<String,Object>，其中只要是非null的value，都会参与到sql的生成
     *
     * @return 如果能获取到，将返回自增主键的值，否则返回-1
     */
    public long insertOne(String prefixSql, Map<String, Object> map) {
        SqlBuilder sql = new SqlBuilder(prefixSql).setDialect(dialect);
        // 如果是mysql就使用set xx=aa, yy=bb这样的方式来insert，这样在日志中显得更直观
        if (isMySql) {
            sql.add("set").addEqualsBean(map);
        } else {
            sql.addInsertBean(map);
        }
        return insert(sql);
    }

    /**
     * <pre>
     * 将一个javabean生成sql插入到数据库，通过反射获取到bean的所有非空字段自动生成sql
     *
     * 这里是【mysql专用】的方法，使用insert into ... on duplicate key update语法
     * 能通过一条sql实现：如果数据不存在，就插入数据，如果已经存在就触发更新
     * </pre>
     *
     * @param prefixSql sql的前半部分，如insert into tablename，后面的values一段将由程序自动生成（mysql将使用set xx=aa, yy=bb的语法）
     * @param bean 预备插入的javabean，其中只要是非null的字段，都会参与到sql的生成<br/>
     * ●如果不想某个字段参与生成， 请在字段上加注解@{@link DbField}并设置@{@link DbField#writeToDb()}=false<br/>
     * ●如果想让null值的字段参与生成，请在字段上加注解@{@link DbField}并设置@{@link DbField#writeNullToDb()}=true
     */
    public void upsertOne(String prefixSql, Object bean) {
        if (!isMySql) {
            throw new IllegalStateException("该方法仅支持mysql，不支持" + dialect);
        }
        SqlBuilder sql = new SqlBuilder(prefixSql).setDialect(dialect);
        // 使用mysql特有的insert into tablename set xx=yy, aa=bb语法，而不是insert into tablename values语法，这样在日志中显得更直观
        sql.add("set").addEqualsBean(bean);
        sql.add("on duplicate key update");
        sql.addEqualsBean(bean);
        update(sql);
    }

    /**
     * <pre>
     * 将一个Map<String,Object>生成sql插入到数据库，所有非空value自动生成sql
     *
     * 这里是【mysql专用】的方法，使用insert into ... on duplicate key update语法
     * 能通过一条sql实现：如果数据不存在，就插入数据，如果已经存在就触发更新
     * </pre>
     *
     * @param prefixSql sql的前半部分，如insert into tablename，后面的values一段将由程序自动生成（mysql将使用set xx=aa, yy=bb的语法）
     * @param map 预备插入的Map<String,Object>，其中只要是非null的value，都会参与到sql的生成
     */
    public void upsertOne(String prefixSql, Map<String, Object> map) {
        if (!isMySql) {
            throw new IllegalStateException("该方法仅支持mysql，不支持" + dialect);
        }
        SqlBuilder sql = new SqlBuilder(prefixSql).setDialect(dialect);
        sql.add("set").addEqualsBean(map); // 使用mysql特有的insert语法，这样在日志中显得更直观
        sql.add("on duplicate key update");
        sql.addEqualsBean(map);
        update(sql);
    }

    /**
     * 用于执行delete/update操作，返回的int代表该操作影响了多少行数据
     */
    public int update(String sql, Object... args) {
        int affected = 0;
        Throwable ex = null;
        String logString = null;
        long start = System.currentTimeMillis();
        try {
            for (int i = 0; i <= retryTimes; i++) { // 为了应对网络不稳定的情况，当执行SQL失败时，重试一下
                try {
                    affected = jdbcTemplate.update(new SqlCreator(sql, args));
                    break;
                } catch (CannotGetJdbcConnectionException | TransientDataAccessResourceException | ConcurrencyFailureException ext) {
                    if (i == retryTimes) {
                        throw ext;
                    }
                }
            }
            return affected;
        } catch (Throwable e) {
            ex = e;
            logString = StringTools.mergeSql(sql, args);
            throw new SqlExecuteException(logString, e);
        } finally {
            if (log.isDebugEnabled()) {
                if (null == ex) {
                    logString = StringTools.mergeSql(sql, args) + " -- " + (System.currentTimeMillis() - start) + "ms|affected: " + affected;
                    log.debug(logString);
                } else {
                    logString += " -- " + StringTools.printThrowable(ex);
                    log.error(logString);
                }
            }
            StatJdbcUtils.record(sql, 1, start);
        }
    }

    /**
     * 用于执行delete/update操作，返回的int代表该操作影响了多少行数据
     */
    public int update(String sql, List<Object> args) {
        return update(sql, args.toArray());
    }

    /**
     * 用于执行delete/update操作，返回的int代表该操作影响了多少行数据
     */
    public int update(SqlBuilder sql) {
        return update(sql.sql.toString(), sql.args);
    }

    /**
     * 用于批量执行delete/update操作（目前仍是一条条执行，稍候要修改成真正的批量更新）
     */
    public void batchUpdate(String sql, List<Object[]> args) {
        for (Object[] objs : args) {
            update(sql, objs);
        }
    }

    /**
     * 用于批量执行delete/update操作
     */
    public void batchUpdate(List<SqlBuilder> list) {
        for (SqlBuilder sql : list) {
            update(sql);
        }
    }

    /**
     * 将一个javabean生成sql更新到数据库，通过反射获取到bean的所有非空字段自动生成sql
     *
     * @param prefixSql sql的前半部分，如update tablename set，后面的XX=aa等键值对将由程序自动生成
     * @param bean 预备插入的javabean，其中只要是非null的字段，都会参与到sql的生成<br/>
     * ●如果不想某个字段参与生成， 请在字段上加注解@{@link DbField}并设置@{@link DbField#writeToDb()}=false<br/>
     * ●如果想让null值的字段参与生成，请在字段上加注解@{@link DbField}并设置@{@link DbField#writeNullToDb()}=true
     * @param suffixSql sql尾部的where部分，如where userId=xxx
     *
     * @return 返回影响的数据行数，一般>1
     */
    public int updateOne(String prefixSql, Object bean, String suffixSql, Object... args) {
        SqlBuilder sql = new SqlBuilder(prefixSql).setDialect(dialect);
        sql.addEqualsBean(bean);
        sql.add(suffixSql, args);
        return update(sql);
    }

    /**
     * 将一个javabean生成sql更新到数据库，通过反射获取到bean的所有非空字段自动生成sql
     *
     * @param prefixSql sql的前半部分，如update tablename set，后面的XX=aa等键值对将由程序自动生成
     * @param bean 预备更新的javabean，其中只要是非null的字段，都会参与到sql的生成<br/>
     * ●如果不想某个字段参与生成， 请在字段上加注解@{@link DbField}并设置@{@link DbField#writeToDb()}=false<br/>
     * ●如果想让null值的字段参与生成，请在字段上加注解@{@link DbField}并设置@{@link DbField#writeNullToDb()}=true
     * @param suffixSql sql尾部的where部分，如where userId=xxx
     *
     * @return 返回影响的数据行数，一般>1
     */
    public int updateOne(String prefixSql, Object bean, String suffixSql, List<Object> args) {
        SqlBuilder sql = new SqlBuilder(prefixSql).setDialect(dialect);
        sql.addEqualsBean(bean);
        sql.add(suffixSql, args);
        return update(sql);
    }

    /**
     * 将一个Map<String,Object>生成sql更新到数据库，所有非空value自动生成sql
     *
     * @param prefixSql sql的前半部分，如insert into tablename，后面的values一段将由程序自动生成
     * @param map 预备插入的Map<String,Object>，其中只要是非null的value，都会参与到sql的生成
     * @param suffixSql sql尾部的where部分，如where userId=xxx
     *
     * @return 返回影响的数据行数，一般>1
     */
    public int updateOne(String prefixSql, Map<String, Object> map, String suffixSql, Object... args) {
        SqlBuilder sql = new SqlBuilder(prefixSql).setDialect(dialect);
        sql.addEqualsBean(map);
        sql.add(suffixSql, args);
        return update(sql);
    }

    /**
     * 将一个Map<String,Object>生成sql更新到数据库，所有非空value自动生成sql
     *
     * @param prefixSql sql的前半部分，如insert into tablename，后面的values一段将由程序自动生成
     * @param map 预备插入的Map<String,Object>，其中只要是非null的value，都会参与到sql的生成
     * @param suffixSql sql尾部的where部分，如where userId=xxx
     *
     * @return 返回影响的数据行数，一般>1
     */
    public int updateOne(String prefixSql, Map<String, Object> map, String suffixSql, List<Object> args) {
        SqlBuilder sql = new SqlBuilder(prefixSql).setDialect(dialect);
        sql.addEqualsBean(map);
        sql.add(suffixSql, args);
        return update(sql);
    }

    /**
     * 查询并获取到一个{@link Boolean}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Boolean queryBool(String sql, Object... args) {
        String bool = queryOne(String.class, sql, args);
        return StringTools.getBool(bool, null);
    }

    /**
     * 查询并获取到一个{@link Boolean}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Boolean queryBool(String sql, List<Object> args) {
        return queryBool(sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link Boolean}
     *
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Boolean queryBool(SqlBuilder sql) {
        return queryBool(sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link Boolean}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Boolean queryBoolOrDefault(Boolean defaultValue, String sql, Object... args) {
        String bool = queryOne(String.class, sql, args);
        return StringTools.getBool(bool, defaultValue);
    }

    /**
     * 查询并获取到一个{@link Boolean}
     *
     * @param defaultValue 当没有查到结果时，默认返回值*
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Boolean queryBoolOrDefault(Boolean defaultValue, String sql, List<Object> args) {
        return queryBoolOrDefault(defaultValue, sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link Boolean}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Boolean queryBoolOrDefault(Boolean defaultValue, SqlBuilder sql) {
        return queryBoolOrDefault(defaultValue, sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link Byte}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Byte queryByte(String sql, Object... args) {
        return queryOne(Byte.class, sql, args);
    }

    /**
     * 查询并获取到一个{@link Byte}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Byte queryByte(String sql, List<Object> args) {
        return queryByte(sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link Byte}
     *
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Byte queryByte(SqlBuilder sql) {
        return queryByte(sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link Byte}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Byte queryByteOrDefault(Byte defaultValue, String sql, Object... args) {
        return queryOneOrDefault(defaultValue, Byte.class, sql, args);
    }

    /**
     * 查询并获取到一个{@link Byte}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Byte queryByteOrDefault(Byte defaultValue, String sql, List<Object> args) {
        return queryByteOrDefault(defaultValue, sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link Byte}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Byte queryByteOrDefault(Byte defaultValue, SqlBuilder sql) {
        return queryByteOrDefault(defaultValue, sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link Short}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Short queryShort(String sql, Object... args) {
        return queryOne(Short.class, sql, args);
    }

    /**
     * 查询并获取到一个{@link Short}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Short queryShort(String sql, List<Object> args) {
        return queryShort(sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link Short}
     *
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Short queryShort(SqlBuilder sql) {
        return queryShort(sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link Short}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Short queryShortOrDefault(Short defaultValue, String sql, Object... args) {
        return queryOneOrDefault(defaultValue, Short.class, sql, args);
    }

    /**
     * 查询并获取到一个{@link Short}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Short queryShortOrDefault(Short defaultValue, String sql, List<Object> args) {
        return queryShortOrDefault(defaultValue, sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link Short}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Short queryShortOrDefault(Short defaultValue, SqlBuilder sql) {
        return queryShortOrDefault(defaultValue, sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link Integer}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Integer queryInt(String sql, Object... args) {
        return queryOne(Integer.class, sql, args);
    }

    /**
     * 查询并获取到一个{@link Integer}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Integer queryInt(String sql, List<Object> args) {
        return queryInt(sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link Integer}
     *
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Integer queryInt(SqlBuilder sql) {
        return queryInt(sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link Integer}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Integer queryIntOrDefault(Integer defaultValue, String sql, Object... args) {
        return queryOneOrDefault(defaultValue, Integer.class, sql, args);
    }

    /**
     * 查询并获取到一个{@link Integer}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Integer queryIntOrDefault(Integer defaultValue, String sql, List<Object> args) {
        return queryIntOrDefault(defaultValue, sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link Integer}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Integer queryIntOrDefault(Integer defaultValue, SqlBuilder sql) {
        return queryIntOrDefault(defaultValue, sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link Long}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Long queryLong(String sql, Object... args) {
        return queryOne(Long.class, sql, args);
    }

    /**
     * 查询并获取到一个{@link Long}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Long queryLong(String sql, List<Object> args) {
        return queryLong(sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link Long}
     *
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Long queryLong(SqlBuilder sql) {
        return queryLong(sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link Long}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Long queryLongOrDefault(Long defaultValue, String sql, Object... args) {
        return queryOneOrDefault(defaultValue, Long.class, sql, args);
    }

    /**
     * 查询并获取到一个{@link Long}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Long queryLongOrDefault(Long defaultValue, String sql, List<Object> args) {
        return queryLongOrDefault(defaultValue, sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link Long}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Long queryLongOrDefault(Long defaultValue, SqlBuilder sql) {
        return queryLongOrDefault(defaultValue, sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link Float}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Float queryFloat(String sql, Object... args) {
        return queryOne(Float.class, sql, args);
    }

    /**
     * 查询并获取到一个{@link Float}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Float queryFloat(String sql, List<Object> args) {
        return queryFloat(sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link Float}
     *
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Float queryFloat(SqlBuilder sql) {
        return queryFloat(sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link Float}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Float queryFloatOrDefault(Float defaultValue, String sql, Object... args) {
        return queryOneOrDefault(defaultValue, Float.class, sql, args);
    }

    /**
     * 查询并获取到一个{@link Float}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Float queryFloatOrDefault(Float defaultValue, String sql, List<Object> args) {
        return queryFloatOrDefault(defaultValue, sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link Float}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Float queryFloatOrDefault(Float defaultValue, SqlBuilder sql) {
        return queryFloatOrDefault(defaultValue, sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link Double}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Double queryDouble(String sql, Object... args) {
        return queryOne(Double.class, sql, args);
    }

    /**
     * 查询并获取到一个{@link Double}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Double queryDouble(String sql, List<Object> args) {
        return queryDouble(sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link Double}
     *
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Double queryDouble(SqlBuilder sql) {
        return queryDouble(sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link Double}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Double queryDoubleOrDefault(Double defaultValue, String sql, Object... args) {
        return queryOneOrDefault(defaultValue, Double.class, sql, args);
    }

    /**
     * 查询并获取到一个{@link Double}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Double queryDoubleOrDefault(Double defaultValue, String sql, List<Object> args) {
        return queryDoubleOrDefault(defaultValue, sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link Double}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Double queryDoubleOrDefault(Double defaultValue, SqlBuilder sql) {
        return queryDoubleOrDefault(defaultValue, sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link String}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public String queryString(String sql, Object... args) {
        return queryOne(String.class, sql, args);
    }

    /**
     * 查询并获取到一个{@link String}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public String queryString(String sql, List<Object> args) {
        return queryString(sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link String}
     *
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public String queryString(SqlBuilder sql) {
        return queryString(sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link String}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public String queryStringOrDefault(String defaultValue, String sql, Object... args) {
        return queryOneOrDefault(defaultValue, String.class, sql, args);
    }

    /**
     * 查询并获取到一个{@link String}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public String queryStringOrDefault(String defaultValue, String sql, List<Object> args) {
        return queryStringOrDefault(defaultValue, sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link String}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public String queryStringOrDefault(String defaultValue, SqlBuilder sql) {
        return queryStringOrDefault(defaultValue, sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link Datetime}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Datetime queryDate(String sql, Object... args) {
        return queryOne(Datetime.class, sql, args);
    }

    /**
     * 查询并获取到一个{@link Datetime}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Datetime queryDate(String sql, List<Object> args) {
        return queryDate(sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link Datetime}
     *
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public Datetime queryDate(SqlBuilder sql) {
        return queryDate(sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到一个{@link Datetime}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Datetime queryDateOrDefault(Datetime defaultValue, String sql, Object... args) {
        return queryOneOrDefault(defaultValue, Datetime.class, sql, args);
    }

    /**
     * 查询并获取到一个{@link Datetime}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Datetime queryDateOrDefault(Datetime defaultValue, String sql, List<Object> args) {
        return queryDateOrDefault(defaultValue, sql, args.toArray());
    }

    /**
     * 查询并获取到一个{@link Datetime}
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public Datetime queryDateOrDefault(Datetime defaultValue, SqlBuilder sql) {
        return queryDateOrDefault(defaultValue, sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到二进制内容
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public byte[] queryBin(String sql, Object... args) {
        return queryOne(byte[].class, sql, args);
    }

    /**
     * 查询并获取到二进制内容
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public byte[] queryBin(String sql, List<Object> args) {
        return queryBin(sql, args.toArray());
    }

    /**
     * 查询并获取到二进制内容
     *
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public byte[] queryBin(SqlBuilder sql) {
        return queryBin(sql.sql.toString(), sql.args);
    }

    /**
     * 查询并获取到二进制内容
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public byte[] queryBinOrDefault(byte[] defaultValue, String sql, Object... args) {
        return queryOneOrDefault(defaultValue, byte[].class, sql, args);
    }

    /**
     * 查询并获取到二进制内容
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public byte[] queryBinOrDefault(byte[] defaultValue, String sql, List<Object> args) {
        return queryBinOrDefault(defaultValue, sql, args.toArray());
    }

    /**
     * 查询并获取到二进制内容
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public byte[] queryBinOrDefault(byte[] defaultValue, SqlBuilder sql) {
        return queryBinOrDefault(defaultValue, sql.sql.toString(), sql.args);
    }

    /**
     * 根据sql查找单个对象
     *
     * @param clazz 对象的class
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public <T> T queryOne(Class<T> clazz, String sql, Object... args) {
        List<T> list = queryList(clazz, sql, args);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 根据sql查找单个对象
     *
     * @param clazz 对象的class
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public <T> T queryOne(Class<T> clazz, String sql, Collection<Object> args) {
        return queryOne(clazz, sql, args.toArray());
    }

    /**
     * 根据sql查找单个对象
     *
     * @param clazz 对象的class
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public <T> T queryOne(Class<T> clazz, SqlBuilder sql) {
        return queryOne(clazz, sql.sql.toString(), sql.args);
    }

    /**
     * 根据sql查找单个对象
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param clazz 对象的class
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public <T> T queryOneOrDefault(T defaultValue, Class<T> clazz, String sql, Object... args) {
        List<T> list = queryList(clazz, sql, args);
        if (list.isEmpty()) {
            return defaultValue;
        }
        T one = list.get(0);
        if (null == one) {
            return defaultValue;
        }
        return one;
    }

    /**
     * 根据sql查找单个对象
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param clazz 对象的class
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public <T> T queryOneOrDefault(T defaultValue, Class<T> clazz, String sql, Collection<Object> args) {
        return queryOneOrDefault(defaultValue, clazz, sql, args.toArray());
    }

    /**
     * 根据sql查找单个对象
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param clazz 对象的class
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public <T> T queryOneOrDefault(T defaultValue, Class<T> clazz, SqlBuilder sql) {
        return queryOneOrDefault(defaultValue, clazz, sql.sql.toString(), sql.args);
    }

    /**
     * 根据sql查找单个对象，查找结果通过一个包含字段名和值的WrappedMap返回
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public DbMap queryOne(String sql, Object... args) {
        List<DbMap> list = queryList(sql, args);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 根据sql查找单个对象，查找结果通过一个包含字段名和值的Map返回
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public DbMap queryOne(String sql, Collection<Object> args) {
        return queryOne(sql, args.toArray());
    }

    /**
     * 根据sql查找单个对象，查找结果通过一个包含字段名和值的Map返回
     *
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回null
     */
    public DbMap queryOne(SqlBuilder sql) {
        return queryOne(sql.sql.toString(), sql.args);
    }

    /**
     * 根据sql查找单个对象，查找结果通过一个包含字段名和值的WrappedMap返回
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public DbMap queryOneOrDefault(DbMap defaultValue, String sql, Object... args) {
        List<DbMap> list = queryList(sql, args);
        if (list.isEmpty()) {
            return null;
        }
        DbMap one = list.get(0);
        if (null == one) {
            return defaultValue;
        }
        return one;
    }

    /**
     * 根据sql查找单个对象，查找结果通过一个包含字段名和值的Map返回
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public DbMap queryOneOrDefault(DbMap defaultValue, String sql, Collection<Object> args) {
        return queryOneOrDefault(defaultValue, sql, args.toArray());
    }

    /**
     * 根据sql查找单个对象，查找结果通过一个包含字段名和值的Map返回
     *
     * @param defaultValue 当没有查到结果时，默认返回值
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回defaultValue
     */
    public DbMap queryOneOrDefault(DbMap defaultValue, SqlBuilder sql) {
        return queryOneOrDefault(defaultValue, sql.sql.toString(), sql.args);
    }

    /**
     * 查询一个列表的内容
     *
     * @param clazz 目标bean的class
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回空的List
     */
    public <T> List<T> queryList(Class<T> clazz, String sql, Object... args) {
        int rows = 0;
        String result = null;
        Throwable ex = null;
        String logString = null;
        long start = System.currentTimeMillis();
        try {
            RowMapper<T> rowMapper = JdbcRowMappers.getMapper(clazz);
            List<T> list = Collections.emptyList();
            for (int i = 0; i <= retryTimes; i++) { // 为了应对网络不稳定的情况，当执行SQL失败时，重试一下
                try {
                    list = jdbcTemplate.query(new SqlCreator(sql, args), rowMapper);
                    break;
                } catch (CannotGetJdbcConnectionException | TransientDataAccessResourceException | ConcurrencyFailureException ext) {
                    if (i == retryTimes) {
                        throw ext;
                    }
                }
            }
            rows = list.size();
            if (rows == 1) { // 如果结果只有1列并且属于可以输出的就直接把结果输出
                if (BeanTools.isPrimitiveWrapperType(clazz)) {
                    result = StringTools.escapeWhitespace(String.valueOf(list.get(0)));
                } else if (Date.class.isAssignableFrom(clazz)) {
                    result = new Datetime((Date) (list.get(0))).toString();
                }
            }
            return list;
        } catch (Throwable e) {
            ex = e;
            logString = StringTools.mergeSql(sql, args);
            throw new SqlExecuteException(logString, e);
        } finally {
            if (log.isDebugEnabled()) {
                if (null == ex) {
                    if (rows == 1 && null != result) {
                        logString = StringTools.mergeSql(sql, args) + " -- " + (System.currentTimeMillis() - start) + "ms|result: " + result;
                    } else {
                        logString = StringTools.mergeSql(sql, args) + " -- " + (System.currentTimeMillis() - start) + "ms|rows: " + rows;
                    }
                    log.debug(logString);
                } else {
                    logString += " -- " + StringTools.printThrowable(ex);
                    log.error(logString);
                }
            }
            StatJdbcUtils.record(sql, 1, start);
        }
    }

    /**
     * 查询一个列表的内容
     *
     * @param clazz 目标bean的class
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回空的List
     */
    public <T> List<T> queryList(Class<T> clazz, String sql, Collection<Object> args) {
        return queryList(clazz, sql, args.toArray());
    }

    /**
     * 查询一个列表的内容
     *
     * @param clazz 目标bean的class
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回空的List
     */
    public <T> List<T> queryList(Class<T> clazz, SqlBuilder sql) {
        return queryList(clazz, sql.sql.toString(), sql.args);
    }

    /**
     * 查询一个列表的内容，返回一个包含各字段名和值的{@link DbMap}的列表
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回空的List
     */
    public List<DbMap> queryList(String sql, Object... args) {
        int rows = 0;
        Throwable ex = null;
        String logString = null;
        long start = System.currentTimeMillis();
        try {
            for (int i = 0; i <= retryTimes; i++) { // 为了应对网络不稳定的情况，当执行SQL失败时，重试一下
                try {
                    return jdbcTemplate.query(new SqlCreator(sql, args), new RowMapperResultSetExtractor<>(new ColumnDbMapRowMapper()));
                } catch (CannotGetJdbcConnectionException | TransientDataAccessResourceException | ConcurrencyFailureException ext) {
                    if (i == retryTimes) {
                        throw ext;
                    }
                }
            }
        } catch (Throwable e) {
            ex = e;
            logString = StringTools.mergeSql(sql, args);
            throw new SqlExecuteException(logString, e);
        } finally {
            if (log.isDebugEnabled()) {
                if (null == ex) {
                    logString = StringTools.mergeSql(sql, args) + " -- " + (System.currentTimeMillis() - start) + "ms|rows: " + rows;
                    log.debug(logString);
                } else {
                    logString += " -- " + StringTools.printThrowable(ex);
                    log.error(logString);
                }
            }
            StatJdbcUtils.record(sql, 1, start);
        }
        return Collections.emptyList();
    }

    /**
     * 查询一个列表的内容，返回一个包含各字段名和值的{@link WrappedMap}的列表
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回空的List
     */
    public List<DbMap> queryList(String sql, Collection<Object> args) {
        return queryList(sql, args.toArray());
    }

    /**
     * 查询一个列表的内容，返回一个包含各字段名和值的{@link WrappedMap}的列表
     *
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回空的List
     */
    public List<DbMap> queryList(SqlBuilder sql) {
        return queryList(sql.sql.toString(), sql.args);
    }

    /**
     * 查询一个列表的内容，返回一个以第1列的值为key，与其对应行的其他列值为value的Map
     *
     * key只能是基本类型（数字/字符串等）
     * value可以是基本型或任意的javabean
     *
     * 举例说明：
     * select user_id, nickname, birthday from user_info
     *
     * 就会返回一个Map，以user_id（结果集的第1列）为key，value是对应那一行的结果集中包含user_id/nick_name/birthday的javabean
     *
     * 注意：
     *
     * 如果结果集中有多个相同的key，返回的Map的key会对应最后的一个value
     *
     * 例如源数据为
     * user_id      nickname
     * abc           xyz111
     * abc           xyz222
     * abc           xyz333
     *
     * 最后返回的Map只包含abc:xyz333这一对数据
     *
     * 如果想要包含所有的value，即在上面的例子中，abc可以对应到xyz111/xyz222/xyz333这三条数据
     * key查询的结果是一个列表，包含所有符合条件的数据，可使用{@link #queryKVMultimap(Class, String, Object...)}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回空的Map
     */
    @Deprecated
    public <K, V> Map<K, V> queryKVMap(Class<K> keyClass, Class<V> valueClass, String sql, Object... args) {
        return null;
    }

    /**
     * 查询一个列表的内容，返回一个以第1列的值为key，与其对应行的其他列值为value的Map
     *
     * key只能是基本类型（数字/字符串等）
     * value可以是基本型或任意的javabean
     *
     * 举例说明：
     * select user_id, nickname, birthday from user_info
     *
     * 就会返回一个Map，以user_id（结果集的第1列）为key，value是对应那一行的结果集中包含user_id/nick_name/birthday的javabean
     *
     * 注意：
     *
     * 如果结果集中有多个相同的key，返回的Map的key会对应最后的一个value
     *
     * 例如源数据为
     * user_id      nickname
     * abc           xyz111
     * abc           xyz222
     * abc           xyz333
     *
     * 最后返回的Map只包含abc:xyz333这一对数据
     *
     * 如果想要包含所有的value，即在上面的例子中，abc可以对应到xyz111/xyz222/xyz333这三条数据
     * key查询的结果是一个列表，包含所有符合条件的数据，可使用{@link #queryKVMultimap(Class, String, Object...)}
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回空的Map
     */
    @Deprecated
    public <K, V> Map<K, V> queryKVMap(Class<K> keyClass, Class<V> valueClass, String sql, Collection<Object> args) {
        return queryKVMap(keyClass, valueClass, sql, args.toArray());
    }

    /**
     * 查询一个列表的内容，返回一个以第1列的值为key，与其对应行的其他列值为value的Map
     *
     * key只能是基本类型（数字/字符串等）
     * value可以是基本型或任意的javabean
     *
     * 举例说明：
     * select user_id, nickname, birthday from user_info
     *
     * 就会返回一个Map，以user_id（结果集的第1列）为key，value是对应那一行的结果集中包含user_id/nick_name/birthday的javabean
     *
     * 注意：
     *
     * 如果结果集中有多个相同的key，返回的Map的key会对应最后的一个value
     *
     * 例如源数据为
     * user_id      nickname
     * abc           xyz111
     * abc           xyz222
     * abc           xyz333
     *
     * 最后返回的Map只包含abc:xyz333这一对数据
     *
     * 如果想要包含所有的value，即在上面的例子中，abc可以对应到xyz111/xyz222/xyz333这三条数据
     * key查询的结果是一个列表，包含所有符合条件的数据，可使用{@link #queryKVMultimap(Class, String, Object...)}
     *
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回空的Map
     */
    @Deprecated
    public <K, V> Map<K, V> queryKVMap(Class<K> keyClass, Class<V> valueClass, SqlBuilder sql) {
        return queryKVMap(keyClass, valueClass, sql.sql.toString(), sql.args);
    }

    /**
     * 查询一个列表的内容，返回一个以第一列的值为key，与其对应的第二列的值为value的{@link DbMap}
     * 注意如果有多个相同的key，结果集中只会包含最后的一个value
     * 如果想要包含所有的value（以列表形式返回），可使用{@link #queryKVMultimap(Class, String, Object...)}
     *
     * @param sqlBuilder 查询sql，只能select两个字段，否则会抛异常，例如：select username,phonenumber from xxx...
     *
     * @return 如果没有查到对应的记录将返回空的map
     */
    public DbMap queryKVMap(SqlBuilder sqlBuilder) {
        return queryKVMap(sqlBuilder.sql.toString(), sqlBuilder.args.toArray());
    }

    /**
     * 查询一个列表的内容，返回一个以第一列的值args为key，与其对应的第二列的值为value的{@link DbMap}
     * 注意如果有多个相同的key，结果集中只会包含最后的一个value
     * 如果想要包含所有的value（以列表形式返回），可使用{@link #queryKVMultimap(Class, String, Object...)}
     *
     * @param sql 查询sql，只能select两个字段，否则会抛异常，例如：select username,phonenumber from xxx...
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回空的map
     */
    public DbMap queryKVMap(String sql, Object... args) {
        int rows = 0;
        Throwable ex = null;
        String logString = null;
        long start = System.currentTimeMillis();
        try {
            return jdbcTemplate.query(new SqlCreator(sql, args), new KeyValueMapResultSetExtractor());
        } catch (Throwable e) {
            ex = e;
            logString = StringTools.mergeSql(sql, args);
            throw new SqlExecuteException(logString, e);
        } finally {
            if (log.isDebugEnabled()) {
                if (null == ex) {
                    logString = StringTools.mergeSql(sql, args) + " -- " + (System.currentTimeMillis() - start) + "ms|rows: " + rows;
                    log.debug(logString);
                } else {
                    logString += " -- " + StringTools.printThrowable(ex);
                    log.error(logString);
                }
            }
            StatJdbcUtils.record(sql, 1, start);
        }
    }

    /**
     * 查询一个列表的内容，返回一个包含第一列为key，第二列为value结果集列表的{@link Multimap}
     * 即同一个key可对应多个value
     *
     * 只能select两个字段，否则会抛异常，例如：select username,phonenumber from xxx...
     *
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回空的map
     */
    public <T> Multimap<String, T> queryKVMultimap(Class<T> valueClassType, String sql, Object... args) {
        int rows = 0;
        Throwable ex = null;
        String logString = null;
        long start = System.currentTimeMillis();
        try {
            return jdbcTemplate.query(new SqlCreator(sql, args), new KeyValueMultimapResultSetExtractor<T>(valueClassType));
        } catch (Throwable e) {
            ex = e;
            logString = StringTools.mergeSql(sql, args);
            throw new SqlExecuteException(logString, e);
        } finally {
            if (log.isDebugEnabled()) {
                if (null == ex) {
                    logString = StringTools.mergeSql(sql, args) + " -- " + (System.currentTimeMillis() - start) + "ms|rows: " + rows;
                    log.debug(logString);
                } else {
                    logString += " -- " + StringTools.printThrowable(ex);
                    log.error(logString);
                }
            }
            StatJdbcUtils.record(sql, 1, start);
        }
    }

    /**
     * 查询一页的内容
     *
     * @param clazz 目标bean的class
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回空的List
     */
    public <T> List<T> queryPage(Class<T> clazz, int pageNo, int pageSize, String sql, Object... args) {
        int startIndex = (pageNo - 1) * pageSize;
        startIndex = startIndex < 0 ? 0 : startIndex;
        String querySql = null;
        switch (dialect) {
        case "mysql":
            querySql = sql + " limit " + startIndex + ", " + pageSize;
            break;
        case "postgresql":
            querySql = sql + " limit " + pageSize + " offset " + startIndex;
            break;
        }
        return queryList(clazz, querySql, args);
    }

    /**
     * 查询一页的内容
     *
     * @param clazz 目标bean的class
     * @param sql 查询sql
     * @param args 查询sql语句中的?参数的值
     *
     * @return 如果没有查到对应的记录将返回空的List
     */
    public <T> List<T> queryPage(Class<T> clazz, int pageNo, int pageSize, String sql, Collection<Object> args) {
        return queryPage(clazz, pageNo, pageSize, sql, args.toArray());
    }

    /**
     * 查询一页的内容
     *
     * @param clazz 目标bean的class
     * @param sql 查询sql以及其参数
     *
     * @return 如果没有查到对应的记录将返回空的List
     */
    public <T> List<T> queryPage(Class<T> clazz, int pageNo, int pageSize, SqlBuilder sql) {
        return queryPage(clazz, pageNo, pageSize, sql.sql.toString(), sql.args);
    }

    /**
     * 显示mysq当前正在执行的sql（仅支持mysql）
     */
    public List<Process> showProcessList() {
        if (isMySql) {
            return queryList(Process.class, "show processlist");
        }
        return Collections.emptyList();
    }

    public static class SqlCreator implements PreparedStatementCreator {

        protected String sql;

        protected Object[] args;

        public SqlCreator(String sql, Object[] args) {
            this.sql = sql;
            this.args = args;
        }

        protected PreparedStatement create(Connection con) throws SQLException {
            return con.prepareStatement(sql);
        }

        @Override
        public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
            PreparedStatement ps = create(con);
            if (args != null) {
                int i = 1;
                for (Object arg : args) {
                    if (arg instanceof Timestamp) {
                        ps.setTimestamp(i++, (Timestamp) arg);
                    } else if (arg instanceof Date) {
                        ps.setTimestamp(i++, new Timestamp(((Date) arg).getTime()));
                    } else if (arg instanceof Enum) {
                        ps.setString(i++, arg.toString());
                    } else {
                        ps.setObject(i++, arg);
                    }
                }
            }
            return ps;
        }
    }

    public static class SqlCreatorForInsert extends SqlCreator {

        public SqlCreatorForInsert(String sql, Object[] args) {
            super(sql, args);
        }

        @Override
        protected PreparedStatement create(Connection con) throws SQLException {
            return con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        }
    }
}
