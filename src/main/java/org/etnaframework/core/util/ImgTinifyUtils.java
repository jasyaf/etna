package org.etnaframework.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.spring.annotation.Config;
import org.etnaframework.core.util.HttpClientUtils.HttpResult;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson.JSONObject;
import static org.etnaframework.core.util.JsonObjectUtils.buildMap;
import static org.etnaframework.core.util.JsonObjectUtils.createJson;
/**
 * 图片压缩工具,采用tinypng.com,每个月免费500张
 * 由于官方的java工具包依赖太多，所以重写工具类
 * <p>
 * 申请API KEY
 * https://tinypng.com/developers
 * <p>
 * https://tinypng.com/developers/reference
 * https://tinypng.com/developers/reference/java
 * <p>
 * Created by yuanhaoliang on 2016-12-15.
 */
@Service
public final class ImgTinifyUtils {

    private static final Logger logger = Log.getLogger();

    /** API域名 */
    private static final String API_ENDPOINT = "https://api.tinify.com";

    /** 默认的API KEY */
    @Config("etna.imgtinify.apikey")
    public static String ApiKey = "4L7jJ5vLQi7yUjzlgmgrKo_2Jyy83vkW";

    /** 默认KEY本月已压缩次数 */
    private static int compressionCount = 0;

    /**
     * 压缩图片文件,支持jpg/png
     *
     * @param fromFilePath 要压缩的文件路径
     * @param toFilePath 压缩后的文件路径
     */
    public static void tiny(String fromFilePath, String toFilePath) throws IOException {
        byte[] bytes = tiny(fromFilePath).fetch().data;
        Files.write(Paths.get(toFilePath), bytes, StandardOpenOption.CREATE_NEW);
    }

    /**
     * 压缩图片文件,支持jpg/png
     *
     * @param fromFileBytes 要压缩的文件字节
     *
     * @return toFileBytes  压缩后的文件字节
     */
    public static byte[] tinyFromBytes(byte[] fromFileBytes) throws IOException {
        return tiny(fromFileBytes).fetch().data;
    }

    /**
     * 压缩图片文件,支持jpg/png
     *
     * @param fromFileBytes 要压缩的文件字节
     * @param toFilePath 压缩后的文件路径
     */
    public static void tinyToFile(byte[] fromFileBytes, String toFilePath) throws IOException {
        byte[] bytes = tiny(fromFileBytes).fetch().data;
        Files.write(Paths.get(toFilePath), bytes, StandardOpenOption.CREATE_NEW);
    }

    /**
     * 压缩图片文件,支持jpg/png
     *
     * @param fromFile 要压缩的文件字节
     *
     * @return toFileBytes  压缩后的文件字节
     */
    public static byte[] tinyToByte(String fromFile) throws IOException {
        return tiny(fromFile).fetch().data;
    }

    /**
     * 通过图片文件字节数组创建请求
     */
    public static ImgTinifyBuilder tiny(byte[] fromFileBytes) {
        return new ImgTinifyBuilder(fromFileBytes);
    }

    /**
     * 通过文件路径创建
     */
    public static ImgTinifyBuilder tiny(String filePath) throws IOException {
        return new ImgTinifyBuilder(Files.readAllBytes(Paths.get(filePath)));
    }

    /**
     * 通过图片文件URL创建请求
     */
    public static ImgTinifyBuilder tinyFromUrl(String url) {
        return new ImgTinifyBuilder(url);
    }

    /**
     * 通过图片文件File创建请求
     */
    public static ImgTinifyBuilder tiny(File file) throws IOException {
        return new ImgTinifyBuilder(Files.readAllBytes(file.toPath()));
    }

    /**
     * 默认KEY本月已压缩次数
     */
    public static int getCompressionCount() {
        return compressionCount;
    }

    public static class ImgTinifyBuilder {

        /** tinyPNG的api Key */
        private String apiKey = ApiKey;

        /** 图片字节数组 */
        private byte[] imgBytes;

        /** 网络上的图片URL */
        private String url;

        /** 日志输出log对象 */
        private Logger log = logger;

        public ImgTinifyBuilder(byte[] imgBytes) {
            this.imgBytes = imgBytes;
        }

        public ImgTinifyBuilder(String url) {
            this.url = url;
        }

        public ImgTinifyBuilder setApiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * 设置日志输出log对象
         */
        public ImgTinifyBuilder log(Logger log) {
            if (log != null) {
                this.log = log;
            }
            return this;
        }

        public ImgTinifyResult fetch() {
            long start = System.currentTimeMillis();
            HttpResult result = null;

            try {
                if (url == null) {
                    // 使用数组
                    result = HttpClientUtils.post(API_ENDPOINT + "/shrink").auth("api", this.apiKey).content(imgBytes).log(log).fetch();
                } else {
                    // 使用URL
                    String json = createJson(buildMap("source", buildMap("url", this.url)));
                    result = HttpClientUtils.post(API_ENDPOINT + "/shrink").auth("api", this.apiKey).header("Content-Type", "application/json").content(json).log(log).fetch();
                }
                int statusCode = result.getStatusCode();

                if (statusCode >= 400) {

                    JSONObject jsonObject = JsonObjectUtils.parseJson(result.getString());
                    String error = "";
                    String message = "";
                    if (jsonObject != null) {
                        error = jsonObject.getString("error");
                        message = jsonObject.getString("message");
                    }
                    if (statusCode == 401) {
                        throw new ImgTinifyException("ApiKey无效:" + this.apiKey);
                    } else {
                        throw new ImgTinifyException(statusCode + "错误：" + error + ",原因：" + message);
                    }
                }

                int _compressionCount = StringTools.getInt(result.getHeader("Compression-Count"), 0);
                if (this.apiKey.equals(ApiKey)) {
                    //如果使用的是默认API，则填充使用次数
                    compressionCount = _compressionCount;
                }

                long requestSpan = System.currentTimeMillis() - start;
                start = System.currentTimeMillis();

                ImgTinifyResult r = new ImgTinifyResult(_compressionCount, result.getString(), this.apiKey, this.log);
                long receiveDataSpan = System.currentTimeMillis() - start;

                log.info("OK|{}ms|{}ms|{}|{}x{}|{}|{}|{}|{}", requestSpan, receiveDataSpan, r.type, r.width, r.height, r.inputSize, r.size, r.ratio, r.compressionCount);

                return r;
            } catch (ImgTinifyException e) {
                log.error("ERR|{}ms|{}|{}", System.currentTimeMillis() - start, e.getMessage(), result != null ? result.getStatusCode() : "");
                throw e;
            } catch (Throwable t) {
                log.error("ERR|{}ms|{}", System.currentTimeMillis() - start, result != null ? result.getStatusCode() : "", t);
            }
            return ImgTinifyResult.EMPTY;
        }
    }

    public static class ImgTinifyResult {

        private static final ImgTinifyResult EMPTY = new ImgTinifyResult();

        /** 上传图片大小 */
        public int inputSize;

        /** 上传图片类型 */
        public String inputType;

        /** 返回图片大小 */
        public int size;

        /** 返回图片类型 */
        public String type;

        /** 返回图片宽，单位px */
        public int width;

        /** 返回图片高，单位px */
        public int height;

        /** 压缩率 */
        public double ratio;

        /** 本月已压缩次数 */
        public int compressionCount;

        /** 二进制数据 */
        public byte[] data;

        private ImgTinifyResult() {
        }

        private ImgTinifyResult(int compressionCount, String content, String apiKey, Logger log) {
            this.compressionCount = compressionCount;

            JSONObject jsonObject = JsonObjectUtils.parseJson(content);

            JSONObject input = jsonObject.getJSONObject("input");
            this.inputSize = input.getIntValue("size");
            this.inputType = input.getString("type");
            JSONObject output = jsonObject.getJSONObject("output");
            this.size = output.getIntValue("size");
            this.type = output.getString("type");
            this.width = output.getIntValue("width");
            this.height = output.getIntValue("height");
            this.ratio = output.getDoubleValue("ratio");
            String url = output.getString("url");
            this.data = HttpClientUtils.get(url).auth("api", apiKey).log(log).fetch().getBytes();
        }
    }

    public static class ImgTinifyException extends RuntimeException {

        public ImgTinifyException(String message) {
            super(message);
        }
    }
}
