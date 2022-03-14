package test.cmd;

import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.HttpEvent.TimeoutHandler;
import org.etnaframework.core.web.cmd.HttpCmd;
import org.springframework.stereotype.Service;

/**
 * 测试HTTP返回超时
 *
 * @author BlackCat
 * @since 2016-01-13
 */
@Service
public class TimeoutCmd extends HttpCmd {

    @Override
    public void index(HttpEvent he) throws Throwable {
        he.holdOn(Datetime.MILLIS_PER_SECOND * 30, new TimeoutHandler() {

            @Override
            public void onTimeout() throws Throwable {
                he.writeText("你好，超时啦！");
            }
        });
    }
}
