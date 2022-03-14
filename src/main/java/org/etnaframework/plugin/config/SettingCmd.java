package org.etnaframework.plugin.config;

import org.etnaframework.core.spring.ConfigAnnotationBeanPostProcessor;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.annotation.Cmd;
import org.etnaframework.core.web.cmd.HttpCmd;
import org.etnaframework.core.web.constant.CmdCategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

/**
 * 内部服务接口
 *
 * @author BlackCats
 * @since 2013-5-3
 */
@Controller
public class SettingCmd extends HttpCmd {

    @Autowired
    private ConfigAnnotationBeanPostProcessor configProcessor;

    @Cmd(desc = "显示@Config配置项", category = CmdCategory.SYSTEM)
    public void config(HttpEvent he) throws Throwable {
        boolean reload = he.getBool("reload", false);
        if (reload) {
            configProcessor.reloadAllConfig();
        }
        he.set("metas", configProcessor.getAllConfig());
        he.set("title", "CONFIG");
        he.renderHtml("/etna/setting/config.html");
    }
}
