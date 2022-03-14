package org.etnaframework.plugin.stat;

import org.etnaframework.core.util.CacheManager;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.annotation.CmdPath;
import org.etnaframework.core.web.cmd.HttpCmd;
import org.springframework.stereotype.Controller;
/**
 * Created by Daniel on 2015/12/2.
 */
@Controller
@CmdPath("/stat/cache")
public class StatCacheCmd extends HttpCmd {

    @Override
    public void index(HttpEvent he) throws Throwable {
        auth(this, he);
        he.writeText(CacheManager.printCacheInfo());
    }
}
