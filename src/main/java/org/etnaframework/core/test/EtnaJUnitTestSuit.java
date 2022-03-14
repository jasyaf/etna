package org.etnaframework.core.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.spring.SpringContext;
import org.etnaframework.core.web.EtnaServer;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;
import org.junit.runners.model.RunnerScheduler;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

/**
 * 用于执行项目下所有的{@link Runner}测试用例（必须是要加了{@link Service}和@{@link RunWith}({@link EtnaJUnitRunner}.class)的才会被执行）
 *
 * @author BlackCat
 * @since 2015-03-11
 */
public class EtnaJUnitTestSuit extends ParentRunner<Runner> {

    protected Logger log = Log.getLogger();

    private List<Runner> runners;

    public EtnaJUnitTestSuit(final Class<?> clazz, RunnerBuilder builder) throws InitializationError {
        super(clazz);
        EtnaServer.echo("********** 开始执行测试组[" + clazz.getSimpleName() + "] **********");
        EtnaJUnitRunner.validate(clazz, null);
        List<Class<?>> list = new ArrayList<Class<?>>();
        for (Object bean : SpringContext.getBeansWithAnnotation(RunWith.class).values()) {
            RunWith rw = bean.getClass().getAnnotation(RunWith.class);
            if (EtnaJUnitRunner.class.equals(rw.value())) {
                list.add(bean.getClass());
            }
        }
        Collections.sort(list, new Comparator<Class<?>>() {

            @Override
            public int compare(Class<?> o1, Class<?> o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        runners = builder.runners(clazz, list);
        this.setScheduler(new RunnerScheduler() {

            @Override
            public void schedule(Runnable paramRunnable) {
                paramRunnable.run();
            }

            @Override
            public void finished() {
                EtnaServer.echo("********** 测试组执行结束[" + clazz.getSimpleName() + "] **********");
            }
        });
    }

    @Override
    protected List<Runner> getChildren() {
        return runners;
    }

    @Override
    protected Description describeChild(Runner child) {
        return child.getDescription();
    }

    @Override
    protected void runChild(Runner child, RunNotifier notifier) {
        child.run(notifier);
    }
}
