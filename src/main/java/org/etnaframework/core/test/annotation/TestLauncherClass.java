package org.etnaframework.core.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于在测试用例中标记服务器启动类
 *
 * @author BlackCat
 * @since 2015-01-06
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TestLauncherClass {

    /**
     * 服务器启动类class
     */
    Class<?> value();

    /**
     * main方法对应的参数
     */
    String[] args() default {};
}
