package org.etnaframework.core.util;

import java.util.Arrays;
import org.etnaframework.core.util.KeyValueGetter.DbMap;

/**
 * 钉钉机器人发送辅助工具类
 *
 * 文档参考 https://open-doc.dingtalk.com/docs/doc.htm?spm=a219a.7629140.0.0.karFPe&treeId=257&articleId=105735&docType=1
 *
 * @author BlackCat
 * @since 2017-07-13
 */
public class DingTalkRobotUtils {

    /**
     * 发送纯文本消息，可通过手机号指定@谁
     */
    public static void sendText(String robotUrl, CharSequence content, String... at) {
        // {
        //     "msgtype":"text",
        //     "text":{
        //         "content": "我就是我, 是不一样的烟火"
        //     },
        //     "at":{
        //         "atMobiles": [
        //             "156xxxx8827",
        //             "189xxxx8325"
        //         ],
        //         "isAtAll": false
        //     }
        // }
        DbMap data = new DbMap() {

            {
                put("msgtype", "text");
                put("text", new DbMap() {

                    {
                        put("content", content);
                    }
                });
                put("at", new DbMap() {

                    {
                        put("atMobiles", Arrays.asList(at));
                        put("isAtAll", false);
                    }
                });
            }
        };
        HttpClientUtils.post(robotUrl)
                       .header("Content-Type", "application/json")
                       .content(JsonObjectUtils.createJson(data))
                       .fetch();
    }

    /**
     * 发送markdown消息，可通过手机号指定@谁
     *
     * 这里会原样发出content的内容，如果需要对机器人推送的消息做定制请使用本接口，一般情况用{@link #sendMarkdownGeneral(String, String, CharSequence, String...)}即可
     */
    public static void sendMarkdown(String robotUrl, String title, CharSequence content, String... at) {
        if (StringTools.isEmpty(robotUrl)) { // 地址为空时直接忽略
            return;
        }
        // {
        //     "msgtype":"markdown",
        //     "markdown":{
        //         "title":"杭州天气",
        //         "text": "#### 杭州天气 @156xxxx8827\n" +
        //                 "> 9度，西北风1级，空气良89，相对温度73%\n\n" +
        //                 "> ![screenshot](http://image.jpg)\n"  +
        //                 "> ###### 10点20分发布 [天气](http://www.thinkpage.cn/) \n"
        //     },
        //     "at":{
        //         "atMobiles": [
        //             "156xxxx8827",
        //             "189xxxx8325"
        //         ],
        //         "isAtAll":false
        //     }
        // }
        DbMap data = new DbMap() {

            {
                put("msgtype", "markdown");
                put("markdown", new DbMap() {

                    {
                        put("title", title);
                        put("text", content);
                    }
                });
                put("at", new DbMap() {

                    {
                        put("atMobiles", Arrays.asList(at));
                        put("isAtAll", false);
                    }
                });
            }
        };
        HttpClientUtils.post(robotUrl)
                       .header("Content-Type", "application/json")
                       .content(JsonObjectUtils.createJson(data))
                       .fetch();
    }

    /**
     * 发送markdown消息，可通过手机号指定@谁，通用格式
     *
     * 如果需要对content内容定制，请使用{@link #sendMarkdown(String, String, CharSequence, String...)}
     */
    public static void sendMarkdownGeneral(String robotUrl, String title, CharSequence content, String... at) {
        // 对内容进行markdown标记处理，在钉钉机器人里面显示清晰一些
        String nt = title + "@" + SystemInfo.HOSTNAME;
        StringBuilder c = new StringBuilder("**").append(StringTools.escapeMarkdown(nt))
                                                 .append("**  \n  \n");
        c.append("***\n");
        // 特殊处理：把单引号中的URL单独剥离出来，以便在钉钉消息中可直接点击
        boolean meetApos = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '\'') {
                meetApos = !meetApos;
            }
            if (meetApos) {
                sb.append(ch);
            } else {
                if (sb.length() == 0) {
                    c.append(StringTools.escapeMarkdown("" + ch));
                } else {
                    sb.append(ch);
                    String in = sb.toString();
                    if (in.startsWith("\'http")) {
                        c.append(sb);
                    } else {
                        c.append(StringTools.escapeMarkdown(in));
                    }
                    sb = new StringBuilder();
                }
            }
        }
        sendMarkdown(robotUrl, nt, c.toString(), at);
    }
}
