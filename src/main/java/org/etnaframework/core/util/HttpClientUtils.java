package org.etnaframework.core.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.spring.annotation.Config;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.KeyValueGetter.DbMap;
import org.etnaframework.core.util.StringTools.CharsetEnum;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson.JSONObject;

/**
 * http请求封装，支持http/https
 *
 * 基于jdk自带的{@link URLConnection}包装实现，不依赖第三方库，简化了使用方式
 * 默认支持301/302跳转，内容gzip压缩，multipart文件上传等功能
 *
 * 参考：
 * a:) HttpURLConnection的connect()函数，实际上只是建立了一个与服务器的tcp连接，并没有实际发送http请求。
 *
 * 无论是post还是get，http请求实际上直到HttpURLConnection的getInputStream()这个函数里面才正式发送出去。
 *
 * b:) 在用POST方式发送URL请求时，URL请求参数的设定顺序是重中之重，
 * 对connection对象的一切配置（那一堆set函数）
 * 都必须要在connect()函数执行之前完成。而对outputStream的写操作，又必须要在inputStream的读操作之前。
 * 这些顺序实际上是由http请求的格式决定的。
 * 如果inputStream读操作在outputStream的写操作之前，会抛出例外：
 * java.net.ProtocolException: Cannot write output after reading input.......
 *
 * c:) http请求实际上由两部分组成，
 * 一个是http头，所有关于此次http请求的配置都在http头里面定义，
 * 一个是正文content。
 * connect()函数会根据HttpURLConnection对象的配置值生成http头部信息，因此在调用connect函数之前，
 * 就必须把所有的配置准备好。
 *
 * d:) 在http头后面紧跟着的是http请求的正文，正文的内容是通过outputStream流写入的，
 * 实际上outputStream不是一个网络流，充其量是个字符串流，往里面写入的东西不会立即发送到网络，
 * 而是存在于内存缓冲区中，待outputStream流关闭时，根据输入的内容生成http正文。
 * 至此，http请求的东西已经全部准备就绪。在getInputStream()函数调用的时候，就会把准备好的http请求
 * 正式发送到服务器了，然后返回一个输入流，用于读取服务器对于此次http请求的返回信息。由于http
 * 请求在getInputStream的时候已经发送出去了（包括http头和正文），因此在getInputStream()函数
 * 之后对connection对象进行设置（对http头的信息进行修改）或者写入outputStream（对正文进行修改）
 * 都是没有意义的了，执行这些操作会导致异常的发生。
 *
 * @author BlackCat
 * @see <a href="http://www.cnblogs.com/guodongli/archive/2011/04/05/2005930.html">查看参考</a>
 * @since 2016-11-25
 */
@Service
public final class HttpClientUtils {

    /** 什么https的证书都采信，尽量增加兼容性 */
    public static final TrustManager[] trustAllCerts = new TrustManager[] {
        new X509TrustManager() {

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        }
    };

    private static final Logger logger = Log.getLogger();

    /** 默认的连接超时时间，单位毫秒 */
    @Config("etna.httpClient.defaultConnectionTimeoutMS")
    public static int defaultConnectionTimeoutMS = Datetime.MILLIS_PER_SECOND * 10;

    /** 默认的请求过慢警告阈值，单位毫秒 */
    @Config("etna.httpClient.defaultSlowThresholdMS")
    public static int defaultSlowThresholdMS = Datetime.MILLIS_PER_SECOND * 1;

    /** 默认的读取超时时间，单位毫秒 */
    @Config("etna.httpClient.defaultSoTimeoutMS")
    public static int defaultSoTimeoutMS = Datetime.MILLIS_PER_SECOND * 10;

    /** 默认User-Agent，包含了服务信息和机器信息 */
    @Config("etna.httpClient.defaultUserAgent")
    public static String defaultUserAgent = "Mozilla/5.0 (" + SystemInfo.COMMAND_SHORT + "; " + SystemInfo.HOSTNAME + ")";

    /** 记录日志时，记录返回内容的最大长度 */
    @Config("etna.httpClient.defaultResultSize")
    public static int defaultResultSize = 500;

    /** https连接共用的socketFactory */
    private static SSLSocketFactory defaultSocketFactory;

    /** https连接共用的域名检测器 */
    private static HostnameVerifier defaultHostnameVerifier;

    static {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAllCerts, new SecureRandom());
            defaultSocketFactory = ctx.getSocketFactory();
            // https证书域名校验直接放过
            defaultHostnameVerifier = (s, sslSession) -> true;
        } catch (Exception ignore) {
        }
    }

    public static String httpGet(String url) {
        return get(url).fetch()
                       .getString();
    }

    public static byte[] httpGetBytes(String url) {
        return get(url).fetch()
                       .getBytes();
    }

    public static String httpPost(String url, String postContent) {
        return post(url).content(postContent)
                        .fetch()
                        .getString();
    }

    public static byte[] httpPostBytes(String url, String postContent) {
        return post(url).content(postContent)
                        .fetch()
                        .getBytes();
    }

    /**
     * 准备一个GET请求，支持http/https
     */
    public static HttpClientBuilder get(String url) {
        return new HttpClientBuilder(HttpMethod.GET, url);
    }

    /**
     * 准备一个POST请求，支持http/https
     */
    public static HttpClientBuilder post(String url) {
        return new HttpClientBuilder(HttpMethod.POST, url);
    }

    /**
     * 准备一个PUT请求，支持http/https
     */
    public static HttpClientBuilder put(String url) {
        return new HttpClientBuilder(HttpMethod.PUT, url);
    }

    /**
     * 准备一个DELETE请求，支持http/https
     */
    public static HttpClientBuilder delete(String url) {
        return new HttpClientBuilder(HttpMethod.DELETE, url);
    }

    /**
     * 准备一个HEAD请求，支持http/https
     */
    public static HttpClientBuilder head(String url) {
        return new HttpClientBuilder(HttpMethod.HEAD, url);
    }

    /**
     * 准备一个PATCH请求，支持http/https
     */
    public static HttpClientBuilder patch(String url) {
        return new HttpClientBuilder(HttpMethod.PATCH, url).header("X-HTTP-Method-Override", "PATCH");
    }

    /**
     * Http请求方法
     */
    private enum HttpMethod {
        GET,
        POST,
        HEAD,
        PUT,
        DELETE,
        TRACE,
        CONNECT,
        OPTIONS,
        PATCH
    }

    /**
     * 对http/https返回的结果进行校验，并根据校验结果决定是否进行重试操作
     */
    public interface HttpResultChecker<T> {

        /**
         * 对返回结果进行检查
         *
         * 返回true表示检查通过，不会触发重试机制，会接下来调用{@link #onSuccess(HttpResult)}
         * 返回false会触发重试，直到达到【重试次数上限】后调用{@link #onFailure(HttpResult)}
         *
         * 请控制好这里的代码，不要抛出异常，如果有异常【不会重试】，直接抛到最外面
         *
         * @param req 当检查不通过时，允许修改请求参数后再重试
         */
        boolean isExpected(HttpClientBuilder req, HttpResult hr);

        /**
         * 当检查结果为通过时，在这里提取用来返回的数据
         *
         * 请控制好这里的代码，不要抛出异常，如果有异常【不会重试】，直接抛到最外面
         */
        T onSuccess(HttpResult hr);

        /**
         * 如果到达重试次数上限仍未检查通过，就会调用本方法
         *
         * 在该方法里，可做如下的处理：
         *
         * 1、检查失败，返回一个默认的失败结果
         * 2、抛出异常阻断接下来的流程
         */
        T onFailure(HttpResult hr);
    }

    public static class HttpClientBuilder {

        /** 请求方式，参见HttpMethod的变量定义 */
        private HttpMethod method;

        /** 请求URL */
        private String url;

        /** 请求编码/解码使用的字符集 */
        private Charset charset = CharsetEnum.UTF_8;

        /** 连接超时时间，单位毫秒 */
        private int connTimeout = defaultConnectionTimeoutMS;

        /** 读取超时时间，单位毫秒 */
        private int soTimeout = defaultSoTimeoutMS;

        /** 请求使用的User-Agent */
        private String userAgent = defaultUserAgent;

        /** 默认使用短连接 */
        private boolean keepAlive = false;

        /** 默认开启gzip */
        private boolean gzip = true;

        /** 请求带的cookies */
        private Map<String, Object> cookies = new LinkedHashMap<>();

        /** 请求带的headers */
        private Map<String, Object> headers = new LinkedHashMap<>();

        /** 请求在URL上添加的参数 */
        private Map<String, Object> urlParams = new LinkedHashMap<>();

        /** POST请求添加到content的参数 */
        private Map<String, Object> contentParams = new LinkedHashMap<>();

        /** POST请求的包体内容 */
        private byte[] content;

        /** POST请求的包体内容字符串 */
        private String contentString;

        /** 日志输出log对象 */
        private Logger log = logger;

        /** 是否为multipart */
        private boolean multipart;

        /** multipart的边界分隔符 */
        private String boundary;

        /** 上传文件列表 */
        private List<UploadFile> fileList = new ArrayList<>();

        /** 代理服务器 */
        private Proxy proxy;

        /** socks代理服务器的密码配置 */
        private PasswordAuthentication socksProxyPassword;

        /** 遇到3xx自动跳转 */
        private boolean autoRedirect = true;

        /** https连接共用的socketFactory */
        private SSLSocketFactory socketFactory = defaultSocketFactory;

        /** 记录访问日志，是否只在出错的时候记录 */
        private boolean logOnErrorOnly = false;

        private HttpClientBuilder(HttpMethod method, String url) {
            this.method = method;
            this.url = url;
        }

        /**
         * 设置日志输出log对象
         */
        public HttpClientBuilder log(Logger log) {
            if (log != null) {
                this.log = log;
            }
            return this;
        }

        /**
         * 记录访问日志，是否只在出错的时候记录
         */
        public HttpClientBuilder logOnErrorOnly() {
            this.logOnErrorOnly = true;
            return this;
        }

        /**
         * 设置自定义的socketFactory
         */
        public HttpClientBuilder socketFactory(SSLSocketFactory socketFactory) {
            this.socketFactory = socketFactory;
            return this;
        }

        /**
         * 设置不自动跳转，可用于捕获header信息，默认情况是会自动完成跳转的
         */
        public HttpClientBuilder noAutoRedirect() {
            this.autoRedirect = false;
            return this;
        }

        /**
         * 设置请求编码/解码使用的字符集
         */
        public HttpClientBuilder charset(Charset charset) {
            this.charset = charset;
            return this;
        }

        /**
         * 设置请求使用的User-Agent
         */
        public HttpClientBuilder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * 使用系统自带的连接池，能提升性能，但会有连接丢失卡死的问题，请按需斟酌使用
         */
        public HttpClientBuilder keepAlive() {
            this.keepAlive = true;
            return this;
        }

        /**
         * 使用post multipart/form-data上传数据
         */
        public HttpClientBuilder multipart() {
            _ensurePost();
            if (multipart) {
                return this;
            }
            this.multipart = true;
            this.boundary = "------------------------" + Long.toHexString(System.currentTimeMillis());
            this.header("Content-Type", "multipart/form-data; boundary=" + this.boundary);
            return this;
        }

        /**
         * 关闭gzip压缩，能提升CPU性能，但会导致网络流量加大，在有带宽限制的环境下谨慎使用
         */
        public HttpClientBuilder noGZip() {
            this.gzip = false;
            return this;
        }

        /**
         * 设置连接/读取的超时时间，一个值赋给两个参数，单位毫秒
         */
        public HttpClientBuilder timeout(int mills) {
            this.connTimeout = mills;
            this.soTimeout = mills;
            return this;
        }

        /**
         * 设置连接/读取的超时时间，一个值赋给两个参数，可指定单位
         */
        public HttpClientBuilder timeout(int timeout, TimeUnit unit) {
            int mills = (int) unit.toMillis(timeout);
            this.connTimeout = mills;
            this.soTimeout = mills;
            return this;
        }

        /**
         * 设置连接的超时时间，单位毫秒
         */
        public HttpClientBuilder timeoutForConn(int mills) {
            this.connTimeout = mills;
            return this;
        }

        /**
         * 设置连接的超时时间，可指定单位
         */
        public HttpClientBuilder timeoutForConn(int timeout, TimeUnit unit) {
            this.connTimeout = (int) unit.toMillis(timeout);
            ;
            return this;
        }

        /**
         * 设置读取的超时时间，单位毫秒
         */
        public HttpClientBuilder timeoutForSo(int mills) {
            this.soTimeout = mills;
            return this;
        }

        /**
         * 设置读取的超时时间，可指定单位
         */
        public HttpClientBuilder timeoutForSo(int timeout, TimeUnit unit) {
            this.soTimeout = (int) unit.toMillis(timeout);
            return this;
        }

        /**
         * 设置Basic认证账号密码
         */
        public HttpClientBuilder auth(String username, String password) {
            String authorization = username + ":" + password;
            return header("Authorization", "Basic " + Base64.getEncoder()
                                                            .encodeToString(authorization.getBytes()));
        }

        /**
         * 设置请求头内容
         */
        public HttpClientBuilder header(String key, Object value) {
            if ("User-Agent".equals(key)) {
                throw new HttpClientException("User-Agent请通过userAgent方法设置");
            }
            headers.put(key, value);
            return this;
        }

        /**
         * 设置请求头内容
         */
        public HttpClientBuilder header(Map<String, Object> header) {
            headers.putAll(header);
            return this;
        }

        /**
         * 添加cookie
         */
        public HttpClientBuilder cookie(String key, Object value) {
            cookies.put(key, value);
            return this;
        }

        /**
         * 添加cookie
         */
        public HttpClientBuilder cookie(Map<String, Object> cookie) {
            cookies.putAll(cookie);
            return this;
        }

        /**
         * 添加URL参数，会自动添加&=等参数并进行UrlEncode
         */
        public HttpClientBuilder urlParam(String key, Object value) {
            urlParams.put(key, value);
            return this;
        }

        /**
         * 添加URL参数，会自动添加&=等参数并进行UrlEncode
         */
        public HttpClientBuilder urlParam(Map<String, Object> params) {
            urlParams.putAll(params);
            return this;
        }

        /**
         * 确保使用POST请求
         */
        private void _ensurePost() {
            if (HttpMethod.POST != method && HttpMethod.PUT != method) {
                throw new HttpClientException("该方法只能在POST/PUT请求使用");
            }
        }

        /**
         * 确保POST请求不出现contentParam和content同时设置
         */
        private void _ensurePostContentNoConflict() {
            if (null != content && !contentParams.isEmpty()) {
                throw new HttpClientException("contentParam和content不能同时设置");
            }
        }

        /**
         * 仅限POST请求使用（GET请求会报错），添加content参数，会自动添加&=等参数并进行UrlEncode
         */
        public HttpClientBuilder contentParam(String key, Object value) {
            _ensurePost();
            contentParams.put(key, value);
            _ensurePostContentNoConflict();
            return this;
        }

        /**
         * 仅限POST请求使用（GET请求会报错），添加content参数，会自动添加&=等参数并进行UrlEncode
         */
        public HttpClientBuilder contentParam(Map<String, Object> params) {
            _ensurePost();
            contentParams.putAll(params);
            _ensurePostContentNoConflict();
            return this;
        }

        /**
         * 仅限POST请求使用（GET请求会报错），添加content内容，使用请求指定的编码方案编码
         */
        public HttpClientBuilder content(String string) {
            _ensurePost();
            if (null != content) {
                throw new HttpClientException("content内容已存在，不能再次赋值");
            }
            content = string.getBytes(charset);
            contentString = string;
            _ensurePostContentNoConflict();
            return this;
        }

        /**
         * 仅限POST请求使用（GET请求会报错），添加content二进制内容
         */
        public HttpClientBuilder content(byte[] bytes) {
            _ensurePost();
            if (null != content) {
                throw new HttpClientException("content内容已存在，不能再次赋值");
            }
            content = bytes;
            contentString = "[BINARY_DATA=" + bytes.length + "]";
            _ensurePostContentNoConflict();
            return this;
        }

        /**
         * 仅限POST请求使用（GET请求会报错），上传文件
         */
        public HttpClientBuilder file(String name, File file, String filename, String contentType) {
            _ensurePost();
            if (!multipart) {
                multipart();
            }
            if (file == null || file.length() == 0) {
                throw new HttpClientException("file为空");
            }

            UploadFile uploadFile = new UploadFile(name, file, filename, contentType);
            fileList.add(uploadFile);
            return this;
        }

        /**
         * 仅限POST请求使用（GET请求会报错），上传文件
         */
        public HttpClientBuilder file(String name, byte[] bytes, String filename, String contentType) {
            _ensurePost();
            if (!multipart) {
                multipart();
            }
            if (bytes == null || bytes.length == 0) {
                throw new HttpClientException("bytes内容为空");
            }

            UploadFile uploadFile = new UploadFile(name, bytes, filename, contentType);
            fileList.add(uploadFile);
            return this;
        }

        /**
         * 仅限POST请求使用（GET请求会报错），上传文件
         */
        public HttpClientBuilder file(String name, File file, String contentType) {
            _ensurePost();
            if (!multipart) {
                multipart();
            }
            if (file == null || file.length() == 0) {
                throw new HttpClientException("file为空");
            }

            UploadFile uploadFile = new UploadFile(name, file, contentType);
            fileList.add(uploadFile);
            return this;
        }

        /**
         * 仅限POST请求使用（GET请求会报错），上传文件
         */
        public HttpClientBuilder file(String name, byte[] bytes, String contentType) {
            _ensurePost();
            if (!multipart) {
                multipart();
            }
            if (bytes == null || bytes.length == 0) {
                throw new HttpClientException("bytes内容为空");
            }

            UploadFile uploadFile = new UploadFile(name, bytes, contentType);
            fileList.add(uploadFile);
            return this;
        }

        /**
         * 仅限POST请求使用（GET请求会报错），上传文件
         */
        public HttpClientBuilder file(String name, byte[] bytes) {
            _ensurePost();
            if (!multipart) {
                multipart();
            }
            if (bytes == null || bytes.length == 0) {
                throw new HttpClientException("bytes内容为空");
            }

            UploadFile uploadFile = new UploadFile(name, bytes);
            fileList.add(uploadFile);
            return this;
        }

        /**
         * 仅限POST请求使用（GET请求会报错），上传文件
         */
        public HttpClientBuilder file(String name, File file) {
            _ensurePost();
            if (!multipart) {
                multipart();
            }
            if (file == null || file.length() == 0) {
                throw new HttpClientException("file为空");
            }

            UploadFile uploadFile = new UploadFile(name, file);
            fileList.add(uploadFile);
            return this;
        }

        /**
         * 设置HTTP代理服务器
         */
        public HttpClientBuilder proxyHttp(String host, int port) {
            proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
            return this;
        }

        /**
         * 设置HTTP代理服务器
         */
        public HttpClientBuilder proxyHttp(String host, int port, String proxyUsername, String proxyPassword) {
            proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
            String authorization = proxyUsername + ":" + proxyPassword;
            header("Proxy-Authorization", "Basic " + Base64.getEncoder()
                                                           .encodeToString(authorization.getBytes()));
            return this;
        }

        /**
         * 设置SOCKS代理服务器
         */
        public HttpClientBuilder proxySocks(String host, int port) {
            proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port));
            return this;
        }

        /**
         * 设置SOCKS代理服务器
         */
        public HttpClientBuilder proxySocks(String host, int port, String proxyUsername, String proxyPassword) {
            proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port));
            socksProxyPassword = new PasswordAuthentication(proxyUsername, proxyPassword.toCharArray());
            return this;
        }

        /**
         * 指定Host连接的IP
         * 注：仅支持单次连接Host指定IP，如果遇到302，跳转后的链接会重新解析实际域名
         */
        public HttpClientBuilder ip(String ip) {
            try {
                URL _url = new URL(this.url);
                String host = _url.getHost();
                header("Host", host);
                // 由于安全原因，HttpURLConnection默认限制了一些header不能被自定义覆盖
                // 如果要使用此功能，则要关闭这个安全限制，JVM级关闭该特性。
                // sun.net.www.protocol.http.HttpURLConnection.restrictedHeaders
                // String[] restrictedHeaders = new String[] {
                //     "Access-Control-Request-Headers",
                //     "Access-Control-Request-Method",
                //     "Connection",
                //     "Content-Length",
                //     "Content-Transfer-Encoding",
                //     "Host",
                //     "Keep-Alive",
                //     "Origin",
                //     "Trailer",
                //     "Transfer-Encoding",
                //     "Upgrade",
                //     "Via"
                // };

                // 但是！设置"Connection":"close"是允许的！"Connection"默认是"keep-alive"
                // 参考：sun.net.www.protocol.http.HttpURLConnection.isRestrictedHeader()

                if (!"true".equals(System.getProperty("sun.net.http.allowRestrictedHeaders"))) {
                    System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
                }
                this.url = this.url.replaceFirst(host, ip);
            } catch (MalformedURLException ignore) {
            }
            return this;
        }

        /**
         * 执行HTTP请求，并获取返回的所有数据，如果是连接/读取失败【不会返回null】，会返回一个没有内容的{@link HttpResult}
         */
        public HttpResult fetch() {
            // 1、准备生成请求，以及对应的cURL命令，用于记录日志备查

            // 生成URL传参的参数，构成完整的URL
            String fullUrl = StringTools.addParamsToUrl(url, urlParams);
            StringBuilder cmd = new StringBuilder("curl -X ").append(method.toString())
                                                             .append(" -v '")
                                                             .append(fullUrl)
                                                             .append("'");

            HttpResult result = null;
            String code = "ERR";
            long start = System.currentTimeMillis();
            try {

                // 构造header，注意，按照http标准，header部分不应当做任何编解码处理
                if (!headers.containsKey("User-Agent")) {
                    headers.put("User-Agent", userAgent);
                }
                headers.put("Connection", keepAlive ? "keep-alive" : "close");
                if (gzip) {
                    headers.put("Accept-Encoding", "gzip");
                }
                for (Entry<String, Object> e : headers.entrySet()) {
                    cmd.append(" -H '")
                       .append(e.getKey())
                       .append(":")
                       .append(e.getValue())
                       .append("'");
                }
                StringBuilder cookieString = new StringBuilder();
                for (Entry<String, Object> e : cookies.entrySet()) {
                    cookieString.append(e.getKey())
                                .append("=")
                                .append(e.getValue())
                                .append(";");
                }
                String cookie = null;
                if (cookieString.length() > 1) {
                    cookieString.deleteCharAt(cookieString.length() - 1);
                    cookie = cookieString.toString();
                    cmd.append(" -b '")
                       .append(cookie)
                       .append("'");
                }

                // 构造content部分
                if (multipart) {
                    Object ct = headers.get("Content-Type");
                    if (ct == null || !ct.toString()
                                         .startsWith("multipart/form-data")) {
                        throw new HttpClientException("multipart请求的Content-Type不能被覆盖");
                    }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();

                    // 正文中的边界线会有--作为前缀
                    byte[] boundaryBytes = ("--" + this.boundary + "\r\n").getBytes(charset);
                    // 最后一个边界线以--作为后缀
                    byte[] boundaryEndBytes = ("--" + this.boundary + "--\r\n").getBytes(charset);
                    byte[] CRLF = "\r\n".getBytes(charset);

                    // 普通文本字段
                    for (Entry<String, Object> e : contentParams.entrySet()) {
                        out.write(boundaryBytes);
                        out.write(("Content-Disposition: form-data; name=\"" + e.getKey() + "\"").getBytes(charset));
                        out.write(CRLF);
                        out.write(CRLF);
                        out.write(StringTools.encodeURIComponent(String.valueOf(e.getValue()), charset)
                                             .getBytes(charset));
                        out.write(CRLF);

                        cmd.append(" -F ")
                           .append(e.getKey())
                           .append("=")
                           .append(StringTools.encodeURIComponent(String.valueOf(e.getValue()), charset));
                    }

                    // 文件上传
                    for (UploadFile uploadFile : fileList) {
                        out.write(boundaryBytes);
                        out.write(("Content-Disposition: form-data; name=\"" + uploadFile.getName() + "\"").getBytes(charset));
                        // 必须要有filename，否则会被认为是普通字段
                        out.write(("; filename=\"" + uploadFile.getFilename() + "\"").getBytes(charset));
                        out.write(CRLF);
                        out.write(("Content-Type: " + uploadFile.getContentType()).getBytes(charset));
                        out.write(CRLF);
                        out.write(CRLF);
                        out.write(uploadFile.getBytes());
                        out.write(CRLF);

                        cmd.append(" -F ")
                           .append(uploadFile.getName())
                           .append("=@")
                           .append(uploadFile.getFilename());
                    }
                    // 结束正文
                    out.write(boundaryEndBytes);
                    content = out.toByteArray();
                } else if (!contentParams.isEmpty()) {
                    StringBuilder cp = new StringBuilder();
                    for (Entry<String, Object> e : contentParams.entrySet()) {
                        cp.append(e.getKey())
                          .append("=")
                          .append(StringTools.encodeURIComponent(String.valueOf(e.getValue()), charset))
                          .append("&");
                    }
                    if (cp.length() > 0) { // 去除最后的&
                        cp.deleteCharAt(cp.length() - 1);
                    }
                    contentString = cp.toString();
                    content = contentString.getBytes(charset);
                    headers.put("Content-Type", "application/x-www-form-urlencoded;charset=" + charset.toString());
                    cmd.append(" -d '")
                       .append(contentString)
                       .append("'");
                } else if (null != content) {
                    cmd.append(" -d '")
                       .append(contentString)
                       .append("'");
                }

                // 2、准备连接配置，开始请求

                start = System.currentTimeMillis();

                URL realUrl = new URL(fullUrl);
                URLConnection urlConn = proxy == null ? realUrl.openConnection() : realUrl.openConnection(proxy);

                if (!(urlConn instanceof HttpURLConnection)) { // 只能支持http/https
                    throw new HttpClientException("不支持的协议类型" + realUrl.getProtocol());
                }
                HttpURLConnection conn = (HttpURLConnection) urlConn;

                // 针对https的增强兼容性处理，如果网站使用的证书不合法，忽略报错强行请求，确保最大兼容性
                if (conn instanceof HttpsURLConnection) {
                    HttpsURLConnection c = (HttpsURLConnection) conn;
                    c.setSSLSocketFactory(socketFactory);
                    c.setHostnameVerifier(defaultHostnameVerifier);
                }

                // 添加请求头
                for (Entry<String, Object> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), String.valueOf(e.getValue()));
                }
                // 添加cookie（实质也是header的一部分，比较特殊单独提出来考虑）
                // 如果已经通过设置header指定了cookie，那这里为了防止覆盖，报错让使用者自己二选一
                if (headers.containsKey("Cookie") && !cookies.isEmpty()) {
                    throw new HttpClientException("已经在header中指定了cookie，请不要再单独传入cookie设置");
                }
                if (null != cookie) {
                    conn.setRequestProperty("Cookie", cookieString.toString());
                }

                // 设置连接参数
                conn.setRequestMethod(method.toString());
                if (HttpMethod.POST == method || HttpMethod.PUT == method) {
                    // 只能在有outputstream的请求时才能设置为true.
                    conn.setDoOutput(true);
                }
                conn.setDoInput(true);
                conn.setConnectTimeout(connTimeout);
                conn.setReadTimeout(soTimeout);
                conn.setUseCaches(false);
                conn.setInstanceFollowRedirects(autoRedirect);

                // 在调用此方法前，必需完成conn的各种set配置
                if (null != socksProxyPassword) {
                    // 由于java本身的限制，使用带密码的socks代理是全局性的
                    // 为了能在一定程度上支持并发，这里加锁处理
                    synchronized (HttpClientUtils.class) {
                        try {
                            Authenticator.setDefault(new Authenticator() {

                                @Override
                                protected PasswordAuthentication getPasswordAuthentication() {
                                    return socksProxyPassword;
                                }
                            });
                            conn.connect();
                        } finally {
                            Authenticator.setDefault(null);
                        }
                    }
                } else {
                    conn.connect();
                }

                // 如果是POST/PUT请求，这里提交数据
                if (HttpMethod.POST == method || HttpMethod.PUT == method) {
                    OutputStream out = conn.getOutputStream();
                    if (null != content) {
                        out.write(content);
                    }
                    out.flush();
                }

                // 读取返回数据，由于采用的短连接方式，直接读到EOF即可
                code = String.valueOf(conn.getResponseCode());
                // 提前创建HttpResult，即使404也能返回header内容
                result = new HttpResult(cmd.toString(), conn.getResponseCode(), conn.getHeaderFields(), charset);
                // 读取返回数据

                InputStream is;
                if (result.getStatusCode() >= 400) {
                    // 当请求回包大于等于400，即请求错误时，获取错误输入流
                    is = conn.getErrorStream();
                } else {
                    // 正常情况下，获取输入流
                    is = conn.getInputStream();
                }
                if (is != null) {
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        int r;
                        while ((r = is.read()) != -1) {
                            baos.write(r);
                        }
                        // 设置接收到的content数据
                        result.setContent(baos.toByteArray());
                    } finally {
                        is.close();
                    }
                }
                // 打印日志
                if (!logOnErrorOnly) { // 如果设置了访问正常不记录日志就不记录
                    long span = System.currentTimeMillis() - start;
                    if (span >= defaultSlowThresholdMS) {
                        log.warn("OK-SLOW/{}/{}ms/{}/{}/RESULT: {}", result.getStatusCode(), span, result.getContentLength(), cmd,
                            StringTools.escapeWhitespaceAndTruncate(result.getString(), defaultResultSize));
                    } else {
                        log.info("OK/{}/{}ms/{}/{}/RESULT: {}", result.getStatusCode(), span, result.getContentLength(), cmd,
                            StringTools.escapeWhitespaceAndTruncate(result.getString(), defaultResultSize));
                    }
                }
            } catch (HttpClientException e) { // 参数检查抛的错，直接抛出去提醒
                throw e;
            } catch (Throwable e) {
                log.info("FAILED/{}/{}ms/{}", code, System.currentTimeMillis() - start, cmd, e);
            }
            if (null == result) { // 如果请求过程中出现异常，需要保存请求信息备查
                result = new HttpResult(cmd.toString());
            }
            return result;
        }

        /**
         * 执行HTTP请求，并获取返回的数据，带内容检查机制
         *
         * @param checker 请参考{@link HttpResultChecker}的定义说明
         */
        public <T> T fetch(HttpResultChecker<T> checker) {
            HttpResult result = fetch();
            if (checker.isExpected(this, result)) {
                return checker.onSuccess(result);
            }
            return checker.onFailure(result);
        }

        /**
         * 执行HTTP请求，并获取返回的数据，带内容检查和重试机制
         *
         * @param checker 请参考{@link HttpResultChecker}的定义说明
         * @param retryCount 重试次数，注意第一次不算在其中，<=0代表不重试
         * @param retryDelay 两次重试之间等待的时间间隔，单位由unit参数控制
         */
        public <T> T fetch(HttpResultChecker<T> checker, int retryCount, long retryDelay, TimeUnit unit) {
            HttpResult result;
            int count = retryCount;
            do {
                result = fetch();
                if (checker.isExpected(this, result)) {
                    return checker.onSuccess(result);
                }
                ThreadUtils.sleep(retryDelay, unit);
            } while (count-- > 0);
            return checker.onFailure(result);
        }
    }

    /**
     * 完整的http/https返回结果
     */
    public static class HttpResult {

        /** 发出的请求的cURL命令模拟 */
        private String cmd;

        /** 返回的headers原始信息 */
        private Map<String, List<String>> headers;

        /** 返回的设置cookies信息 */
        private DbMap setCookies;

        /** 返回内容 */
        private byte[] content;

        /** 返回内容的字符串表示形式 */
        private String contentString;

        /** 返回内容以json方式解析的结果 */
        private DbMap json;

        /** HTTP返回状态码 */
        private int statusCode;

        /** 返回内容的类型 */
        private String contentType;

        /** 返回内容是否为文本类型 */
        private boolean isText = false;

        /** 表示返回内容是否已经解压过，解压只能解一次需标记出来 */
        private boolean isContentUnzipped = false;

        /** 返回内容的charset */
        private Charset charset;

        private HttpResult(String cmd) {
            this.cmd = cmd;
        }

        private HttpResult(String cmd, int statusCode) {
            this.cmd = cmd;
            this.statusCode = statusCode;
            this.headers = Collections.emptyMap();
            this.setCookies = new DbMap();
        }

        private HttpResult(String cmd, int statusCode, Map<String, List<String>> headers, Charset charset) throws Throwable {
            this.cmd = cmd;
            this.statusCode = statusCode;
            this.headers = headers;
            // 对返回的header进行处理，提取cookie赋值内容
            this.setCookies = new DbMap();

            for (Entry<String, List<String>> e : headers.entrySet()) {
                // 解析返回的cookie
                if ("Set-Cookie".equals(e.getKey())) {
                    for (String val : e.getValue()) {
                        int eqIndex = val.indexOf('=');
                        int semiIndex = val.indexOf(';');
                        if (semiIndex < 0) {
                            semiIndex = val.length(); // 没有找到分号，直接定位到最后
                        }
                        if (semiIndex > eqIndex && eqIndex > 0) { // 这样才是一个有效的key=value键值对
                            String k = val.substring(0, eqIndex);
                            String v = val.substring(eqIndex + 1, semiIndex);
                            setCookies.put(k, v);
                        }
                    }

                    // 初步判断返回的是文本还是其他二进制数据
                } else if ("Content-Type".equals(e.getKey())) {
                    for (String val : e.getValue()) {
                        if (val.contains("text") || val.contains("javascript") || val.contains("json") || val.contains("xml")) { // 表示是文本内容，需要解析文本
                            isText = true;
                            // 可能会出现返回的文本字符集和请求不同的情况，这里尝试从Content-Type获取字符集，如果能取到就替换
                            try {
                                String find = "charset=";
                                int i = val.indexOf(find);
                                if (i > 0) {
                                    int end = val.indexOf(';', i); // 以;作为结束
                                    if (end < 0) {
                                        end = val.length();
                                    }
                                    String cs = val.substring(i + find.length(), end);
                                    charset = Charset.forName(cs);
                                }
                            } catch (Throwable ignore) { // 这里的任何异常都忽略，防止阻断请求
                            }
                        }
                        this.contentType = val; // 记录一下返回类型
                    }
                }
            }
            this.charset = charset;
        }

        /**
         * 设置返回内容
         */
        private void setContent(byte[] content) {
            this.content = content;

            // 对返回内容判断并进行解压缩
            List<String> vals = headers.get("Content-Encoding");
            if (!isContentUnzipped && vals != null) {
                for (String val : vals) {
                    if (val.contains("gzip")) { // 如果发现内容是压缩过的需自动解压
                        try {
                            content = ZipTools.ungzip(content);
                            isContentUnzipped = true;
                        } catch (Exception ignore) {
                        }
                    }
                }
            }
            if (isText) {
                this.contentString = new String(content, charset);
            } else { // 无法直接判断文本类型的，尝试转一下字符串，看能否转回来
                String cs = new String(content, charset);
                if (Arrays.equals(cs.getBytes(charset), content)) {
                    this.contentString = cs;
                } else {
                    this.contentString = "[BINARY_DATA=" + content.length + "]" + contentType;
                }
            }
        }

        /**
         * 获取请求的cURL模拟命令
         */
        public String getCmd() {
            return cmd;
        }

        /**
         * 获取返回内容字节数
         */
        public int getContentLength() {
            return this.content == null ? 0 : this.content.length;
        }

        /**
         * 获取返回的header信息
         */
        public Map<String, List<String>> getHeaders() {
            return headers;
        }

        /**
         * 获取指定header的value，如果存在多个，只返回第一个
         */
        public String getHeader(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }

        /**
         * 获取指定header的value，如果存在多个，只返回第一个
         */
        public String getHeader(String name, String defaultValue) {
            String value = getHeader(name);
            return value == null ? defaultValue : value;
        }

        /**
         * 获取返回的内容类型
         */
        public String getContentType() {
            return contentType;
        }

        /**
         * 获取返回写入的cookie信息
         */

        public DbMap getCookies() {
            return setCookies;
        }

        /**
         * 获取返回写入的cookie信息
         */
        public String getCookie(String name) {
            return this.setCookies.getString(name);
        }

        /**
         * 获取返回写入的cookie信息
         */
        public String getCookie(String name, String defaultValue) {
            return this.setCookies.getString(name, defaultValue);
        }

        /**
         * 获取http/https请求的返回码
         */
        public int getStatusCode() {
            return statusCode;
        }

        /**
         * 获取返回内容的二进制内容
         */
        public byte[] getBytes() {
            return content;
        }

        /**
         * 获取返回内容的二进制内容，如果获取失败或为null返回defaultValue
         */
        public byte[] getBytes(byte[] defaultValue) {
            return null == content ? defaultValue : content;
        }

        /**
         * 将返回内容转化为字符串，使用发起http/https请求时指定的字符编码
         */
        public String getString() {
            return contentString;
        }

        /**
         * 将返回内容转化为字符串，使用发起http/https请求时指定的字符编码，如果获取失败或为null返回defaultValue
         */
        public String getString(String defaultValue) {
            return null == contentString ? defaultValue : contentString;
        }

        /**
         * 将返回的结果转化为JSON，如果转换失败返回的是一个空的{@link DbMap}（注意不是null）
         */
        public DbMap getJson() {
            if (null != json) {
                return json;
            }
            if (null == contentString) {
                json = new DbMap();
            } else {
                JSONObject jso = JsonObjectUtils.parseJson(contentString);
                json = null == jso ? new DbMap() : new DbMap(jso);
            }
            return json;
        }

        /**
         * 将返回的结果转化为指定的JSON对象，如果转换失败将返回null
         */
        public <T> T getJson(Class<T> clazz) {
            return null == contentString ? null : JsonObjectUtils.parseJson(contentString, clazz);
        }
    }

    /**
     * 上传文件实例
     */
    private static class UploadFile {

        /** multipart的fieldname */
        private String name;

        /** 文件对象 */
        private File file;

        /** 文件的字节数组 */
        private byte[] bytes;

        /** 文件名 */
        private String filename;

        /** 文件类型,自动识别会很消耗性能，所以需要手动填 */
        private String contentType = "application/octet-stream";

        public UploadFile(String name, File file, String filename, String contentType) {
            this.name = name;
            this.file = file;
            this.filename = filename;
            this.contentType = contentType;
        }

        public UploadFile(String name, byte[] bytes, String filename, String contentType) {
            this.name = name;
            this.bytes = bytes;
            this.filename = filename;
            this.contentType = contentType;
        }

        public UploadFile(String name, File file, String contentType) {
            this.name = name;
            this.file = file;
            this.contentType = contentType;
            this.filename = file.getName();
        }

        public UploadFile(String name, byte[] bytes, String contentType) {
            this.name = name;
            this.bytes = bytes;
            this.contentType = contentType;
            this.filename = name;
        }

        public UploadFile(String name, File file) {
            this.name = name;
            this.file = file;
            this.filename = file.getName();
        }

        public UploadFile(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
            this.filename = name;
        }

        public byte[] getBytes() throws Throwable {
            if (file != null) {
                FileInputStream in = new FileInputStream(file);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[1024];
                int n;
                while ((n = in.read(b)) != -1) {
                    out.write(b, 0, n);
                }
                in.close();
                return out.toByteArray();
            }

            return bytes;
        }

        public String getName() {
            return name;
        }

        public String getFilename() {
            return filename;
        }

        public String getContentType() {
            return contentType;
        }
    }

    /**
     * HTTP客户端在初始化的时候抛出的异常
     */
    public static class HttpClientException extends IllegalArgumentException {

        public HttpClientException(String s) {
            super(s);
        }
    }

    /**
     * 远程HTTP调用时如果失败，抛出特定的异常
     */
    public static class RemoteProxyException extends RuntimeException {

        public HttpResult hr;

        public RemoteProxyException(HttpResult hr) {
            super("请求接口失败\n请求：" + hr.getCmd() + "\n返回文本：" + hr.getString());
            this.hr = hr;
        }

        public RemoteProxyException(HttpResult hr, String msg) {
            super(msg + "\n\n请求接口失败\n请求：" + hr.getCmd() + "\n返回文本：" + hr.getString());
            this.hr = hr;
        }
    }
}
