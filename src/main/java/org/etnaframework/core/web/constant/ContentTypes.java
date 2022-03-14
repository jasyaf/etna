package org.etnaframework.core.web.constant;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.etnaframework.core.util.StringTools.CharsetEnum;

/**
 * HTTP请求返回的内容类型
 *
 * @author BlackCat
 * @since 2012-7-21
 */
public class ContentTypes {

    public static final String HTML = "text/html; charset=" + CharsetEnum.UTF_8.name();

    public static final String PLAIN = "text/plain; charset=" + CharsetEnum.UTF_8.name();

    public static final String JSON = "application/json; charset=" + CharsetEnum.UTF_8.name();

    public static final String XML = "text/xml; charset=" + CharsetEnum.UTF_8.name();

    public static final String JS = "application/x-javascript; charset=" + CharsetEnum.UTF_8.name();

    public static final String CSS = "text/css; charset=" + CharsetEnum.UTF_8.name();

    public static final String PNG = "image/png";

    public static final String GIF = "image/gif";

    public static final String JPEG = "image/jpeg";

    /** 扩展名对应的ContentType的映射 */
    private static final Map<String, String> suffixToTypes;

    static {
        suffixToTypes = new HashMap<String, String>();
        suffixToTypes.put("txt", PLAIN);
        suffixToTypes.put("htm", HTML);
        suffixToTypes.put("html", HTML);
        suffixToTypes.put("xml", XML);
        suffixToTypes.put("js", JS);
        suffixToTypes.put("css", CSS);
        suffixToTypes.put("png", PNG);
        suffixToTypes.put("gif", GIF);
        suffixToTypes.put("jpg", JPEG);
        suffixToTypes.put("jpeg", JPEG);
    }

    /** 文本类的ContentType（可以走gzip压缩的） */
    private static final Set<String> textTypes;

    static {
        textTypes = new HashSet<>();
        textTypes.add(PLAIN);
        textTypes.add(HTML);
        textTypes.add(XML);
        textTypes.add(JS);
        textTypes.add(CSS);
    }

    /**
     * 根据传入的扩展名获取对应的ContentType
     */
    public static String getContentType(String suffix) {
        String ct = suffixToTypes.get(suffix);
        return null == ct ? "" : ct;
    }

    /**
     * 判断是否是文本类型的ContentType（如果是的话就可以走gzip）
     */
    public static boolean isNotTextType(String contentType) {
        return !textTypes.contains(contentType);
    }
}
