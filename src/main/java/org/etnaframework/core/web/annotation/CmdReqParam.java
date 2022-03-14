package org.etnaframework.core.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.etnaframework.core.web.annotation.CmdParam.ParamScope;
import org.etnaframework.core.web.annotation.CmdParam.ParamType;

/**
 * API使用的请求参数，用于定义在javabean的字段上进行自动包装处理
 *
 * @author BlackCat
 * @since 2014-12-23
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CmdReqParam {

    /**
     * 参数名称默认跟字段名一致，这里可以设置别名
     */
    String alias() default "";

    /**
     * 参数名称对应的说明文字，如username就对应“用户名”，如果没有默认就跟alias一样，该值用于生成参数校验失败时的提示信息
     */
    String name();

    /**
     * 参数获取来源，默认是GET/POST均可的
     */
    ParamScope[] scope() default ParamScope.GET_OR_POST;

    /**
     * 取值范例，用于将文档给使用者调试接口时传入，此项必须传入，否则将无法在启动时生成文档
     */
    String sample();

    /**
     * 参数类型
     */
    ParamType type() default ParamType.STRING;

    /**
     * 参数长度限制，最小长度，默认是0表示不限制最小长度。长度包含该数值，如设置为2，则长度>=2就可以了
     */
    int minLength() default 0;

    /**
     * 参数长度限制，最大长度，默认不限制最大长度。长度包含该数值，如设置为8，则长度<=8就可以了
     */
    int maxLength() default Integer.MAX_VALUE;

    /**
     * 参数值的范围指定，如果指定的是enum类就会自动去获取它的所有值，如果是static field这样的也会自动去获取值
     */
    Class<?> typeEnum() default Object.class;

    /**
     * 该参数是否是必须要传的，默认必须传
     */
    boolean required() default true;

    /**
     * 如果不是必须传，需要在这里说明参数的默认值
     */
    String defaultValue() default "";

    /**
     * 参数描述，用于详细说明参数格式
     */
    String[] desc() default "";

    String errmsg() default "";
}
