package org.etnaframework.core.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API返回的参数，用于定义在javabean的字段上自动生成文档
 *
 * @author BlackCat
 * @since 2015-06-30
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CmdRespParam {

    /**
     * 字段描述，用于详细说明含义和用处
     */
    String desc() default "";

    /**
     * 取值范例，用于将文档给使用者调试接口时传入，此项必须传入，否则将无法在启动时生成文档，如果是列表类型，可以传入多个表示不同的值
     */
    String sample() default "";

    /**
     * 范围指定，如果指定的是enum类就会自动去获取它的所有值，如果是static field这样的也会自动去获取值
     */
    Class<?> typeEnum() default Object.class;
}
