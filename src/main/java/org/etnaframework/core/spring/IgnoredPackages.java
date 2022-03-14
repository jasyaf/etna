package org.etnaframework.core.spring;

/**
 * 内部框架不进行扫描处理的包
 *
 * @author BlackCat
 * @since 2019-12-31
 */
public class IgnoredPackages {

    public static final String[] IGNORE = {
        "java.",
        "javax.",
        "org.springframework.",
        "com.sun.proxy.",
        "com.sun.jmx.",
        "springfox.documentation.",
        "com.alibaba.",
        "com.aliyuncs.",
        "com.google.gson.",
        "com.fasterxml.",
        "freemarker.",
        "io.lettuce.",
        "io.micrometer."
    };

    public static boolean filter(Object bean) {
        String name = bean.getClass()
                          .getName();
        for (String ignore : IGNORE) {
            if (name.startsWith(ignore)) {
                return true;
            }
        }
        return false;
    }
}
