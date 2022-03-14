package test;

import org.etnaframework.core.util.JsonObjectUtils;
import org.etnaframework.core.util.KeyValueGetter.DbMap;
import org.etnaframework.core.util.StringTools;
import com.alibaba.fastjson.JSONObject;

/**
 * 测试JSON对特殊字符的支持情况
 *
 * @author BlackCat
 * @since 2017-01-22
 */
public class TestJsonEmoji {

    public static void main(String[] args) {
        String name = StringTools.decodeURIComponent("%F0%9F%99%82%F0%9F%A4%91%F0%9F%98%B0%F0%9F%98%88%F0%9F%99%8F%E2%9C%8D%EF%B8%8F%F0%9F%91%BC%F0%9F%91%AC%F0%9F%92%91");
        String json = JsonObjectUtils.createJson(new DbMap("name", name));
        JSONObject data = JsonObjectUtils.parseJson(json);
        System.out.println(data);
        System.out.println(name.equals(data.getString("name")));
    }
}
