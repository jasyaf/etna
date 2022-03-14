package org.etnaframework.core.web;

import org.etnaframework.core.web.exception.RenderableException;
import org.springframework.stereotype.Service;

/**
 * 异常渲染器，即当业务代码执行时抛了异常时，要怎么给前端返回内容，如果没有实现本接口，服务器默认会报告异常，并把堆栈信息返回给前端显示
 *
 * @author BlackCat
 * @since 2013-1-19
 */
public interface ExceptionRender {

    /** 默认的异常渲染器，如果是可以转JSON就转JSON返回，否则就记录该异常（如配置发邮件） */
    ExceptionRender DEFAULT = new ExceptionRender() {

        @Override
        public void renderException(HttpEvent he, Throwable t) throws Throwable {
            if (t instanceof RenderableException) {
                he.writeJson(((RenderableException) t).getDataObject());
            } else {
                DispatchFilter.recordThrowable(he, t);
            }
        }
    };

    /**
     * <pre>
     * 渲染异常结果，只需要实现此方法，并在类上加@{@link Service}注解即可在启动时自动生效
     *
     * 如果希望异常被报告（如配置发邮件），请在实现中将对应的异常【再次抛出】即可
     * </pre>
     */
    void renderException(HttpEvent he, Throwable t) throws Throwable;
}
