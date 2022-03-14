package test.cases;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.logging.logback.FixSizeMemAppender;
import org.etnaframework.core.logging.logback.FixSizeMemAppender.FixSizeLog;
import org.etnaframework.core.test.EtnaTestCase;
import org.etnaframework.core.test.annotation.TestDescr;
import org.etnaframework.core.test.annotation.TestLauncherClass;
import org.etnaframework.core.util.HttpClientUtils;
import org.etnaframework.core.util.HttpClientUtils.HttpClientBuilder;
import org.etnaframework.core.util.HttpClientUtils.HttpResult;
import org.etnaframework.core.util.HttpClientUtils.HttpResultChecker;
import org.etnaframework.core.util.HttpClientUtils.RemoteProxyException;
import org.etnaframework.core.util.JsonObjectUtils;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.web.EtnaServer;
import org.etnaframework.core.web.bean.RtnObject;
import org.etnaframework.core.web.constant.RtnCodes;
import org.junit.Test;
import org.springframework.stereotype.Service;
import test.TestEtnaLaunch;

/**
 * 测试HTTP客户端
 *
 * @author BlackCat
 * @since 2016-12-08
 */
@Service
@TestLauncherClass(TestEtnaLaunch.class)
public class TestHttpClientUtils extends EtnaTestCase {

    @Override
    protected void cleanup() throws Throwable {
    }

    @Test
    @TestDescr("普通的get/post测试")
    public void test001() throws Throwable {
        HttpResult hr = HttpClientUtils.post(EtnaServer.getLocalUrl() + "/echo")
                                       .urlParam("FengYe", "凤爷大宝剑")
                                       .contentParam("HuaYe", "华爷填坑")
                                       .cookie("user_id", 123)
                                       .cookie("sid",
                                           "FFFFXXXXXIIIII")
                                       .fetch();
        System.out.println(hr.getHeaders());
        System.out.println(hr.getCookies());
        System.out.println(hr.getString());
    }

    @Test
    @TestDescr("测试获取cookie")
    public void test002() throws Throwable {
        HttpResult hr = HttpClientUtils.get("http://blog.csdn.net/wangpeng047/article/details/38303865")
                                       .cookie("user_id", 123)
                                       .fetch();
        System.out.println(hr.getHeaders());
        System.out.println(hr.getCookies());
        // 把获取到的cookie应用于下一次请求
        HttpResult hr1 = HttpClientUtils.post(EtnaServer.getLocalUrl() + "/echo")
                                        .urlParam("TEST", "测试提交cookie")
                                        .cookie(hr.getCookies())
                                        .fetch();
        System.out.println(hr1.getHeaders());
        System.out.println(hr1.getCookies());
        System.out.println(hr1.getString());
    }

    @Test
    @TestDescr("测试post数据")
    public void test003() throws Throwable {
        HttpResult hr = HttpClientUtils.post(EtnaServer.getLocalUrl() + "/echo")
                                       .urlParam("FengYe", "凤爷大宝剑")
                                       .contentParam("data", "放在content包体的数据")
                                       .fetch();
        System.out.println(hr.getHeaders());
        System.out.println(hr.getCookies());
        System.out.println(hr.getString());
    }

    @Test
    @TestDescr("测试https")
    public void test004() throws Throwable {
        // 正常有效的证书
        HttpResult hr = HttpClientUtils.get("https://narya.huainanhai.com/echo")
                                       .cookie("user_id", 456)
                                       .fetch();
        System.out.println(hr.getHeaders());
        System.out.println(hr.getString());

        // 自己生成的证书（不做特殊处理忽略错误的话URLConnection会被异常卡断无法继续）
        hr = HttpClientUtils.post("https://127.0.0.1:8443/echo")
                            .cookie("user_id", "SABSDFSDF")
                            .content("甲赛甲棒")
                            .fetch();
        System.out.println(hr.getHeaders());
        System.out.println(hr.getString());
    }

    @Test
    @TestDescr("测试超时返回设置")
    public void test005() throws Throwable {
        HttpResult hr = HttpClientUtils.get(EtnaServer.getLocalUrl() + "/timeout")
                                       .timeout(2000)
                                       .urlParam("SY", "松爷头发烧了")
                                       .fetch();
        System.out.println(hr.getHeaders());
        System.out.println(hr.getString());
        System.out.println(hr.getString("失败默认返回这个"));
    }

    @Test
    @TestDescr("测试获取https资源")
    public void test006() throws Throwable {
        // 微信的这个头像用wget都经常出问题！原因是https证书认证拒绝
        HttpResult hr = HttpClientUtils.get("https://wx.qlogo.cn/mmopen/T9lAficvVCMng3vtKJxdfupR7IFBfZfPZKOT9ZjtkKL1icuHkM4qhzV4QMfiafhtvmlBiamwCMScVVdcXg2V08ubkXKtv2dzBhBo/0")
                                       .fetch();
        System.out.println("header:" + hr.getHeaders());
        System.out.println("cookie:" + hr.getCookies());
        System.out.println("statusCode:" + hr.getStatusCode());
        byte[] bytes = hr.getBytes();
        System.out.println("bytes length:" + bytes.length);
    }

    @Test
    @TestDescr("测试请求速度")
    public void test007() throws Throwable {

        // String url = EtnaServer.getLocalUrl() + "/echo";
        String url = "https://www.alipay.com";

        int testCount = 100;

        long start1 = System.currentTimeMillis();
        for (int i = 0; i < testCount; i++) {
            HttpClientUtils.get(url)
                           .keepAlive()
                           .fetch();
        }
        long end1 = System.currentTimeMillis();

        long start2 = System.currentTimeMillis();
        for (int i = 0; i < testCount; i++) {
            HttpClientUtils.httpGet(url);
        }
        long end2 = System.currentTimeMillis();

        System.err.println("span1:" + (end1 - start1));
        System.err.println("span2:" + (end2 - start2));
    }

    @Test
    @TestDescr("测试自定义日志输出")
    public void test008() throws Throwable {
        String url = "https://www.alipay.com";
        String url2 = "https://www.baidu.com";

        HttpClientUtils.get(url)
                       .fetch();

        String loggerName1 = HttpClientUtils.class.getName();
        String loggerName2 = "test008";

        HttpClientUtils.get(url2)
                       .log(Log.getLogger(loggerName2))
                       .fetch();

        FixSizeLog log1 = FixSizeMemAppender.getFixSizeLog(loggerName1);
        StringBuilder s = new StringBuilder();
        StringTools.append(s, "[", log1.getLoggerName(), "]\n", log1, "\n\n");

        FixSizeLog log2 = FixSizeMemAppender.getFixSizeLog(loggerName2);
        StringTools.append(s, "[", log2.getLoggerName(), "]\n", log2, "\n\n");

        System.err.println(s.toString());

        // 预期结果：
        // [org.etnaframework.core.util.HttpClientUtilsNeo] 显示请求https://www.alipay.com
        // [test008] 显示请求https://www.baidu.com
    }

    @Test
    @TestDescr("测试multipart")
    public void test009() throws Throwable {
        String url = EtnaServer.getLocalUrl() + "/multipart/submit";
        HttpClientUtils.post(url)
                       .multipart()
                       .contentParam("name", "YuanHaoliang")
                       .fetch();
    }

    @Test
    @TestDescr("测试multipart文件上传")
    public void test010() throws Throwable {
        String url = EtnaServer.getLocalUrl() + "/multipart/submit";
        HttpClientUtils.post(url)
                       .multipart()
                       .contentParam("name", "YuanHaoliang")
                       .file("file", "yuanhaoliang".getBytes())
                       .file("file2", "yuanhaoliang".getBytes())
                       .file("file3",
                           "yuanhaoliang".getBytes())
                       .file("file4", "yuanhaoliang".getBytes())
                       .fetch();
    }

    @Test
    @TestDescr("测试PUT请求")
    public void test011() throws Throwable {
        // TODO: 没找到相应服务测试
    }

    @Test
    @TestDescr("测试DELETE请求")
    public void test012() throws Throwable {
        // TODO: 没找到相应服务测试
    }

    @Test
    @TestDescr("测试HEAD请求")
    public void test013() throws Throwable {
        String url = "https://www.baidu.com";
        HttpResult result = HttpClientUtils.head(url)
                                           .fetch();
        System.err.println(JsonObjectUtils.createJsonPretty(result.getHeaders()));
        System.err.println("response body:" + result.getString());
    }

    @Test
    @TestDescr("测试ipv6请求")
    public void test014() throws Throwable {
        String url = "https://ipv6.google.com";
        HttpResult result = HttpClientUtils.get(url)
                                           .fetch();
        System.err.println(result.getString());

        // 如果在mac下出现错误：No route to host
        // 执行以下命令
        // sudo ifconfig awdl0 down
        // by http://stackoverflow.com/a/37841632/2765092
    }

    @Test
    @TestDescr("测试设置代理服务器")
    public void test015() throws Throwable {
        String url = "https://www.baidu.com";
        HttpResult result = HttpClientUtils.get(url)
                                           .proxyHttp("127.0.0.1", 8888)
                                           .fetch();
        System.err.println(result.getString());
    }

    @Test
    @TestDescr("测试访问404")
    public void test016() throws Throwable {
        String url = "http://busi.vip.kankan.com/aa";
        HttpResult result = HttpClientUtils.get(url)
                                           .fetch();
        System.err.println(JsonObjectUtils.createJsonPretty(result));
    }

    @Test
    @TestDescr("测试Post Json")
    public void test017() throws Throwable {
        String url = EtnaServer.getLocalUrl() + "/echo";

        Map<String, Object> map = JsonObjectUtils.buildMap("name", "yuanhaoliang", "adviser", "胡家超");

        HttpResult result = HttpClientUtils.post(url)
                                           .content(JsonObjectUtils.createJson(map))
                                           .header("Content-Type", "application/json")
                                           .fetch();
        System.err.println(JsonObjectUtils.createJsonPretty(result));
        System.err.println(result.getString());
    }

    @Test
    @TestDescr("测试指定HOST IP")
    public void test018() throws Throwable {
        String url = "http://www.baidu.com:8080/echo";

        Map<String, Object> map = JsonObjectUtils.buildMap("name", "yuanhaoliang", "adviser", "胡家超");

        HttpResult result = HttpClientUtils.post(url)
                                           .ip("127.0.0.1")
                                           .content(JsonObjectUtils.createJson(map))
                                           .header("Content-Type", "application/json")
                                           .fetch();
        System.err.println(JsonObjectUtils.createJsonPretty(result));
        System.err.println(result.getString());
    }

    @Test
    @TestDescr("测试遇302不自动跳转")
    public void test019() throws Throwable {
        HttpResult result = HttpClientUtils.get("http://bgp.he.net/AS36351")
                                           .noAutoRedirect()
                                           .fetch();
        System.err.println(JsonObjectUtils.createJsonPretty(result));
    }

    @Test
    @TestDescr("测试keepalive")
    public void test020() throws Throwable {
        String url = "https://www.baidu.com";
        HttpClientUtils.get(url)
                       .keepAlive()
                       .proxyHttp("127.0.0.1", 8888)
                       .fetch();
        HttpClientUtils.get(url)
                       .proxyHttp("127.0.0.1", 8888)
                       .fetch();

        // 在MACOS下，可以使用软件Charles代理，抓包查看请求
    }

    @Test
    @TestDescr("测试指定HOST IP遇302的情况")
    public void test021() throws Throwable {
        String url = "http://www.baidu.com:8080/multipart/redirect";

        Map<String, Object> map = JsonObjectUtils.buildMap("name", "yuanhaoliang", "adviser", "胡家超");

        HttpResult result = HttpClientUtils.post(url)
                                           .ip("127.0.0.1")
                                           .content(JsonObjectUtils.createJson(map))
                                           .header("Content-Type", "application/json")
                                           .fetch();
        System.err.println(JsonObjectUtils.createJsonPretty(result));

        // 结果是：连接超时，不支持302继续沿用指定IP
    }

    @Test
    @TestDescr("测试对返回内容进行检验的机制（检测成功）")
    public void test022() throws Throwable {
        String url = EtnaServer.getLocalUrl() + "/text/test1";
        String text = HttpClientUtils.get(url)
                                     .fetch(new HttpResultChecker<String>() {

                                         @Override
                                         public boolean isExpected(HttpClientBuilder req, HttpResult hr) {
                                             // 判断返回rtn是不是0
                                             return hr.getJson()
                                                      .getInt(RtnObject.RTN) == RtnCodes.OK;
                                         }

                                         @Override
                                         public String onSuccess(HttpResult hr) {
                                             // 判断返回成功，就提取data部分的内容
                                             return hr.getJson()
                                                      .getDbMap(RtnObject.DATA)
                                                      .getString("text");
                                         }

                                         @Override
                                         public String onFailure(HttpResult hr) {
                                             // 检测失败时的操作
                                             throw new RemoteProxyException(hr, "请求失败啦");
                                         }
                                     });
        System.out.println("返回的内容是：" + text);
    }

    @Test(expected = RemoteProxyException.class)
    @TestDescr("测试对返回内容进行检验的机制（检测失败）")
    public void test023() throws Throwable {
        String url = EtnaServer.getLocalUrl() + "/text/test2";
        String text = HttpClientUtils.get(url)
                                     .fetch(new HttpResultChecker<String>() {

                                         @Override
                                         public boolean isExpected(HttpClientBuilder req, HttpResult hr) {
                                             // 判断返回rtn是不是0
                                             return hr.getJson()
                                                      .getInt(RtnObject.RTN) == RtnCodes.OK;
                                         }

                                         @Override
                                         public String onSuccess(HttpResult hr) {
                                             // 判断返回成功，就提取data部分的内容
                                             return hr.getJson()
                                                      .getDbMap(RtnObject.DATA)
                                                      .getString("text");
                                         }

                                         @Override
                                         public String onFailure(HttpResult hr) {
                                             // 检测失败时的操作
                                             throw new RemoteProxyException(hr, "请求失败啦");
                                         }
                                     });
        System.out.println("返回的内容是：" + text);
    }

    @Test(expected = RemoteProxyException.class)
    @TestDescr("测试对返回内容进行检验的机制（检测失败），并执行重试")
    public void test024() throws Throwable {
        String url = EtnaServer.getLocalUrl() + "/text/test2";
        String text = HttpClientUtils.post(url)
                                     .contentParam("name", "雷猴")
                                     .content("")
                                     .fetch(new HttpResultChecker<String>() {

                                         @Override
                                         public boolean isExpected(HttpClientBuilder req, HttpResult hr) {
                                             // 判断返回rtn是不是0
                                             return hr.getJson()
                                                      .getInt(RtnObject.RTN) == RtnCodes.OK;
                                         }

                                         @Override
                                         public String onSuccess(HttpResult hr) {
                                             // 判断返回成功，就提取data部分的内容
                                             return hr.getJson()
                                                      .getDbMap(RtnObject.DATA)
                                                      .getString("text");
                                         }

                                         @Override
                                         public String onFailure(HttpResult hr) {
                                             // 检测失败时的操作
                                             throw new RemoteProxyException(hr, "请求失败啦");
                                         }
                                     }, 3, 1, TimeUnit.SECONDS);
        System.out.println("返回的内容是：" + text);
    }

    @Test
    @TestDescr("测试带密码的代理服务器")
    public void test025() throws Throwable {
        String result = HttpClientUtils.get("http://icanhazip.com/")
                                       .timeout(5, TimeUnit.SECONDS)
                                       // .proxySocks("127.0.0.1", 7999)
                                       .fetch()
                                       .getString();
        System.out.println(result);
    }
}
