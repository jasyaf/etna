package test.cmd;

import java.io.IOException;
import java.io.InputStream;
import org.etnaframework.core.util.HumanReadableUtils;
import org.etnaframework.core.util.JsonObjectUtils;
import org.etnaframework.core.web.FileUploadHandler;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.cmd.HttpCmd;
import org.springframework.stereotype.Service;

/**
 * multipart测试
 *
 * @author YuanHaoliang
 * @since 2014-8-7
 */
@Service
public class MultipartCmd extends HttpCmd {

    /**
     * 整合到框架里的测试
     */
    public void submit(HttpEvent he) throws Throwable {
        System.err.println("multipart receive:");
        he.getFile(new FileUploadHandler() {

            @Override
            public void file(String fieldname, String filename, long filesize, InputStream stream, HttpEvent he1) throws IOException {
                System.err.println("field=" + fieldname + ",filename=" + filename + ",size=" + HumanReadableUtils.byteSize(filesize));
            }
        });

        System.err.println(JsonObjectUtils.createJsonPretty(he.getRequestMapAll()));
    }

    public void redirect(HttpEvent he) throws Throwable {
        he.sendRedirect("http://www.baidu.com:8080/now");
    }
}
