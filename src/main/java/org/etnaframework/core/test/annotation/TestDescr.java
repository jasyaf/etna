package org.etnaframework.core.test.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于在测试用例中测试项的描述
 *
 * @author BlackCat
 * @since 2015-01-06
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TestDescr {

    /**
     * 一句话描述测试项
     */
    String value();
}
