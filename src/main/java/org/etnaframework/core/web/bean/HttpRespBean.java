package org.etnaframework.core.web.bean;

import com.alibaba.fastjson.annotation.JSONField;

/**
 * 用于生成文档时，指定哪个字段是可以替换内容的，请在实现方法中返回对应的字段名
 *
 * @author BlackCat
 * @since 2015-06-30
 */
public interface HttpRespBean {

    @JSONField(serialize = false, deserialize = false)
    public String getDataFieldName();
}
