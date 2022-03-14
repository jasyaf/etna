package org.etnaframework.plugin.doc.json;

import com.alibaba.fastjson.serializer.DocJavaBeanSerializer;
import com.alibaba.fastjson.serializer.JavaBeanSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson.serializer.SerializeBeanInfo;
import com.alibaba.fastjson.serializer.SerializeConfig;

/**
 * 文档格式化fastjson序列化配置类
 */
public class DocSerializeConfig extends SerializeConfig {

    public final static DocSerializeConfig instance = new DocSerializeConfig();

    public DocSerializeConfig() {
        super();
    }

    public ObjectSerializer createJavaBeanSerializer(SerializeBeanInfo beanInfo) {

        return new DocJavaBeanSerializer(beanInfo);
    }

}
