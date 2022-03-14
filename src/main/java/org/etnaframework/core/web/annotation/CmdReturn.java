package org.etnaframework.core.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.etnaframework.core.web.bean.HttpRespBean;

/**
 * API返回的数据结构
 *
 * @author BlackCat
 * @since 2013-5-5
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CmdReturn {

    /** 返回内容描述 */
    String[] value() default {};

    /** 如果返回的内容是基于javabean构建的，这里是javabean的类名，必要要有默认构造方法，用于自动生成文档 */
    Class<?> respClass() default Object.class;

    /** 如果返回的{@link #respClass()}外面还有一层“外壳对象”，必要要有默认构造方法，请在这里指定其类名 */
    Class<? extends HttpRespBean> shellClass() default HttpRespBean.class;
}
