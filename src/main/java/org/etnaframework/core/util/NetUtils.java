package org.etnaframework.core.util;

import java.lang.management.ManagementFactory;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import org.etnaframework.core.util.SystemInfo.OsEnum;

/**
 * 本类主要提供网络状态相关的方法
 *
 * @author BlackCat
 * @since 2015-01-04
 */
public class NetUtils {

    private static Set<String> localIPSet;

    private static Set<String> localIPWith127001Set;

    private static Set<String> localIPAllSet;

    private static String localSampleIP;

    /**
     * 获得inetSocketAddress对应的IP地址
     */
    public static String getIP(InetSocketAddress inetSocketAddress) {
        if (inetSocketAddress != null) {
            InetAddress addr = inetSocketAddress.getAddress();
            if (addr != null) {
                return addr.getHostAddress();
            }
        }
        return "";
    }

    /**
     * 获得inetSocketAddress对应的host和port，如果是0.0.0.0的就不展示host
     */
    public static String toString(InetSocketAddress inetSocketAddress) {
        if (inetSocketAddress != null) {
            String host = getIP(inetSocketAddress);
            if (StringTools.isNotEmpty(host) && "0.0.0.0".equals(host)) {
                return "" + inetSocketAddress.getPort();
            }
            return host + ":" + inetSocketAddress.getPort();
        }
        return "";
    }

    /**
     * 获得inetSocketAddress对应的host和port，如果是0.0.0.0的就不展示host
     */
    public static String toString(Collection<InetSocketAddress> list) {
        Iterator<InetSocketAddress> it = list.iterator();
        if (!it.hasNext()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (; ; ) {
            InetSocketAddress e = it.next();
            sb.append(toString(e));
            if (!it.hasNext()) {
                return sb.append(']').toString();
            }
            sb.append(',').append(' ');
        }
    }

    /**
     * 获取本机所有的IP，包含127.0.0.1但不包含0.0.0.0
     */
    public static Set<String> getLocalIPWith127001() {
        if (null == localIPWith127001Set) {
            synchronized (NetUtils.class) {
                if (null == localIPWith127001Set) {
                    Set<String> localIPSetTmp = new LinkedHashSet<String>(3);
                    try {
                        Enumeration<?> e1 = NetworkInterface.getNetworkInterfaces();
                        while (e1.hasMoreElements()) {
                            NetworkInterface ni = (NetworkInterface) e1.nextElement();

                            Enumeration<?> e2 = ni.getInetAddresses();
                            while (e2.hasMoreElements()) {
                                InetAddress ia = (InetAddress) e2.nextElement();
                                if (ia instanceof Inet6Address) {
                                    continue;
                                }
                                String ip = ia.getHostAddress();
                                localIPSetTmp.add(ip);
                            }
                        }
                    } catch (SocketException e) {
                        // log.error("", e);//因为logback在初始化时就要用到此方法,所以不能使用
                        e.printStackTrace();
                    }
                    localIPWith127001Set = localIPSetTmp;
                }
            }
        }
        return localIPWith127001Set;
    }

    /**
     * 获取本机所有的IP，包含0.0.0.0/127.0.0.1
     */
    public static Set<String> getLocalIPAll() {
        if (null == localIPAllSet) {
            synchronized (NetUtils.class) {
                if (null == localIPAllSet) {
                    Set<String> localIPSetTmp = new LinkedHashSet<String>(3);
                    localIPSetTmp.add("0.0.0.0");
                    try {
                        Enumeration<?> e1 = NetworkInterface.getNetworkInterfaces();
                        while (e1.hasMoreElements()) {
                            NetworkInterface ni = (NetworkInterface) e1.nextElement();

                            Enumeration<?> e2 = ni.getInetAddresses();
                            while (e2.hasMoreElements()) {
                                InetAddress ia = (InetAddress) e2.nextElement();
                                if (ia instanceof Inet6Address) {
                                    continue;
                                }
                                String ip = ia.getHostAddress();
                                localIPSetTmp.add(ip);
                            }
                        }
                    } catch (SocketException e) {
                        // log.error("", e);//因为logback在初始化时就要用到此方法,所以不能使用
                        e.printStackTrace();
                    }
                    localIPAllSet = localIPSetTmp;
                }
            }
        }
        return localIPAllSet;
    }

    /**
     * 获得本地IP（除去127.0.0.1之外的IP）
     */
    public static Set<String> getLocalIP() {
        if (null == localIPSet) {
            synchronized (NetUtils.class) {
                if (null == localIPSet) {
                    Set<String> localIPSetTmp = new LinkedHashSet<String>(3);
                    localIPSetTmp.addAll(getLocalIPWith127001());
                    localIPSetTmp.remove("127.0.0.1");
                    localIPSet = localIPSetTmp;
                }
            }
        }
        return localIPSet;
    }

    /**
     * 获得本地特征IP（会排除0.0.0.0和127.0.0.1）
     */
    public static String getLocalSampleIP() {
        if (null == localSampleIP) {
            synchronized (NetUtils.class) {
                if (null == localSampleIP) {
                    Set<String> set = getLocalIP();
                    localSampleIP = CollectionTools.isEmpty(set) ? "N/A" : set.iterator().next();
                }
            }
        }
        return localSampleIP;
    }

    /**
     * 通过domainName获得IP地址
     */
    public static Set<String> getIPByDomainName(String domainName) {
        Set<String> domainIPSet = new LinkedHashSet<String>(2);
        try {
            InetAddress[] inets = InetAddress.getAllByName(domainName);
            for (InetAddress inetAddress : inets) {
                domainIPSet.add(inetAddress.getHostAddress());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return domainIPSet;
    }

    /**
     * 简单校验端口是否被占用
     */
    public static boolean isSocketPortOccupied(int... ports) {
        for (int port : ports) {
            if (isSocketPortOccupied(port)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 简单校验端口是否被占用
     */
    public static boolean isSocketPortOccupied(int port) {
        if (port > 0) {
            ServerSocket s = null;
            try {
                s = new ServerSocket(port);
            } catch (Exception ex) {
                String es = String.valueOf(ex.getMessage());
                // mac系统默认不允许非root使用1024以下端口，如果是没有权限，需要报出来
                if (es.contains("Permission denied")) {
                    String msg = "检查端口" + port + "的占用状态失败，没有权限";
                    String os = ManagementFactory.getOperatingSystemMXBean().getName();
                    if (null != os && os.contains("Mac OS") && port < 1024) {
                        msg += "(Mac OS不允许非root用户使用1024以下的端口)";
                    }
                    throw new IllegalStateException(msg, ex);
                }
                return true;
            } finally {
                CloseUtils.closeSilently(s);
            }
        }
        return false;
    }

    /**
     * 校验指定IP的端口是否被占用
     */
    public static boolean isSocketPortOccupied(Collection<InetSocketAddress> ports) {
        for (InetSocketAddress port : ports) {
            if (isSocketPortOccupied(port)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验指定IP的端口是否被占用
     */
    public static boolean isSocketPortOccupied(InetSocketAddress... ports) {
        for (InetSocketAddress port : ports) {
            if (isSocketPortOccupied(port)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验指定IP的端口是否被占用，注意只能填本机有的IP，否则会报错
     */
    public static boolean isSocketPortOccupied(String ip, int port) {
        return isSocketPortOccupied(new InetSocketAddress(ip, port));
    }

    /**
     * 校验指定IP的端口是否被占用，注意只能填本机有的IP，否则会报错
     */
    public static boolean isSocketPortOccupied(InetSocketAddress addr) {
        if (!getLocalIPAll().contains(addr.getHostString())) {
            throw new IllegalArgumentException(addr.getHostString() + " IS NOT LOCAL IP !");
        }
        ServerSocket s = null;
        try {
            s = new ServerSocket();
            s.bind(addr);
        } catch (Exception ex) {
            String es = String.valueOf(ex.getMessage());
            // mac系统默认不允许非root使用1024以下端口，如果是没有权限，需要报出来
            if (es.contains("Permission denied")) {
                String msg = "检查" + addr + "的占用状态失败，没有权限";
                if (OsEnum.MAC_OS.equals(SystemInfo.OS) && addr.getPort() < 1024) {
                    msg += "(Mac OS不允许非root用户使用小于1024的端口)";
                }
                throw new IllegalStateException(msg, ex);
            }
            return true;
        } finally {
            CloseUtils.closeSilently(s);
        }
        return false;
    }
}
