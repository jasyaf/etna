package org.etnaframework.core.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口功能描述信息，用于提供给其他客户调用时生成文档之用，如果接口没有这个注解将不会有文档生成
 *
 * @author BlackCat
 * @since 2013-12-1
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Cmd {

    /**
     * 接口功能描述
     */
    String[] desc();

    /**
     * 接口分类，用于生成文档时划分权限
     */
    String category();

    /**
     * 接口域名前缀，用于指定接口使用的域名和端口等信息，如果不填的话默认就按服务自己的URL来构造
     */
    String domain() default "";
}
