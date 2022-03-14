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
 * 配置字段的值，支持通过命令重设，支持任意JSON可转换的类型
 *
 * 基本类型和字符串，将直接通过配置值的字符串转换赋值，其他类型，通过配置值JSON
 * 反序列化后进行赋值，请注意如果是一个类，在多个托管bean中都有设置，这几个地方
 * 引用的将是【同一个对象】
 *
 * 例如有下列类：
 * public class Bean {
 *     public String name;
 *     public int age;
 * }
 * 在托管类A和B中，都通过注解设置了相同名称的成员变量Bean person，这两处地方默认
 * 引用的是同一个person，在A中修改name，那么B中的name也会随之变化。当然，如果在
 * 业务代码中修改了A的person引用的话，就不是引用的同一对象了
 *
 * 如果本注解加在类字段上，就是直接给字段赋值
 * 如果加在set方法上（方法名称必须以set开头，要求有且只有一个参数，参数类型为赋
 * 值类型），就是通过对应的方法名称调用set方法
 *
 * 例如有方法setName(String str)，那么默认取name对应的配置值，类型转换为字符串，
 * 调用setName方法来赋值。默认的名称是用的set后面的字符串，首字母小写（如果第2个
 * 字母也是大写，就不会使首字母小写，例如setEDM默认的名称就为EDM而非eDM）
 *
 * 在一个托管bean中，赋值的顺序按照源码中注解的顺序，首先按【字段】在源码的顺序
 * 进行赋值，然后按【set方法】在源码中的顺序进行赋值
 *
 * 通过命令重设值时，如果注解是在字段上，系统会比对当前值是不是跟设置值相同（如
 * 果是其他类型，会转化为JSON字符串进行比对）比对不同才会触发赋值；如果注解在set
 * 方法上，那么不管什么情况都会触发调用set方法
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
 * @since 2014-7-24
 */
@Inherited
@Target({
    ElementType.FIELD,
    ElementType.METHOD
})
@Retention(RetentionPolicy.RUNTIME)
public @interface Config {

    /**
     * 在配置中对应的key名称，如不指定默认为字段名
     */
    String value() default "";

    /**
     * 是否允许通过命令重设值，默认都是允许重设的
     */
    boolean resetable() default true;
}
