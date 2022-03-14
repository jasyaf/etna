package org.etnaframework.core.web.cmd;

import org.etnaframework.core.util.NetUtils;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.util.SystemInfo;
import org.etnaframework.core.web.EtnaServer;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.annotation.CmdPath;
import org.etnaframework.core.web.constant.RtnCodes;
import org.springframework.stereotype.Controller;

/**
 * 关闭服务器接口，用于一般的停止服务，或用于替代式重启
 *
 * @author BlackCat
 * @since 2014-11-3
 */
@Controller
public class ShutdownCmd extends HttpCmd {

    /** 关闭服务器接口的地址，尽量避免命名冲突，在名称中加入了下划线 */
    public static final String CMD_PATH = "/__shutdown__";

    private static volatile boolean closing = false;

    @Override
    @CmdPath(CMD_PATH)
    public synchronized void index(HttpEvent he) throws Throwable {
        if (closing) {
            he.writeText("CLOSING");
            return;
        }
        String token = he.getString("token", null);
        // 必须是本机请求才接受，并且必须是关闭同一个启动类下的服务
        if (StringTools.isNotEmpty(token) && NetUtils.getLocalIPWith127001().contains(he.getRemoteIP()) && SystemInfo.COMMAND.equals(token)) {
            closing = true;
            EtnaServer.echo(log, "recv" + ShutdownCmd.class.getSimpleName() + " -> KILL PROGRESS", "PID[" + SystemInfo.PID + "]");
            // 这里是在服务器启动时，在etna用Runtime.getRuntime().addShutdownHook加jvm关闭事件实现的
            // 如果是用restart方式重启，是直接kill进程，将不会执行关闭事件
            System.exit(0);
        }
        throw RtnCodes.getRtnException(RtnCodes.OPERATION_FORBIDDEN);
    }
}
