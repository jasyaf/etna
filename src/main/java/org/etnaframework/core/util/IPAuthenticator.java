package org.etnaframework.core.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.etnaframework.core.logging.Log;
import org.slf4j.Logger;

/**
 * 判定传入的IP是否在指定的IP段中的工具类，支持完整IP列表、IP段、通配符
 *
 * @author Daniel.Zhan
 */
public class IPAuthenticator {

    /** IP范围匹配表达式 */
    private static final Pattern ipPattern_RANGE = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.\\d{1,3}\\-(\\d{1,3}|(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})){0,1}$");

    /** IP规则名单 */
    private Set<String> ipList = new LinkedHashSet<String>();

    /**
     * <pre>
     * 接受范围规则192.168.1.1-24|192.168.1.1-193.168.1.1
     *
     * 接受通配符规则1.1.1.*|1.1.*.*|1.*.*.*|*.*.*.*
     *
     * 【不接受】1.*.1.1
     *
     * 配置文件示例：
     * ipList=["172.*.*.*","173.1.*.*","174.0.0.*","175.1.1.6","192.168.1.1-24","202.23.56.13-201.23.14.25","202.34.25.235-222.0.0.1"]
     * </pre>
     */
    public IPAuthenticator(Collection<String> ipWhiteList) {
        if (ipWhiteList != null && ipWhiteList.size() > 0 && !ipWhiteList.contains("*")) {

            for (String ips : ipWhiteList) {
                if (StringTools.isEmpty(ips)) {
                    continue;
                } else if (ipPattern_RANGE.matcher(ips).find()) { // 范围
                    String[] iprange = ips.split("-");
                    String beginIP = iprange[0];
                    String endIP = iprange[1];
                    if (endIP.indexOf(".") == -1) {
                        endIP = beginIP.substring(0, beginIP.lastIndexOf(".") + 1) + endIP;
                    }
                    if (IPTool.strToLong(beginIP) > -1 && IPTool.strToLong(endIP) > -1 && IPTool.strToLong(beginIP) <= IPTool.strToLong(endIP)) {
                        ipList.add(mergeIP(beginIP, endIP));
                    }
                } else if (ips.split("\\.").length == 4) {// 独立IP或者是通配符
                    if (ips.equals("*.*.*.*")) {
                        return;
                    } else if (ips.contains("*")) {
                        String[] ipSegment = ips.split("\\.");
                        String beginIP = "";
                        String endIP = "";
                        if (ipSegment[3].trim().equals("*") && ipSegment[2].trim().equals("*") && ipSegment[1].trim().equals("*")) {
                            beginIP = ipSegment[0].trim() + ".0.0.0";
                            endIP = ipSegment[0].trim() + ".255.255.255";
                        } else if (ipSegment[3].trim().equals("*") && ipSegment[2].trim().equals("*")) {
                            beginIP = ipSegment[0].trim() + "." + ipSegment[1].trim() + ".0.0";
                            endIP = ipSegment[0].trim() + "." + ipSegment[1].trim() + ".255.255";
                        } else if (ipSegment[3].trim().equals("*")) {
                            beginIP = ipSegment[0].trim() + "." + ipSegment[1].trim() + "." + ipSegment[2].trim() + ".0";
                            endIP = ipSegment[0].trim() + "." + ipSegment[1].trim() + "." + ipSegment[2].trim() + ".255";
                        } else {
                            continue;
                        }
                        if (IPTool.strToLong(beginIP) != -1 && IPTool.strToLong(endIP) != -1) {
                            ipList.add(mergeIP(beginIP, endIP));
                        }
                    } else if (IPTool.strToLong(ips) != -1) {
                        ipList.add(mergeIP(ips));
                    }
                }
            }
        }
    }

    /**
     * 查询IP地址是否包含在IP列表中，包含将返回true，其他情况返回false
     *
     * @param ip 客户端传回来的IP字址
     */
    public boolean contains(String ip) {
        for (String ipRangge : ipList) {
            String[] ipRangges = ipRangge.split("-");
            long minip = IPTool.strToLong(ipRangges[0]);
            long maxip = IPTool.strToLong(ipRangges[1]);
            long lip = IPTool.strToLong(ip);
            if (lip >= minip && lip <= maxip) {
                return true;
            }
        }
        return false;
    }

    private String mergeIP(String begin, String end) {
        return begin + "-" + end;
    }

    private String mergeIP(String ip) {
        return mergeIP(ip, ip);
    }

    public static class IPTool {

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
}
