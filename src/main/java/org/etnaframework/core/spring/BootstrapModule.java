package org.etnaframework.core.spring;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import org.etnaframework.core.spring.annotation.OnBeanInited;
import org.etnaframework.core.spring.annotation.OnContextInited;
import org.etnaframework.core.spring.annotation.OnJvmShutdown;
import org.etnaframework.core.util.ReflectionTools;

/**
 * <pre>
 * 用于在服务器启动时，http服务初始化完毕，准备绑定端口前，初始化其他挂载的业务模块并绑定端口（例如启动thriftServer/RPC Server等）
 *
 * 服务器初始化时，会扫描{@link BootstrapModule}的托管bean实现，并执行其中的初始化方法
 * 通过{@link #getPorts()}确定最后需要绑定的端口，并在绑定前判断端口占用情况
 * 通过{@link #bind()}完成服务端口的绑定
 * </pre>
 *
 * @author BlackCat
 * @since 2015-01-15
 */
public interface BootstrapModule {

    /** 不允许添加@{@link OnBeanInited}/@{@link OnContextInited}/@{@link OnJvmShutdown}注解的方法列表 */
    Collection<Method> NO_NEED_FOR_OnXXX = ReflectionTools.getDeclaredMethodsInSourceCodeOrder(BootstrapModule.class);

    /**
     * 该业务模块需要绑定的端口，用于服务器启动时检测端口的占用状态，如果不需要绑定端口请返回空List
     */
    List<InetSocketAddress> getPorts();

    /**
     * <pre>
     * 在模块初始化完毕后，绑定端口，开始服务
     *
     * 不要在此方法内执行耗费时间的操作，这会导致替代式重启时绑定端口的时间过长，无服务的“缝隙”过大，费时间的操作请写一个无参数的方法，然后加@{@link OnContextInited}服务器启动时会自动处理
     * </pre>
     */
    void bind() throws Throwable;
}
