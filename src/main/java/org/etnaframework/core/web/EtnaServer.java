package org.etnaframework.core.web;

import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.server.AbstractNetworkConnector;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.spring.BootstrapModule;
import org.etnaframework.core.spring.SpringContext;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.NetUtils;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.util.StringTools.CharsetEnum;
import org.etnaframework.core.util.SystemInfo;
import org.etnaframework.core.util.ThreadUtils;
import org.etnaframework.core.web.cmd.ShutdownCmd;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

/**
 * HTTP服务的启动入口
 *
 * @author BlackCat
 * @since 2013-5-3
 */
public class EtnaServer {

    private static final Logger log = Log.getLogger();

    /** 预备在etna启动时，http服务初始化完毕后准备绑定端口前，初始化其他挂载的业务模块 */
    static List<BootstrapModule> bootstrapModules = new ArrayList<>();

    /** HTTP服务的监听端口列表 */
    private static List<InetSocketAddress> ports = new ArrayList<>();

    /** HTTPS服务的监听端口列表 */
    private static List<InetSocketAddress> sslPorts = new ArrayList<>();

    /** 所有的需要检查绑定状态的端口列表，包括http/https/挂载启动的其他服务模块 */
    private static List<InetSocketAddress> checkPorts = new ArrayList<>();

    private static String localIP = "";

    private static int localPort;

    private static String localSslIP = "";

    private static int localSslPort;

    private static String localUrl = "";

    private volatile static Server server;

    private static boolean shutdownExistingService;

    /**
     * etna的字符logo，可在http://www.kammerl.de/ascii/AsciiSignature.php生成
     */
    private static String etnaLogo = StringTools.concat("          __\n", "  ____  _/  |_   _____   _____\n", "_/ __ \\ \\   __\\ /  _  \\  \\_ _ \\\n", "\\  ___/_ |  |_  | | |  \\ _/|_| \\_\n",
        " \\___  / | __/  |_| |  / \\____  /\n", "     \\/  |/          \\/       \\/\n\n");

    /**
     * 以嵌入式方式启动服务器
     */
    public synchronized static void start(String jettyConfig, String... args) {
        if (null != server) { // 不允许重复调用启动，重复调用会按无效处理
            return;
        }
        try {
            long now = System.currentTimeMillis();
            // 记录启动PID用于给启动脚本检测
            String sep = System.getProperty("file.separator");
            String pidFile = System.getProperty("java.io.tmpdir");
            if (pidFile.endsWith(sep)) {
                pidFile = pidFile + SystemInfo.COMMAND + ".pid.launch";
            } else {
                pidFile = pidFile + sep + SystemInfo.COMMAND + ".pid.launch";
            }
            FileUtils.writeStringToFile(new File(pidFile), String.valueOf(SystemInfo.PID));
            // 一些第三方组件如slf4j/c3p0等在启动时会检测本机的hostname，如果不能获取的话一般也没问题但会导致启动很慢很慢
            // 可以在日志看到java.net.UnknownHostException的异常，解决办法就是在host里面把本机的hostname指向127.0.0.1就可以了
            // 这里获取一次localHost，如果有问题直接报出来能及时发现解决
            InetAddress.getLocalHost();

            SystemInfo.EMBEDDED_MODE = true;
            // 决定是否采用替代式重启模式，没有参数时也默认替代式，这样本机测试时直接启动即可替换掉之前的进程
            if (args.length == 0 || args[0].equals("force") || args[0].equals("restart")) {
                shutdownExistingService = true;
            }
            echo(); // 输出空行，以便在控制台下启动时能让输出信息对齐
            echo(log, "starting...");
            server = new Server();
            XmlConfiguration cfg = new XmlConfiguration(ClassLoader.getSystemResource(jettyConfig));
            cfg.configure(server);
            ThreadUtils.setDefaultThreadPool(server.getThreadPool()); // 将jetty的线程池供别的业务使用，避免开过多的线程耗费CPU资源
            Set<String> ips = NetUtils.getLocalIPAll();
            for (Connector c : server.getConnectors()) {
                if (c instanceof AbstractNetworkConnector) {
                    int port = ((AbstractNetworkConnector) c).getPort();
                    if (port > 0) {
                        String host = ((AbstractNetworkConnector) c).getHost();
                        host = null == host ? "0.0.0.0" : host;
                        if (!ips.contains(host)) {
                            throw new IllegalArgumentException("host参数设置错误，当前设置的" + host + "不在" + ips + "之中，请检查" + jettyConfig + "配置");
                        }
                        if (c.getProtocols()
                             .toString()
                             .toLowerCase()
                             .contains("ssl")) {
                            InetSocketAddress addr = new InetSocketAddress(host, port);
                            sslPorts.add(addr); // 包含ssl就添加到https监听端口去
                            checkPorts.add(addr);
                            // 添加的优先级是0.0.0.0 > 127.0.0.1 > LanIP
                            if ("0.0.0.0".equals(host)) {
                                if (!"0.0.0.0".equals(localSslIP)) {
                                    localSslIP = host;
                                    localSslPort = port;
                                }
                            } else if ("127.0.0.1".equals(host)) {
                                if (!"0.0.0.0".equals(localSslIP) && !"127.0.0.1".equals(localSslIP)) {
                                    localSslIP = host;
                                    localSslPort = port;
                                }
                            } else {
                                if (localSslIP.isEmpty()) {
                                    localSslIP = host;
                                    localSslPort = port;
                                }
                            }
                        } else {
                            InetSocketAddress addr = new InetSocketAddress(host, port);
                            ports.add(addr);
                            checkPorts.add(addr);
                            // 添加的优先级是0.0.0.0 > 127.0.0.1 > LanIP
                            if ("0.0.0.0".equals(host)) {
                                if (!"0.0.0.0".equals(localIP)) {
                                    localIP = host;
                                    localPort = port;
                                }
                            } else if ("127.0.0.1".equals(host)) {
                                if (!"0.0.0.0".equals(localIP) && !"127.0.0.1".equals(localIP)) {
                                    localIP = host;
                                    localPort = port;
                                }
                            } else {
                                if (localIP.isEmpty()) {
                                    localIP = host;
                                    localPort = port;
                                }
                            }
                        }
                    }
                }
            }
            if (ports.isEmpty()) {
                throw new IllegalArgumentException("监听端口号设置错误，必须要设置1个http监听端口，请检查" + jettyConfig + "配置");
            }
            // 计算本地访问URL
            localUrl = "0.0.0.0".equals(localIP) ? ("http://127.0.0.1:" + localPort) : ("http://" + localIP + ":" + localPort);
            // 启动jetty
            server.start();
            // 需要检查etna是否启动成功了，判断的依据是Spring初始化完成了
            if (!SpringContext.isContextInited()) {
                throw new IllegalArgumentException("etna初始化失败，请检查web.xml和Spring配置");
            }
            String prefix = String.format("[%s]/PID[%s] STARTED (HTTP", SystemInfo.COMMAND_SHORT, SystemInfo.PID);
            StringBuilder mods = new StringBuilder();
            for (BootstrapModule bm : bootstrapModules) {
                mods.append(String.format("%" + prefix.length() + "s@%s\n", bm.getClass()
                                                                              .getSimpleName(), NetUtils.toString(bm.getPorts())));
            }
            String success = (mods.length() == 0 ? "" : mods.toString() + "\n") + prefix + String.format("@%s) [%sMS]", NetUtils.toString(ports) + (!sslPorts.isEmpty() ? " / HTTPS@" + NetUtils.toString(sslPorts) : ""),
                (System.currentTimeMillis() - now));
            for (String line : success.split("\n")) {
                log.info(line);
            }
            echo();
            echo(etnaLogo + success);
            echo();
            // 启动成功，记录启动PID用于给启动脚本检测
            FileUtils.writeStringToFile(new File(System.getProperty("java.io.tmpdir") + System.getProperty("file.separator") + SystemInfo.COMMAND + ".pid.started"), String.valueOf(SystemInfo.PID));
        } catch (Throwable e) {
            printStackTrace(e);
            // 在IntelliJ IDEA里面启动需要设置一下工作目录，否则无法找到web.xml
            if (WebInfConfiguration.class.getName()
                                         .equals(e.getStackTrace()[0].getClassName())) {
                echo("============================================================================================");
                echo("如果你是在IntelliJ IDEA运行程序，需设置正确的工作目录才能加载到配置文件");
                echo("请确保 Run -> Edit Configurations -> 右侧Configuration -> Working Directory值为$MODULE_DIR$");
                echo("============================================================================================");
            }
            String fail = String.format("**** FAILED, PROGRESS KILLED!! **** (HTTP@%s)", NetUtils.toString(ports) + (!sslPorts.isEmpty() ? " / HTTPS@" + NetUtils.toString(sslPorts) : ""));
            echo();
            echo(log, fail);
            echo();
            System.exit(-1);
        }
    }

    /**
     * 获得http服务启动绑定的端口号，如果有多个端口只取第1个，etna确保这个端口是自己访问自己肯定能访问到，请确保操作系统防火墙不拦截即可，必须要etna初始化之后才能获取到
     */
    public static int getPort() {
        return localPort;
    }

    /**
     * 获得http服务启动绑定的端口号（https），如果有多个端口只取第1个，etna确保这个端口是自己访问自己肯定能访问到，请确保操作系统防火墙不拦截即可，必须要etna初始化之后才能获取到
     */
    public static int getSslPort() {
        return localSslPort;
    }

    /**
     * 获得http服务启动后本地访问的URl，默认为http://127.0.0.1:[port]（系统确保这个URL是自己访问自己肯定能访问到，请确保操作系统防火墙不拦截即可）
     */
    public static String getLocalUrl() {
        return localUrl;
    }

    /**
     * 如果有挂载的其他服务，即实现了{@link BootstrapModule}的spring托管类（记得别忘记加@{@link Service}注解或在xml里面配置），准备相关资源
     */
    static void initBootstrapModules() {
        bootstrapModules = SpringContext.getBeansOfTypeAsList(BootstrapModule.class);
        if (!bootstrapModules.isEmpty()) {
            // 启动顺序按类全名的字典序排列进行
            Collections.sort(bootstrapModules, new Comparator<BootstrapModule>() {

                @Override
                public int compare(BootstrapModule o1, BootstrapModule o2) {
                    String n1 = o1.getClass()
                                  .getName();
                    String n2 = o2.getClass()
                                  .getName();
                    return n1.compareTo(n2);
                }
            });
            // 如果同时挂载了RPC服务和thrift服务，两个服务都想使用12306端口，这样肯定是无法启动的
            // 为了防止出现这种情况，这里把所有挂载服务需要使用的端口都放到inUse进行去重判断
            Map<InetSocketAddress, String> inUse = new LinkedHashMap<>();
            // checkPorts里面已经有HTTP/HTTPS服务的端口了，先加上
            for (InetSocketAddress ex : checkPorts) {
                inUse.put(ex, "HTTP/HTTPS");
            }
            for (BootstrapModule bm : bootstrapModules) {
                for (InetSocketAddress addr : bm.getPorts()) {
                    // 端口号必须>0需要检查是否有非法数据
                    if (addr.getPort() <= 0) {
                        throw new IllegalArgumentException(bm.getClass()
                                                             .getSimpleName() + "模块中指定的端口号" + addr.getPort() + "非法，请检查配置信息");
                    }
                    for (InetSocketAddress ex : inUse.keySet()) {
                        // 全局IP占用或者相同IP占用，都可以认为端口出现重复
                        if ((("0.0.0.0".equals(NetUtils.getIP(ex)) || "0.0.0.0".equals(NetUtils.getIP(addr))) && ex.getPort() == addr.getPort()) || ex.equals(addr)) {
                            throw new IllegalArgumentException(bm.getClass()
                                                                 .getSimpleName() + "不能使用端口" + NetUtils.toString(addr) + "，该端口已被本系统" + inUse.get(ex) + "服务使用，请修改配置");
                        }
                    }
                    inUse.put(addr, bm.getClass()
                                      .getName());
                }
            }
            checkPorts = new ArrayList<>(inUse.keySet());
        }
    }

    /**
     * 当以嵌入方式启动服务器时，可通过此方法关闭在相同端口已有的服务
     */
    static void shutdownWhenNecessary() {
        if (shutdownExistingService) {
            long timeout = Datetime.MILLIS_PER_SECOND * 30; // 设定超时毫秒数，如果超过这个时间还未能绑定端口就启动失败
            long start = System.currentTimeMillis();
            long lastSentTime = 0;
            // 目标端口必须是占用了才能发送关闭命令，否则起不到任何作用
            if (NetUtils.isSocketPortOccupied(getPort())) {
                while (NetUtils.isSocketPortOccupied(checkPorts)) {
                    if (System.currentTimeMillis() - start > timeout) {
                        break;
                    }
                    if (System.currentTimeMillis() - lastSentTime > Datetime.MILLIS_PER_SECOND) { // 防止关闭不了时被消息刷屏
                        echo(log, "send" + ShutdownCmd.class.getSimpleName(), "HTTP@[" + getPort() + "]");
                        lastSentTime = System.currentTimeMillis();
                    }
                    try {
                        String url = getLocalUrl() + ShutdownCmd.CMD_PATH + "?token=" + SystemInfo.COMMAND;
                        HttpURLConnection httpUrlConnection = (HttpURLConnection) new URL(url).openConnection();
                        InputStreamReader isr = new InputStreamReader(httpUrlConnection.getInputStream(), CharsetEnum.UTF_8);
                        while (isr.read() != -1) {
                        }
                        Thread.sleep(100);
                    } catch (Exception ex) {
                    }
                }
            }
        }
        if (NetUtils.isSocketPortOccupied(checkPorts)) {
            // 找出具体是哪一个端口被占用了
            for (InetSocketAddress port : checkPorts) {
                if (NetUtils.isSocketPortOccupied(port)) {
                    throw new RuntimeException("PORT " + NetUtils.toString(port) + " IS OCCUPIED, FAILED TO BIND !!");
                }
            }
        }
    }

    /**
     * 如果有挂载的其他服务，即实现了{@link BootstrapModule}的spring托管类（记得别忘记加@{@link Service}注解或在xml里面配置），这里执行绑定端口的动作
     */
    static void bindBootstrapModules() throws Throwable {
        for (BootstrapModule bm : bootstrapModules) {
            echo(log, "bind" + bm.getClass()
                                 .getSimpleName(), "PORT@" + NetUtils.toString(bm.getPorts()));
            bm.bind();
        }
    }

    /**
     * 在控制台输出一些内容
     */
    public static void echo() {
        synchronized (System.out) { // 占用，减少out和err流内容混合到一起的情况，尽可能保持控制台输出有序状态（线上日志不会通过控制台输出，此仅为本地测试方便而做的）
            System.err.println();
            ThreadUtils.sleep(1);
        }
    }

    /**
     * 在控制台输出一些内容
     */
    public static void echo(CharSequence msg) {
        synchronized (System.out) { // 占用，减少out和err流内容混合到一起的情况，尽可能保持控制台输出有序状态（线上日志不会通过控制台输出，此仅为本地测试方便而做的）
            System.err.println(msg);
            ThreadUtils.sleep(1);
        }
    }

    /**
     * 输出错误信息
     */
    public static void printStackTrace(Throwable ex) {
        echo(StringTools.printThrowable(ex));
    }

    /**
     * 输出状态，同时输出到日志
     */
    public static void echo(Logger logger, String title) {
        String msg = StringTools.format("[%s] %s", SystemInfo.COMMAND_SHORT, title);
        synchronized (System.out) {
            logger.info(msg);
            echo(msg);
        }
    }

    /**
     * 输出进度状态信息
     */
    public static void echo(Logger logger, String title, String status) {
        String msg = StringTools.format("%-50s%s", "[" + SystemInfo.COMMAND_SHORT + "] " + title, status);
//        synchronized (System.out) {
            logger.info(msg);
            echo(msg);
//        }
    }
}
