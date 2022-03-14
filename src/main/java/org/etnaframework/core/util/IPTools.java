package org.etnaframework.core.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.etnaframework.core.logging.Log;
import org.slf4j.Logger;

/**
 * IP相关的操作，如IP格式的校验、IP格式的转换
 *
 * @since 2015-05-28
 */
public class IPTools {

    public static final long BAD_IP_CODE = -1L;

    private static final Pattern ipPattern = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private static final Logger log = Log.getLogger();

    /**
     * 把IP地址以byte数组的形式转化成字符串
     */
    public static String bytesToStr(byte[] bytes) {
        String rtn = null;
        if (bytes != null && bytes.length == 4) {
            StringBuilder sb = new StringBuilder();
            sb.append(bytes[0] & 0xff).append(".").append(bytes[1] & 0xff).append(".").append(bytes[2] & 0xff).append(".").append(bytes[3] & 0xff);
            rtn = sb.toString();
        }
        return rtn;
    }

    /**
     * 将IP地址以btye数组的形式转化成Long型
     *
     * @param bytes 字节数组
     */
    public static long byteToLong(byte[] bytes) {
        long rtn = BAD_IP_CODE;
        if (bytes != null && bytes.length == 4) {
            rtn = (bytes[0] & 0xffL) << 24 | (bytes[1] & 0xffL) << 16 | (bytes[2] & 0xffL) << 8 | (bytes[3] & 0xffL);
        }
        return rtn;
    }

    /**
     * 将Long型的IP地址转化成传统的IP字符串
     */
    public static String longToStr(long ipLong) {
        String rtn = null;
        if (ipLong > 0 && ipLong < 0xffffffffL) {
            StringBuilder sb = new StringBuilder();
            sb.append(ipLong >> 24 & 0xff).append(".").append(ipLong >> 16 & 0xff).append(".").append(ipLong >> 8 & 0xff).append(".").append(ipLong & 0xff);
            rtn = sb.toString();
        }
        return rtn;
    }

    /**
     * 从IP地址转换为数值
     *
     * @param address IP地址(可以为主机名,或者数字IP。
     *
     * @return iplong <br/>
     * 长整形的IP值。 <br/>
     * -1,如果IP字符串不合法,或者主机不存在。
     */
    public static long strToLong(String address) {
        long rtn = BAD_IP_CODE;
        Matcher matcher = ipPattern.matcher(address);
        if (matcher.find()) {
            long byte1 = Long.parseLong(matcher.group(1));
            long byte2 = Long.parseLong(matcher.group(2));
            long byte3 = Long.parseLong(matcher.group(3));
            long byte4 = Long.parseLong(matcher.group(4));
            if (byte1 > 0xff || byte2 > 0xff || byte3 > 0xff || byte4 > 0xff) {
                return rtn;
            }
            rtn = byte1 << 24 | byte2 << 16 | byte3 << 8 | byte4;
        } else {
            try {
                InetAddress inetAddress = InetAddress.getByName(address);
                if (inetAddress != null) {
                    byte[] addByte = inetAddress.getAddress();
                    rtn = (addByte[0] & 0xffL) << 24 | (addByte[1] & 0xffL) << 16 | (addByte[2] & 0xffL) << 8 | (addByte[3] & 0xffL);
                }
            } catch (UnknownHostException e) {
                log.error("", e);
            }
        }
        return rtn;
    }
}
