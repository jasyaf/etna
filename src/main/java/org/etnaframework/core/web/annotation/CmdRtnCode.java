package org.etnaframework.core.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 返回码对应的错误提示信息
 *
 * @author BlackCat
 * @since 2015-04-01
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CmdRtnCode {

    /**
     * 提示信息，第0个元素为返回给前端的信息，后续的为这个返回码的说明文字，用于生成文档
     */
    String[] value();
}
