package org.etnaframework.plugin.stat.jdbc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.sql.DataSource;
import org.etnaframework.core.spring.SpringContext;
import org.etnaframework.core.spring.annotation.Config;
import org.etnaframework.core.util.DatetimeUtils;
import org.etnaframework.core.util.HumanReadableUtils;
import org.etnaframework.core.util.TimeSpanStat;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.annotation.CmdPath;
import org.etnaframework.core.web.cmd.HttpCmd;
import org.etnaframework.jdbc.JdbcTemplate;
import org.etnaframework.jdbc.Process;
import org.etnaframework.plugin.stat.jdbc.StatJdbcUtils.JdbcStat;
import org.springframework.stereotype.Controller;

/**
 * 用于统计SQL执行的情况
 *
 * @author dragonlai
 * @since 2015-6-9
 */
@Controller
@CmdPath("/stat/jdbc")
public class StatJdbcCmd extends HttpCmd {

    /** 是否开启sql统计 */
    private boolean statJdbcEnable = false;

    private Map<String, JdbcTemplate> beanMap;

    @Config("etna.statJdbcEnable")
    public void setStatJdbcEnable(boolean statJdbcEnable) {
        this.statJdbcEnable = statJdbcEnable;
        StatJdbcUtils.recordSwitch(statJdbcEnable);
    }

    /**
     * 初始化,并返回带有当前时间戳的 StringBuilder
     */
    protected StringBuilder initWithTime() {
        return new StringBuilder(DatetimeUtils.now().toString()).append('\n');
    }

    @Override
    public void index(HttpEvent he) throws Throwable {
        auth(this, he);
        boolean top = he.getBool("top", false);
        if (top) {
            StringBuilder sb = initWithTime();
            sb.append("\n");
            for (Entry<String, JdbcTemplate> e : getBeanMap().entrySet()) {
                sb.append(e.getKey()).append("\n");
                sb.append(Process.HEADER);
                for (Process p : e.getValue().showProcessList()) {
                    sb.append(p);
                }
            }
            he.writeText(sb);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\">");
        sb.append("<head><meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\"/>");
        sb.append("<style type=\"text/css\">* {font: normal medium simsun, monospace;} ");
        sb.append("a {color:blue;text-decoration: underline;} a:visited{color:blue;} a:hover{color:red;} </style></head>");

        sb.append("<body><table><tbody>\n");
        sb.append("<tr><td colspan=\"8\">").append(DatetimeUtils.now()).append("</td></tr>\n");

        if (statJdbcEnable) {
            // 保存一份副本，用于不受干扰地进行排序
            List<JdbcStat> statList = new ArrayList<JdbcStat>();

            for (Entry<StackTraceElement, JdbcStat> e : StatJdbcUtils.getStat().entrySet()) {
                statList.add(e.getValue());

                // 下面为了排序，将StackTraceElement设置到 jdbcstat里去
                e.getValue().setStackTraceElement(e.getKey());
            }
            boolean returnflag = false;
            // 计算hit总数
            long countAll = 0;
            for (JdbcStat jdbcStat : statList) {
                countAll += jdbcStat.getAllNum();
                if (jdbcStat.isNeedReset()) {
                    StatJdbcUtils.reset();
                    sb.append("<script>alert(\"").append("数值超出系统范围，为保证统计正确，统计自动归零").append("\");</script>");
                    returnflag = true;
                }
            }
            if (returnflag) {
                he.writeHtml(sb.toString());
                return;
            }

            TimeSpanStat.sort(statList, he); // 对statList进行排序
            long statSec = StatJdbcUtils.getStatTime();
            sb.append("<tr><td colspan=\"8\">&nbsp;</td></tr>\n");
            String sumFmt = "<tr><td nowrap>%-20s</td><td nowrap colspan=\"8\">%s</td></tr>\n";
            sb.append(String.format(sumFmt, "列表数据总数:", statList.size()));
            sb.append(String.format(sumFmt, "方法执行总次数:", countAll));
            sb.append(String.format(sumFmt, "统计时间:", HumanReadableUtils.timeSpan(statSec)));
            sb.append(String.format(sumFmt, "平均每秒执行次数:", String.format("%.3f", countAll * 1000f / statSec)));
            sb.append("<tr><td colspan=\"8\"><a href=\"?top=true\" target=\"_Blank\">查看MySQL线程列表</a></td></tr>\n");
            sb.append("<tr><td colspan=\"8\">&nbsp;</td></tr>\n");
            String format = "<tr><td>%-21s</td><td colspan=\"7\">%s</td></tr>\n";
            boolean printHead = true;
            for (JdbcStat js : statList) {
                if (printHead) {
                    sb.append(js.getTableHeader(false));
                    printHead = false;
                }
                sb.append(js.toHtmlString(String.format("%.2f%%", js.getAllNum() * 100f / countAll)));
                sb.append(String.format(format, "", js.getStackTraceElement()));
                sb.append(String.format(format, "", js.getLastSql()));
                sb.append("<tr><td colspan=\"8\">&nbsp;</td></tr>\n");
            }
        } else {
            sb.append("<tr><td>SQL统计功能未打开，请设置etna.statJdbcEnable=true</td></tr>\n");
        }
        sb.append("</tbody></table></body></html>");

        he.writeHtml(sb.toString());
    }

    public void reset(HttpEvent he) throws Throwable {
        StringBuilder sb = initWithTime();
        StatJdbcUtils.reset();
        sb.append("SQL统计归零成功\n");
        he.writeText(sb.toString());
    }

    public Map<String, JdbcTemplate> getBeanMap() {
        if (beanMap == null) {
            beanMap = SpringContext.getBeansOfType(JdbcTemplate.class);
        }
        return beanMap;
    }

    public void config(HttpEvent he) throws Throwable {
        StringBuilder sb = new StringBuilder();
        for (JdbcTemplate jt : getBeanMap().values()) {
            DataSource ds = jt.getDataSource();
            sb.append(ds).append("\n");
        }
        if (sb.length() == 0) {
            sb.append("未配置JDBC");
        }
        he.writeText(sb.toString());
    }
}
