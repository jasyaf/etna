package org.etnaframework.core.web.constant;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import org.etnaframework.core.spring.annotation.OnContextInited;
import org.etnaframework.core.util.CollectionTools;
import org.etnaframework.core.web.annotation.CmdRtnCode;
import org.etnaframework.core.web.exception.SimpleRtnBaseException;
import org.springframework.stereotype.Service;

/**
 * JSON返回码，仅用于参考，实际接入系统时可以另外再起一套
 *
 * @author BlackCat
 * @since 2012-10-3
 */
@Service
public class RtnCodes {

    protected static Map<Integer, String> id2descr = new LinkedHashMap<Integer, String>();

    protected static Map<Integer, String> id2msg = new LinkedHashMap<Integer, String>();

    protected static Map<Integer, SimpleRtnBaseException> id2ex = new LinkedHashMap<Integer, SimpleRtnBaseException>();

    @OnContextInited
    protected final void init() {
        _init(getClass());
    }

    private void _init(Class<?> clazz) {
        try {
            for (Field f : clazz.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isPublic(mod) && Modifier.isStatic(mod) && Modifier.isFinal(mod) && f.getType().equals(int.class)) {
                    final int id = f.getInt(null);
                    if (null != id2descr.get(id)) {
                        throw new IllegalArgumentException(clazz.getSimpleName() + "." + f.getName() + " (id=" + id + ")已被" + id2descr.get(id) + "使用，无法再添加");
                    }
                    id2descr.put(id, f.getName());
                    CmdRtnCode crc = f.getAnnotation(CmdRtnCode.class);
                    if (null != crc && CollectionTools.isNotEmpty(crc.value())) {
                        id2msg.put(id, crc.value()[0]);
                    } else {
                        id2msg.put(id, "");
                    }
                    id2ex.put(id, new SimpleRtnBaseException() {

                        private static final long serialVersionUID = -6695112199377329872L;

                        @Override
                        public int getRtn() {
                            return id;
                        }
                    });
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static String getDescr(int rtn) {
        assert id2descr.containsKey(rtn);
        return id2descr.get(rtn);
    }

    public static String getMsg(int rtn) {
        assert id2msg.containsKey(rtn);
        return id2msg.get(rtn);
    }

    /**
     * 获取简单的流程异常，用于只有一个返回码的情形
     */
    public static SimpleRtnBaseException getRtnException(int rtn) {
        assert id2ex.containsKey(rtn);
        return id2ex.get(rtn);
    }

    /** 0 成功 */
    public static final int OK = 0;

    /* 1X 与请求参数相关 */

    @CmdRtnCode("参数值格式不正确")
    public static final int PARAM_INVALID_FORMAT = 9;

    @CmdRtnCode("验证码无效")
    public static final int VCODE_INVALID = 10;

    @CmdRtnCode("登录态验证失败")
    public static final int SESSIONID_INVALID = 11;

    @CmdRtnCode("参数值未传递")
    public static final int PARAM_EMPTY = 12;

    @CmdRtnCode({
        "参数值经验证无效",
        "可作为通用自定义返回内容来使用"
    })
    public static final int PARAM_INVALID_VALUE = 13;

    @CmdRtnCode({
        "操作被禁止",
        "如请求过于频繁等"
    })
    public static final int OPERATION_FORBIDDEN = 14;

    @CmdRtnCode("服务器内部错误")
    public static final int INTERNAL_SERVER_ERROR = 500;

    @CmdRtnCode("没有访问权限")
    public static final int FORBIDDEN = 403;
}
