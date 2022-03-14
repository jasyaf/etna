package org.etnaframework.core.web;

/**
 * 用于启动脚本获取临时目录所在位置（免去写死/tmp造成的各种问题，提高系统兼容性）
 *
 * @author BlackCat
 * @since 2016-02-25
 */
public class EtnaTmpDirFinder {

    public static void main(String[] args) {
        System.out.println(System.getProperty("java.io.tmpdir"));
    }
}
