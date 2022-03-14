package org.etnaframework.core.web.exception;

import org.etnaframework.core.web.constant.RtnCodes;

/**
 * 12 参数值经验证无效
 *
 * @author BlackCat
 * @since 2013-1-28
 */
public class ParamInvalidValueException extends ParamBaseException {

    private static final long serialVersionUID = 1499104945671971952L;

    @Override
    public int getRtn() {
        return RtnCodes.PARAM_INVALID_VALUE;
    }

    public ParamInvalidValueException(String field, String msg) {
        super(field, msg);
    }

    public ParamInvalidValueException(String field) {
        super(field);
    }
}
