package org.etnaframework.core.web;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.spring.SpringContext;
import org.etnaframework.core.util.AntiDos;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.DingTalkRobotUtils;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.util.StringTools.CharsetEnum;
import org.etnaframework.core.util.SystemInfo;
import org.etnaframework.core.web.exception.ProcessFinishedException;
import org.etnaframework.core.web.mapper.CmdMappers;
import org.etnaframework.core.web.mapper.CmdMeta;
import org.etnaframework.plugin.monitor.SystemMonitor;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;

/**
 * HTTP请求分发器，如果能找到对应的匹配规则就执行对应的方法，否则就走默认的请求流程
 *
 * @author BlackCat
 * @since 2013-3-10
 */
public final class DispatchFilter extends OncePerRequestFilter {

    static final Logger access_log = Log.getLoggerWithPrefix("access");

    /** 配置的通过邮件发送的错误日志，可使用{@link #sendMail(String, CharSequence)}来发送邮件通知 */
    private static final Logger mail_log = Log.getLogger();

    /** 设置默认的请求和返回编码 */
    static Charset encoding = CharsetEnum.UTF_8;

    /** 如果运行过程中出现了不能自动处理的异常，是否要把异常返回给前端，默认不显示，在开发模式下为了方便查错应当为true */
    static boolean responseError = false;

    /** 是否要记录其他的模块处理的访问日志log（这里一般是指访问静态资源），默认记录，如果嫌多可以关掉 */
    static boolean recordOtherAccessLog = true;

    /** 返回头的Server信息，可自由定制 */
    static String server = "etna";

    /** 默认的支持JSONP跨域前端传入的key */
    static String jsonpCallbackKey = "callback";

    /** 是否支持jsonp,如果不需要,则可以关掉 */
    static boolean jsonpCallbackEnable = true;

    /** 支持jsonp时,允许跨域调用的域名后缀. */
    static Set<String> jsonpCallbackCredibleDomains;

    /** 默认的异步模式连接挂起超时时间，单位毫秒，可在web.xml中配置 */
    static int asyncHoldOnTimeoutMs = Datetime.MILLIS_PER_SECOND * 60;

    /** 用于在部署的服务前放置nginx转发请求时，将客户端的IP放到转发header里面，以便让后端服务能获取到请求者真实的IP，如X-Forwarded-For/X-Real-IP等 */
    static String headerNameForRealIP;

    /** 通过header读取真实IP，有可能会被某些别有用心的用户在header里设置虚假IP欺骗服务器，这里可设置采信的转发来源IP（如果有多个IP可用,分隔），只有可信的IP转发来的请求才从header中读取IP，如此处不设置服务器将无条件采信header中的IP */
    static Set<String> credibleForwardSourceIPs;

    /** 为了防止报错邮件发太多，这里可以加条件予以限制，如设置1,60表示60s内只报告1条异常消息 */
    static AntiDos logMailLimit = null;

    /** 访问日志记录器 */
    static AccessLogRecorder accessLogRecorder;

    /** cmd 映射 */
    static CmdMappers cmdMappers;

    /** 异常渲染器 */
    static ExceptionRender exceptionRender;

    /** etna接口请求过程跟踪器 */
    static RequestTraceHandler requestTraceHandler = RequestTraceHandler.DEFAULT;

    /**
     * 发送通知邮件
     */
    public static void sendMail(String title, CharSequence content) {
        MDC.put("mailTitle", SystemInfo.RUN_APP_NAME + title);
        mail_log.error("{}\n{}", title, content);
        DingTalkRobotUtils.sendMarkdownGeneral(SystemMonitor.getDingTalkRobotUrl(), title, content);
    }

    /**
     * 发送通知邮件
     */
    public static void sendMail(String title, Throwable ex) {
        if (null != logMailLimit) {
            String key = null == ex ? "" : ex.getClass()
                                             .getName();
            if (!logMailLimit.visit(key)) { // 发现如果异常在规定时间内报告过了，就不再报
                return;
            }
        }
        MDC.put("mailTitle", SystemInfo.RUN_APP_NAME + title);
        mail_log.error("{}\n", title, ex);
        DingTalkRobotUtils.sendMarkdownGeneral(SystemMonitor.getDingTalkRobotUrl(), title, StringTools.printTrace(ex, true, 20, 0));
    }

    public static RequestTraceHandler getRequestTraceHandler() {
        return requestTraceHandler;
    }

    /**
     * 发送通知邮件
     */
    public static void sendMail(String title, CharSequence content, Throwable ex) {
        if (null != logMailLimit) {
            String key = null == ex ? "" : ex.getClass()
                                             .getName();
            if (!logMailLimit.visit(key)) { // 发现如果异常在规定时间内报告过了，就不再报
                return;
            }
        }
        MDC.put("mailTitle", SystemInfo.RUN_APP_NAME + title);
        mail_log.error("{}\n{}", title, content, ex);

        StringBuilder c = new StringBuilder(content);
        c.append(Thread.currentThread()
                       .getName())
         .append('\n')
         .append(StringTools.printTrace(ex, true, 20, 0));

        DingTalkRobotUtils.sendMarkdownGeneral(SystemMonitor.getDingTalkRobotUrl(), title, c);
    }

    /**
     * 记录HTTP请求业务处理时抛出的异常
     */
    static void recordThrowable(HttpEvent he, Throwable ex) {
        // http连接idle超时导致连接中断的，不需要报告，纯粹是用户用户网络原因
        if (ex instanceof IOException && ex.getCause() instanceof TimeoutException) {
            return;
        }
        String name = ex.getClass()
                        .getSimpleName();
        if (StringTools.isEmpty(name)) { // 如果异常是一个子类，无法获取名称就取其父类名称
            name = ex.getClass()
                     .getSuperclass()
                     .getSimpleName();
        }
        String title = name + ":" + he.getRequestURI();

        // 如果在日志配置了遇到异常发邮件就会自动发出
        sendMail(title, he.getDetailInfo(), ex);

        if (!he.isCommitted()) {  // 如果开关打开，并且之前没有返回过数据，就把异常信息返回给前端
            try {
                he.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
                if (responseError) {
                    he.writeText(he.getDetailInfo() + "\n" + StringTools.printThrowable(ex));
                }
            } catch (Throwable ex1) {
                // 此时抛的异常没有必要再报出来了，无非是不能两次返回一类，没有业务排查意义
            }
        }
    }

    @Override
    protected void initFilterBean() throws ServletException {
        try {
            super.initFilterBean();
            // 准备启动挂载启动的服务模块
            EtnaServer.initBootstrapModules();
            EtnaServer.shutdownWhenNecessary();
            EtnaServer.bindBootstrapModules();

            cmdMappers = SpringContext.getBean(CmdMappers.class);
            exceptionRender = SpringContext.getBeanOfType(ExceptionRender.class);
            if (null == exceptionRender) {
                exceptionRender = ExceptionRender.DEFAULT;
            }
            requestTraceHandler = SpringContext.getBeanOfType(RequestTraceHandler.class);
            if (null == requestTraceHandler) {
                requestTraceHandler = RequestTraceHandler.DEFAULT;
            }
            accessLogRecorder = SpringContext.getBeanOfType(AccessLogRecorder.class);
            if (null == accessLogRecorder) {
                if (recordOtherAccessLog) {
                    accessLogRecorder = AccessLogRecorder.DEFAULT_ALL;
                } else {
                    accessLogRecorder = AccessLogRecorder.DEFAULT_NECESSARY;
                }
            }
        } catch (ServletException se) {
            throw se;
        } catch (Throwable ex) {
            throw new ServletException(ex);
        }
    }

    public void setEncoding(String encoding) {
        DispatchFilter.encoding = Charset.forName(encoding);
    }

    public void setResponseError(boolean responseError) {
        DispatchFilter.responseError = responseError;
    }

    public void setRecordOtherAccessLog(boolean recordOtherAccessLog) {
        DispatchFilter.recordOtherAccessLog = recordOtherAccessLog;
    }

    public void setServer(String server) {
        if (StringTools.isNotEmpty(server)) {
            DispatchFilter.server = server;
        }
    }

    public void setJsonpCallbackKey(String jsonpCallbackKey) {
        if (StringTools.isNotEmpty(jsonpCallbackKey)) {
            DispatchFilter.jsonpCallbackKey = jsonpCallbackKey;
        }
    }

    public void setJsonpCallbackEnable(boolean jsonpCallbackEnable) {
        DispatchFilter.jsonpCallbackEnable = jsonpCallbackEnable;
    }

    public void setAsyncHoldOnTimeoutMs(int asyncHoldOnTimeoutMs) {
        if (asyncHoldOnTimeoutMs > 0) {
            DispatchFilter.asyncHoldOnTimeoutMs = asyncHoldOnTimeoutMs;
        }
    }

    public void setHeaderNameForRealIP(String headerNameForRealIP) {
        if (StringTools.isNotEmpty(headerNameForRealIP)) {
            DispatchFilter.headerNameForRealIP = headerNameForRealIP;
        }
    }

    public void setCredibleForwardSourceIPs(String credibleForwardSourceIPs) {
        if (StringTools.isNotEmpty(credibleForwardSourceIPs)) {
            List<String> cIPs = StringTools.splitAndTrim(credibleForwardSourceIPs, ",");
            if (!cIPs.isEmpty()) {
                DispatchFilter.credibleForwardSourceIPs = Sets.newHashSet(cIPs);
            }
        }
    }

    public void setJsonpCallbackCredibleDomains(String jsonpCallbackCredibleDomains) {
        if (StringTools.isNotEmpty(jsonpCallbackCredibleDomains)) {
            List<String> domains = Splitter.on(',')
                                           .omitEmptyStrings()
                                           .trimResults()
                                           .splitToList(jsonpCallbackCredibleDomains);
            if (!domains.isEmpty()) {
                DispatchFilter.jsonpCallbackCredibleDomains = Sets.newHashSet(domains);
            }
        }
    }

    public void setlogMailLimit(String conf) {
        logMailLimit = new AntiDos(conf).initSweeper();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 设定请求返回编码
        request.setCharacterEncoding(encoding.toString());
        response.setCharacterEncoding(encoding.toString());

        // 修改返回的服务器信息
        response.addHeader(HttpHeaders.SERVER, server);
        response.setStatus(HttpStatus.OK.value());
        long startTime = System.currentTimeMillis();

        HttpEvent he = new HttpEvent(startTime, request, response);
        CmdMeta cm = cmdMappers.getCmdMetaByPath(he);
        if (null != cm) {
            try {
                requestTraceHandler.requestBegin(he); // 请求开始
                cm.invoke(he);
            } catch (Throwable ex) {
                try {
                    // 如果是通过反射调用产生的异常，需要把真实的异常剥离出来
                    if (ex instanceof InvocationTargetException) {
                        Throwable t = ((InvocationTargetException) ex).getTargetException();
                        if (null != t) {
                            ex = t;
                        }
                    }
                    // 如果抛出的是表示已经处理完毕的异常，就什么都不用管，其他的异常就需要进一步做处理
                    if (!(ex instanceof ProcessFinishedException)) {
                        exceptionRender.renderException(he, ex);
                        if (!he.isCommitted()) { // 检测是否已经返回前端，如果没有返回说明异常没有得到处理，使用默认的报告机制
                            recordThrowable(he, ex);
                        }
                    }
                } catch (Throwable ex1) {
                    recordThrowable(he, ex1);
                }
            } finally {
                // 记录etna接口的访问日志
                if (access_log.isInfoEnabled()) {
                    String logPart = accessLogRecorder.getAccessLog(he, false);
                    if (null != logPart) {
                        access_log.info(logPart);
                    }
                }
                cm.getStat()
                  .record(startTime + he.getRequestRunningTimeMs(), startTime, cm);
                requestTraceHandler.requestEnd(he); // 请求执行完毕
                MDC.clear(); // 清除当前线程中记录的TAG
            }
        } else {
            filterChain.doFilter(request, response);
            // 记录非etna接口的访问日志，根据配置的策略决定怎么记录
            if (access_log.isInfoEnabled()) {
                String logPart = accessLogRecorder.getAccessLog(startTime, request, response);
                if (null != logPart) {
                    access_log.info(logPart);
                }
            }
        }
    }
}
