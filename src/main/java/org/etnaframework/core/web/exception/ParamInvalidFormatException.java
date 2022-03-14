package org.etnaframework.core.web.exception;

import org.etnaframework.core.web.constant.RtnCodes;

/**
 * 11 参数值格式不正确
 *
 * @author BlackCat
 * @since 2013-1-28
 */
public class ParamInvalidFormatException extends ParamBaseException {

    private static final long serialVersionUID = 2000574046396427498L;

    @Override
    public int getRtn() {
        return RtnCodes.PARAM_INVALID_FORMAT;
    }

    public ParamInvalidFormatException(String field, String msg) {
        super(field, msg);
    }

    public ParamInvalidFormatException(String field) {
        super(field);
    }
}
