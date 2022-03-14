package org.etnaframework.core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.StringTokenizer;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.util.StringTools.CharsetEnum;
import org.slf4j.Logger;

/**
 * 命令服务类，主要完成命令的执行、监控、中断
 *
 * @author BlackCat
 * @since 2015-03-27
 */
public class CommandService {

    private final static Logger log = Log.getLogger();

    /** 当前操作系统的类型 */
    private static final int OS_TYPE = getOsType();

    /** 无法分配内存时的处理对象 */
    public static CantAllocateMemErrorHandler cantAllocateMemErrorHandler;

    /** cmd处理模式,默认使用兼容方式 */
    protected CmdMode cmdMode = CmdMode.compatible;

    /** cmd监控 */
    protected CmdMonitor cmdMonitor;

    /** 要真正执行的命令序列 */
    protected String[] commandArray;

    /** 要真正执行的命令序列 的 字符串表示 */
    protected String commandArrayStr;

    /** 命令生成时间 的字符串表示 */
    protected String createTime = DatetimeUtils.now().toString() + " ";

    /** 当前正在刷新的行(会被下一行覆盖) */
    protected String flushingLine = "";

    /** 当前执行的行是否要被重刷(被下一行回车覆盖) */
    protected boolean flushingProcessingLing;

    /** 是否执行完 */
    protected boolean processComplete;

    /** 执行中具体结果 */
    protected StringBuilder processingDetail = new StringBuilder();

    /** 执行中当前获取的行 */
    protected String processingLine;

    /** 当前执行的行数，获取一行时其自动累加 */
    protected int processingLineIndex = 0;

    /** 执行结果 */
    protected boolean success = false;

    /** 读入的命令行缓冲 */
    private StringBuilder _readLineBuffer = new StringBuilder();

    private int _readLineRemainChar = -1;

    /**
     * 是否使用linux的bin/sh来执行当前命令 其作用主要是为了解决: Java具有使用Runtime.exec对本地程序调用进行重定向的能力，但是用重定向或者管道进行命令调用将会出错的问题 注意:在Java中调用本地程序会破坏平台独立性规则 搜索关键词:使用Runtime.exec重定向本地程序调用
     * http://hi.baidu.com/javaroad/blog/item/a56d74e7ce7fba28b8382053.html
     */
    private boolean useShell = false;

    /**
     * 生成[/bin/sh,-c,cmd字符串]方式执行命令
     */
    public CommandService(String commandString) {
        this(commandString, true);
    }

    /**
     * <pre>
     * 传入cmd字符串
     * 1.userShell = true,则会在linux系统中生成[/bin/sh,-c,cmd字符串]
     * 2.userShell = false,则会分析字元
     * </pre>
     *
     * @param commandString 命令序列的字符串表示
     * @param useShell 是否使用Shell
     */
    public CommandService(String commandString, boolean useShell) {
        this.useShell = useShell;
        if (this.useShell) {
            this.commandArray = buildOsSpcArgs(commandString);
        } else {
            this.commandArray = buildArgs(commandString);
        }
        init();
    }

    /**
     * 直接传入cmd[]
     */
    public CommandService(String[] command) {
        this.commandArray = command;
        this.useShell = false;
        init();
    }

    /**
     * 获得当前操作系统的编号，0为linux，1为windows, 2为Mac OS X
     */
    private static int getOsType() {
        String osName = System.getProperty("os.name");
        if (osName.startsWith("Linux")) {
            return 0;
        } else if (osName.startsWith("Windows")) {
            return 1;// 不处理老掉牙的win98,win95
        } else if (osName.startsWith("Mac")) {
            return 2;
        }
        return -1;
    }

    /**
     * 使用ssh命令远程调用remote机器命令
     *
     * @param remoteHost 远程计算机名或者IP
     * @param ori_cmd 原始命令
     */
    public static CommandService ssh(String remoteHost, String ori_cmd) {
        String cmd = getSshCmd(remoteHost, ori_cmd);
        return new CommandSsh(cmd);
    }

    /**
     * 根据远程计算机和原始命令来获得ssh命令
     *
     * @param remoteHost 远程计算机
     * @param ori_cmd 原始命令
     *
     * @return ssh命令
     */
    public static String getSshCmd(String remoteHost, String ori_cmd) {
        return new StringBuilder(5 + ori_cmd.length()).append("ssh ").append(remoteHost).append(" '").append(ori_cmd).append("'").toString();
    }

    /**
     * 将字符串命令转换为数组格式的命令
     *
     * @param commandString 要转换的字符串命令
     */
    private String[] buildArgs(String commandString) {
        StringTokenizer st = new StringTokenizer(commandString);
        String[] cmdarray = new String[st.countTokens()];
        for (int i = 0; st.hasMoreTokens(); i++) {
            cmdarray[i] = st.nextToken();
        }
        return cmdarray;
    }

    /**
     * 根据操作系统的不同将字符串命令转换为数组格式的命令
     *
     * @param commandString 要转换的字符串命令
     */
    private String[] buildOsSpcArgs(String commandString) {
        switch (OS_TYPE) {
        case 0:
            String[] tmp = {
                "/bin/sh",
                "-c",
                commandString
            };
            return tmp;
        case 1:
            String[] tmp1 = {
                "cmd.exe",
                "/c",
                commandString
            };
            return tmp1;
        case 2:
            String[] tmp2 = {
                "/bin/sh",
                "-c",
                commandString
            };
            return tmp2;
        }

        return null;
    }

    /**
     * 检查当前命令是否要 准备状态,而非已经执行完
     */
    public void checkIsPreparing() {
        if (processComplete) {
            throw new IllegalStateException("cmd is already completed:" + commandArrayStr);
        }
    }

    /**
     * 获得相同命令的新vo
     */
    public CommandService copy() {
        CommandService cmd = new CommandService(this.commandArray);
        cmd.useShell = this.useShell;
        return cmd;
    }

    /**
     * 执行当前命令
     */
    public CommandService execute() {
        // 如果是windows，默认使用GBK编码，其它默认使用UTF-8
        return execute(OS_TYPE == 1 ? "GBK" : "UTF-8");
    }

    public CommandService execute(String charset) {
        return execute(charset, log);
    }

    /**
     * 执行当前命令
     *
     * @param charset 命令的编码格式
     */
    public synchronized CommandService execute(String charset, Logger logger) {
        checkIsPreparing();
        try {
            Charset ch = CharsetEnum.UTF_8;
            if (StringTools.isNotEmpty(charset)) {
                ch = Charset.forName(charset);
            }
            logger.info("RUN CMD:{} ({})", commandArrayStr, cmdMode);
            Process process = new ProcessBuilder(commandArray).redirectErrorStream(true).start();
            BufferedReader reader = null;
            try {
                if (cmdMode != CmdMode.ignore) {
                    InputStreamReader innerIs = new InputStreamReader(process.getInputStream(), ch);

                    if (cmdMode == CmdMode.simple) {
                        LineNumberReader lr = new LineNumberReader(innerIs);
                        reader = lr;
                        cmdMonitor = new CmdMonitor(process, reader);

                        while ((processingLine = readLineSimple(lr)) != null) {
                            cmdMonitor.updateLastProcessTime();
                            processLine();
                            if (!flushingProcessingLing) {
                                processingLineIndex++;
                                // lineReader.getLineNumber()
                                // 测试发现lineReader.getLineNumber()其实就是这里要标志的processingLineIndex
                            }
                        }
                    } else {
                        reader = new BufferedReader(innerIs);
                        cmdMonitor = new CmdMonitor(process, reader);
                        // 在windows平台上，运行被调用程序的DOS窗口在程序执行完毕后往往并不会自动关闭，从而导致Java应用程序阻塞在waitfor()。
                        // 导致该现象的一个可能的原因是，该可执行程序的标准输出比较多，而运行窗口的标准输出缓冲区不够大。
                        // 解决的办法是，利用Java提供的Process类提供的方法让Java虚拟机截获被调用程序的DOS运行窗口的标准输出，
                        // 在waitfor()命令之前读出窗口的标准输出缓冲区中的内容。
                        while ((processingLine = readLineCompatible(reader)) != null) {
                            cmdMonitor.updateLastProcessTime();
                            processLine();
                            if (!flushingProcessingLing) {
                                processingLineIndex++;
                            }
                        }
                    }
                }

                int existCode = process.waitFor();// 这里一直阻塞等待结果

                if (existCode == 0) {
                    success = true;
                    logger.info("RUN CMD OK:{}", commandArrayStr);
                } else {
                    success = false;
                    logger.error("RUN CMD ERR:{},CODE:{}", commandArrayStr, existCode);
                }
            } catch (Exception e) {
                if (isInterrupt()) {
                    cmdMonitor.interuptedMsg = "[INTERRUPTED(" + e.getClass().getSimpleName() + ")]";
                    logger.error("RUN CMD {}:{}", new Object[] {
                        cmdMonitor,
                        commandArrayStr,
                        e
                    });
                } else {
                    logger.error("RUN CMD EXCEPTION:{}", commandArrayStr, e);
                }
            } finally {
                processComplete = true;
                if (process != null) {
                    try {
                        if (reader != null) {
                            reader.close();
                        }
                        process.getInputStream().close();
                    } catch (Exception e2) {
                    }
                    process.destroy();
                }
            }
        } catch (IOException e) {
            logger.error("RUN CMD ERR:{}", commandArrayStr, e);
            String error = e.toString().toLowerCase();
            if (cantAllocateMemErrorHandler != null && error.contains("cannot allocate memory")) {
                logger.error("\n====Waiting to resolve the fatal Exception====\n");
                cantAllocateMemErrorHandler.handleCantAllocateMemError(e);
            }
        }
        return this;
    }

    /**
     * 获得命令模式
     */
    public CmdMode getCmdMode() {
        return cmdMode;
    }

    /**
     * 设置命令模式
     */
    public void setCmdMode(CmdMode cmdMode) {
        checkIsPreparing();
        this.cmdMode = cmdMode;
    }

    /**
     * 获得命令序列的字符串表示
     */
    public String getCommandArrayStr() {
        return commandArrayStr;
    }

    /**
     * 获得执行中的结果
     */
    public StringBuilder getProcessingDetail() {
        return processingDetail;
    }

    /**
     * 初始化，将命令的数组格式转化为字符串格式，并设置success为false
     */
    private void init() {
        commandArrayStr = Arrays.toString(commandArray);
        success = false;
    }

    /**
     * 中断当前命令
     */
    public void interrupt(String interuptMsg) {
        if (cmdMode == CmdMode.ignore) {
            throw new IllegalAccessError("cant interrupt cmd with [ignore] CmdMode:" + commandArrayStr);
        }
        if (cmdMonitor == null) {
            throw new IllegalStateException("cmd isnot started:" + commandArrayStr);
        }
        cmdMonitor.interrupt(interuptMsg);
    }

    /**
     * 当前命令是否被中断
     */
    public boolean isInterrupt() {
        if (cmdMonitor == null) {
            return false;
        }
        return cmdMonitor.interrupt;
    }

    /**
     * 当前命令是否处理完成
     */
    public boolean isProcessComplete() {
        return processComplete;
    }

    /**
     * 当前命令是否执行成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 监控当前命令是否超过了指定的时间
     *
     * @param tolerantSec 指定的中断时间
     */
    public boolean monitor(int tolerantSec) {
        if (cmdMonitor == null) {
            return false;
        }
        return cmdMonitor.monitor(tolerantSec);
    }

    protected void processLine() {
    }

    /**
     * 可以鉴定当前处理行是否是 只以 /r结尾的(也就是 当前行会被下一行覆盖)
     *
     * @param br 命令行
     */
    private String readLineCompatible(BufferedReader br) throws IOException {
        _readLineBuffer.delete(0, _readLineBuffer.length());
        int readingChar = -1;
        int lastChar = -1;
        if (_readLineRemainChar != -1) {
            lastChar = _readLineRemainChar;
            if (_readLineRemainChar != 13 && _readLineRemainChar != 10) {
                _readLineBuffer.append((char) _readLineRemainChar);
            }
        }
        flushingProcessingLing = false;
        while ((readingChar = br.read()) != -1) {
            char c = (char) readingChar;
            if (readingChar == 10) {// \n
                if (lastChar == 13) { // \r\n
                    _readLineRemainChar = -1;
                    break;
                } else if (lastChar == 10) { // \n\n
                    _readLineRemainChar = readingChar;
                    break;
                } else {
                    lastChar = readingChar;
                    continue;
                }
            } else if (readingChar == 13) {// \r
                if (lastChar == 10) {// \n\r
                    _readLineRemainChar = -1;
                    break;
                }
                // \r\r 或 A\r
                lastChar = readingChar;
                continue;
            } else {
                if (lastChar == 10) {// 当A遇到/n
                    _readLineRemainChar = readingChar;
                    break;
                } else if (lastChar == 13) {// 当A遇到/r,说明当前行是要被刷新覆盖的
                    _readLineRemainChar = readingChar;
                    flushingProcessingLing = true;
                    break;
                } else {
                    _readLineBuffer.append(c);
                    continue;
                }
            }
        }
        if (readingChar == -1) {
            if (_readLineBuffer.length() == 0) {
                return null;
            }
            _readLineRemainChar = -1;
        }
        String result = _readLineBuffer.toString();
        if (!flushingProcessingLing) {
            processingDetail.append(result).append('\n');
            flushingLine = "";
        } else {
            flushingLine = result;
        }
        return result;
    }

    /**
     * 简单地处理当前输出行
     *
     * @param lineReader 行数据读取器
     */
    private String readLineSimple(LineNumberReader lineReader) throws IOException {
        processingLine = lineReader.readLine();
        processingDetail.append(processingLine).append('\n');
        return processingLine;
    }

    /**
     * 默认打印所有信息
     */
    @Override
    public String toString() {
        return toString(0);
    }

    /**
     * 获得打印信息
     *
     * @param type 0-打印所有,1-只打印命令以及最后一行,2-只打印命令
     *
     * @return 要打印的信息
     */
    public String toString(int type) {
        StringBuilder tmp = new StringBuilder();
        if (!isProcessComplete()) {
            long span = cmdMonitor == null ? 0 : System.currentTimeMillis() - cmdMonitor.lastProcessTime;// 距离上次接收到信息的时间
            tmp.append(">>>(").append(span).append("MS)");
        }
        tmp.append(createTime);
        tmp.append(commandArrayStr);
        tmp.append('\n');
        if (type == 0) {
            tmp.append(processingDetail);
            tmp.append(flushingLine).append('\n');
        } else if (type == 1) {
            if (processingLine != null) {
                tmp.append(processingLine).append('\n');
            }
        }
        if (cmdMonitor != null) {
            tmp.append(cmdMonitor);
        }
        return tmp.toString();
    }

    /**
     * 处理执行结果的模式
     */
    protected enum CmdMode {
        /** 使用更兼容的方式处理执行结果：可以处理刷新当前行的情况 */
        compatible,
        /** 不处理执行结果 */
        ignore,
        /** 使用简单方式处理执行结果 */
        simple;
    }

    /**
     * 不能分配内存错误处理接口
     */
    public static interface CantAllocateMemErrorHandler {

        /**
         * 处理异常的方法
         */
        public void handleCantAllocateMemError(IOException e);
    }

    /**
     * SSH命令
     */
    public static class CommandSsh extends CommandService {

        private boolean first = true;

        /**
         * 构造方法
         *
         * @param commandString 命令字符串
         */
        public CommandSsh(String commandString) {
            super(commandString);
        }

        /**
         * 构造方法
         *
         * @param commandString 命令字符串
         * @param useShell 是否使用Shell
         */
        public CommandSsh(String commandString, boolean useShell) {
            super(commandString, useShell);
        }

        /**
         * 构造方法
         *
         * @param command 命令数组
         */
        public CommandSsh(String[] command) {
            super(command);
        }

        /**
         * 检查是否存在信任关系
         */
        protected void checkIsSshTrust() {
            if (first && processingLine.endsWith("password:")) {
                throw new RuntimeException(processingLine);
            }
            first = false;
        }

        /**
         * 处理一行
         */
        @Override
        protected void processLine() {
            checkIsSshTrust();
        }
    }

    /**
     * 用于监控 通过判断 当前执行命令是否还有输出流,来决定是否中断这个命令
     */
    protected class CmdMonitor {

        /** 上次获得输出流时间 */
        protected long lastProcessTime;

        /** 上次获得输出流时间 */
        protected String lastProcessTimeStr;

        /** 是否已提交中断 */
        private boolean interrupt;

        /** 命令被中断时提交的信息 */
        private String interuptedMsg = "";

        /** 监控时提交中断的信息 */
        private String interuptMsg = "";

        /** 进程描述对象 */
        private Process process;

        /** 执行execute()方法使用的线程 */
        @SuppressWarnings("unused")
        private Thread processThread;

        @SuppressWarnings("unused")
        private BufferedReader reader;

        /**
         * 监控器构造方法
         *
         * @param process 输出流
         */
        public CmdMonitor(Process process, BufferedReader reader) {
            this.process = process;
            this.processThread = Thread.currentThread();
            lastProcessTime = System.currentTimeMillis();
            this.reader = reader;
            lastProcessTimeStr = now();
        }

        /**
         * 执行中断，中断信息为msg
         *
         * @param msg 中断信息
         */
        public void interrupt(String msg) {
            interrupt = true;
            this.interuptMsg = MessageFormat.format("EXECUTE {0} - currentTime:{1},lastProcessTime:{2}\n", msg, now(), lastProcessTimeStr);
            try {
                process.getInputStream().close();
            } catch (IOException e) {
                log.error("try to close reader encount exception", e);
            }
            try {
                process.destroy();
            } catch (Exception e) {
                log.error("try to destory process encount exception", e);
            }
        }

        /**
         * 监控当前命令是否超过了指定的时间，超过后就中断
         *
         * @param tolerantSec 指定的时间
         */
        private boolean monitor(int tolerantSec) {
            if (System.currentTimeMillis() - lastProcessTime > tolerantSec * 1000) {
                interrupt("TIMEOUT(>" + tolerantSec + ")");
                return true;
            }
            return false;
        }

        /**
         * 获得当前时间
         */
        public String now() {
            return DatetimeUtils.now().toString();
        }

        /**
         * 监控器描述
         */
        @Override
        public String toString() {
            return interuptedMsg + interuptMsg;
        }

        /**
         * 更新上次获得输出流的时间
         */
        public void updateLastProcessTime() {
            lastProcessTime = System.currentTimeMillis();
            lastProcessTimeStr = now();
        }
    }
}
