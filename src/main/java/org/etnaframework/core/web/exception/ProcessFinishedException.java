package org.etnaframework.core.web.exception;

/**
 * 用于中断业务处理流程，代表处理已经结束了，不需要继续执行剩余代码，直接返回给用户即可
 *
 * @author BlackCat
 * @since 2015-06-04
 */
public class ProcessFinishedException extends RuntimeException {

    private static final long serialVersionUID = -3090825166632536664L;

    public final static ProcessFinishedException INSTANCE = new ProcessFinishedException();

    private ProcessFinishedException() {
    }
}
