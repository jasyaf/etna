package org.etnaframework.plugin.stat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.etnaframework.core.logging.logback.FixSizeMemAppender;
import org.etnaframework.core.logging.logback.FixSizeMemAppender.FixSizeLog;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.util.SystemInfo;
import org.etnaframework.core.util.ThreadUtils;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.annotation.Cmd;
import org.etnaframework.core.web.cmd.HttpCmd;
import org.etnaframework.core.web.constant.CmdCategory;
import org.etnaframework.core.web.mapper.CmdMappers;
import org.etnaframework.core.web.mapper.CmdMeta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

/**
 * 内部服务接口
 *
 * @author BlackCat
 * @since 2013-5-3
 */
@Controller
public class StatCmd extends HttpCmd {

    @Autowired
    private CmdMappers cmdMappers;

    @Override
    @Cmd(desc = "显示系统统计信息", category = CmdCategory.SYSTEM)
    public void index(HttpEvent he) throws Throwable {
        auth(this, he);
        he.writeText(SystemInfo.getSytemInfo());
    }

    @Cmd(desc = "显示线程状态信息", category = CmdCategory.SYSTEM)
    public void threads(HttpEvent he) throws Throwable {
        auth(this, he);
        int max_frames = he.getInt("maxFrames", 16);
        boolean onlyRunnable = he.getBool("runnable", false);
        String name = he.getString("name", "");
        he.writeText(ThreadUtils.getThreadsDetailInfo(name, onlyRunnable, max_frames));
    }

    @Cmd(desc = "显示在线日志", category = CmdCategory.SYSTEM)
    public void log(HttpEvent he) throws Throwable {
        auth(this, he);
        StringBuilder sb = new StringBuilder();
        String name = he.getString("name", "");
        while (true) {
            try {
                StringBuilder s = new StringBuilder();
                for (FixSizeLog fsl : FixSizeMemAppender.getAllLog()) {
                    if (name.isEmpty()) {
                        StringTools.append(s, "[", fsl.getLoggerName(), "]\n", fsl, "\n\n");
                    } else {
                        if (name.equals(fsl.getLoggerName())) {
                            StringTools.append(s, "[", fsl.getLoggerName(), "]\n", fsl, "\n\n");
                        }
                    }
                }
                sb.append(s);
                break;
            } catch (ConcurrentModificationException e) {
            }
        }
        he.setAccessLogContent("[LOGS Online]");
        he.writeText(sb);
    }

    @Cmd(desc = "显示服务器接口列表", category = CmdCategory.SYSTEM)
    public void cmd(HttpEvent he) throws Throwable {
        boolean timesOrder = he.getBool("timesOrder", false);
        boolean avgOrder = he.getBool("avgOrder", false);

        Map<CmdMeta, List<String>> cmd_urls_map = cmdMappers.getReverseCmdAllSortedMap();

        List<Entry<CmdMeta, List<String>>> entry_list = new ArrayList<Entry<CmdMeta, List<String>>>(cmd_urls_map.entrySet());
        // 按照各个字段排序
        // 按请求比例排序
        if (timesOrder) {
            Collections.sort(entry_list, new Comparator<Entry<CmdMeta, List<String>>>() {

                public int compare(Entry<CmdMeta, List<String>> o1, Entry<CmdMeta, List<String>> o2) {
                    return (int) (o2.getKey().getStat().getAllNum() - o1.getKey().getStat().getAllNum());
                }
            });
        }
        // 按平均请求时长排序
        if (avgOrder) {
            Collections.sort(entry_list, new Comparator<Entry<CmdMeta, List<String>>>() {

                public int compare(Entry<CmdMeta, List<String>> o1, Entry<CmdMeta, List<String>> o2) {
                    int r2 = (int) (o2.getKey().getStat().getAllNum() == 0 ? 0 : o2.getKey().getStat().getAllSpan() / o2.getKey().getStat().getAllNum());
                    int r1 = (int) (o1.getKey().getStat().getAllNum() == 0 ? 0 : o1.getKey().getStat().getAllSpan() / o1.getKey().getStat().getAllNum());
                    return r2 - r1;
                }
            });
        }
        he.set("timesOrder", timesOrder ? "▼" : "");
        he.set("avgOrder", avgOrder ? "▼" : "");
        he.set("url", he.getRequestURL());
        he.set("title", SystemInfo.COMMAND_SHORT + " CMD");
        he.set("cmds", entry_list);
        he.setAccessLogContent("[CMD List]");
        he.renderHtml("/etna/stat/cmd.html");
    }
}
