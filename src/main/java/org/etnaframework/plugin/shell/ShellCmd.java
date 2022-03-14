package org.etnaframework.plugin.shell;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import org.etnaframework.core.spring.annotation.Config;
import org.etnaframework.core.spring.annotation.OnContextInited;
import org.etnaframework.core.util.CommandService;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.annotation.Cmd;
import org.etnaframework.core.web.cmd.HttpCmd;
import org.etnaframework.core.web.constant.CmdCategory;
import org.springframework.stereotype.Controller;

/**
 * 把常用的调试使用的命令输出到页面上,注意这些命令的权限
 *
 * @author BlackCat
 * @since 2015-03-27
 */
@Controller
public class ShellCmd extends HttpCmd {

    @Config("etna.plugin.shell.cmds")
    private String[] shells = {
        "uname  -a ",
        "systeminfo "
    };// demo配置

    private String[] shellsReal = {};// 实际的命令

    /** 命令行输出默认的charset,为空则根据系统类型来判断。 */
    @Config("etna.plugin.shell.defaultCharset")
    private String defaultCharset = "";

    @OnContextInited
    protected void init() {
        String name = ManagementFactory.getRuntimeMXBean().getName(); // 形如:3948@twin0942
        String selfPid = StringTools.splitAndTrim(name, "@").get(0);
        shellsReal = new String[shells.length];
        for (int i = 0; i < shells.length; i++) {
            shellsReal[i] = shells[i].replaceAll("#SELF_PID#", selfPid);
        }
        if (shellsReal != null && shellsReal.length > 0) {
            log.warn("INIT SHELLS:\t\t{}", Arrays.toString(shellsReal));
        }
    }

    @Override
    @Cmd(desc = "预设定命令行", category = CmdCategory.SYSTEM)
    public void index(HttpEvent he) throws Throwable {
        auth(this, he);
        int index = he.getInt("exeIndex", -1);
        if (index == -1) { // 显示出页面
            StringBuilder tmp = new StringBuilder();
            tmp.append("<html><body><table><tbody>\n");
            for (int i = 0; i < shellsReal.length; i++) {
                String m = shellsReal[i];
                tmp.append(String.format("<tr><td><a target=\"_blank\" href=\"?exeIndex=%s\">%s</a></td></tr>\n", i, m));
            }
            tmp.append("</tbody></table></body></html>");
            he.writeHtml(tmp);
        } else if (index >= shellsReal.length || index < 0) { // 范围不对要给提示
            he.writeText("找不到对应的命令");
        } else {
            CommandService cs = new CommandService(shellsReal[index]);
            if (StringTools.isEmpty(defaultCharset)) {
                // 如果没设定默认charset，就自动根据操作系统来判断
                cs.execute();
            } else {
                cs.execute(defaultCharset);
            }
            he.writeText(cs.getProcessingDetail());
        }
    }
}
