package org.etnaframework.core.spring;

import javax.servlet.ServletContextEvent;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.spring.annotation.OnContextInited;
import org.etnaframework.core.web.EtnaServer;
import org.slf4j.Logger;
import org.springframework.web.context.ContextLoaderListener;

/**
 * Spring初始化器，初始化后执行后续操作
 *
 * @author BlackCat
 * @since 2013-3-8
 */
public class WebContextLoaderListener extends ContextLoaderListener {

    private Logger log = Log.getLogger();

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            // 初始化Spring，内部完成@Config和@OnBeanInited初始化
            long now = System.currentTimeMillis();
            super.contextInitialized(event);
            SpringContext.contextInited = true;
            EtnaServer.echo(log, "initSpringContextCompleted", "[" + (System.currentTimeMillis() - now) + "MS]");

            // 执行带@OnContextInited的方法
            now = System.currentTimeMillis();
            SpringContext.onContextInited();
            EtnaServer.echo(log, "exec" + OnContextInited.class.getSimpleName() + "Completed", "[" + (System.currentTimeMillis() - now) + "MS]");
        } catch (RuntimeException re) {
            throw re;
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }
}
