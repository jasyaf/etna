package org.etnaframework.core.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口返回格式
 *
 * @author BlackCat
 * @since 2015-01-07
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CmdContentType {

    public static enum CmdContentTypes {
        HTML,
        JSON,
        PLAIN,
        XML,
        LUA
    }

    CmdContentTypes[] value() default CmdContentTypes.JSON;
}
