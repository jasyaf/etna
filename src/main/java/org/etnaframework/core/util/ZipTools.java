package org.etnaframework.core.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 和压缩/解压缩有关的工具类集合
 *
 * @author BlackCat
 * @since 2016-02-17
 */
public class ZipTools {

    /**
     * 将传入的byte[]使用gzip压缩，如果传入null将会返回null，传入byte[0]会返回byte[0]
     */
    public static byte[] gzip(byte[] source) throws Exception {
        if (null == source) {
            return null;
        }
        if (source.length == 0) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(out);
        gzip.write(source);
        gzip.finish();
        gzip.close();
        return out.toByteArray();
    }

    /**
     * 将传入的byte[]使用gzip解压缩，如果传入null将会返回null，传入byte[0]会返回byte[0]
     */
    public static byte[] ungzip(byte[] source) throws Exception {
        if (null == source) {
            return null;
        }
        if (source.length == 0) {
            return new byte[0];
        }
        ByteArrayInputStream in = new ByteArrayInputStream(source);
        GZIPInputStream gzip = new GZIPInputStream(in);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int read;
        while ((read = gzip.read()) != -1) {
            out.write(read);
        }
        out.flush();
        gzip.close();
        return out.toByteArray();
    }
}
