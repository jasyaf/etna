package org.etnaframework.core.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于加到枚举字段，生成接口文档用
 *
 * @author BlackCat
 * @since 2013-12-1
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CmdEnum {

    /**
     * 字段值描述
     */
    String value();
}
