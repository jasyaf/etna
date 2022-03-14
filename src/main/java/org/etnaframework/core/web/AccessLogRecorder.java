package org.etnaframework.core.web;

import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.etnaframework.core.util.CollectionTools;
import org.etnaframework.core.util.StringTools;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * HTTP访问日志的记录策略，可以自定义要如何记录访问日志
 *
 * @author BlackCat
 * @since 2016-03-02
 */
public interface AccessLogRecorder {

    Set<String> headers = CollectionTools.buildSet(HttpHeaders.USER_AGENT);

    Set<String> cookieKeys = null;

    /** 默认的记录日志方法1，记录全部的接口访问日志 */
    AccessLogRecorder DEFAULT_ALL = new AccessLogRecorder() {

        @Override
        public String getAccessLog(HttpEvent he, boolean isAsyncResp) {
            return DEFAULT_NECESSARY.getAccessLog(he, isAsyncResp);
        }

        @Override
        public String getAccessLog(long startTime, HttpServletRequest request, HttpServletResponse response) {
            String format = "%8s|%15s:%-5s|%3s|%sms|%s";
            Object[] args = {
                "-",
                HttpEvent.getRemoteIP(request),
                request.getRemotePort(),
                response.getStatus(),
                System.currentTimeMillis() - startTime,
                HttpEvent.getDetailInfoSimple(request, headers, cookieKeys)
            };
            return StringTools.format(format, args);
        }
    };

    /** 默认的记录日志方法2，只记录etna接口的访问日志和异常的访问日志 */
    AccessLogRecorder DEFAULT_NECESSARY = new AccessLogRecorder() {

        @Override
        public String getAccessLog(HttpEvent he, boolean isAsyncResp) {
            // 请求结束时间|用户帐号|请求来源IP和端口|返回码|处理耗时ms|cURL模拟请求命令|请求参数|返回数据内容
            // 如果有记录详细的请求信息，将会另起一行记录
            String format = "%8s|%15s:%-5s|%3s|%sms|%s|%s";
            Object[] args = {
                he.getAccessLogUserAccount(),
                he.getRemoteIP(),
                he.getRemotePort(),
                he.getStatusInt(),
                he.getRequestRunningTimeMs(),
                he.getDetailInfoSimple(headers, cookieKeys),
                // 异步请求会写两次日志，第一次为接受请求连接挂起，第二次为异步请求返回内容
                (null != he.getTimeoutHandler() && !isAsyncResp) ? "[ASYNC HoldOn]" + he.getTimeoutHandler().timeoutMs + "ms" : (null == he.getAccessLogContent() ? "" : he.getAccessLogContent())
            };
            return String.format(format, args);
        }

        @Override
        public String getAccessLog(long startTime, HttpServletRequest request, HttpServletResponse response) {
            // 其他的访问（如静态文件等）失败的，即返回码非200才记录
            if (response.getStatus() != HttpStatus.OK.value()) {
                return DEFAULT_ALL.getAccessLog(startTime, request, response);
            }
            return null;
        }
    };

    /**
     * 生成访问日志，为etna的接口访问日志，如果返回null表示不记录日志
     */
    String getAccessLog(HttpEvent he, boolean isAsyncResp);

    /**
     * 生成访问日志，为除etna接口以外的问日志（如静态文件、其他servlet组件），如果返回null表示不记录日志
     */
    String getAccessLog(long startTime, HttpServletRequest request, HttpServletResponse response);
}
