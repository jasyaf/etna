package org.etnaframework.core.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.InvalidFileNameException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.FileCleanerCleanup;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.eclipse.jetty.server.AsyncContextState;
import org.eclipse.jetty.server.HttpChannelState.State;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.MultiMap;
import org.etnaframework.core.util.CollectionTools;
import org.etnaframework.core.util.DatetimeUtils;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.HttlTemplateUtils;
import org.etnaframework.core.util.JsonObjectUtils;
import org.etnaframework.core.util.KeyValueGetter;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.web.bean.RtnObject;
import org.etnaframework.core.web.constant.ContentTypes;
import org.etnaframework.core.web.constant.RtnCodes;
import org.etnaframework.core.web.exception.ParamEmptyException;
import org.etnaframework.core.web.exception.ParamInvalidFormatException;
import org.etnaframework.core.web.exception.ParamInvalidValueException;
import org.etnaframework.core.web.exception.SimpleRtnBaseException;
import org.etnaframework.core.web.mapper.ValidatorMappers;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import com.alibaba.fastjson.JSONObject;
import httl.Template;

/**
 * <pre>
 * HTTP请求事件，用于业务逻辑处理，请求时建立，请求结束后销毁
 *
 * 该类的实例不是线程安全的，请小心在多线程环境下使用
 * </pre>
 *
 * @author BlackCat
 * @since 2013-12-26
 */
public class HttpEvent implements KeyValueGetter {

    private static final String[] CURL_ESCAPE_LIST;

    static {
        CURL_ESCAPE_LIST = new String[255];
        CURL_ESCAPE_LIST['\r'] = "\\r";
        CURL_ESCAPE_LIST['\n'] = "\\n";
        CURL_ESCAPE_LIST['\f'] = "\\f";
        CURL_ESCAPE_LIST['\t'] = "\\t";
        CURL_ESCAPE_LIST['\b'] = "\\b";
        CURL_ESCAPE_LIST['\''] = "'\\''";
        CURL_ESCAPE_LIST['\u00a0'] = " ";
    }

    /** 当前请求的客户端使用的区域语言信息，用于加入国际化支持 */
    Locale locale = Locale.getDefault();

    /** 如果是异步模式，当{@link TimeoutHandler#onTimeout()}方法执行后，此参数将会被改为true，用于后续判断是否重复给客户端写数据 */
    volatile boolean onTimeoutCalled = false;

    /** HTTP请求开始时间 */
    long requestStartTime;

    /** 标记当前请求事件是否在异步业务逻辑处理中，如果不为null表示当前请求中提交了异步事件，正在等待事件完成通知回调 */
    private volatile TimeoutHandler timeoutHandler;

    /** 当前请求的用户帐户信息，请在验证登录态时设置，以便记录进访问日志 */
    private String accessLogUserAccount = "-";

    /** 记录到访问日志里面的返回内容 */
    private String accessLogContent;

    private HttpServletRequest request;

    private HttpServletResponse response;

    /**
     * <pre>
     * 获取请求的URI，不包含?后面的部分，系统会自动去除contextPath部分
     * 如部署在/demo目录下，请求/demo/test?name=123，此处返回/test
     * 如部署在/下，接口名为/demo/test，就返回/demo/test
     * </pre>
     */
    private String requestURI;

    /**
     * 获取请求的URL，不包含?后面的部分，包含contextPath部分
     */
    private String requestURL;

    /** 从URL的正则表达式中提取到的匹配结果，用于RESTful风格请求的处理，只有RESTful风格的请求此处才不为null */
    private Matcher matcher;

    /** 请求中的cookies，注意这里不会包含addCookie方法加入的内容！ */
    private WrappedMap<String, Cookie> cookies;

    /** 如果客户端的http请求包含content部分，这里是该部分的二进制内容 */
    private byte[] contentBytes;

    /** 如果客户端的http请求包含content部分，这里是该部分解码后的字符串内容 */
    private String contentString;

    /** 请求的所有Header的名称列表 */
    private List<String> headerNames;

    /** 用于给渲染模板使用的数据 */
    private DbMap renderData;

    /** 当前请求是否有文件上传 */
    private boolean hasFileUpload = false;

    HttpEvent(long requestStartTime, HttpServletRequest request, HttpServletResponse response) {
        this.requestStartTime = requestStartTime;
        this.request = request;
        this.response = response;
        // 提取请求中的cookies信息，并处理URL传递cookies的问题
        this.requestURI = request.getRequestURI();
        int idx = requestURI.indexOf(';');
        if (idx > 0) {
            requestURI = requestURI.substring(0, idx);
        }
        this.requestURL = request.getRequestURL().toString();
        idx = requestURL.indexOf(';');
        if (idx > 0) {
            requestURL = requestURL.substring(0, idx);
        }

        Cookie[] co = request.getCookies();
        if (CollectionTools.isNotEmpty(co)) {
            cookies = new WrappedMap<String, Cookie>() {

                @Override
                public String getString(String key) {
                    Cookie c = get(key);
                    return null == c ? null : c.getValue();
                }
            };
            for (Cookie c : co) {
                cookies.put(c.getName(), c);
            }
        } else {
            cookies = WrappedMap.emptyMap();
        }

        initPostJsonContent();
    }

    /**
     * 获取客户端的真实IP，会自动判断是否是转发请求
     */
    public static String getRemoteIP(HttpServletRequest req) {
        String headerIP = req.getHeader(DispatchFilter.headerNameForRealIP);
        if (StringTools.isNotEmpty(headerIP)) {
            // 检验来源是否是可信来源转发IP的
            if (null != DispatchFilter.credibleForwardSourceIPs) {
                if (DispatchFilter.credibleForwardSourceIPs.contains(req.getRemoteAddr())) {
                    return headerIP;
                }
                return req.getRemoteAddr();
            }
            // 未设置可信来源转发IP的，直接取header的IP
            return headerIP;
        }
        return req.getRemoteAddr();
    }

    /**
     * 获取用于生成cURL模拟请求的POST_CONTENT内容
     */
    private static String _getContentStringForCURL(String content) {
        StringBuilder sb = new StringBuilder(content.length() + 16);
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch < CURL_ESCAPE_LIST.length) {
                String append = CURL_ESCAPE_LIST[ch];
                sb.append(null != append ? append : ch);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * 获取简化的请求信息，单行输出，可指定输出head部分的内容
     *
     * @param contentString 当传入的content有二进制内容时，为了防止记录的日志乱码，允许直接指定记录内容
     * @param headers 请求头中header部分，需要记录的key，如果传null就不记录header
     * @param cookieKeys 请求中的cookie中需要记录的key，如果传null就不记录，如果想全部记下来，在headers里面加入{@link HttpHeaders#COOKIE}就可以了
     */
    private static String _getDetailInfoSimple(HttpServletRequest request, String contentString, Set<String> headers, Set<String> cookieKeys) {
        StringBuilder r = new StringBuilder();
        r.append("curl -X ").append(request.getMethod());
        // 如果请求头中包含了压缩参数，就应该在curl的参数中加上，否则模拟请求会乱码
        String ae = request.getHeader(HttpHeaders.ACCEPT_ENCODING);
        if (StringTools.isNotEmpty(ae) && (ae.contains("gzip") || ae.contains("deflate"))) {
            r.append(" --compressed");
        }
        String qs = request.getQueryString();
        String url = request.getRequestURL() + (null == qs ? "" : "?" + qs);

        r.append("  \"").append(StringTools.escapeQuotedString(url)).append("\"");
        // 只有传入header参数需要的才记录下来，防止记录的日志太多
        Enumeration<String> en = request.getHeaderNames();
        if (en.hasMoreElements() && CollectionTools.isNotEmpty(headers)) {
            while (en.hasMoreElements()) {
                String name = en.nextElement();
                if (headers.contains(name)) {
                    r.append(" -H '").append(StringTools.escapeQuotedString(name)).append(":").append(StringTools.escapeQuotedString(request.getHeader(name))).append("'");
                }
            }
        }
        // 只有指定了key的cookie才会被记录下来，缩短记录长度
        Cookie[] cookies = request.getCookies();
        if (CollectionTools.isNotEmpty(cookies) && CollectionTools.isNotEmpty(cookieKeys)) {
            r.append(" -b '");
            for (Cookie c : cookies) {
                if (cookieKeys.contains(c.getName())) {
                    r.append(c.getName()).append("=").append(StringTools.encodeURIComponent(c.getValue(), DispatchFilter.encoding)).append(";");
                }
            }
            r.append("'");
        }
        // 记录请求的content部分
        String content;
        if (null != contentString) { // 防止二进制内容导致日志乱码，允许做特殊处理，让业务代码自己指定content该怎么显示
            content = contentString;
        } else {
            byte[] contentBytes = _getContentBytes(request);
            content = _getContentString(request, contentBytes, DispatchFilter.encoding);
        }
        if (StringTools.isNotEmpty(content)) {
            r.append(" -d '").append(_getContentStringForCURL(content)).append("'");
        }
        StringBuilder pm = new StringBuilder();
        // 最后把内容编码过的请求参数重新打印一遍，如果没转义过就不再打出来，方便根据内容grep日志
        Map<String, String[]> map = request.getParameterMap();
        for (Entry<String, String[]> e : map.entrySet()) {
            for (String val : e.getValue()) {
                String esc = StringTools.encodeURIComponent(val);
                if (!esc.equals(val)) {
                    pm.append(e.getKey()).append("=").append(StringTools.escapeWhitespace(val)).append("&");
                }
            }
        }
        if (pm.length() > 0) {
            r.append("; ENCODED_PARAMS: ").append(pm.subSequence(0, pm.length() - 1));
        } else {
            r.append(";");
        }
        return r.toString();
    }

    /**
     * 获取简化的请求信息，单行输出，可指定输出head部分的内容
     *
     * @param headers 请求头中header部分，需要记录的key，如果传null就不记录header
     * @param cookieKeys 请求中的cookie中需要记录的key，如果传null就不记录，如果想全部记下来，在headers里面加入{@link HttpHeaders#COOKIE}就可以了
     */
    public static String getDetailInfoSimple(HttpServletRequest request, Set<String> headers, Set<String> cookieKeys) {
        return _getDetailInfoSimple(request, null, headers, cookieKeys);
    }

    /**
     * 获取浏览器http请求的content部分二进制内容
     */
    private static byte[] _getContentBytes(HttpServletRequest request) {
        byte[] contentBytes;
        int len = request.getContentLength(); // 注意，某些客户端请求信息不完整，此处的长度仅供参考，不可以真的相信客户端传入的大小
        if (len == -1) { // -1表示没有body数据,返回空字符串
            contentBytes = new byte[0];
        } else {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(len);
                InputStream in = request.getInputStream();
                int tmp;
                while ((tmp = in.read()) != -1) {
                    baos.write(tmp);
                }
                contentBytes = baos.toByteArray();
            } catch (IOException e) { // 读取出现异常，认为没有content部分
                contentBytes = new byte[0];
            }
        }
        return contentBytes;
    }

    /**
     * 获取浏览器http请求的content部分的字符串，可指定编码
     *
     * @param contentBytes content的内容需要提前使用{@link #_getContentBytes(HttpServletRequest)}读出
     */
    private static String _getContentString(HttpServletRequest request, byte[] contentBytes, Charset charset) {
        String contentString = "";
        // 如果是通过POST传参，从parameter里面去掉URL传参的内容，模拟出POST的内容
        // 这是由于jetty内部实现没有暴露出来，被迫采取的变通办法
        if ("POST".equals(request.getMethod()) && CollectionTools.isEmpty(contentBytes)) {
            Map<String, String[]> map = request.getParameterMap();
            if (CollectionTools.isNotEmpty(map)) {
                Set<String> exclude = new HashSet<>(map.size());
                String get = request.getQueryString();
                if (null != get) {
                    String[] arr = get.split("&");
                    for (String kv : arr) {
                        String key = kv.split("=")[0];
                        exclude.add(key);
                    }
                }
                StringBuilder cs = new StringBuilder();
                for (Entry<String, String[]> e : map.entrySet()) {
                    if (!exclude.contains(e.getKey())) {
                        for (String val : e.getValue()) {
                            cs.append(e.getKey()).append("=").append(StringTools.encodeURIComponent(val, DispatchFilter.encoding)).append("&");
                        }
                    }
                }
                if (cs.length() > 0 && cs.charAt(cs.length() - 1) == '&') {
                    cs.deleteCharAt(cs.length() - 1);
                }
                contentString = cs.toString();
            }
        } else {
            contentString = new String(contentBytes, charset);
        }
        return contentString;
    }

    /**
     * 如果是POST过来的数据，而且提交内容是json(contentType需为application/json)，尝试把json反序列化加入到parameterMap里。
     *
     * 注：只处理一层的JSON，多层的不解析。
     * 应用场景：微信小程序的wx.request接口post的时候把整个JSON提交了。(https://mp.weixin.qq.com/debug/wxadoc/dev/api/network-request.html)
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Type">https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Type</a>
     */
    private void initPostJsonContent() {
        String contentType = getContentType();
        if (isPostMethod() && contentType != null && contentType.startsWith("application/json")) {
            Request _request = getRequest();
            if (_request == null) {
                return;
            }
            byte[] contentBytes = getContentBytes();
            if (CollectionTools.isEmpty(contentBytes)) {
                return;
            }
            String content = new String(contentBytes, DispatchFilter.encoding);
            JSONObject jsonObject = JsonObjectUtils.parseJson(content);

            if (jsonObject == null) {
                return;
            }

            MultiMap<String> parameters = _request.getParameters();
            for (String key : jsonObject.keySet()) {
                String value = jsonObject.getString(key);
                if (value != null) {
                    parameters.add(key, value);
                }
            }
        }
    }

    /**
     * 获取请求内容的类型，当POST/PUT请求，请求可以告诉服务器传输的是什么类型的内容
     */
    public String getContentType() {
        return request.getContentType();
    }

    /**
     * 获取HttpServletRequest实例，仅用于框架使用，请勿调用
     */
    public HttpServletRequest getHttpServletRequest() {
        return this.request;
    }

    /**
     * 获取HttpServletResponse实例，仅用于框架使用，请勿调用
     */
    public HttpServletResponse getHttpServletResponse() {
        return this.response;
    }

    /**
     * 标记当前请求事件是否在异步业务逻辑处理中，如果不为null表示当前请求中提交了异步事件，正在等待事件完成通知回调
     */
    public TimeoutHandler getTimeoutHandler() {
        return timeoutHandler;
    }

    /**
     * 当前请求的用户帐户信息，请在验证登录态时设置，以便记录进访问日志
     */
    public String getAccessLogUserAccount() {
        return accessLogUserAccount;
    }

    /**
     * 在验证登录态时，请调用此方法将用户的登录帐户写入，以便记录访问日志
     */
    public void setAccessLogUserAccount(String accessLogUserAccount) {
        this.accessLogUserAccount = accessLogUserAccount;
    }

    /**
     * 记录到访问日志里面的返回内容
     */
    public String getAccessLogContent() {
        return accessLogContent;
    }

    /**
     * 设置记录在访问日志中的返回给前端的内容，如果没有在业务代码中指定，将默认取response的返回内容，如果是二进制内容默认将不记录
     */
    public void setAccessLogContent(Object... contents) {
        if (null == this.accessLogContent) {
            if (DispatchFilter.access_log.isInfoEnabled()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < contents.length; i++) {
                    Object obj = contents[i];
                    if (null != obj) {
                        if (obj instanceof Map) { // 如果是Map就将其中的内容转为JSON
                            obj = JsonObjectUtils.createJson(obj);
                        }
                        sb.append(obj);
                        if (i != contents.length - 1) {
                            sb.append(" ");
                        }
                    }
                }
                this.accessLogContent = StringTools.escapeWhitespace(sb.toString());
            }
        }
    }

    /**
     * 如果POST上来的数据包含二进制内容，为了防止打到access日志乱码，允许业务代码直接指定记录内容
     */
    public void setAccessLogPostContent(String contentString) {
        if (DispatchFilter.access_log.isInfoEnabled()) {
            this.contentString = contentString;
        }
    }

    /**
     * 初始化RESTful风格的URL中匹配结果数据
     */
    public void initRESTfulMatcher(Matcher m) {
        this.matcher = m;
    }

    /**
     * 获取HTTP请求执行时间,单位ms
     */
    long getRequestRunningTimeMs() {
        return System.currentTimeMillis() - requestStartTime;
    }

    /**
     * 获取当前请求的session
     */
    public HttpSession getSession() {
        return request.getSession(true);
    }

    /**
     * 获取请求中的cookies，注意这里不会包含addCookie方法加入的内容！
     */
    public WrappedMap<String, Cookie> getCookies() {
        return cookies;
    }

    /**
     * 返回应用部署的目录，如果不是特别设置一般都为/，当为/时返回的将是空字符串
     */
    public String getContextPath() {
        return request.getContextPath();
    }

    /**
     * 获得请求协议版本字符串，如HTTP/1.1
     */
    public String getProtocol() {
        return request.getProtocol();
    }

    /**
     * 获取浏览器http请求的content部分二进制内容（有缓存机制）
     */
    public byte[] getContentBytes() {
        if (null == contentBytes) {
            contentBytes = _getContentBytes(request);
        }
        return contentBytes;
    }

    /**
     * 获取浏览器http请求的content部分的字符串，可指定编码
     */
    public String getContentString(Charset charset) {
        byte[] contentBytes = getContentBytes();
        return _getContentString(request, contentBytes, charset);
    }

    /**
     * 获取浏览器http请求的content部分的字符串，以系统设定的默认编码方式解码（有缓存机制）
     */
    public String getContentString() {
        if (null == contentString) {
            byte[] contentBytes = getContentBytes();
            contentString = _getContentString(request, contentBytes, DispatchFilter.encoding);
        }
        return contentString;
    }

    /**
     * 获取请求来源客户端的IP，如果是转发请求会从header中获取原始IP，一般建议用本方法
     */
    public String getRemoteIP() {
        return getRemoteIP(request);
    }

    /**
     * 获取请求来源客户端的IP，转发请求会返回转发服务器的IP，不会从请求header中获取原始IP，一般情况建议不用该方法，而是用{@link #getRemoteIP()}
     */
    public String getRemoteIPSource() {
        return request.getRemoteAddr();
    }

    /**
     * 获取请求来源客户端的端口
     */
    public int getRemotePort() {
        return request.getRemotePort();
    }

    /**
     * 获取请求来源客户端的IP和端口
     */
    public String getRemoteIPAndPort() {
        return getRemoteIP() + ":" + getRemotePort();
    }

    /**
     * 获取客户端请求的服务器的IP，在多网卡的服务器上可用于判断用户从哪个线路请求进来
     */
    public String getLocalIP() {
        return request.getLocalAddr();
    }

    /**
     * 获取客户端请求的服务器的端口
     */
    public int getLocalPort() {
        return request.getLocalPort();
    }

    /**
     * 获取客户端请求的服务器的IP和端口
     */
    public String getLocalIPAndPort() {
        return getLocalIP() + ":" + getLocalPort();
    }

    /**
     * 返回给前端的HTTP响应码
     */
    public HttpStatus getStatus() {
        return HttpStatus.valueOf(response.getStatus());
    }

    /**
     * 设置返回的HTTP响应码
     */
    public void setStatus(HttpStatus code) {
        response.setStatus(code.value());
    }

    /**
     * 返回给前端的HTTP响应码，数字形式
     */
    public int getStatusInt() {
        return response.getStatus();
    }

    /**
     * 获取请求方式，字符串形式
     */
    String getMethodString() {
        return request.getMethod();
    }

    /**
     * 获取请求方式，获得{@code HttpMethod}的枚举类型
     */
    public HttpMethod getMethod() {
        return HttpMethod.valueOf(request.getMethod());
    }

    /**
     * 返回当前请求是否是GET请求
     */
    public boolean isGetMethod() {
        return HttpMethod.GET.equals(getMethod());
    }

    /**
     * 返回当前请求是否是POST请求
     */
    public boolean isPostMethod() {
        return HttpMethod.POST.equals(getMethod());
    }

    /**
     * 获取前端请求使用的域名
     */
    public String getHost() {
        return request.getHeader(HttpHeaders.HOST);
    }

    /**
     * <pre>
     * 获取请求的URI，不包含?后面的部分，系统会自动去除contextPath部分
     * 如部署在/demo目录下，请求/demo/test?name=123，此处返回/test
     * 如部署在/下，接口名为/demo/test，就返回/demo/test
     * </pre>
     */
    public String getRequestURI() {
        return requestURI;
    }

    /**
     * <pre>
     * 获取请求的URI，包含?后面的部分，系统会自动去除contextPath部分
     * 如部署在/demo目录下，请求/demo/test?name=123，此处返回/test?name=123
     * 如部署在/下，接口名为/demo/test，就返回/demo/test?name=123
     * </pre>
     */
    public String getRequestURIWithQueryString() {
        String qs = getQueryString();
        return getRequestURI() + (null == qs ? "" : "?" + qs);
    }

    /**
     * 获取请求的URL，不包含?后面的部分，包含contextPath部分
     *
     * @return 返回范例 http://127.0.0.1:8080/doc
     */
    public String getRequestURL() {
        return requestURL;
    }

    /**
     * 获取请求的URL，包含?后面的部分，包含contextPath部分
     *
     * @return 返回范例http://127.0.0.1:8080/doc?echo=test
     */
    public String getRequestURLWithQueryString() {
        String qs = getQueryString();
        return getRequestURL() + (null == qs ? "" : "?" + qs);
    }

    /**
     * 返回请求URL的?传参部分，如果没有带参数将返回null，返回内容是没有经过解码处理的
     *
     * @return 如请求URL为http://127.0.0.1:8080/echo?msg=123将返回msg=123
     */
    public String getQueryString() {
        return request.getQueryString();
    }

    /**
     * 获取请求的Header，如果不存在返回null
     */
    public String getHeader(String name) {
        return request.getHeader(name);
    }

    /**
     * 获取请求的Header
     */
    public String getHeader(String name, String defaultValue) {
        String value = request.getHeader(name);
        return value == null ? defaultValue : value;
    }

    /**
     * 获取请求的所有Header的名称列表
     */
    public List<String> getHeaderNames() {
        if (null == headerNames) {
            Enumeration<String> names = request.getHeaderNames();
            List<String> headers = new ArrayList<>();
            while (names.hasMoreElements()) {
                headers.add(names.nextElement());
            }
            Collections.sort(headers); // 按字典序排序一下
            headerNames = headers;
        }
        return headerNames;
    }

    private String _getString(String key) {
        // 空格的ascii码值有两个：从键盘输入的空格ascii值为0x20；从网页上的&nbsp;字符表单提交而来的空格ascii值为0xa0
        // 为了能处理网页表单提交的空格0xa0，这里将其替换成普通的0x20空格方便接下来的处理
        String str = request.getParameter(key);
        return null == str ? null : str.replace('\u00a0', ' ');
    }

    /**
     * <pre>
     * 获取请求的参数Map，可用于遍历，包含可从URL里提取的queryString和从POST CONTENT中提取的参数
     * 注意如果同一个key在请求参数中出现了多次，比如?key=1&key=2&key=3这样的请求，将按顺序出现在String[]中
     * </pre>
     */
    public Map<String, String[]> getRequestMapAll() {
        return request.getParameterMap();
    }

    /**
     * <pre>
     * 获取请求参数及其值，可用于遍历，包含可从URL里提取的queryString和从POST CONTENT中提取的参数
     * 注意如果同一个key在请求参数中出现了多次，比如?key=1&key=2&key=3这样的请求，将只取第一个
     * </pre>
     */
    public Map<String, String> getRequestMap() {
        Map<String, String> map = new HashMap<String, String>();
        for (Entry<String, String[]> e : request.getParameterMap().entrySet()) {
            map.put(e.getKey(), e.getValue()[0].replace('\u00a0', ' '));
        }
        return map;
    }

    /**
     * <pre>
     * 获取请求参数及其值，可用于遍历，包含可从URL里提取的queryString和从POST CONTENT中提取的参数
     * 注意如果同一个key在请求参数中出现了多次，比如?key=1&key=2&key=3这样的请求，将只取第一个
     * </pre>
     */
    public DbMap getRequestDbMap() {
        DbMap dm = new DbMap();
        for (Entry<String, String[]> e : request.getParameterMap().entrySet()) {
            dm.put(e.getKey(), e.getValue()[0].replace('\u00a0', ' '));
        }
        return dm;
    }

    /**
     * 输出http请求的详细信息
     */
    public StringBuilder getDetailInfo() {
        // 为了对齐，准备取最长的列作为列长度
        int keyMaxLen = getProtocol().length();
        Enumeration<String> params = request.getParameterNames();
        while (params.hasMoreElements()) {
            String name = params.nextElement();
            keyMaxLen = Math.max(keyMaxLen, name.length());
        }
        String fmt = "%" + (keyMaxLen + 1) + "s  %s\n";

        StringBuilder r = new StringBuilder();
        r.append("REQUEST FROM: ");
        r.append(getRemoteIPSource()).append(":").append(getRemotePort()).append(" -> ").append(getLocalIPAndPort()).append("\n");
        r.append("     REAL IP: ").append(getRemoteIP()).append("\n");
        r.append("cURL COMMAND:\n\n");
        r.append("curl -X ").append(getMethod());
        if ("HTTP/1.0".equals(getProtocol())) {
            r.append(" --http1.0");
        } else if ("HTTP/1.1".equals(getProtocol())) {
            r.append(" --http1.1");
        }
        // 如果请求头中包含了压缩参数，就应该在curl的参数中加上，否则模拟请求会乱码
        String ae = getHeader(HttpHeaders.ACCEPT_ENCODING);
        if (StringTools.isNotEmpty(ae) && (ae.contains("gzip") || ae.contains("deflate"))) {
            r.append(" --compressed");
        }
        r.append(" \\\n");
        r.append("-v '").append(StringTools.escapeQuotedString(getRequestURLWithQueryString())).append("' \\\n");
        // 注意，由于servlet的限制，只要通过getPrameter获取过参数，自动就解析了content部分
        // 无法之后再另行提取，故这里变通实现，根据parameter数据重建，这样contentLength可能有变，需要重新计算
        String content = getContentString();
        int contentLength = 0;
        if (StringTools.isNotEmpty(content)) {
            contentLength = content.getBytes(DispatchFilter.encoding).length;
        }
        if (!getHeaderNames().isEmpty()) {
            for (String name : getHeaderNames()) {
                if (contentLength > 0 && HttpHeaders.CONTENT_LENGTH.equals(name)) {
                    // 正常情况下这个长度信息是不会有问题的，但由于jetty的限制，不能直接提取到原始请求数据
                    // 这里的content是根据解码后的数据重建出来的，有可能跟原始的Content-Length不一致，故这里重新计算了
                    r.append("-H '").append(StringTools.escapeQuotedString(name)).append(":").append(contentLength).append("' \\\n");
                } else {
                    r.append("-H '").append(StringTools.escapeQuotedString(name)).append(":").append(StringTools.escapeQuotedString(getHeader(name))).append("' \\\n");
                }
            }
        }
        if (StringTools.isNotEmpty(content)) {
            if (hasFileUpload) {
                r.append("-d '[FileUpload Data Omit]'");
            } else {
                r.append("-d '").append(_getContentStringForCURL(content)).append("'");
            }
        } else {
            r.append("-d ''");
        }
        r.append("\n\n");

        Map<String, String[]> map = request.getParameterMap();
        if (CollectionTools.isNotEmpty(map)) {
            r.append("PARAM:\n");
            for (Entry<String, String[]> e : map.entrySet()) {
                for (String val : e.getValue()) {
                    r.append(String.format(fmt, e.getKey(), val));
                }
            }
        }
        return r;
    }

    /**
     * 获取简化的请求信息，单行输出，可指定输出head部分的内容
     *
     * @param headers 请求头中header部分，需要记录的key，如果传null就不记录header
     * @param cookieKeys 请求中的cookie中需要记录的key，如果传null就不记录，如果想全部记下来，在headers里面加入{@link HttpHeaders#COOKIE}就可以了
     */
    public String getDetailInfoSimple(Set<String> headers, Set<String> cookieKeys) {
        if (hasFileUpload) {
            return _getDetailInfoSimple(request, "[FileUpload Data Omit]", headers, cookieKeys);
        }
        String contentString = getContentString();
        return _getDetailInfoSimple(request, contentString, headers, cookieKeys);
    }

    /**
     * 从URL的正则表达式中提取指定组的值，请自行处理好下标越界的问题
     *
     * @param groupId 组ID，下标从1开始，即URL正则匹配规则里面用括号括起来的部分为一个组，从左到右组ID递增
     */
    public String getUrlString(int groupId) {
        return matcher.group(groupId);
    }

    /**
     * 从URL的正则表达式中提取指定组的值并转化为Character，失败会返回null
     *
     * @param groupId 组ID，下标从1开始，即URL正则匹配规则里面用括号括起来的部分为一个组，从左到右组ID递增
     */
    public Character getUrlChar(int groupId) {
        return StringTools.getChar(getUrlString(groupId));
    }

    /**
     * 从URL的正则表达式中提取指定组的值并转化为Integer，失败则取默认值
     *
     * @param groupId 组ID，下标从1开始，即URL正则匹配规则里面用括号括起来的部分为一个组，从左到右组ID递增
     */
    public Integer getUrlInt(int groupId, Integer defaultValue) {
        return StringTools.getInt(getUrlString(groupId), defaultValue);
    }

    /**
     * 从URL的正则表达式中提取指定组的值并转化为Long，失败则取默认值
     *
     * @param groupId 组ID，下标从1开始，即URL正则匹配规则里面用括号括起来的部分为一个组，从左到右组ID递增
     */
    public Long getUrlLong(int groupId, Long defaultValue) {
        return StringTools.getLong(getUrlString(groupId), defaultValue);
    }

    /**
     * 从URL的正则表达式中提取指定组的值并转化为Short，失败则取默认值
     *
     * @param groupId 组ID，下标从1开始，即URL正则匹配规则里面用括号括起来的部分为一个组，从左到右组ID递增
     */
    public Short getUrlShort(Short groupId, Short defaultValue) {
        return StringTools.getShort(getUrlString(groupId), defaultValue);
    }

    /**
     * 从URL的正则表达式中提取指定组的值并转化为Byte，失败则取默认值
     *
     * @param groupId 组ID，下标从1开始，即URL正则匹配规则里面用括号括起来的部分为一个组，从左到右组ID递增
     */
    public Byte getUrlByte(int groupId, Byte defaultValue) {
        return StringTools.getByte(getUrlString(groupId), defaultValue);
    }

    /**
     * 从URL的正则表达式中提取指定组的值并转化为Float，失败则取默认值
     *
     * @param groupId 组ID，下标从1开始，即URL正则匹配规则里面用括号括起来的部分为一个组，从左到右组ID递增
     */
    public Float getUrlFloat(int groupId, Float defaultValue) {
        return StringTools.getFloat(getUrlString(groupId), defaultValue);
    }

    /**
     * 从URL的正则表达式中提取指定组的值并转化为Double，失败则取默认值
     *
     * @param groupId 组ID，下标从1开始，即URL正则匹配规则里面用括号括起来的部分为一个组，从左到右组ID递增
     */
    public Double getUrlDouble(int groupId, Double defaultValue) {
        return StringTools.getDouble(getUrlString(groupId), defaultValue);
    }

    /**
     * 从URL的正则表达式中提取指定组的值并转化为Date，失败则取默认值
     *
     * @param groupId 组ID，下标从1开始，即URL正则匹配规则里面用括号括起来的部分为一个组，从左到右组ID递增
     */
    public Date getUrlDate(int groupId, String dateFormat, Date defaultValue) {
        Date date = DatetimeUtils.parse(getUrlString(groupId), dateFormat);
        return null == date ? defaultValue : date;
    }

    /**
     * 从请求参数中获取字符串，要求参数中必须有key，值允许为空字符串("")，否则就提示错误
     */
    @Override
    public String getString(String key) {
        String value = _getString(key);
        if (null == value) {
            throw new ParamEmptyException(key);
        }
        return value.trim();
    }

    /**
     * 从请求参数中获取字符串，如果参数中没有key，就使用defaultValue
     */
    @Override
    public String getString(String key, String defaultValue) {
        String value = _getString(key);
        if (null == value) {
            return defaultValue;
        }
        return value.trim();
    }

    /**
     * 从请求参数中获取布尔值，要求参数中必须要有key，如果key对应的值为true/Y/y/1将返回true，其他情况返回false
     */
    @Override
    public Boolean getBool(String key) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("y") || value.equals("1");
    }

    @Override
    public Boolean getBool(String key, Boolean defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        return value.equals("true") || value.equalsIgnoreCase("y") || value.equals("1");
    }

    @Override
    public Byte getByte(String key) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        try {
            return Byte.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidFormatException(key);
        }
    }

    @Override
    public Byte getByte(String key, Byte defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Byte.valueOf(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Character getChar(String key) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        return value.charAt(0);
    }

    @Override
    public Character getChar(String key, Character defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        return value.charAt(0);
    }

    @Override
    public Short getShort(String key) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        try {
            return Short.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidFormatException(key);
        }
    }

    @Override
    public Short getShort(String key, Short defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Short.valueOf(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Integer getInt(String key) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidFormatException(key);
        }
    }

    @Override
    public Integer getInt(String key, Integer defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Long getLong(String key) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidFormatException(key);
        }
    }

    @Override
    public Long getLong(String key, Long defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Float getFloat(String key) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        try {
            return Float.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidFormatException(key);
        }
    }

    @Override
    public Float getFloat(String key, Float defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Float.valueOf(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Double getDouble(String key) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidFormatException(key);
        }
    }

    @Override
    public Double getDouble(String key, Double defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Datetime getDate(String key) {
        return getDate(key, DatetimeUtils.getDefaultDatetimeFormat());
    }

    @Override
    public Datetime getDate(String key, Datetime defaultValue) {
        return getDate(key, DatetimeUtils.getDefaultDatetimeFormat(), defaultValue);
    }

    @Override
    public Datetime getDate(String key, String dateFormat) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        Date date = DatetimeUtils.parse(value, dateFormat);
        if (null == date) {
            throw new ParamInvalidFormatException(key);
        }
        return new Datetime(date);
    }

    @Override
    public Datetime getDate(String key, String dateFormat, Datetime defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        Date date = DatetimeUtils.parse(value, dateFormat);
        if (null == date) {
            return defaultValue;
        }
        return new Datetime(date);
    }

    /**
     * 从请求参数中获取字符串，与getString相比，不仅要求参数中必须要有key，还不允许出现空字符串(null或"")
     */
    public String getStringForce(String key) {
        String value = _getString(key);
        if (null == value) {
            throw new ParamEmptyException(key);
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new ParamEmptyException(key);
        }
        return value;
    }

    /**
     * 从请求参数中获取字符串，要求参数中必须要有key，且不允许出现空字符串(null或"")，否则就提示errMsg
     */
    public String getStringForce(String key, String errMsgEmpty) {
        String value = _getString(key);
        if (null == value) {
            throw new ParamInvalidValueException(key, errMsgEmpty);
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new ParamInvalidValueException(key, errMsgEmpty);
        }
        return value;
    }

    /**
     * 从请求参数中获取布尔值，要求参数中必须要有key，否则提示errMsgEmpty
     *
     * @param errMsgEmpty 如果参数值为空，提示的错误信息
     */
    public Boolean getBool(String key, String errMsgEmpty) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamInvalidValueException(key, errMsgEmpty);
        }
        return value.equals("true") || value.equalsIgnoreCase("y") || value.equals("1");
    }

    public Byte getByte(String key, String errMsgEmpty, String errMsgInvalid) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamInvalidValueException(key, errMsgEmpty);
        }
        try {
            return Byte.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidValueException(key, errMsgInvalid);
        }
    }

    public Character getChar(String key, String errMsgEmpty) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamInvalidValueException(key, errMsgEmpty);
        }
        return value.charAt(0);
    }

    public Short getShort(String key, String errMsgEmpty, String errMsgInvalid) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamInvalidValueException(key, errMsgEmpty);
        }
        try {
            return Short.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidValueException(key, errMsgInvalid);
        }
    }

    /**
     * 获取一个VO对象,VO对象中的字段可以实现CmdReqParam注解,来完成服务器端验证
     *
     * @see org.etnaframework.core.web.annotation.CmdReqParam
     */
    public <T> T getObjectForValidate(Class<T> clazz) throws Throwable {
        return ValidatorMappers.createFormObj(clazz, this, true);
    }

    /**
     * 获取一个VO对象,不进行校验判断
     */
    public <T> T getObjectWithoutValidate(Class<T> clazz) throws Throwable {
        return ValidatorMappers.createFormObj(clazz, this, false);
    }

    public Integer getInt(String key, String errMsgEmpty, String errMsgInvalid) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamInvalidValueException(key, errMsgEmpty);
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidValueException(key, errMsgInvalid);
        }
    }

    public Long getLong(String key, String errMsgEmpty, String errMsgInvalid) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamInvalidValueException(key, errMsgEmpty);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidValueException(key, errMsgInvalid);
        }
    }

    public Float getFloat(String key, String errMsgEmpty, String errMsgInvalid) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamInvalidValueException(key, errMsgEmpty);
        }
        try {
            return Float.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidValueException(key, errMsgInvalid);
        }
    }

    public Double getDouble(String key, String errMsgEmpty, String errMsgInvalid) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamInvalidValueException(key, errMsgEmpty);
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidValueException(key, errMsgInvalid);
        }
    }

    public Datetime getDate(String key, String errMsgEmpty, String errMsgInvalid) {
        return getDate(key, DatetimeUtils.getDefaultDatetimeFormat(), errMsgEmpty, errMsgInvalid);
    }

    public Datetime getDate(String key, String dateFormat, String errMsgEmpty, String errMsgInvalid) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamInvalidValueException(key, errMsgEmpty);
        }
        Date date = DatetimeUtils.parse(value, dateFormat);
        if (null == date) {
            throw new ParamInvalidValueException(key, errMsgInvalid);
        }
        return new Datetime(date);
    }

    /**
     * 判断是否已经有过回写前端的操作
     */
    public boolean isCommitted() {
        return response.isCommitted();
    }

    /**
     * 检查重复返回数据到客户端的情况
     */
    private void _checkCommittedOps() throws Throwable {
        // 除非是异步模式超时回包，否则如果多次给客户端返回（这是不允许的，而且会导致执行多余代码的问题），就要报错给出提醒
        if (onTimeoutCalled) {
            return;
        }
        throw new IllegalAccessException("已执行过返回数据到客户端的操作，不允许再次返回数据，请检查业务代码确保没有多次调用write/render/sendRedirect等方法！如果有分支情况，请记得在返回的代码后加return");
    }

    /**
     * 获取当前请求的客户端使用的区域语言信息，用于加入国际化支持
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * 设置当前请求的客户端使用的区域语言信息，用于加入国际化支持，例如zh_CN
     */
    public void setLocale(String localeString) {
        String[] sp = localeString.split("_");
        if (sp.length == 1) {
            this.locale = new Locale(sp[0]);
        } else if (sp.length == 2) {
            this.locale = new Locale(sp[0], sp[1]);
        }
    }

    /**
     * 设置当前请求的客户端使用的区域语言信息，用于加入国际化支持
     */
    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    /**
     * 在返回结果中添加cookie信息，注意加入的cookie不能用getCookies()来获取到！
     */
    public void addCookie(String key, Object value) {
        Cookie c = new Cookie(key, value.toString());
        c.setPath(request.getContextPath() + "/");
        response.addCookie(c);
    }

    /**
     * 在返回结果中添加cookie信息，注意加入的cookie不能用getCookies()来获取到！
     */
    public void addCookie(Cookie c) {
        response.addCookie(c);
    }

    /**
     * 在返回结果中添加cookie信息，注意加入的cookie不能用getCookies()来获取到！
     *
     * @param maxAge 从现在开始cookies的有效时间，单位秒
     */
    public void addCookie(String key, Object value, int maxAge) {
        Cookie c = new Cookie(key, value.toString());
        c.setPath(request.getContextPath() + "/");
        c.setMaxAge(maxAge);
        response.addCookie(c);
    }

    /**
     * 指示前端将当前跳转到指定地址（302临时跳转）
     */
    public void sendRedirect(String location) throws Throwable {
        synchronized (this) { // 当异步超时处理和回写操作同时触发时，只允许先来的操作
            if (isCommitted()) {
                _checkCommittedOps();
            }
            try {
                response.sendRedirect(location);
                setStatus(HttpStatus.FOUND);
                setAccessLogContent("[REDIRECT]", HttpStatus.FOUND.value(), location);
            } catch (IOException ex) { // 回写IO类异常全部不需要报出来，这些都是客户端断开连接所致的，客户端不会收到返回的数据，记录该情况到access日志里面就可以了
                Throwable cause = ex.getCause();
                // 这样格式的日志在eclipse的控制台里面会被标记超链接，默认是蓝色的，非常明显
                if (null == cause) {
                    String msg = ex.getMessage();
                    accessLogContent = "[DISCONNECTED]" + ex.getClass().getName() + (null == msg ? "" : ":" + msg);
                } else {
                    String msg = cause.getMessage();
                    accessLogContent = "[DISCONNECTED]" + cause.getClass().getName() + (null == msg ? "" : ":" + msg);
                }
            } finally {
                if (null != timeoutHandler) { // 有回写动作，就标记异步事件完成
                    timeoutHandler.complete();
                }
            }
        }
    }

    /**
     * 指示前端将当前跳转到指定地址（301永久跳转）
     */
    public void sendRedirectPermanently(String location) throws Throwable {
        synchronized (this) { // 当异步超时处理和回写操作同时触发时，只允许先来的操作
            if (isCommitted()) {
                _checkCommittedOps();
            }
            try {
                response.sendRedirect(location);
                setStatus(HttpStatus.MOVED_PERMANENTLY);
                setAccessLogContent("[REDIRECT]", HttpStatus.MOVED_PERMANENTLY.value(), location);
            } catch (IOException ex) { // 回写IO类异常全部不需要报出来，这些都是客户端断开连接所致的，客户端不会收到返回的数据，记录该情况到access日志里面就可以了
                Throwable cause = ex.getCause();
                // 这样格式的日志在eclipse的控制台里面会被标记超链接，默认是蓝色的，非常明显
                if (null == cause) {
                    String msg = ex.getMessage();
                    accessLogContent = "[DISCONNECTED]" + ex.getClass().getName() + (null == msg ? "" : ":" + msg);
                } else {
                    String msg = cause.getMessage();
                    accessLogContent = "[DISCONNECTED]" + cause.getClass().getName() + (null == msg ? "" : ":" + msg);
                }
            } finally {
                if (null != timeoutHandler) { // 有回写动作，就标记异步事件完成
                    timeoutHandler.complete();
                }
            }
        }
    }

    /**
     * 设置返回内容的head部分
     */
    public void setHeader(String name, Object value) {
        response.setHeader(name, value.toString());
    }

    /**
     * 设置用于渲染模板的数据
     */
    public void set(String key, Object value) {
        if (null == renderData) {
            renderData = new DbMap();
        }
        renderData.put(key, value);
    }

    /**
     * 获取renderData的数据
     */
    public DbMap getDatas() {
        if (null == renderData) {
            renderData = new DbMap();
        }
        return renderData;
    }

    /**
     * 返回字节信息到前端
     */
    private void _write(byte[] bytes, String contentType, Object... customAccessLogContent) throws Throwable {
        synchronized (this) { // 当异步超时处理和回写操作同时触发时，只允许先来的操作
            if (isCommitted()) {
                _checkCommittedOps();
            }
            try {
                response.setHeader(HttpHeaders.CONTENT_TYPE, contentType);
                response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(bytes.length));
                response.getOutputStream().write(bytes);
                response.flushBuffer();
                setAccessLogContent(customAccessLogContent);
            } catch (IOException ex) { // 回写IO类异常全部不需要报出来，这些都是客户端断开连接所致的，客户端不会收到返回的数据，记录该情况到access日志里面就可以了
                Throwable cause = ex.getCause();
                // 这样格式的日志在eclipse的控制台里面会被标记超链接，默认是蓝色的，非常明显
                if (null == cause) {
                    String msg = ex.getMessage();
                    accessLogContent = "[DISCONNECTED]" + ex.getClass().getName() + (null == msg ? "" : ":" + msg);
                } else {
                    String msg = cause.getMessage();
                    accessLogContent = "[DISCONNECTED]" + cause.getClass().getName() + (null == msg ? "" : ":" + msg);
                }
            } finally {
                if (null != timeoutHandler) { // 有回写动作，就标记异步事件完成
                    timeoutHandler.complete();
                }
            }
        }
    }

    /**
     * 返回字节信息到前端
     */
    public void write(byte[] bytes, String contentType) throws Throwable {
        _write(bytes, contentType, "[BINARY Content]" + bytes.length + "bytes");
    }

    /**
     * 返回纯文本信息到前端
     */
    public void writeText(Object text) throws Throwable {
        String t = text.toString();
        byte[] bytes = t.getBytes(DispatchFilter.encoding);
        _write(bytes, ContentTypes.PLAIN, t);
    }

    /**
     * 返回html文本到前端
     */
    public void writeHtml(Object text) throws Throwable {
        String t = text.toString();
        byte[] bytes = t.getBytes(DispatchFilter.encoding);
        _write(bytes, ContentTypes.HTML, t);
    }

    /**
     * 返回xml文本到前端
     */
    public void writeXml(Object text) throws Throwable {
        String t = text.toString();
        byte[] bytes = t.getBytes(DispatchFilter.encoding);
        _write(bytes, ContentTypes.XML, t);
    }

    /**
     * 给前端返回指定的JS脚本
     */
    public void writeJS(String... js) throws Throwable {
        StringBuilder sb = new StringBuilder("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">");
        sb.append("<head><script type=\"text/javascript\">");
        for (String j : js) {
            sb.append(j);
        }
        sb.append("</script></head></html>");
        String t = sb.toString();
        byte[] bytes = t.getBytes(DispatchFilter.encoding);
        _write(bytes, ContentTypes.HTML, "[RUN JS]", js);
    }

    /**
     * 返回只带rtn的JSON文本到前端
     */
    public void writeJson(int rtn) throws Throwable {
        writeJson(new RtnObject(rtn, null));
    }

    /**
     * 返回只rtn对应的异常信息JSON文本到前端，如果没有对应的异常就会返回空字符串
     */
    public void writeJsonEx(int rtn) throws Throwable {
        SimpleRtnBaseException err = RtnCodes.getRtnException(rtn);
        if (null == err) {
            writeText("");
        } else {
            writeJson(err.getDataObject());
        }
    }

    /**
     * 返回带rtn和data部分的JSON文本到前端
     */
    public void writeJson(int rtn, Object data) throws Throwable {
        writeJson(new RtnObject(rtn, data));
    }

    /**
     * 将传入的对象转化为JSON文本返回到前端
     */
    public void writeJson(Object obj) throws Throwable {
        writeJson(obj, DispatchFilter.jsonpCallbackKey);
    }

    /**
     * 将传入的对象转化为JSON文本返回到前端
     *
     * @param jsonpCallback 如果需要支持JSONP跨域，请在这里指定key
     */
    public void writeJson(Object obj, String jsonpCallback) throws Throwable {
        String callback = getString(jsonpCallback, "");
        if (DispatchFilter.jsonpCallbackEnable && StringTools.isNotEmpty(callback)) { // 如果支持jsonp且请求参数带了callback，自动转换为jsonp输出
            // 增加对Referer的域名限制,预防被第三方域名恶意调用接口,可配置,可开关.
            // @see http://blog.knownsec.com/2015/03/jsonp_security_technic/
            if (DispatchFilter.jsonpCallbackCredibleDomains != null && !DispatchFilter.jsonpCallbackCredibleDomains.isEmpty()) {
                // 如果有设置Referer域名限制,则检测判断
                String referer = getHeader(HttpHeaders.REFERER, "");
                if (StringTools.isEmpty(referer)) {
                    // 不支持空referer
                    writeText("FORBIDDEN");
                    return;
                }

                boolean checked = false;
                try {
                    URL url = new URL(referer);
                    for (String domain : DispatchFilter.jsonpCallbackCredibleDomains) {
                        if (url.getHost().endsWith(domain)) {
                            // 域名后缀匹配
                            checked = true;
                            break;
                        }
                    }
                } catch (Exception ignore) {
                }
                if (!checked) {
                    // 检测不过的话
                    writeText("FORBIDDEN");
                    return;
                }
            }
            if (callback.contains("<")) { // 封锁xss漏洞，特殊处理<符号
                writeText("FORBIDDEN");
            } else {
                String text = JsonObjectUtils.createJson(obj);
                String t = callback + "(" + text + ")";
                byte[] bytes = t.getBytes(DispatchFilter.encoding);
                _write(bytes, ContentTypes.JSON, t);
            }
            return;
        }
        String t = JsonObjectUtils.createJson(obj);
        byte[] bytes = t.getBytes(DispatchFilter.encoding);
        _write(bytes, ContentTypes.JSON, t);
    }

    /**
     * 使用模板引擎渲染并返回结果
     */
    public void render(String templatePath, String contentType) throws Throwable {
        synchronized (this) { // 当异步超时处理和回写操作同时触发时，只允许先来的操作
            if (isCommitted()) {
                _checkCommittedOps();
            }
            try {
                Template template = HttlTemplateUtils.getTemplate(templatePath);
                response.setContentType(contentType);
                setStatus(HttpStatus.OK);
                template.render(renderData, response.getOutputStream());
                setAccessLogContent("[RENDER]", contentType, template.getName() + "(" + new Datetime(template.getLastModified()) + ")", renderData);
            } catch (IOException ex) { // 回写IO类异常全部不需要报出来，这些都是客户端断开连接所致的，客户端不会收到返回的数据，记录该情况到access日志里面就可以了
                Throwable cause = ex.getCause();
                // 这样格式的日志在eclipse的控制台里面会被标记超链接，默认是蓝色的，非常明显
                if (null == cause) {
                    String msg = ex.getMessage();
                    accessLogContent = "[DISCONNECTED]" + ex.getClass().getName() + (null == msg ? "" : ":" + msg);
                } else {
                    String msg = cause.getMessage();
                    accessLogContent = "[DISCONNECTED]" + cause.getClass().getName() + (null == msg ? "" : ":" + msg);
                }
            } finally {
                if (null != timeoutHandler) { // 有回写动作，就标记异步事件完成
                    timeoutHandler.complete();
                }
            }
        }
    }

    /**
     * 使用模板引擎渲染文本并返回结果
     */
    public void renderText(String templatePath) throws Throwable {
        render(templatePath, ContentTypes.PLAIN);
    }

    /**
     * 使用模板引擎渲染HTML并返回结果
     */
    public void renderHtml(String templatePath) throws Throwable {
        render(templatePath, ContentTypes.HTML);
    }

    /**
     * 使用模板引擎渲染XML并返回结果
     */
    public void renderXml(String templatePath) throws Throwable {
        render(templatePath, ContentTypes.XML);
    }

    /**
     * 对字段值进行验证，如果表达式的结果为true，就会抛出错误提示给前端
     */
    public void validate(String field, boolean condition, String msg) {
        if (condition) {
            throw new ParamInvalidValueException(field, msg);
        }
    }

    /**
     * 对字段值进行验证，如果传入的object为null，就会抛出错误提示给前端
     */
    public void validateNull(String field, Object object, String msg) {
        if (null == object) {
            throw new ParamInvalidValueException(field, msg);
        }
    }

    /**
     * 对字段值进行验证，如果传入的object为null，就会抛出错误提示给前端
     */
    public void validateEmpty(String field, String object, String msg) {
        if (StringTools.isEmpty(object)) {
            throw new ParamInvalidValueException(field, msg);
        }
    }

    /**
     * 对字段值进行验证，如果传入的object不为null，就会抛出错误提示给前端
     */
    public void validateNotNull(String field, Object object, String msg) {
        if (null != object) {
            throw new ParamInvalidValueException(field, msg);
        }
    }

    /**
     * 执行一个异步任务，任务执行完成前将当前http请求的连接挂起，进入异步模式。超时时间受全局超时机制{@link DispatchFilter#asyncHoldOnTimeoutMs}控制
     *
     * @param handler 超时处理策略
     */
    public void holdOn(final TimeoutHandler handler) {
        holdOn(DispatchFilter.asyncHoldOnTimeoutMs, handler);
    }

    /**
     * 执行一个异步任务，任务执行完成前将当前http请求的连接挂起，进入异步模式。可指定连接超时时间
     *
     * @param timeoutMs 连接挂起的超时毫秒数，要求必须>0否则将不生效，如果在超时期限内没有任何回写动作，将会执行{@link TimeoutHandler#onTimeout()}方法
     * @param handler 超时处理策略
     */
    public void holdOn(int timeoutMs, final TimeoutHandler handler) {
        if (timeoutMs > 0) { // =0时连接将一直不断，这对系统有潜在风险，不准一直连着
            handler.he = this;
            handler.timeoutMs = timeoutMs;
            handler.asyncContext = (AsyncContextState) request.startAsync(request, response);
            handler.asyncContext.setTimeout(timeoutMs);
            handler.asyncContext.addListener(new AsyncListener() {

                @Override
                public void onComplete(AsyncEvent event) {
                    // 记录etna接口异步返回内容的日志
                    if (DispatchFilter.access_log.isInfoEnabled()) {
                        String logPart = DispatchFilter.accessLogRecorder.getAccessLog(HttpEvent.this, true);
                        if (null != logPart) {
                            DispatchFilter.access_log.info(logPart);
                        }
                    }
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                    synchronized (HttpEvent.this) { // 当异步超时处理和回写操作同时触发时，只允许先来的操作
                        if (isCommitted()) {
                            // 如果业务代码回写操作和超时自动回写操作同时触发，而超时这里是后执行的（检查时已经commit即可认定是后执行），这里直接不管就可以了，不需要检查报错了
                            return;
                        }
                        try {
                            handler.onTimeout();
                        } catch (Throwable e) {
                            DispatchFilter.recordThrowable(HttpEvent.this, e);
                        } finally {
                            // 如果在onTimeout方法中没有将请求事件完成，那么就主动完成之（如果没有这样做的话，这个就算请求失败了，直接穿到了DefaultServelt）
                            handler.complete();
                            onTimeoutCalled = true;
                        }
                    }
                }

                @Override
                public void onError(AsyncEvent event) {
                }

                @Override
                public void onStartAsync(AsyncEvent event) {
                }
            });
            this.timeoutHandler = handler;
        }
    }

    /**
     * 获取jetty的{@link Request}
     */
    Request getRequest() {
        Request req = null;
        if (request instanceof ServletRequestWrapper) { // javamelody会把request包装了一层
            ServletRequest realRequest = ((ServletRequestWrapper) request).getRequest();
            if (realRequest instanceof Request) {
                req = (Request) realRequest;
                req.extractParameters(); // 触发初始化参数map
            } else if (realRequest instanceof ServletRequestWrapper) { // 再寻找一次。
                ServletRequest realRequest2 = ((ServletRequestWrapper) realRequest).getRequest();
                if (realRequest2 instanceof Request) {
                    req = (Request) realRequest2;
                    req.extractParameters(); // 触发初始化参数map
                }
            }
        }
        if (request instanceof Request) {
            req = (Request) request;
            req.extractParameters(); // 触发初始化参数map
        }
        return req;
    }

    /**
     * <pre>
     * 如果form的enctype="multipart/form-data"，则需要使用此方法来解析字段和获取文件上传内容。
     *
     * 注意！必须放在he.getString()获取参数的前面！
     *
     * 如果遇到文件上传，此方法会把上传的文件放入缓冲区，然后再调用handler.file处理。
     * 简单说，就是先上传完毕，再处理。
     *
     * 如果超过文件上传限制，就抛FileSizeLimitExceededException
     *
     * 遗留bug:
     * 在jvm退出起前，如果File没有被GC掉，临时文件夹里的tmp文件在jvm退出后还会存在，再也不会被删掉了。
     * 临时文件夹路径：
     * windows: %TEMP%
     * linux:   /tmp
     *
     * 解决方法一：
     * 这里有个解决方法，但是尝试了，不知道哪里锁定了文件，不能立即被删除。
     * http://stackoverflow.com/questions/12301934/org-apache-commons-io-filecleaningtracker-does-not-delete-temp-files-unless-expl/13463785#13463785
     *
     * 解决方法二：
     * 在处理完后，调用System.gc(); 此方法有效（至少能清除上一次上传的文件）。
     *
     *
     * 特殊用法：
     * 如果上传文件较大，可以把FileItem强转为DiskFileItem，然后getStoreLocation()取得临时文件File，
     * 然后用rename转移目录就可以不用重新copy了，文件在同一分区会非常有效。
     * </pre>
     *
     * @see <a href="http://commons.apache.org/proper/commons-fileupload/using.html">http://commons.apache.org/proper/commons-fileupload/using.html</a>
     */
    public void getFile(FileUploadHandler handler) throws Throwable {
        boolean isMultipart = ServletFileUpload.isMultipartContent(request);
        if (!isMultipart) { // 不是multipart，不处理
            return;
        }
        Request req = getRequest(); // 获取jetty里的Request
        if (null != req) {
            req.extractParameters(); // 触发初始化参数map
        }
        DiskFileItemFactory factory = new DiskFileItemFactory();
        // 设置临时文件清理跟踪器，当File obj被gc时，会把临时文件清理掉。
        factory.setFileCleaningTracker(FileCleanerCleanup.getFileCleaningTracker(request.getServletContext()));
        // Create a new file upload handler
        ServletFileUpload upload = new ServletFileUpload(factory);
        // 设置单文件大小限制
        upload.setFileSizeMax(handler.getFileSizeMax());
        // 设置本次上传总大小限制
        upload.setSizeMax(handler.getSizeMax());
        // 设置进度监听器
        upload.setProgressListener(handler.getListener());
        // 解析上传的内容，如果超过限制，就报文件大小限制Exception
        // parse的实现原理是先使用streaming方式，把上传文件放到缓冲区，然后再构造出一个List<FileItem>
        List<FileItem> items = upload.parseRequest(request);

        // 先把表单元素加到ParameterMap里去，方便文件处理时可以从he里获取
        for (FileItem item : items) {
            if (item.isFormField()) { // 如果item是普通的表单元素，则加到ParameterMap里去
                if (req != null) {
                    String value;
                    try {
                        value = item.getString(request.getCharacterEncoding());
                    } catch (UnsupportedEncodingException e) {
                        value = item.getString();
                    }
                    req.getParameters().add(item.getFieldName(), value);
                }
            }
        }
        // 再处理已经上传好的文件
        for (FileItem item : items) {
            if (!item.isFormField()) {
                hasFileUpload = true;
                // 文件上传处理，如果没选择文件的话，item.getName() 是空的！
                String filename;
                try {
                    filename = item.getName();
                } catch (InvalidFileNameException e) {
                    filename = String.valueOf(System.currentTimeMillis());
                }

                if (!filename.isEmpty()) {
                    handler.file(item.getFieldName(), filename, item.getSize(), item.getInputStream(), this);
                    try {
                        item.getInputStream().close();
                        item.getOutputStream().close();
                        item.delete();// 处理完尝试把临时文件删除。
                    } catch (Exception e) {
                    }
                }
            }
        }
    }

    /**
     * <pre>
     * 如果form的enctype="multipart/form-data"，则需要使用此方法来解析字段和获取文件上传内容。
     *
     * 注意！必须放在he.getString()获取参数的前面！
     *
     * 如果遇到文件上传，此方法会边上传，边调用handler.file处理。
     * 简单说，就是边上传，边处理。效率会更高！
     *
     * 注意：file方法传的filesize是本次请求的总大小，并不是当前文件的大小。
     *
     * 如果超过文件上传限制，就抛FileSizeLimitExceededException
     * </pre>
     *
     * @see <a href="http://commons.apache.org/proper/commons-fileupload/streaming.html">http://commons.apache.org/proper/commons-fileupload/streaming.html</a>
     */
    public void getFileByStreaming(FileUploadHandler handler) throws Throwable {
        boolean isMultipart = ServletFileUpload.isMultipartContent(request);
        if (!isMultipart) { // 不是multipart不处理
            return;
        }
        Request req = getRequest();
        // Create a new file upload handler
        ServletFileUpload upload = new ServletFileUpload();
        // 设置单文件大小限制
        upload.setFileSizeMax(handler.getFileSizeMax());
        // 设置本次上传总大小限制
        upload.setSizeMax(handler.getSizeMax());
        // 设置进度监听器
        upload.setProgressListener(handler.getListener());

        long contentLength = request.getContentLength();
        // Parse the request
        FileItemIterator iter = upload.getItemIterator(request);
        while (iter.hasNext()) {
            FileItemStream item = iter.next();
            InputStream stream = item.openStream();

            if (item.isFormField()) {// 如果item是普通的表单元素，则加到ParameterMap里去
                if (req != null) {
                    String value;
                    try {
                        value = Streams.asString(stream, request.getCharacterEncoding());
                    } catch (UnsupportedEncodingException e) {
                        value = Streams.asString(stream);
                    }
                    req.getParameters().add(item.getFieldName(), value);
                }
            } else {
                hasFileUpload = true;
                // 文件上传处理，如果没选择文件的话，item.getName() 是空的！
                String filename;
                try {
                    filename = item.getName();
                } catch (InvalidFileNameException e) {
                    filename = String.valueOf(System.currentTimeMillis());
                }

                if (!filename.isEmpty()) {
                    handler.file(item.getFieldName(), filename, contentLength, stream, this);
                }
            }
        }
    }

    public static abstract class TimeoutHandler {

        protected HttpEvent he;

        AsyncContextState asyncContext;

        int timeoutMs;

        /**
         * 结束异步状态，完成请求
         */
        void complete() {
            State state = asyncContext.getHttpChannelState().getState();
            if (state == State.COMPLETECALLED || state == State.COMPLETED || state == State.COMPLETING) {
                return;
            }
            asyncContext.complete();
        }

        /**
         * 当进入异步模式挂起连接时，达到超时时间时需要做的事情，如果什么都不做请求将会给前端返回空
         */
        public abstract void onTimeout() throws Throwable;
    }
}
