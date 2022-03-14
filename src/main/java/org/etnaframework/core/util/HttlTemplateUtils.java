package org.etnaframework.core.util;

import java.io.IOException;
import java.io.StringWriter;
import java.text.ParseException;
import org.etnaframework.core.spring.annotation.OnContextInited;
import org.etnaframework.core.util.StringTools.CharsetEnum;
import org.springframework.stereotype.Service;
import httl.Engine;
import httl.Template;

/**
 * HTTL模板工具类
 *
 * @author BlackCat
 * @since 2015-08-30
 */
@Service
public final class HttlTemplateUtils {

    private static Engine engine;

    /**
     * 初始化httl模板引擎，如果有设置预编译模板则启动之
     */
    @OnContextInited
    protected static void init() {
        try {
            engine = Engine.getEngine();
        } catch (Throwable ex) {
            // httl初始化报错信息套了很多层，需要提取最里面的最有用的信息展示出来
            Throwable cause = ex;
            while (null != cause.getCause()) {
                cause = cause.getCause();
            }
            throw new RuntimeException("初始化httl模板引擎失败，请检查httl相关配置项，如果开启了precompiled预编译功能，请确保扫描路径template.directory和模板的正确性", cause);
        }
    }

    /**
     * 获取模板
     */
    public static Template getTemplate(String templatePath) throws IOException, ParseException {
        try {
            return engine.getTemplate(templatePath, CharsetEnum.UTF_8.name());
        } catch (NullPointerException ex) {
            if (null == engine) { // 直接调用，如果没初始化就手动初始化一下
                init();
            }
            return engine.getTemplate(templatePath, CharsetEnum.UTF_8.name());
        }
    }

    /**
     * 将模板和传入的对象内容进行合并，返回生成的文本内容
     */
    public static String mergeToString(String templatePath, Object map) throws ParseException {
        StringWriter sw = new StringWriter();
        String result = "";
        try {
            getTemplate(templatePath).render(map, sw);
        } catch (IOException e) {
        }
        result = sw.toString();
        return result;
    }

    /**
     * 将模板内容传入和传入的对象内容进行合并，返回生成的文本内容
     */
    public static String mergeTemplateToString(String templateContent, Object map) throws ParseException {
        StringWriter sw = new StringWriter();
        try {
            engine.parseTemplate(templateContent).render(map, sw);
        } catch (IOException e) {
        }
        return sw.toString();
    }
}
