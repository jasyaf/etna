package test.cases;

import org.etnaframework.core.test.EtnaTestCase;
import org.etnaframework.core.test.annotation.TestDescr;
import org.etnaframework.core.test.annotation.TestLauncherClass;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.JsonObjectUtils;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.stereotype.Service;
import test.TestEtnaLaunch;

/**
 * 测试JSON转化工具
 *
 * @author BlackCat
 * @since 2016-04-20
 */
@Service
@TestLauncherClass(TestEtnaLaunch.class)
public class TestJsonObjectUtils extends EtnaTestCase {

    @Override
    protected void cleanup() throws Throwable {
    }

    @Test
    public void test001() {
        String json = "{\"name\":\"bobo\",\"time\":\"2016-04-20 12:46:44.333+0800\"}";
        TestObj obj = JsonObjectUtils.parseJson(json, TestObj.class);
        System.out.println(obj.time);
    }

    @Test
    @TestDescr("测试pretty的字符串输出")
    public void test002() {
        TestObj2 t = new TestObj2();
        String json = JsonObjectUtils.createJson(t);
        System.err.println(json);
        String jsonPretty = JsonObjectUtils.createJsonPretty(t); // 这个处理会抛异常
        System.err.println(jsonPretty);
    }

    @Test
    @TestDescr("测试Long型数值大于js范围转为字符串输出")
    public void test003() {

        TestObj3 t=new TestObj3();
        String json1 = JsonObjectUtils.createJson(t);
        String json2 = JsonObjectUtils.createJsonPretty(t);

        System.err.println(json1);
        System.err.println(json2);

        Assert.assertEquals(json1,"{\"max\":\"9223372036854775807\",\"min\":\"-9223372036854775808\",\"intmax\":2147483647,\"jsMax\":9007199254740991,\"jsMin\":-9007199254740991,\"jsMaxPlus1\":\"9007199254740992\",\"jsMinMinus1\":\"-9007199254740992\",\"orderId\":\"2017013117284393260\"}");
        Assert.assertEquals(json2,"{\n" + "\t\"max\":\"9223372036854775807\",\n" + "\t\"min\":\"-9223372036854775808\",\n" + "\t\"intmax\":2147483647,\n" + "\t\"jsMax\":9007199254740991,\n" + "\t\"jsMin\":-9007199254740991,\n" + "\t\"jsMaxPlus1\":\"9007199254740992\",\n" + "\t\"jsMinMinus1\":\"-9007199254740992\",\n" + "\t\"orderId\":\"2017013117284393260\"\n" + "}");
    }


    public static class TestObj {

        public String name;

        public Datetime time;
    }

    public static class TestObj2 {

        public String name="name";

        public long unix=111L;

        public String getUnix() {
            return "" + unix;
        }
    }
    public static class TestObj3 {

        public long max=Long.MAX_VALUE;
        public long min=Long.MIN_VALUE;
        public long intmax=Integer.MAX_VALUE;
        public long jsMax=0x1fffffffffffffL;
        public long jsMin=-0x1fffffffffffffL;
        public long jsMaxPlus1=0x1fffffffffffffL+1;
        public long jsMinMinus1=-0x1fffffffffffffL-1;
        public long orderId=2017013117284393260L;
    }
}
