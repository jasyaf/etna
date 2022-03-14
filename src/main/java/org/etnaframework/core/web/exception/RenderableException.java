package org.etnaframework.core.web.exception;

import org.etnaframework.core.web.bean.RtnObject;
import org.springframework.http.HttpStatus;

/**
 * 可经渲染生成指定返回内容的异常
 *
 * @author BlackCat
 * @since 2013-1-28
 */
public abstract class RenderableException extends RuntimeException {

    private static final long serialVersionUID = -5378482555127105172L;

    protected RtnObject inner;

    public abstract int getRtn();

    public RenderableException() {
        inner = new RtnObject(getRtn());
    }

    /**
     * 异常在返回前端时，HTTP的状态码，默认是200，如果有特殊情况请覆盖该方法
     */
    public int getHttpResponseStatus() {
        return HttpStatus.OK.value();
    }

    /**
     * 异常中包含的数据对象，可供填充模版输出到前端
     */
    public RtnObject getDataObject() {
        return inner;
    }
}
