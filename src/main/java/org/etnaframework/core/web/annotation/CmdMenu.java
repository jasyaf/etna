package org.etnaframework.core.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口配置菜单信息，包括在菜单显示的名称，
 *
 * @author BlackCat
 * @since 2013-5-8
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CmdMenu {

    /**
     * 接口显示名称
     */
    String title();

    /**
     * 是否要显示到菜单
     */
    boolean visible() default true;
}
