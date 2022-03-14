package org.etnaframework.plugin.echo;

import org.etnaframework.core.spring.annotation.Config;
import org.etnaframework.core.util.DatetimeUtils;
import org.etnaframework.core.util.KeyValueGetter.DbMap;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.annotation.Cmd;
import org.etnaframework.core.web.annotation.CmdAuthor;
import org.etnaframework.core.web.annotation.CmdContentType;
import org.etnaframework.core.web.annotation.CmdContentType.CmdContentTypes;
import org.etnaframework.core.web.annotation.CmdParam;
import org.etnaframework.core.web.annotation.CmdParams;
import org.etnaframework.core.web.annotation.CmdReturn;
import org.etnaframework.core.web.annotation.CmdSession;
import org.etnaframework.core.web.annotation.CmdSession.CmdSessionType;
import org.etnaframework.core.web.cmd.HttpCmd;
import org.etnaframework.core.web.constant.CmdCategory;
import org.etnaframework.core.web.constant.RtnCodes;
import org.springframework.stereotype.Controller;

/**
 * 服务器http测试接口，一些通用的杂项也将放置在此
 *
 * @author YuanHaoliang
 * @since 2014-08-02
 */
@Controller
public class EchoCmd extends HttpCmd {

    @Config("etna.echoNowKey")
    private String echoNowKey = "ECHONOWHASHKEY";

    @Cmd(desc = "输出http请求的详细信息", category = CmdCategory.SYSTEM)
    @Override
    public void index(HttpEvent he) throws Throwable {
        StringBuilder sb = new StringBuilder();
        sb.append("================ etna ================\n");
        StringBuilder detail = he.getDetailInfo();
        sb.append(detail);
        he.setAccessLogContent(detail);
        he.writeText(sb);
    }

    @Cmd(desc = "获取jquery.js", category = CmdCategory.SYSTEM)
    public void jquery(HttpEvent he) throws Throwable {
        he.renderText("/etna/jquery-1.11.0.min.js");
    }

    @Cmd(desc = "通用服务器时间接口", category = CmdCategory.SYSTEM)
    @CmdSession(CmdSessionType.DISPENSABLE)
    @CmdAuthor("yuanhaoliang")
    @CmdParams({
        @CmdParam(field = "p", name = "时间格式", desc = "时间格式，例如utc,millis,yyyyMMdd", required = false, defaultValue = "default"),
        @CmdParam(field = "r", name = "时间戳", desc = "如果有指定时间戳，则返回时间和由时间、时间戳和echoNowKey生成的md5值。时间和md5值之间用\"\\n\"分隔。", required = false, defaultValue = "空"),
        @CmdParam(field = "f", name = "返回格式", desc = "f表示返回格式，为空就是直接返回时间，json就返回json，jsonp返回jsonp需要加callback参数", required = false, defaultValue = "空"),
    })
    @CmdReturn({
        "{",
        "  'rtn':0,                                         // 0:正常返回",
        "  'data':{  ",
        "      't':'2014-08-02 23:51:00',                   // 当前时间",
        "      's':'e5865beded33852bc142da4b5c4643fd',      // 签名",
        "  }  ",
        "}  "
    })
    @CmdContentType({
        CmdContentTypes.JSON,
        CmdContentTypes.PLAIN
    })
    public void now(HttpEvent he) throws Throwable {
        String pattern = he.getString("p", "default");
        String random = he.getString("r", "");
        String format = he.getString("f", "");

        StringBuilder result = new StringBuilder();
        if (pattern.equalsIgnoreCase("utc")) {
            result.append(System.currentTimeMillis() / 1000);
        } else if (pattern.equalsIgnoreCase("millis")) {
            result.append(System.currentTimeMillis());
        } else if (pattern.equalsIgnoreCase("default")) {
            result.append(DatetimeUtils.now().toString());
        } else if (StringTools.isNotEmpty(pattern)) {
            try {
                result.append(DatetimeUtils.format(DatetimeUtils.now(), pattern));
            } catch (IllegalArgumentException ex) {
                // 如果传入的格式无法识别，就返回默认的格式
                result.append(DatetimeUtils.now().toString());
            }
        }

        String verifySecret = "";
        if (StringTools.isNotEmpty(random)) {
            String verifyString = result.toString() + random + echoNowKey;
            verifySecret = StringTools.md5AsHex(verifyString);
        }

        // 指定返回的格式
        if (format.equalsIgnoreCase("json") || format.equalsIgnoreCase("jsonp")) {
            if (StringTools.isEmpty(random)) {
                he.writeJson(RtnCodes.OK, new DbMap("t", result, "s", verifySecret));
            } else {
                he.writeJson(RtnCodes.OK, new DbMap("t", result, "s", verifySecret));
            }
        } else {
            if (StringTools.isEmpty(random)) {
                he.writeText(result);
            } else {
                he.writeText(result.append("\n").append(verifySecret));
            }
        }
    }
}
