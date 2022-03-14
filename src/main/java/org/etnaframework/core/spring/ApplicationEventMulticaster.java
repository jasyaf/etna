package org.etnaframework.core.spring;

import org.etnaframework.core.util.ThreadUtils;
import org.etnaframework.core.web.DispatchFilter;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.stereotype.Service;

/**
 * 事件发布器 Created by yuanhaoliang on 2015-07-16.
 */
@Service
public class ApplicationEventMulticaster extends SimpleApplicationEventMulticaster {

    public ApplicationEventMulticaster() {
        // 直接使用框架的共用线程池，所有事件都走异步执行，如果要同步执行的事件就应该直接耦合不使用事件了。
        setTaskExecutor(ThreadUtils.getDefault());
        setErrorHandler(t -> {
            // 发邮件通知错误
            DispatchFilter.sendMail("ApplicationEventHandleError", t);
            //TODO：更恰当的通知方式。
        });
    }
}
