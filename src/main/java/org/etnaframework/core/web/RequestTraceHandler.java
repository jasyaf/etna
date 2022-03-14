package org.etnaframework.core.web;

/**
 * etna接口的跟踪处理器
 * Created by yuanhaoliang on 2016-05-29.
 */
public interface RequestTraceHandler {

    /** 默认什么都不执行 */
    RequestTraceHandler DEFAULT = new RequestTraceHandler() {

        @Override
        public void requestBegin(HttpEvent he) {
        }

        @Override
        public void requestEnd(HttpEvent he) {
        }
    };

    /**
     * HTTP 请求开始
     */
    void requestBegin(HttpEvent he);

    /**
     * HTTP 请求结束
     */
    void requestEnd(HttpEvent he);

    /**
     * 运行过程中执行的SQL
     */
    default void execSql(String sql) {
    }

    /**
     * 是否跟踪执行SQL
     */
    default boolean isTraceSql() {
        return false;
    }
}
