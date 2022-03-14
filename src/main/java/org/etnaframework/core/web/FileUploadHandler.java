package org.etnaframework.core.web;

import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.fileupload.ProgressListener;

/**
 * 文件上传处理器
 *
 * @author YuanHaoliang
 * @since 2014-8-7
 */
public abstract class FileUploadHandler {

    /** 本次请求总的文件上传限制大小，单位字节，-1表示无限限制 */
    private long sizeMax = -1;

    /** 本次请求每个文件上传限制大小，单位字节，-1表示无限限制 */
    private long fileSizeMax = -1;

    /**
     * 上传进度监听器
     *
     * @see http://commons.apache.org/proper/commons-fileupload/using.html 的Watching progress部分，注意性能问题 ！
     */
    private ProgressListener listener;

    /***
     * 创建文件上传处理器，不限制文件大小
     */
    public FileUploadHandler() {
    }

    /**
     * 创建文件上传处理器，限制文件大小
     *
     * @param sizeMax 本次请求总的文件上传限制大小。-1表示无限限制
     * @param fileSizeMax 本次请求每个文件上传限制大小。-1表示无限限制
     */
    public FileUploadHandler(long sizeMax, long fileSizeMax) {
        this.sizeMax = sizeMax;
        this.fileSizeMax = fileSizeMax;
    }

    /**
     * 创建文件上传处理器，限制文件大小
     *
     * @param sizeMax 本次请求总的文件上传限制大小。-1表示无限限制
     * @param fileSizeMax 本次请求每个文件上传限制大小。-1表示无限限制
     * @param listener 上传进度监听器
     */
    public FileUploadHandler(long sizeMax, long fileSizeMax, ProgressListener listener) {
        this.sizeMax = sizeMax;
        this.fileSizeMax = fileSizeMax;
        this.listener = listener;
    }

    /**
     * 上传文件处理
     *
     * @param fieldname form的input标签里name属性
     * @param filename 文件名，如果文件名非法，则返回System.currentTimeMillis()
     * @param filesize 文件大小，单位字节，如果使用ByStreaming方法，则返回本次总上传大小。
     * @param stream 文件输入流
     * @param he 如果不是使用ByStreaming方法,he里可以正常getString等，如果使用ByStreaming，he里不一定能get到String，当多个接口共用一个handler的时候，he就很有用了，可以根据上下文来处理上传的文件。
     */
    public abstract void file(String fieldname, String filename, long filesize, InputStream stream, HttpEvent he1) throws IOException;

    /**
     * 本次请求总的文件上传限制大小。-1表示无限限制
     */
    public long getSizeMax() {
        return sizeMax;
    }

    /**
     * 本次请求每个文件上传限制大小。-1表示无限限制
     */
    public long getFileSizeMax() {
        return fileSizeMax;
    }

    /**
     * 上传进度监听器
     */
    public ProgressListener getListener() {
        return listener;
    }

    /**
     * 本次请求总的文件上传限制大小。-1表示无限限制
     */
    public void setSizeMax(long sizeMax) {
        this.sizeMax = sizeMax;
    }

    /**
     * 本次请求每个文件上传限制大小。-1表示无限限制
     */
    public void setFileSizeMax(long fileSizeMax) {
        this.fileSizeMax = fileSizeMax;
    }

    /**
     * 上传进度监听器
     */
    public void setListener(ProgressListener listener) {
        this.listener = listener;
    }
}
