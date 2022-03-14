package org.etnaframework.plugin.doc.json;

import java.util.List;
import org.etnaframework.core.util.JsonObjectUtils.LargeNumberFixToStringValueFilter;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.common.base.Splitter;
/**
 * json文档生成类
 * Created by yuanhaoliang on 2017-02-25.
 */
public final class DocJsonGenerator {

    /** 注释对齐所在列 */
    private static final int commentAlign = 80;

    private DocJsonGenerator() {
    }

    /**
     * 生成回包的JSON示例
     */
    public static String genRespDoc(Object resp) {
        String json = JSON.toJSONString(resp, DocSerializeConfig.instance, LargeNumberFixToStringValueFilter.instance,
            SerializerFeature.UseSingleQuotes, SerializerFeature.PrettyFormat).replace("\t", "    ");

        return fixCommentAlign(json);
    }

    /**
     * 修正注释对齐
     */
    private static String fixCommentAlign(String json) {
        List<String> lines = Splitter.on('\n').splitToList(json);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                if (i != line.length() - 1 && line.charAt(i) == '/' && line.charAt(i + 1) == '*') {
                    // 找到注释的开始
                    int stringLength = getStringLength(line.substring(0, i));
                    // 注释偏移
                    for (int j = 0; j < commentAlign - stringLength; j++) {
                        s.append(' ');
                    }
                    s.append(line.charAt(i));
                } else {
                    s.append(line.charAt(i));
                }
            }
            sb.append(s).append('\n');
        }

        return sb.toString();
    }

    private static int getStringLength(String s) {
        int length = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 128) {
                length += 2;
            } else {
                length++;
            }
        }
        return length;
    }
}
