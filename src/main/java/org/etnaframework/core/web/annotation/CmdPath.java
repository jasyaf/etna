package org.etnaframework.core.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.etnaframework.core.web.HttpEvent;

/**
 * URL匹配规则
 *
 * @author BlackCat
 * @since 2014-7-23
 */
@Inherited
@Target({
    ElementType.TYPE,
    ElementType.METHOD
})
@Retention(RetentionPolicy.RUNTIME)
public @interface CmdPath {

    /**
     * <pre>
     * 分为类和方法两种用法
     *
     * 1、如果用到方法上
     *
     * 直接匹配映射规则`如/test/home.html，支持同时指定多条规则
     *
     * 如果是/开头，如/test/home.html就是直接匹配这个URL，这是绝对规则匹配，不会再和其他的匹配规则叠加
     * 如果不是/开头，如hello/doSth，这是相对规则匹配，会跟其他的匹配规则进行叠加
     * 如HiCmd.test，加在方法test上，那么就会匹配成/hi/hello/doSth
     *
     * etna支持的RESTful模式处理，可以填入正则表达式来参与处理，只要发现配置的规则是【以$结尾】即会启动正则表达式规则
     *
     * 在直接匹配未命中的情况下，会按照加载的顺序逐个检查是否匹配正则表达式，符合条件的就会进入处理，也适用上述的叠加规则
     * 例如，有TestCmd的方法account，填入的规则为/account/(\\w+)/(\\d+)$，后面括号中的两个参数表示需要服务器解析的
     * 匹配的URL就是诸如/account/myUserAccount/1这样的URL
     *
     * 使用前，请对你的正则表达式进行测试，确保能正确进行匹配，参考测试网站http://tool.chinaz.com/regex/
     *
     * 在业务代码中，请使用{@link HttpEvent}的getUrlXXX方法来获取对应的参数值
     *
     * 2、如果用在类上
     *
     * 用于指定这个类自动生成的URL的别名，要求必须以/开头，支持同时指定多条规则
     * 如HiCmd，加了规则/hello/world，那么其下的方法HiCmd.test，就被映射为/hello/world/test
     *
     * 可以在类和方法上同时使用，使用效果会叠加
     * </pre>
     */
    String[] value();

    /**
     * 是否会覆盖默认生成的匹配规则，默认情况是会覆盖的，如HiCmd.test，默认规则为/hi/test，如果override=true那么/hi/ test就无效了
     */
    boolean override() default true;
}
