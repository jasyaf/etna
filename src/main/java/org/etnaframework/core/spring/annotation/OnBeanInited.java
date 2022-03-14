package org.etnaframework.core.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.etnaframework.core.spring.SpringContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

/**
 * <pre>
 * 标记在Spring容器实例化【一个】托管bean时，在该bean中需要执行的方法（要求不能
 * 有任何参数）方法调用顺序按在源码中书写的顺序，注意从Spring容器中取出时，其他
 * 托管bean不一定初始化好了，故不要尝试对其他托管bean进行操作！
 *
 * 如果标记的方法中有对其他托管bean的操作，请改用@{@link OnContextInited}
 *
 *
 * 相关注解的处理顺序：
 *
 * 【服务器启动】
 *
 * 1、初始化Spring，开始逐个实例化托管bean
 *
 * 2、Srping开始实例化【一个】托管bean
 *
 * 3、Spring将标注有@{@link Autowired}等注解的进行注入赋值
 *    （注意，注入的对象可能并未完成接下来的4/5/6步骤，请谨慎操作）
 *
 * 4、给标记有@{@link Config}的【字段】赋值
 *
 * 5、调用标记有@{@link Config}的【set方法】并赋值
 *
 * 6、调用标记有@{@link OnBeanInited}的方法
 *
 * 7、初始化完成【一个】托管bean
 *
 * 8、【所有的】托管bean初始化完毕
 *
 * 9、按类路径全名的字典序（etna框架的类优先）
 *    调用托管bean中标记有@{@link OnContextInited}的方法
 *
 *
 * 【服务器运行过程中 从{@link SpringContext}获取托管bean】
 *
 * 1、如果托管bean是单例的（默认单例），将返回启动时初始化好的对象
 *
 * 2、如果是非单例bean，即在xml里面设置scope为prototype
 *    或标注@{@link Scope}({@link ConfigurableBeanFactory}.SCOPE_PROTOTYPE)
 *    将执行服务器启动时的步骤2->7
 *
 *    【不会】执行标记有@{@link OnContextInited}的方法！
 *
 *
 * 【服务器关闭】
 *
 * 1、System.exit触发JVM关闭事件（如果直接杀进程就不会执行后面的了）
 *
 * 2、按类路径全名的字典序（etna框架的类优先）
 *    调用托管bean中标记有@{@link OnJvmShutdown}的无参数方法
 *
 * 3、JVM关闭，服务进程结束
 * </pre>
 *
 * @author BlackCat
 * @since 2015-01-17
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnBeanInited {

}
