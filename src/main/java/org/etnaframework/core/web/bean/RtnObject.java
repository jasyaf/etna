package org.etnaframework.core.web.bean;

import java.io.Serializable;
import java.util.Map;
import org.etnaframework.core.util.JsonObjectUtils;
import org.etnaframework.core.web.annotation.CmdRespParam;
import com.alibaba.fastjson.annotation.JSONField;

/**
 * JSON类型返回的通用格式
 *
 * @author BlackCat
 * @since 2014-12-23
 */
public class RtnObject implements HttpRespBean, Serializable {

    public static final String RTN = "rtn";

    public static final String DATA = "data";

    private static final long serialVersionUID = 9158029395296752468L;

    @JSONField(name = RTN)
    @CmdRespParam(desc = "返回码，为0时表示成功，非0时为失败", sample = "0")
    public int rtn;

    @JSONField(name = DATA)
    @CmdRespParam(desc = "返回内容，请客户端解析这部分的内容进行业务处理", sample = "0")
    public Object data;

    public RtnObject() {
    }

    public RtnObject(int rtn) {
        this.rtn = rtn;
    }

    public RtnObject(int rtn, Object data) {
        this.rtn = rtn;
        this.data = data;
    }

    /**
     * 获取data并转化为指定的类型，data为空或转换失败会返回null
     */
    public <T> T getData(Class<T> clazz) {
        if (null != data) {
            if (data instanceof String) {
                return JsonObjectUtils.parseJson(String.valueOf(data), clazz);
            } else if (data instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) data;
                return JsonObjectUtils.parseJson(map, clazz);
            }
        }
        return clazz.cast(data);
    }

    @JSONField(serialize = false, deserialize = false)
    public String getDataFieldName() {
        return DATA;
    }
}
