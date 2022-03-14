package org.etnaframework.core.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.annotation.CmdParam.ParamScope;

/**
 * API使用的请求参数集合
 *
 * @author BlackCat
 * @since 2013-5-5
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CmdParams {

    /** 使用WEB表单方式提交的数据的接口，定义在cmd类的代码中，需要写代码从{@link HttpEvent}获取 */
    CmdParam[] value() default {};

    /** 自动从请求中提取参数并封装javabean，同时进行基本的数据校验 */
    Class<?> reqClass() default Object.class;

    /**
     * 参数获取来源，默认是GET/POST均可的
     */
    ParamScope[] scope() default ParamScope.GET_OR_POST;
}
