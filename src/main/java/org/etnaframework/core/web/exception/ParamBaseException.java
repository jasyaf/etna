package org.etnaframework.core.web.exception;

import org.etnaframework.core.web.constant.RtnCodes;

/**
 * 参数验证异常的基类
 *
 * @author BlackCat
 * @since 2013-1-28
 */
public abstract class ParamBaseException extends RenderableException {

    private static final long serialVersionUID = -3294237698518743232L;

    public class Data {

        private String field;

        private String descr;

        private String msg;

        public Data(String field, String descr, String msg) {
            this.field = field;
            this.descr = descr;
            this.msg = msg;
        }

        public Data(String field, String msg) {
            this.field = field;
            this.msg = msg;
        }

        public String getField() {
            return field;
        }

        public String getDescr() {
            return descr == null ? RtnCodes.getDescr(getRtn()) : descr;
        }

        public String getMsg() {
            return msg;
        }
    }

    /**
     * @param field 验证失败的field的id（对应页面上的id值）
     * @param msg 验证失败的提示信息
     */
    public ParamBaseException(String field, String msg) {
        inner.data = new Data(field, msg);
    }

    /**
     * @param field 验证失败的field的id（对应页面上的id值）
     * @param descr 错误描述,一般是错误的变量名
     * @param msg 验证失败的提示信息
     */
    public ParamBaseException(String field, String descr, String msg) {
        inner.data = new Data(field, descr, msg);
    }

    /**
     * @param field 验证失败的field的id（对应页面上的id值）
     */
    public ParamBaseException(String field) {
        this(field, "");
    }

    public Data getData() {
        return (Data) inner.data;
    }
}
