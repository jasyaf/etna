package org.etnaframework.core.web.exception;

import org.etnaframework.core.web.constant.RtnCodes;

/**
 * 10 参数值未传递
 *
 * @author BlackCat
 * @since 2013-1-28
 */
public class ParamEmptyException extends ParamBaseException {

    private static final long serialVersionUID = 2000574046396427498L;

    @Override
    public int getRtn() {
        return RtnCodes.PARAM_EMPTY;
    }

    public ParamEmptyException(String field, String msg) {
        super(field, msg);
    }

    public ParamEmptyException(String field) {
        super(field);
    }
}
