package org.etnaframework.core.web.exception;

import org.etnaframework.core.web.constant.RtnCodes;

/**
 * 只有一个返回码的异常的基类
 *
 * @author BlackCat
 * @since 2013-1-29
 */
public abstract class SimpleRtnBaseException extends RenderableException {

    private static final long serialVersionUID = 239740522892236586L;

    public class Data {

        public String getMsg() {
            return RtnCodes.getMsg(getRtn());
        }

        public String getDescr() {
            return RtnCodes.getDescr(getRtn());
        }
    }

    public SimpleRtnBaseException() {
        inner.data = new Data();
    }
}
