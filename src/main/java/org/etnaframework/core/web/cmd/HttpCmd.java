package org.etnaframework.core.web.cmd;

import java.lang.reflect.Method;
import java.util.List;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.logging.logback.LogFormatFactory;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.web.AuthHandler;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.exception.ProcessFinishedException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 基本的HttpController，用于业务层类继承
 *
 * @author BlackCat
 * @since 2013-3-9
 */
public abstract class HttpCmd {

    protected final Logger log = Log.getLogger();

    protected static final LogFormatFactory logFormat = LogFormatFactory.getInstance("|");

    /** 默认执行方法的方法名 */
    public static String DEFAULT_METHOD_NAME;

    /** 默认的类名的后缀 */
    public static String DEFAULT_NAME_SUFFIX;

    static {
        Method[] methods = HttpCmd.class.getDeclaredMethods();
        for (Method m : methods) {
            Class<?>[] types = m.getParameterTypes();
            if (void.class.equals(m.getReturnType()) && types.length == 1 && types[0].equals(HttpEvent.class)) {
                DEFAULT_METHOD_NAME = m.getName();
                break;
            }
        }

        if (null == DEFAULT_METHOD_NAME) {
            throw new IllegalArgumentException(HttpCmd.class.getSimpleName() + "必须要指定DEFAULT_METHOD_NAME");
        }
        List<String> list = StringTools.splitByUppercaseLetter(HttpCmd.class.getSimpleName());
        DEFAULT_NAME_SUFFIX = list.get(list.size() - 1);
    }

    /**
     * <pre>
     * 当前Controller的默认执行方法，如有需要请覆盖此方法（覆写的方法务必为public，否则会被认为是没有）
     *
     * 之所以默认为protected是因为如果是public就会使每个接口都有默认方法了
     * </pre>
     */
    protected void index(HttpEvent he) throws Throwable {
    }

    @Autowired(required = false)
    private AuthHandler authHandler;

    /**
     * <pre>
     * 对接口访问权限进行限制，如果在业务代码中调用此方法，并且存在{@link AuthHandler}的实现类，就会自动调用鉴权接口
     *
     * 具体的鉴权方式，如是用IP限制还是用cookies可以在具体的{@link AuthHandler}实现类中自行定义
     * </pre>
     *
     * @param cmd 这个参数实际上不是必须的，只是 参数为{@link HttpEvent}的void方法将会被识别为是http接口<br/>
     * （虽然public才行，但不能保证protected在业务代码中不被覆写为public的），故特意加这个参数以示区分
     */
    protected void auth(HttpCmd cmd, HttpEvent he) throws Throwable {
        if (null != authHandler) {
            authHandler.auth(cmd, he);
        }
        if (he.isCommitted()) { // 如果前面的代码已经处理了，抛出异常阻断后续的代码执行
            throw ProcessFinishedException.INSTANCE;
        }
    }
}
