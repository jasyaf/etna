package org.etnaframework.core.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口验证要求
 *
 * @author BlackCat
 * @since 2013-12-1
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CmdSession {

    /** 登录态要求类型 */
    CmdSessionType value();

    /** 对登录态的特殊描述，可以放到这里 */
    String desc() default "";

    public static enum CmdSessionType {

        COMPELLED("必须要求有登录态"),

        NOT_COMPELLED("不需要登录态"),

        DISPENSABLE("有登录态和没有登录态时都能正常使用，如果没有登录态会被当作游客处理"),

        INTERNAL_WITH_AUTH("内部接口，需要认证");

        private String detail;

        private CmdSessionType(String detail) {
            this.detail = detail;
        }

        @Override
        public String toString() {
            return detail;
        }
    }
}
