package org.etnaframework.core.web;

import org.etnaframework.core.web.cmd.HttpCmd;
import org.springframework.stereotype.Service;

/**
 * 通用鉴权接口，用于鉴别访问者有无权限使用指定接口，具体的鉴定逻辑请在实现类中自行定义（例如IP验证等）
 *
 * @author BlackCat
 * @since 2015-06-04
 */
public interface AuthHandler {

    /**
     * 鉴别访问者有无权限使用指定接口，只需要实现此方法，并在类上加@{@link Service}注解即可在启动时自动生效
     */
    void auth(HttpCmd cmd, HttpEvent he) throws Throwable;
}
