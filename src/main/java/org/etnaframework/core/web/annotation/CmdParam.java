package org.etnaframework.core.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API使用的请求参数
 *
 * @author BlackCat
 * @since 2013-5-5
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CmdParam {

    /**
     * 请求参数值的提交方式
     */
    enum ParamScope {
        RESTFUL("RESTful"),
        GET("GET UrlEncoded"),
        POST("POST UrlEncoded"),
        GET_OR_POST("GET/POST UrlEncoded"),
        COOKIE("Cookie"),
        CONTENT("POST Content");

        private String detail;

        ParamScope(String detail) {
            this.detail = detail;
        }

        @Override
        public String toString() {
            return detail;
        }
    }

    /**
     * 请求参数值的数据类型
     */
    enum ParamType {
        STRING("字符串"),
        INT("整数"),
        POSITIVE_INT("正整数(>0整数)"),
        NEGATIVE_INT("负整数(<0整数)"),
        NATURAL_INT("自然数(>=0整数)"),
        FLOAT("浮点数"),
        POSITIVE_FLOAT("正浮点数"),
        NEGATIVE_FLOAT("负浮点数"),
        BOOL("BOOL类型(仅true/y/1为真)"),
        ENUM("枚举类型"),
        IMAGE("图片文件");

        private String detail;

        ParamType(String detail) {
            this.detail = detail;
        }

        @Override
        public String toString() {
            return detail;
        }
    }

    /**
     * 参数获取来源，默认是GET/POST均可的
     */
    ParamScope[] scope() default ParamScope.GET_OR_POST;

    /**
     * 参数名称，如username
     */
    String field();

    /**
     * 参数名称对应的说明文字，如username就对应“用户名”，如果没有默认就跟field一样，该值用于生成参数校验失败时的提示信息
     */
    String name() default "";

    /**
     * 取值范例，用于将文档给使用者调试接口时传入
     */
    String sample() default "";

    /**
     * 参数类型
     */
    ParamType type() default ParamType.STRING;

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
}
