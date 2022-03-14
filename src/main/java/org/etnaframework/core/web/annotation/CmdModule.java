package org.etnaframework.core.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口配置信息，包括在菜单显示的名称，所属功能模块以及需要的操作权限
 *
 * @author BlackCat
 * @since 2013-5-8
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CmdModule {

    public static enum ApiOperation {
        /** 增 */
        CREATE,
        /** 查 */
        RETRIEVE,
        /** 改 */
        UPDATE,
        /** 删 */
        DELETE
    }

    /**
     * 功能模块ID
     */
    String id();

    /**
     * 使用该功能需要的操作权限 增/删/改/查 （只要是有功能权限，默认是允许查的）
     */
    ApiOperation require() default ApiOperation.RETRIEVE;
}
