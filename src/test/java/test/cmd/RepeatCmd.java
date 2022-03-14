package test.cmd;

import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.cmd.HttpCmd;
import org.springframework.stereotype.Service;

/**
 * 测试重复返回数据
 *
 * @author BlackCat
 * @since 2015-06-30
 */
@Service
public class RepeatCmd extends HttpCmd {

    @Override
    public void index(HttpEvent he) throws Throwable {
        he.writeText("测试1");
        he.writeText("测试2");
    }
}
