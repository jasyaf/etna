package test.cmd;

import org.etnaframework.core.util.KeyValueGetter.DbMap;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.cmd.HttpCmd;
import org.etnaframework.core.web.constant.RtnCodes;
import org.springframework.stereotype.Service;

/**
 * 测试返回普通的文本内容
 *
 * @author BlackCat
 * @since 2018-01-04
 */
@Service
public class TextCmd extends HttpCmd {

    public void test1(HttpEvent he) throws Throwable {
        DbMap data = new DbMap("text", "看到我那就对了");
        he.writeJson(RtnCodes.OK, data);
    }

    public void test2(HttpEvent he) throws Throwable {
        he.writeJson(RtnCodes.PARAM_EMPTY);
    }
}
