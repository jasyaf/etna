package org.etnaframework.core.test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.spring.SpringContext;
import org.etnaframework.core.test.annotation.TestDescr;
import org.etnaframework.core.test.annotation.TestLauncherClass;
import org.etnaframework.core.util.ReflectionTools;
import org.etnaframework.core.util.SystemInfo;
import org.etnaframework.core.web.EtnaServer;
import org.junit.Test;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerScheduler;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils.MethodFilter;

/**
 * etna接口测试基类，可在junit测试用例中启动服务器并引入spring
 *
 * @author BlackCat
 * @since 2015-01-06
 */
public class EtnaJUnitRunner extends BlockJUnit4ClassRunner {

    protected Logger log = Log.getLogger();

    protected Class<?> testClazz;

    public EtnaJUnitRunner(Class<?> clazz) throws InitializationError {
        super(clazz);
        testClazz = clazz;
        EtnaServer.echo("********** 开始测试[" + testClazz.getSimpleName() + "] **********");
        validate(clazz, this);
    }

    static void validate(final Class<?> clazz, EtnaJUnitRunner test) {
        try {
            TestLauncherClass launch = clazz.getAnnotation(TestLauncherClass.class);
            if (null == launch) {
                EtnaServer.echo();
                EtnaServer.echo("找不到启动类，请在" + clazz.getName() + "上加@" + TestLauncherClass.class.getSimpleName() + "注解指定服务器启动类");
                System.exit(1);
            } else {
                SystemInfo.IN_TEST = true; // 标记当前是在进行单元测试
                Method m = launch.value().getMethod("main", String[].class);
                m.invoke(null, (Object) launch.args());
            }
            Object bean = SpringContext.getBean(clazz);
            if (EtnaTestCase.class.isAssignableFrom(clazz) && null != test) {
                final EtnaTestCase etc = (EtnaTestCase) bean;
                EtnaServer.echo("********** 执行测试前清理操作 **********");
                etc.cleanup();
                test.setScheduler(new RunnerScheduler() {

                    @Override
                    public void schedule(Runnable paramRunnable) {
                        paramRunnable.run();
                    }

                    @Override
                    public void finished() {
                        EtnaServer.echo("********** 执行测试后清理操作 **********");
                        try {
                            etc.cleanup();
                        } catch (Throwable e) {
                        }
                        EtnaServer.echo("********** 结束测试[" + clazz.getSimpleName() + "] **********");
                    }
                });
            }
        } catch (BeansException e) {
            e.printStackTrace();
            EtnaServer.echo();
            EtnaServer.echo("无法从" + SpringContext.class.getSimpleName() + "中获取到" + clazz.getName() + "的实例，请确定在" + clazz.getSimpleName() + "上加了@" + Service.class.getSimpleName() + "注解并包含在了扫描路径下");
            System.exit(1);
        } catch (Throwable e) {
            e.printStackTrace();
            EtnaServer.echo();
            EtnaServer.echo("服务器启动失败，无法执行测试");
            System.exit(1);
        }
    }

    @Override
    protected List<FrameworkMethod> getChildren() {
        // 执行方法按源码的顺序，必须要求加@Test注解
        Collection<Method> list = ReflectionTools.getDeclaredMethodsInSourceCodeOrder(testClazz, new MethodFilter() {

            @Override
            public boolean matches(Method method) {
                return method.getParameterTypes().length == 0 && null != method.getAnnotation(Test.class);
            }
        });
        List<FrameworkMethod> ret = new ArrayList<FrameworkMethod>(list.size());
        for (Method m : list) {
            ret.add(new FrameworkMethod(m));
        }
        return ret;
    }

    @Override
    protected Object createTest() throws Exception {
        Object target = SpringContext.getBean(testClazz); // 这里不可能为null的，如果为null在加载的时候就报出来了
        return target;
    }

    @Override
    protected Statement methodInvoker(FrameworkMethod method, Object test) {
        String descr = "测试";
        TestDescr descrClazz = method.getAnnotation(TestDescr.class);
        if (descrClazz != null) {
            descr = descrClazz.value();
        }
        EtnaServer.echo("********** 运行测试方法[" + method.getName() + "(" + descr + ")] **********");
        return super.methodInvoker(method, test);
    }
}
