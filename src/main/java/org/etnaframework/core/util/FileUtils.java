package org.etnaframework.core.util;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.etnaframework.core.logging.Log;
import org.slf4j.Logger;
import com.google.common.base.Joiner;

/**
 * 文件操作类
 *
 * <pre>
 * 注意jdk7即将有的方法： FileSystem 提供了许多方法来获得当前文件系统的相关信息。 Path 处理路径(文件和目录)，包括 创建path，Paths.get(String s) 获得path的详细信息 getName(),getXX()… 删除path的冗余信息 toRealPath 转换path toAbsolutePath() 合并两个path resolve()
 * 在两个path之间创建相对路径 relativeze() 比较路径 equal() startsWith(),endWith() Files 支持各种文件操作，包括 移动文件， 复制文件， 删除文件， 更详细的文件属性，包括文件权限，创建者，修改时间…… Walking the File Tree(递归遍历文件树) Watch a Directory for Change (监听文件更改)
 *
 * @author ZengDong
 * @since 2011-8-9 下午09:02:55
 */
public final class FileUtils {

    private static final Logger log = Log.getLogger();

    private static final int MEGABYTE = 1024 * 1024;

    private static final String OS_VERSION = System.getProperty("os.version").toLowerCase();

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();

    /** The path segment indicating the current folder itself. */
    public static final String PATH_SEGMENT_CURRENT = ".";

    /** The path segment indicating the parent folder. */
    public static final String PATH_SEGMENT_PARENT = "..";

    /**
     * The key of the {@link System#getProperty(String) system property} <code>{@value}</code>. It contains the home directory of the user that started this JVM.<br>
     * Examples are <code>/home/mylogin</code> or <code>C:\Windows\Profiles\mylogin</code>.
     */
    public static final String PROPERTY_USER_HOME = "user.home";

    /**
     * The key of the {@link System#getProperty(String) system property} <code>{@value}</code>. It contains the directory to use for temporary files.<br>
     * Examples are <code>/tmp</code>, <code>C:\Temp</code> or <code>/usr/local/tomcat/temp</code>.
     */
    public static final String PROPERTY_TMP_DIR = "java.io.tmpdir";

    /** An empty file array. */
    public static final File[] NO_FILES = new File[0];

    public static final boolean isWindows = OS_NAME.startsWith("windows");

    public static final boolean isOS2 = OS_NAME.startsWith("os/2") || OS_NAME.startsWith("os2");

    public static final boolean isMac = OS_NAME.startsWith("mac");

    public static final boolean isLinux = OS_NAME.startsWith("linux");

    public static final boolean isFileSystemCaseSensitive = !isWindows && !isOS2 && !isMac;

    public static final boolean FILE_CHANNEL_TRANSFER_BROKEN = isLinux && OS_VERSION.startsWith("2.6");

    public static FileFilter defaultCopyFromFileAcceptFilter = null;

    public static FileFilter defaultCopyToFileExistAcceptFilter = null;

    public static boolean defaultCopySyncTimestamp = true;

    // do not use channels to copy files larger than 5 Mb because of possible MapFailed error
    private static final long CHANNELS_COPYING_LIMIT = 5L * 1024L * 1024L;

    private static final ThreadLocal<byte[]> BUFFER = new ThreadLocal<byte[]>() {

        @Override
        protected byte[] initialValue() {
            return new byte[1024 * 20];
        }
    };

    /*
     * ---------------------FILE转成IO对象------------------------------------------------------------------------------------------
     */

    /**
     * <pre>
     * 参考类：org.tmatesoft.sqljet.core.internal.fs.util.SqlJetFileUtil.openFile 其他候选名：fileToRandomAccessFile
     */
    public static RandomAccessFile getRandomAccessFile(File file, String mode) throws FileNotFoundException {
        if (file == null) {
            return null;
        }
        // ensureDirExists(file); //外部来处理
        // if (file.getParentFile() != null && !file.getParentFile().exists()) {
        // file.getParentFile().mkdirs();
        // }
        if (isWindows) {
            long sleep = ATTEMPTS_INITAL_SLEEP;
            for (int i = 0; i < ATTEMPTS_INITAL_SLEEP; i++) {
                try {
                    return new RandomAccessFile(file, mode);
                } catch (FileNotFoundException e) {
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e1) {
                        // Thread.interrupted();
                        return null;
                    }
                }
                if (sleep < ATTEMPTS_MAX_SLEEP) {
                    sleep = sleep * 2;
                }
            }
        }
        return new RandomAccessFile(file, mode);
    }

    /**
     * <pre>
     * 其他候选名：fileToReader Retourne un reader utilisant l'encoding choisi et placé dans un BufferedReader
     *
     * @param file the given file
     * @param encoding (ISO-8859-1, UTF-8, ...)
     * @return the buffered reader in the given encoding
     * @throws IOException if any io pb
     */
    static public BufferedReader getReader(File file, Charset encoding) throws IOException {
        if (file == null) {
            return null;
        }
        FileInputStream inf = new FileInputStream(file);
        InputStreamReader in = new InputStreamReader(inf, encoding);
        BufferedReader result = new BufferedReader(in);
        return result;
    }

    /**
     * <pre>
     * 其他候选名：fileToReader Retourne un writer utilisant l'encoding choisi et placé dans un BufferedWriter
     *
     * @param file the given file
     * @param encoding (ISO-8859-1, UTF-8, ...)
     * @return the buffered writer on the given file with given encoding
     * @throws IOException if any io pb
     */
    static public BufferedWriter getWriter(File file, Charset encoding) throws IOException {
        if (file == null) {
            return null;
        }
        FileOutputStream outf = new FileOutputStream(file);
        OutputStreamWriter out = new OutputStreamWriter(outf, encoding);
        BufferedWriter result = new BufferedWriter(out);
        return result;
    }

    // org.apache.uima.pear.util.FileUtils
    // /**
    // * Converts a given input file path into a valid file URL string.
    // *
    // * @param path
    // * The given file path to be converted.
    // * @return The file URL string for the specified file.
    // */
    // public static String localPathToFileUrl(String path) {
    // // get absolute path
    // File file = new File(path);
    // String absPath = file.getAbsolutePath().replace('\\', '/');
    // // construct file URL
    // StringBuffer urlBuffer = new StringBuffer("file:///");
    // urlBuffer.append(absPath.replace(':', '|'));
    // String fileUrlString = urlBuffer.toString().replaceAll(" ", "%20");
    // URL fileUrl = null;
    // try {
    // fileUrl = new URL(fileUrlString);
    // } catch (MalformedURLException e) {
    // fileUrl = null;
    // }
    // return (fileUrl != null) ? fileUrl.toExternalForm() : fileUrlString;
    // }
    //
    // public static URL[] getFileURLs(File[] files) {
    //
    // com.izforge.izpack.util.FileUtils
    // public static File convertUrlToFile(URL url) {
    // return new File(convertUrlToFilePath(url));
    // }
    //
    // public static String convertUrlToFilePath(URL url) {
    // try {
    // return URLDecoder.decode(url.getFile(), "UTF-8");
    // } catch (UnsupportedEncodingException e) {
    // // yet another stupid checked exception...
    // throw new RuntimeException(e);
    // }
    // }
    // org.openide.filesystems.FileUtils
    // private static boolean canBeCanonicalizedOnWindows(final File file) {
    // /*Flopy and empty CD-drives can't be canonicalized*/
    // boolean canBeCanonizalized = true;
    // if (file.getParent() == null) {//for File.listRoots should be true
    // canBeCanonizalized = !FileSystemView.getFileSystemView().isFloppyDrive(file) && file.exists();
    // }
    // return canBeCanonizalized;
    // }

    public static URL getURL(File file) {
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            return null;
        }
        // URL retVal = null;
        // if (canBeCanonicalizedOnWindows(file)) {
        // retVal = file.toURI().toURL();
        // } else {
        // retVal = new URL("file:/" + file.getAbsolutePath());// NOI18N
        // }
        // return retVal;

        // URL result = null;
        // try {
        // result = file.toURL();//TODO AV - was toURI.toURL that does not works on Java 1.3
        // if (null != result) {
        // return result;
        // }
        // String url = "file:" + file.getAbsolutePath().replace('\\', '/');
        // result = new URL(url + (file.isDirectory() ? "/" : ""));
        // } catch (MalformedURLException e) {
        // String m = "Util.makeURL(\"" + file.getPath() + "\" MUE " + e.getMessage();
        // System.err.println(m);
        // }
        // return result;
    }

    /*
     * ---------------------文件属性获取------------------------------------------------------------------------------------------
     */

    /**
     * <pre>
     * 参考类：com.intellij.openapi.util.io.FileUtils.getParentFile (java.io.File) Get parent for the file. The method correctly processes "." and ".." in file names. The name remains relative if was
     * relative before.
     *
     * @param file a file to analyze
     * @return a parent or the null if the file has no parent.
     */

    public static File getParentFile(final File file) {
        int skipCount = 0;
        File parentFile = file;
        while (true) {
            parentFile = parentFile.getParentFile();
            if (parentFile == null) {
                return null;
            }
            if (".".equals(parentFile.getName())) {
                continue;
            }
            if ("..".equals(parentFile.getName())) {
                skipCount++;
                continue;
            }
            if (skipCount > 0) {
                skipCount--;
                continue;
            }
            return parentFile;
        }
    }

    /**
     * 返回一个文件夹内路径深度,也就是文件夹内部最长的路径长度
     *
     * <pre>
     * 参考类：org.apache.maven.mercury.util.FileUtils.depth 逻辑判断比较慢，比较耗性能 如果是文件返回0，如果文件不存在返回-1
     */
    public static int getDirDepth(File file) {
        if (file == null || !file.exists()) {
            // throw new IllegalArgumentException("file.not.exists.error" + file == null ? "null" : file.getAbsolutePath());
            return -1;
        }
        if (file.isFile()) {
            return 0;
        }
        File[] files = file.listFiles();
        int max = 0;
        for (File f : files) {
            if (f.isDirectory()) {
                int res = getDirDepth(f);
                if (res > max) {
                    max = res;
                }
            }
        }
        return max + 1;
    }

    /**
     * <pre>
     * 参考类：org.apache.uima.pear.util.FileUtils Returns file size for a given file.
     *
     * @param fileLocation The given file location - local file path or URL.
     * @return The given file size, if the specified file can be accessed, -1 otherwise.
     */
    public static long getFileSize(String fileLocation) {// TODO:内部实现考虑了url问题，是否要考虑
        long fileSize = 0;
        // choose file size method: local FS or HTTP
        File file = new File(fileLocation);
        if (file.isFile()) {
            fileSize = file.length();
        } else {
            try {
                URL fileUrl = new URL(fileLocation);
                URLConnection urlConn = fileUrl.openConnection();// See https://issues.apache.org/jira/browse/UIMA-1746
                urlConn.setUseCaches(false);
                fileSize = urlConn.getContentLength();
            } catch (IOException e) {
                fileSize = -1;
            }
        }
        return fileSize;
    }

    /*
     * ---------------------文件路径字符串操作------------------------------------------------------------------------------------------
     */

    /**
     * <pre>
     * 参考类:org.aspectj.util.FileUtils.getBestFile 其他参考:com.sun.enterprise.util.io.FileUtils.safeGetCanonicalFile Render as best file, canonical or absolute.
     *
     * @param file the File to get the best File for (not null)
     * @return File of the best-available path
     * @throws IllegalArgumentException if file is null
     */
    public static File getCanonicalOrAbsoluteFile(File file) {
        if (file.exists()) {
            try {
                return file.getCanonicalFile();
            } catch (IOException e) {
                return file.getAbsoluteFile();
            }
        }
        return file;
    }

    /**
     * <pre>
     * 参考类:org.aspectj.util.FileUtils.getBestPath 其他参考:com.sun.enterprise.util.io.FileUtils.safeGetCanonicalPath Render as best path, canonical or absolute.
     *
     * @param file the File to get the path for (not null)
     * @return String of the best-available path
     * @throws IllegalArgumentException if file is null
     */
    public static String getCanonicalOrAbsolutePath(File file) {
        if (file.exists()) {
            try {
                return file.getCanonicalPath();
            } catch (IOException e) {
                return file.getAbsolutePath();
            }
        }
        return file.getPath();
    }

    private static String _sanitizeSeparatorChar(String aFileName, char separator) {
        if (separator == '/') {
            return aFileName.replace('\\', separator);
        }
        return aFileName.replace('/', separator);
    }

    /**
     * 参考类：net.sf.mmm.util.file.api.FileUtils 原方法名：toSystemDependentName
     */

    public static String getSystemDependentName(String aFileName) {
        return _sanitizeSeparatorChar(aFileName, File.separatorChar);
    }

    /**
     * <pre>
     * 参考类：net.sf.mmm.util.file.api.FileUtils 原方法名：toSystemIndependentName 其他相似方法： nativePath com.sun.enterprise.util.io.FileUtils.makeForwardSlashes(String inputStr)
     * fmpp.util.FileUtils.pathToUnixStyle
     */

    public static String getSystemIndependentName(String aFileName) {
        return aFileName.replace('\\', '/');
    }

    public static String getBaseName(File file) {
        if (file != null) {
            return getBaseName(file.getPath());
        }
        return "";
    }

    /**
     * <pre>
     * 参考类：net.sf.mmm.util.file.api.FileUtils 相似类: org.nuiton.util.FileUtils.basename(String name, String... suffixes) TODO:注意感觉basename就是File.getName() 需要再确定 This method gets the <em>basename</em>
     * of
     * the given <code>filename</code> (path). The basename is the raw name of the file without the {@link #getDirname(String) path}.<br>
     * Examples:
     * <table border="1">
     * <tr>
     * <th>filename</th>
     * <th><code>{@link #getBaseName(String) getBasename}(filename)</code></th>
     * </tr>
     * <tr>
     * <td>&nbsp;</td>
     * <td>&nbsp;</td>
     * </tr>
     * <tr>
     * <td>/</td>
     * <td>/</td>
     * </tr>
     * <tr>
     * <td>\/\</td>
     * <td>\</td>
     * </tr>
     * <tr>
     * <td>/.</td>
     * <td>.</td>
     * </tr>
     * <tr>
     * <td>/foo.bar</td>
     * <td>foo.bar</td>
     * </tr>
     * <tr>
     * <td>/foo/bar/</td>
     * <td>bar</td>
     * </tr>
     * <tr>
     * <td>c:\\</td>
     * <td>&nbsp;</td>
     * </tr>
     * <tr>
     * <td>c:\\foo</td>
     * <td>foo</td>
     * </tr>
     * <tr>
     * <td>http://foo.org/bar</td>
     * <td>bar</td>
     * </tr>
     * </table>
     *
     * @param filename is the path to a file or directory.
     * @return the basename of the given <code>filename</code> .
     */
    public static String getBaseName(String filename) {
        int len = filename.length();
        if (len == 0) {
            return filename;
        }
        // remove trailing slashes
        int end = len - 1;
        char last = filename.charAt(end);
        while ((last == '/') || (last == '\\')) {
            end--;
            if (end < 0) {
                return Character.toString(last);
            }
            last = filename.charAt(end);
        }
        int start = filename.lastIndexOf('/', end);
        if (start < 0) {
            start = filename.lastIndexOf('\\', end);
        }
        if ((last == ':') && (start < 0)) {
            return "";
        }
        return filename.substring(start + 1, end + 1);
    }

    /**
     * <pre>
     * 参考类:org.databene.commons.FileUtils.localFileName 注意其跟new File(filePath).getName()的区别是:getFileName不处理文件夹的情况
     */
    public static String getFileName(String filePath) {
        if (filePath == null) {
            return "";
        }
        int i = filePath.lastIndexOf(File.separatorChar);
        if (File.separatorChar != '/') {
            i = Math.max(i, filePath.lastIndexOf('/'));
        }
        return (i >= 0 ? filePath.substring(i + 1) : filePath);
    }

    public static String getFileName(File file) {
        if (file != null) {
            return getFileName(file.getPath());
        }
        return "";
    }

    public static String getDirName(File file) {
        if (file != null) {
            return getDirName(file.getPath());
        }
        return "";
    }

    /**
     * 参考类：net.sf.mmm.util.file.api.FileUtils
     *
     * <pre>
     * This method gets the directory-name of the given <code>filename</code> (path).<br>
     * Examples:
     * <table border="1">
     * <tr>
     * <th>filename</th>
     * <th>{@link #getDirname(String)}</th>
     * </tr>
     * <tr>
     * <td>foo</td>
     * <td>.</td>
     * </tr>
     * <tr>
     * <td>/foo</td>
     * <td>/</td>
     * </tr>
     * <tr>
     * <td>/foo/bar</td>
     * <td>/foo</td>
     * </tr>
     * <tr>
     * <td>/foo/bar/</td>
     * <td>/foo</td>
     * </tr>
     * <tr>
     * <td>./foo/bar/</td>
     * <td>./foo</td>
     * </tr>
     * <tr>
     * <td>./foo/bar/../</td>
     * <td>./foo/bar</td>
     * </tr>
     * </table>
     *
     * @see #normalizePath(String)
     * @param filename is the path to a file or directory.
     * @return the path to the directory containing the file denoted by the given <code>filename</code>
     */
    public static String getDirName(String filename) {
        int len = filename.length();
        if (len == 0) {
            return PATH_SEGMENT_CURRENT;
        }
        // remove slashes at the end of the path (trailing slashes of filename)
        int pathEnd = len - 1;
        char last = filename.charAt(pathEnd);
        while ((last == '/') || (last == '\\')) {
            pathEnd--;
            if (pathEnd < 0) {
                return Character.toString(last);
            }
            last = filename.charAt(pathEnd);
        }
        // remove slashes at the end of dirname
        char c = '/';
        int dirEnd = filename.lastIndexOf(c, pathEnd);
        if (dirEnd < 0) {
            c = '\\';
            dirEnd = filename.lastIndexOf(c, pathEnd);
        }
        if (dirEnd >= 0) {
            int lastDirSlash = dirEnd;
            while ((c == '/') || (c == '\\')) {
                dirEnd--;
                if (dirEnd < 0) {
                    return Character.toString(c);
                }
                c = filename.charAt(dirEnd);
            }
            if (c == ':') {
                if ((filename.lastIndexOf('/', dirEnd) < 0) && (filename.lastIndexOf('/', dirEnd) < 0)) {
                    // special path (e.g. "C:\\" or "http://")
                    dirEnd = lastDirSlash;
                }
            }
            return filename.substring(0, dirEnd + 1);
        } else if (last == ':') {
            // special path (e.g. "C:\\" or "http://")
            return filename;
        } else {
            // only trailing slashes or none
            return PATH_SEGMENT_CURRENT;
        }
    }

    public static String getExtension(File file) {
        if (file != null) {
            return getExtension(file.getName());
        }
        return "";
    }

    /**
     * 返回文件对应扩展名
     *
     * <pre>
     * 原始参考类：org.aspectj.util.FileUtils 相关参考类：net.sf.mmm.util.file.api.FileUtils 内部实现传入的fileName必须不带路径分隔符,另一个区别： If the <code>filename</code> is just a dot followed by the extension (e.g.
     * <code>".java"</code>), the empty string is returned. extension = filename.substring(lastDot + 1).toLowerCase(Locale.US); 候选方法名： suffix extension
     */

    public static String getExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        int end = fileName.length() - 1;
        if (index < 0 || index == end) {
            return "";
        }
        if (fileName.lastIndexOf("\\") == end || fileName.lastIndexOf("/") == end) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();// 这里统一返回小写形式
    }

    /**
     * <pre>
     * 参考类：com.intellij.openapi.util.io.FileUtils.getNameWithoutExtension 其他类： org.jboss.dna.common.util.FileUtils.removeFileExtension
     *
     * @param file
     * @return
     */
    public static String getNameWithoutExtension(File file) {
        return getNameWithoutExtension(file.getName());
    }

    public static String getNameWithoutExtension(String fileName) {
        if (StringTools.isEmpty(fileName)) {
            return fileName;
        }
        int extIndex = fileName.lastIndexOf('.');// Create the artifact metadata
        if (extIndex < 0) {
            return fileName;
        }
        int end = fileName.length() - 1;
        if (fileName.lastIndexOf("\\") == end || fileName.lastIndexOf("/") == end) {
            return fileName;
        }
        return fileName.substring(0, extIndex);
    }

    /*
     * ---------------------文件简单判断------------------------------------------------------------------------------------------
     */

    /**
     * 参考类：com.sun.enterprise.util.io.FileUtils
     */
    public static boolean hasExtension(String filename, String ext) {
        if (filename == null || filename.length() <= 0) {
            return false;
        }
        return filename.endsWith(ext);
    }

    /**
     * 参考类：com.sun.enterprise.util.io.FileUtils
     */
    public static boolean hasExtension(File f, String ext) {
        if (f == null || !f.exists()) {
            return false;
        }
        return f.getName().endsWith(ext);
    }

    /**
     * 参考类：com.sun.enterprise.util.io.FileUtils
     */
    public static boolean hasExtensionIgnoreCase(String filename, String ext) {
        if (filename == null || filename.length() <= 0) {
            return false;
        }

        return filename.toLowerCase().endsWith(ext.toLowerCase());
    }

    /**
     * 参考类：com.sun.enterprise.util.io.FileUtils
     */
    public static boolean hasExtensionIgnoreCase(File f, String ext) {
        if (f == null || !f.exists()) {
            return false;
        }
        return f.getName().toLowerCase().endsWith(ext.toLowerCase());
    }

    public static boolean isAcceptable(File file, FileFilter fileFilter) {
        return fileFilter == null || fileFilter.accept(file);
    }

    /**
     * <pre>
     * 参考类：com.intellij.openapi.util.io.FileUtils.isFilePathAcceptable 遍历整个文件路径，判断其是否匹配fileFilter
     */
    public static boolean isAcceptableAll(File file, FileFilter fileFilter) {
        if (fileFilter == null) {
            return true;
        }
        do {
            if (!fileFilter.accept(file)) {
                return false;
            }
            file = file.getParentFile();
        } while (file != null);
        return true;
    }

    /**
     * 参考类：com.sun.enterprise.util.io.FileUtils.safeIsDirectory
     */
    public static boolean isDir(String s) {
        return isDir(new File(s));
    }

    /**
     * 参考类：com.sun.enterprise.util.io.FileUtils.safeIsDirectory
     */
    public static boolean isDir(File f) {
        return f != null && f.exists() && f.isDirectory();
    }

    /**
     * 参考类：com.sun.enterprise.util.io.FileUtils.safeIsRealDirectory
     */
    public static boolean isDirExactly(String s) {
        return isDirExactly(new File(s));
    }

    /**
     * 参考类：com.sun.enterprise.util.io.FileUtils.safeIsRealDirectory
     */
    public static boolean isDirExactly(File f) {
        if (isDir(f) == false) {
            return false;
        }
        // these 2 values while be different for symbolic links
        String canonical = getCanonicalOrAbsolutePath(f);
        String absolute = f.getAbsolutePath();
        if (canonical.equals(absolute)) {
            return true;
        }
        /*
         * Bug 4715043 -- WHOA -- Bug Obscura!! In Windows, if you create the File object with, say, "d:/foo", then the absolute path will be "d:\foo" and the canonical path will be "D:\foo" and they
         * won't match!!!
         */
        return isWindows && canonical.equalsIgnoreCase(absolute);
    }

    /**
     * <pre>
     * 参考类：org.databene.commons.FileUtils.isEmptyFolder 注意 如果是文件，返回false 如果文件不存在，返回true
     */
    public static boolean isDirEmpty(File file) {
        if (file.isFile()) {
            return false;
        }
        String[] list = file.list();
        return list == null || list.length == 0;
    }

    public static boolean isAbsolute(String path) {
        return new File(path).isAbsolute();
    }

    /**
     * <pre>
     * 参考类：com.intellij.openapi.util.io.FileUtils.startsWith(String path, String start)
     */
    public static boolean isStartWith(String path, String start) {
        return isStartWith(path, start, isFileSystemCaseSensitive);
    }

    /**
     * <pre>
     * 参考类：com.intellij.openapi.util.io.FileUtils.startsWith(final String path, final String start, final boolean caseSensitive) {
     */
    public static boolean isStartWith(final String path, final String start, final boolean caseSensitive) {
        final int length1 = path.length();
        final int length2 = start.length();
        if (length2 == 0) {
            return true;
        }
        if (length2 > length1) {
            return false;
        }
        if (!path.regionMatches(!caseSensitive, 0, start, 0, length2)) {
            return false;
        }
        if (length1 == length2) {
            return true;
        }
        char last2 = start.charAt(length2 - 1);
        char next1;
        if (last2 == '/' || last2 == File.separatorChar) {
            next1 = path.charAt(length2 - 1);
        } else {
            next1 = path.charAt(length2);
        }
        return next1 == '/' || next1 == File.separatorChar;
    }

    /*
     * ---------------------两文件判断------------------------------------------------------------------------------------------
     */

    /**
     * 参考类:jodd.io.FileUtils.isNewer(File file, long timeMillis)
     */
    public static boolean isNewer(File file, long timeMillis) {
        return file.lastModified() > timeMillis;
    }

    /**
     * <pre>
     * 参考类：org.nuiton.util.FileUtils.isNewer(File f1, File f2) 注意如果文件不存在，其lastModified是0,也就是最老的
     */
    public static boolean isNewer(File f1, File f2) {
        boolean result = f1.lastModified() > f2.lastModified();
        return result;
    }

    /**
     * 获得两文件的相对路径(file相对于base的相对路径)
     *
     * <pre>
     * relativePath不同fileUtil测试效果:（如果报错，改成返回null,这样统一效果） File base = new File("c:\\a\\b"); File[] files = { new File("d:\\"), new File("/unix"), new File("c:/a"), new File("c:\\a/b/"), new File("
     * c:\\a/b/c\\d\\e") }; 1 null x null c\d\e ->com.sun.enterprise.util.io.FileUtils.relativize(File parent, File child) 2 null null . b b\c\d\e
     * ->com.intellij.openapi.util.io.FileUtils.getRelativePath(File base, File file) 3 null null .. c\d\e -> fmpp.util.FileUtils.getRelativePath(File fromDir, File toFileOrDir) 不错***** 4 null null
     * null
     * \c\d\e ->com.jayway.maven.plugins.lab.FileUtils.relativePathFromBase(File file, File basedir) 内部是报错 5 null null .\b . null ->org.rhq.core.util.file.FileUtils.getRelativePath(File path, File
     * base)
     * 内部是报错 6 D: J:\\unix C:\a C:\a\b C:\a\b\c\d\e ->org.codehaus.mojo.natives.util.FileUtils.getRelativePath(String base, File targetFile) 7 d:/ unix c:/a c/d/e
     * ->org.apache.uima.pear.util.FileUtils.getRelativePath(File rootDir, String absolutePath) 内部没有处理..的情况，另外是传file来实现的,但效果不错*****,另一个computeRelativePath有处理..的情况，不过null情况一样有 8 D: J:/unix .. c/d/e
     * ->org.apache.tools.ant.util.FileUtils.getRelativePath(File fromFile, File toFile) 内部是先解析成一个一个pathStack再来处理的，效果不错*****，但是引的方法多 9 c/d/e
     * ->org.tmatesoft.svn.core.internal.util.SVNPathUtil.getRelativePath(String parent, String child) 效果很差 org.eclipse.gmf.runtime.common.ui.util.FileUtils.etRelativePath(String urlPathstr, String
     * modelPathstr) 内部有引用非公共类，没有测试 org.databene.commons.FileUtils.relativePath(File fromFile, File toFile, char separator) 也没有试了 按8的方案，进行整合优化后，效果是： 10 D: /unix .. c/d/e -> 这个
     *
     * @param base
     * @param file
     * @return
     */
    public static String getRelativePath(File base, File file, char separator, boolean caseSensitive) {
        String fromPath = getSystemIndependentName(getCanonicalOrAbsolutePath(base)); // 先弄成系统无关的路径格式
        String toPath = getSystemIndependentName(getCanonicalOrAbsolutePath(file));
        if (caseSensitive) {
            fromPath = fromPath.toLowerCase();
            toPath = toPath.toLowerCase();
        }
        Object[] fromPathStack = fromPath.split("/");
        Object[] toPathStack = toPath.split("/");
        String separtorStr = separator + "";

        if (0 < toPathStack.length && 0 < fromPathStack.length) {
            if (!fromPathStack[0].equals(toPathStack[0])) {
                return Joiner.on(separtorStr).join(toPathStack); // not the same device (would be "" on Linux/Unix)
            }
        } else { // no comparison possible
            return Joiner.on(separtorStr).join(toPathStack);
        }

        int minLength = Math.min(fromPathStack.length, toPathStack.length);
        int same = 1; // Used outside the for loop

        // get index of parts which are equal
        for (; same < minLength && fromPathStack[same].equals(toPathStack[same]); same++) {
            // Do nothing
        }
        List<String> relativePathStack = new ArrayList<String>();

        // if "from" part is longer, fill it up with ".."
        // to reach path which is equal to both paths
        for (int i = same; i < fromPathStack.length; i++) {
            relativePathStack.add("..");
        }
        // fill it up path with parts which were not equal
        for (int i = same; i < toPathStack.length; i++) {
            relativePathStack.add((String) toPathStack[i]);
        }
        return Joiner.on(separtorStr).join(relativePathStack.toArray());
        // org.apache.uima.pear.util.FileUtils.computeRelativePath
        // // compute relative path from reference dir to file dir-tree
        // StringBuilder relBuffer = new StringBuilder();
        // while (refPath != null && !filePath.startsWith(refPath)) {
        // relBuffer.append("../");
        // refPath = (new File(refPath)).getParent();
        // if (refPath != null)
        // refPath = refPath.replace('\\', '/');
        // }
        // if (refPath != null) {
        // // construct relative path
        // String subPath = filePath.substring(refPath.length());
        // if (relBuffer.length() == 0)
        // relBuffer.append("./");
        // if (subPath.startsWith("/"))
        // relBuffer.append(subPath.substring(1));
        // else
        // relBuffer.append(subPath);
        // return relBuffer.toString();
        // }
        // // relative path does not exist
        // return null;
    }

    /**
     * <pre>
     * 参考类： 其他参考类:fmpp.util.FileUtils.isInside(File file, File ascendant) Check if the {@code ancestor} is an ancestor of {@code file}.
     *
     * @param ancestor the file
     * @param file the file
     * @param strict if {@code false} then this method returns {@code true} if {@code ancestor} and {@code file} are equal
     * @return {@code true} if {@code ancestor} is parent of {@code file}; {@code false} otherwise
     */
    public static boolean isAncestor(File ancestor, File file, boolean strict) {
        File parent = strict ? getParentFile(file) : file;
        while (true) {
            if (parent == null) {
                return false;
            }
            if (parent.equals(ancestor)) {
                return true;
            }
            parent = getParentFile(parent);
        }
    }

    /*
     * ---------------------单文件读取------------------------------------------------------------------------------------------
     */

    /**
     * <pre>
     * 最终使用读取文件的方法名用：
     * readString,readChars,readBytes,readLines
     * writeString,writeChars,writeBytes,writeLines,appendLines,appendString
     *
     * 与 jodd.io.FileUtil一致
     *
     *     // TODO:是否参数带 String filenname
     *     // TODO:是否内部吞IoException
     * 文件读取方法名汇总：
     * getFileContent，getFileBytes,getFileLines
     * loadByteArray，loadListOfStrings,
     * fileToByte,
     * asString,resourceAsString,
     * readSmallFile,readRawData
     * loadFileLines,loadFileText,loadFileBytes,loadListOfStrings,loadTextFile
     *
     *  com.mysql.management.util.FileUtils
     *  public String asString(File file) throws IOException {
     *
     *  logback
     *  public static String resourceAsString(ContextAware ca, ClassLoader classLoader, String resourceName)
     *
     *  net.sf.jour.util.FileUtils
     *  public static boolean readTextFile(File file, HashSet list) {
     *
     *  org.apache.uima.pear.util.FileUtils
     *  public static String[] loadListOfStrings(BufferedReader iStream) throws IOException {
     *  public static String loadTextFile(BufferedReader iStream) throws IOException {
     *
     *  com.sun.enterprise.util.io.FileUtils
     *  public static String readSmallFile(final String fileName) throws IOException, FileNotFoundException {
     *
     *  org.apache.maven.mercury.util.FileUtils
     *  public static byte[] readRawData( File file ) throws IOException
     * </pre>
     */
    public static long READ_FILE_LENGTH_LIMIT = 100 * MEGABYTE;

    private static void checkFileLength(File file) throws IOException {
        final long len = file.length();
        if (len < 0) {
            throw new IOException("File length reported negative, probably doesn't exist");
        }
        if (len > READ_FILE_LENGTH_LIMIT) {
            throw new IOException("Attempt to load '" + file + "' in memory buffer, file length is " + len + " bytes.");
        }
    }

    public static LinkedList<String> readLines(File file, Charset encoding) throws IOException {
        checkFileLength(file);
        LinkedList<String> lines = new LinkedList<String>();
        BufferedReader reader = getReader(file, encoding);
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } finally {
            reader.close();
        }
        return lines;
    }

    public static String readString(File file, Charset encoding) throws IOException {
        return new String(readChars(file, encoding));
    }

    public static String readStringAdaptive(File file, Charset encoding) throws IOException {
        checkFileLength(file);
        BufferedReader reader = getReader(file, encoding);
        try {
            return new String(readCharsAdaptive(reader));
        } finally {
            reader.close();
        }
    }

    public static char[] readChars(File file, Charset encoding) throws IOException {
        checkFileLength(file);
        BufferedReader reader = getReader(file, encoding);
        try {
            return readChars(reader, (int) file.length());
        } finally {
            reader.close();
        }
    }

    public static char[] readChars(Reader reader, int length) throws IOException {
        char[] chars = new char[length];
        int count = 0;
        while (count < chars.length) {
            int n = reader.read(chars, count, chars.length - count);
            if (n <= 0) {
                break;
            }
            count += n;
        }
        if (count == chars.length) {
            return chars;
        } else {// 这里说明 比预期的文件大小还大，会是什么情况？//TODO
            char[] newChars = new char[count];
            System.arraycopy(chars, 0, newChars, 0, count);
            return newChars;
        }
    }

    public static char[] readCharsAdaptive(Reader reader) throws IOException {
        char[] chars = new char[4096];
        List<char[]> buffers = null;
        int count = 0;
        int total = 0;
        while (true) {
            int n = reader.read(chars, count, chars.length - count);
            if (n <= 0) {
                break;
            }
            count += n;
            if (total > READ_FILE_LENGTH_LIMIT) {
                throw new IOException("File too big " + reader);
            }
            total += n;
            if (count == chars.length) {
                if (buffers == null) {
                    buffers = new ArrayList<char[]>();
                }
                buffers.add(chars);
                int newLength = Math.min(MEGABYTE, chars.length * 2);
                chars = new char[newLength];
                count = 0;
            }
        }
        char[] result = new char[total];
        if (buffers != null) {
            for (char[] buffer : buffers) {
                System.arraycopy(buffer, 0, result, result.length - total, buffer.length);
                total -= buffer.length;
            }
        }
        System.arraycopy(chars, 0, result, result.length - total, total);
        return result;
    }

    public static byte[] readBytes(File file) throws IOException {
        checkFileLength(file);
        final InputStream stream = new FileInputStream(file);
        try {
            return readBytes(stream, (int) file.length());
        } finally {
            stream.close();
        }
    }

    public static byte[] readBytesAdaptive(File file) throws IOException {
        checkFileLength(file);
        final InputStream stream = new FileInputStream(file);
        try {
            return readBytesAdaptive(stream);
        } finally {
            stream.close();
        }
    }

    public static byte[] readBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            final byte[] bytes = BUFFER.get();
            while (true) {
                int n = stream.read(bytes, 0, bytes.length);
                if (n <= 0) {
                    break;
                }
                buffer.write(bytes, 0, n);
            }
        } finally {
            buffer.close();
        }
        return buffer.toByteArray();
    }

    public static byte[] readBytes(InputStream stream, int length) throws IOException {
        byte[] bytes = new byte[length];
        int count = 0;
        while (count < length) {
            int n = stream.read(bytes, count, length - count);
            if (n <= 0) {
                break;
            }
            count += n;
        }
        return bytes;
    }

    public static byte[] readBytesAdaptive(InputStream stream) throws IOException {
        byte[] bytes = new byte[4096];
        List<byte[]> buffers = null;
        int count = 0;
        int total = 0;
        while (true) {
            int n = stream.read(bytes, count, bytes.length - count);
            if (n <= 0) {
                break;
            }
            count += n;
            if (total > READ_FILE_LENGTH_LIMIT) {
                throw new IOException("File too big " + stream);
            }
            total += n;
            if (count == bytes.length) {
                if (buffers == null) {
                    buffers = new ArrayList<byte[]>();
                }
                buffers.add(bytes);
                int newLength = Math.min(MEGABYTE, bytes.length * 2);
                bytes = new byte[newLength];
                count = 0;
            }
        }
        byte[] result = new byte[total];
        if (buffers != null) {
            for (byte[] buffer : buffers) {
                System.arraycopy(buffer, 0, result, result.length - total, buffer.length);
                total -= buffer.length;
            }
        }
        System.arraycopy(bytes, 0, result, result.length - total, total);
        return result;
    }

    /*
     * ---------------------单文件写入------------------------------------------------------------------------------------------
     */

    /**
     * <pre>
     * 最终使用读取文件的方法名用： readString,readChars,readBytes,readLines writeString,writeChars,writeBytes,writeLines,appendLines,appendString 与 jodd.io.FileUtil一致 (jodd.io.StreamUtil有一系列不错的流操作方法) 文件写入方法名汇总：
     * writeLinesToFile，appendText，appendToFile，replaceFileWithNewContent，writeToFile，writeRawData fitnesse.util.FileUtils public static void writeLinesToFile(File file, Charset encoding, List
     * <String> lines) throws IOException org.apache.maven.mercury.util.FileUtils public static void writeRawData( InputStream in, File f ) throws IOException org.nuiton.util.FileUtils.byteToFile
     * static
     * public File byteToFile(byte[] bytes) throws IOException { static public void writeString(File file, String content) throws IOException { jodd.io.FileUtils.writeString
     * org.aspectj.util.FileUtils
     * writeStringArray(java.lang.String[],java.io.DataOutputStream) org.aspectj.util.FileUtils public static String writeAsString(File file, String contents) {
     * com.intellij.openapi.util.io.FileUtils.writeToFile 其他相关： com.googlecode.mad.mvntools.common.core.FileUtils public void replaceFileWithNewContent(final String file, final String content) {
     */
    private static File _write(final File file, final byte[] bytes, boolean append) throws IOException {
        return _write(file, bytes, 0, bytes.length, append);
    }

    private static File _write(File file, byte[] text, final int off, final int len, boolean append) throws IOException {
        ensureDirExists(file);
        OutputStream stream = new BufferedOutputStream(new FileOutputStream(file, append));
        try {
            stream.write(text, off, len);
        } finally {
            stream.close();
        }
        return file;
    }

    public static File appendBytes(final File file, final byte[] bytes) throws IOException {
        return _write(file, bytes, true);
    }

    // /**
    // * <pre>
    // * 参考类：com.sun.enterprise.util.io.FileUtils
    // * Appends the given line at the end of given text file. If the given file does not exist, an attempt is made to create it. Note that this method can handle only text files.
    // *
    // * @param line the line to append to
    // * @throws RuntimeException in case of any error - that makes it callable from a code not within try-catch. Note that NPE will be thrown if either argument is null. Note that this method is not
    // * tested with String containing characters with 2 bytes.
    // */
    // public static void appendString(File file, Charset encoding, String String) throws IOException {
    // RandomAccessFile rf = getRandomAccessFile(file, "rw");
    // try {
    // rf.seek(rf.getFilePointer() + rf.length());
    // rf.write(String.getBytes(encoding));
    // } finally {
    // rf.close();
    // }
    // }
    public static File appendString(File file, String String, Charset encoding) throws IOException {
        return _write(file, String.getBytes(encoding), true);
    }

    private static final byte[] LINE_SEPARATOR = System.getProperty("line.separator").getBytes();

    public static File appendLines(File file, List<String> lines, Charset encoding) throws IOException {
        ensureDirExists(file);
        RandomAccessFile rf = getRandomAccessFile(file, "rw");
        try {
            rf.seek(rf.getFilePointer() + rf.length());
            for (String line : lines) {
                rf.write(line.getBytes(encoding));
                rf.write(LINE_SEPARATOR);
            }
        } finally {
            rf.close();
        }
        return file;
    }

    public static File writeBytes(final File file, final byte[] bytes) throws IOException {
        return _write(file, bytes, false);
    }

    public static File writeString(File file, String String, Charset encoding) throws IOException {
        return _write(file, String.getBytes(encoding), false);
    }

    public static void writeLines(File file, List<String> lines, Charset encoding) throws IOException {// 参考类fitnesse.util.FileUtils.writeLinesToFile
        BufferedWriter output = getWriter(file, encoding);
        try {
            for (String line : lines) {
                output.write(line);
                output.newLine();
            }
        } finally {
            output.close();
        }
    }

    /*
     * ---------------------文件删除------------------------------------------------------------------------------------------
     */

    /**
     * <pre>
     * 其他待选方法名：deleteFile,deleteContents // 很多参考代码都是这样来写的 // if (!dir.delete()) { // dir.deleteOnExit(); // done = false; // } public static boolean deleteRecursively(String directory public static
     * boolean whack(File parent) { com.sun.enterprise.util.io.FileUtils private static boolean whackResolvedDirectory(File parent, Collection<File> undeletedFiles) { The whackResolvedDirectory
     * method
     * is invoked with a File argument in which any upstream file system links have already been resolved. This method will treate Any file passed in that does not have the same absolute and
     * canonical
     * path - as evaluated in safeIsRealDirectory - as a link and will delete the link without deleting any files in the linked directory. org.apache.ivy.util.FileUtils public static boolean
     * forceDelete(File file) { org.databene.commons.FileUtils public static void deleteIfExists(File file) { public static void deleteDirectoryIfExists(File folder) { public static void
     * deleteDirectory(File folder) { com.samskivert.util.FileUtils public static void recursiveDelete (File file) 全删除 public static void recursiveClean (File file) 清空文件夹 protected static void
     * recursiveWipe (File file, boolean wipeMe) 内部实现 net.sf.mmm.util.file.base.FileUtilImpl public int deleteChildren(File directory) { public int deleteRecursive(File path) {
     * org.apache.hadoop.fs.FileUtils public static boolean fullyDelete(File dir) throws IOException { org.apache.uima.pear.util.FileUtils public static int cleanUpDirectoryContent(File directory)
     * throws IOException { public static int cleanUpDirectoryFiles(File directory, int maxLimit) throws IOException {
     */
    private static int ATTEMPTS_COUNT = 10;

    private static int ATTEMPTS_INITAL_SLEEP = 10;

    private static int ATTEMPTS_MAX_SLEEP = 1000;

    /**
     * <pre>
     * 删除单个文件/文件夹 注意原始的File.delete发现文件夹非空时，是不允许删除的
     *
     * @param file
     * @return
     */
    public static boolean delete(File file) {
        // waitUntilFileDeleted 这里要等待文件真正删除
        String type = file.isDirectory() ? "Dir" : "File";
        long sleep = ATTEMPTS_INITAL_SLEEP;
        for (int i = 0; i < ATTEMPTS_COUNT; i++) {
            /**
             * <pre>
             * The operation succeeds immediately if the file is deletedsuccessfully. On systems that support symbolic links, the file will be reported as non-existent if the file is a symlink to a
             * non-existent directory. In that case invoke delete to remove the link before checking for existence, since File.exists on a symlink checks for the existence of the linked-to directory
             * or file rather than of the link itself.
             */
            if (file.delete()) {
                log.debug("delete{}:[{}] OK", type, file);
                return true;
            } else if (file.isDirectory() && file.listFiles().length > 0) {
                log.warn("delete{}:[{}] FOUND IT NONEMPTY", type, file);
                return false;
            }
            if (!file.exists()) {
                log.warn("delete{}:[{}] FOUND IT NONEXIST", type, file);
                return true;
            }
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {// 原来是直接忽略的，改成被中断
                // Thread.interrupted();
                return false;
            }
            if (sleep < ATTEMPTS_MAX_SLEEP) {
                sleep = sleep * 2;
            }
        }
        // org.tmatesoft.sqljet.core.internal.fs.util.SqlJetFileUtil
        // if (!sync || file.isDirectory() || !file.exists()) {
        // return file.delete();
        // }

        // long sleep = 1;
        // for (int i = 0; i < ATTEMPTS_COUNT; i++) {
        // if (file.delete() && !file.exists()) {
        // return true;
        // }
        // if (!file.exists()) {
        // return true;
        // }
        // try {
        // Thread.sleep(sleep);
        // } catch (InterruptedException e) {
        // return false;
        // }
        // if (sleep < 128) {
        // sleep = sleep * 2;
        // }
        // }
        log.error("delete{}:[{}] ERROR", type, file);
        return false;
    }

    public static int deleteForce(File file) {
        return deleteRecursively(file, null, true, true);
    }

    /**
     * <pre>
     * 参考类：org.aspectj.util.FileUtils Recursively delete some contents of dir, but not the dir itself. If deleteEmptyDirs is true, this deletes any subdirectory which is empty after its files are
     * deleted.
     *
     * @param rootFile the File directory (if a file, the the file is deleted)
     * @return the total number of files deleted
     */
    public static int deleteRecursively(File rootFile, FileFilter filter, boolean deleteSelf, boolean deleteEmptyDirs) {
        // TODO:这里怎么来统计出有哪些文件被真正删除,然后返回
        int count = 0;
        File[] files = rootFile.listFiles();
        if (files != null) {
            for (File file : files) {
                if ((null == filter) || filter.accept(file)) {
                    if (file.isDirectory()) {
                        count += deleteRecursively(rootFile, filter, deleteSelf, deleteEmptyDirs);
                        if (deleteEmptyDirs && (0 == file.list().length)) {
                            if (delete(file)) {
                                count++;
                            }
                        }
                    } else {
                        if (delete(file)) {
                            count++;
                        }
                    }
                }
            }
        }
        if (deleteSelf) {
            if ((null == filter) || filter.accept(rootFile)) {
                if (delete(rootFile)) {
                    count++;
                }
            }
        }
        return count;
    }

    /*
     * ---------------------文件创建------------------------------------------------------------------------------------------
     */

    /**
     * 创建file对应的所有父文件(也就是 文件夹)，返回是否创建成功（原来已经存在也算成功）
     *
     * <pre>
     * 文件夹创建 其他方法：mkdirs,makeDirs,createDir(String path) 参考类： org.aspectj.util.FileUtils.ensureCanCreateFile 其他 org.sonatype.aether.util.fileUtil public static boolean mkdirs(File dir) {内部实现用了递归
     * ch.qos.logback.core.util.FileUtils public static boolean createMissingParentDirectories(File file) org.nuiton.util.FileUtils.createDirectoryIfNecessary
     * org.tmatesoft.svn.core.internal.wc.SVNFileUtil.ensureDirectoryExists 内部实现用了递归，其实parentFile.mkdirs()内部已经实现了 org.aspectj.util.FileUtils public static File ensureParentWritable(File path) {
     * org.hsqldb.lib.FileUtils public void makeParentDirectories(File f) { org.xerial.util.FileUtils public static void mkdirs(File path) {
     * org.tmatesoft.svn.core.internal.wc.SVNFileUtil.ensureDirectoryExists //TODO:未知 com.jayway.maven.plugins.lab.FileUtils // public static File makeDestination(String relativePath, File
     * targetBaseDir) { 其他 org.apache.uima.pear.util.FileUtils public static Collection<File> createDirList(File rootDir, boolean includeSubdirs) throws IOException {
     */
    public static boolean ensureDirExists(File file) {
        if (!file.exists()) {
            File parentFile = file.getParentFile();
            if (parentFile != null) {
                // return parentFile.exists() && parentFile.isDirectory() || parentFile.mkdirs();
                return parentFile.isDirectory() || parentFile.mkdirs();
            }
        }
        return true;
    }

    /**
     * <pre>
     * 参考类： org.aspectj.util.FileUtils.ensureCanCreateFile 在文件不存在时，创建一个空文件 注意file.createNewFile方法如果其文件夹没有创建好时，是会报io错误的
     */
    public static boolean ensureFileExists(File file) {
        if (file.exists()) {
            return true;
        }
        try {
            if (!ensureDirExists(file)) {
                return false;
            }
            return file.createNewFile();
            // OutputStream s = new FileOutputStream(file);
            // s.close();
            // return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * <pre>
     * 参考类： org.aspectj.util.FileUtils.ensureCanCreateFile 确保文件可创建（内部通过创建空文件来判断）
     */
    public static boolean ensuerFileWritable(File file) {
        if (file.exists()) {
            return file.canWrite();
        }
        if (!ensureFileExists(file)) {
            return false;
        }
        return delete(file);
    }

    // /**
    // * <pre>
    // * 参考类： org.aspectj.util.FileUtils
    // * public static File createFile(File dir, String path)
    // *
    // * Create a new File, resolving paths ".." and "." specially.
    // *
    // * @param dir the File for the parent directory of the file
    // * @param path the path in the parent directory (filename only?)
    // * @return File for the new file.
    // */
    // @Deprecated
    // public static File createFile(File dir, String path) {
    // // TODO: 这里也只是简单处理了. 跟..的情况，但没有处理path 是 ../abc/./cc的更特殊情况
    // if (".".equals(path)) {
    // return dir;
    // } else if ("..".equals(path)) {
    // File parentDir = dir.getParentFile();
    // if (null != parentDir) {
    // return parentDir;
    // } else {
    // return new File(dir, "..");
    // }
    // } else {
    // return new File(dir, path);
    // }
    // }
    //
    // /**
    // * <pre>
    // * 参考类：com.samskivert.util.FileUtils
    // * public static File newFile(File root, String... parts)
    // *
    // * Creates a file from the supplied root using the specified child components.
    // * For example: <code>fromPath(new File("dir"), "subdir", "anotherdir", "file.txt")</code>
    // * creates a file with the Unix path
    // * <code>dir/subdir/anotherdir/file.txt</code>
    // */
    // @Deprecated
    // public static File createFile(File root, String... parts) {
    // // TODO:这里没有特别处理:createFile(new File("c:\\"), "a", "..", "b", ".") -> c:\a\..\b\.
    // File path = root;
    // for (String part : parts) {
    // path = new File(path, part);
    // }
    // return path;
    // }

    /*
     * ---------------------两文件复制------------------------------------------------------------------------------------------
     */

    /**
     * <pre>
     * // TODO:是否提供内部吞异常的方法 // TODO:是否提供记录器，返回count 其他待选方法名： static public void copyRecursively(File srcDir, File destDir, String... includePatterns) throws IOException { copyAndRenameRecursively
     * net.sf.mmm.util.file.base.FileUtilImpl public void copyRecursive(File source, File destination, boolean allowOverwrite, FileFilter filter) { org.apache.hadoop.fs.FileUtils public static boolean
     * copyMerge(FileSystem srcFS, Path srcDir,FileSystem dstFS, Path dstFile,boolean deleteSource, Configuration conf, String addString) throws IOException { 其他参考类：
     * com.sun.enterprise.util.io.FileUtils public static void copyWithoutClose(InputStream in, FileOutputStream out, long size) throws IOException { ReadableByteChannel inChannel =
     * Channels.newChannel(in); FileChannel outChannel = out.getChannel(); outChannel.transferFrom(inChannel, 0, size); } public static void copy(InputStream in, OutputStream os, long size) throws
     * IOException { if (os instanceof FileOutputStream) { copy(in, (FileOutputStream) os, size); } else { ReadableByteChannel inChannel = Channels.newChannel(in); WritableByteChannel outChannel =
     * Channels.newChannel(os); if (size==0) { ByteBuffer byteBuffer = ByteBuffer.allocate(10240); int read; do { read = inChannel.read(byteBuffer); if (read&gt;0) {
     * byteBuffer.limit(byteBuffer.position()); byteBuffer.rewind(); outChannel.write(byteBuffer); byteBuffer.clear(); } } while (read!=-1); } else { ByteBuffer byteBuffer =
     * ByteBuffer.allocate(Long.valueOf(size).intValue()); inChannel.read(byteBuffer); byteBuffer.rewind(); outChannel.write(byteBuffer); } } }
     */
    /**
     * inputStream -> outputStream
     */
    public static void transfer(InputStream inputStream, OutputStream outputStream) throws IOException {
        final byte[] buffer = BUFFER.get();
        while (true) {
            int read = inputStream.read(buffer);
            if (read < 0) {
                break;
            }
            outputStream.write(buffer, 0, read);
        }
    }

    /**
     * inputStream -> outputStream 指定的size
     */
    public static void transfer(InputStream inputStream, OutputStream outputStream, int size) throws IOException {
        final byte[] buffer = BUFFER.get();
        int toRead = size;
        while (toRead > 0) {
            int read = inputStream.read(buffer, 0, Math.min(buffer.length, toRead));
            if (read < 0) {
                break;
            }
            toRead -= read;
            outputStream.write(buffer, 0, read);
        }
    }

    public static final FileFilter toFileIgnoreWhenExistFilter = new FileFilter() {

        @Override
        public boolean accept(File pathname) {
            return !pathname.exists();
        }
    };

    public static final FileFilter toFileErrorWhenExistFilter = new FileFilter() {

        @Override
        public boolean accept(File pathname) {
            if (pathname.exists()) {
                throw new IllegalAccessError("Target file already exists: " + pathname);
            }
            return true;
        }
    };

    public static final FileFilter fromFileIngoreSystemFileFilter = new FileFilter() {

        @Override
        public boolean accept(File pathname) {
            return !pathname.getName().startsWith(".");
        }
    };

    /**/
    public static final FileFilter allWithTrueFilter = new FileFilter() {

        @Override
        public boolean accept(File pathname) {
            return true;
        }
    };

    public static void copy(File fromFile, File toFile) throws IOException {
        copy(fromFile, toFile, defaultCopyFromFileAcceptFilter, defaultCopyToFileExistAcceptFilter, defaultCopySyncTimestamp);
    }

    /**
     * <pre>
     * 文件复制功能 自动判断是复制文件夹还是复制文件 from -----> to 是否覆盖（如果不覆盖，存在时，是否忽略），是否同步时间戳，文件过滤器 文件 -----> 文件 源文件不存在，目标文件已存在 文件夹 ----> 文件夹 源文件夹不存在，源是文件，而非文件夹,目标文件夹已存在，目标文件夹中有文件已存在， 文件 ------> 文件夹
     * 把文件复制到指定文件夹上，文件名使用原文件名 文件夹 ----> 文件 不允许
     */
    public static void copy(File fromFile, File toFile, FileFilter fromFileAcceptFilter, FileFilter toFileExistFilter, boolean syncTimestamp) throws IOException {
        if (!fromFile.exists()) {
            throw new FileNotFoundException("cant find sourceFile:" + fromFile);
        }
        if (fromFile.isFile()) { // 文件 -----> 文件
            copyFile(fromFile, toFile, fromFileAcceptFilter, toFileExistFilter, syncTimestamp);
        } else {// 文件夹 ----> 文件夹
            copyDir(fromFile, toFile, fromFileAcceptFilter, toFileExistFilter, syncTimestamp);
        }
    }

    public static void copyFile(File fromFile, File toFile) throws IOException {
        copyFile(fromFile, toFile, defaultCopyFromFileAcceptFilter, defaultCopyToFileExistAcceptFilter, defaultCopySyncTimestamp);
    }

    /**
     * <pre>
     * 复制文件 fromFile -> toFile 1.fromFile 如果不存在，报FileNotFoundException 2.fromFile 如果是文件夹，报FileNotFoundException 无法访问设备 3.toFile 这里表示的是要复制的文件指定 全路径，包括文件名 toFile已经存在，默认是直接覆盖 toFile已经存在，且是文件夹，会报错
     * toFile已经存在，想直接忽略，请使用toFileIgnoreWhenExistFilter toFile已经存在，想直接报错退出，请使用toFileErrorWhenExistFilter
     */
    public static void copyFile(File fromFile, File toFile, FileFilter fromFileAcceptFilter, FileFilter toFileExistFilter, boolean syncTimestamp) throws IOException {
        if (fromFileAcceptFilter != null) {
            if (!fromFileAcceptFilter.accept(fromFile)) {
                log.debug("copyFile{}:[{}]->[{}] ignore fromFile", syncTimestamp ? "(syncTimestamp)" : "", fromFile, toFile);
                // 没在fromFileFilter允许范围内
                return;
            }
        }
        if (toFileExistFilter != null) {
            if (!toFileExistFilter.accept(toFile)) {
                log.debug("copyFile{}:[{}]->[{}] ignore toFile", syncTimestamp ? "(syncTimestamp)" : "", fromFile, toFile);
                // 没在toFileExistFilter允许范围内
                return;
            }
        }
        if (toFile.isDirectory()) {
            // 文件 -----> 文件夹：把文件复制到指定文件夹上，文件名使用原文件名
            // 注意这里肯定是说明toFile已经存在了，也就是 目标是一个已经存在的文件夹
            toFile = new File(toFile, fromFile.getName());
            copyFile(fromFile, toFile, fromFileAcceptFilter, toFileExistFilter, syncTimestamp);
        }

        FileInputStream fis = new FileInputStream(fromFile);// 这里报FileNotFoundException
        FileOutputStream fos = null;
        boolean success = false;
        Throwable error = null;
        try {
            fos = new FileOutputStream(toFile);
        } catch (FileNotFoundException e) {
            File parentFile = toFile.getParentFile();
            if (parentFile == null) {
                final IOException ioException = new IOException("parent file is null for " + toFile.getPath(), e);
                throw ioException;
            }
            ensureDirExists(toFile);
            fos = new FileOutputStream(toFile);
        }

        try {
            if (FILE_CHANNEL_TRANSFER_BROKEN || fromFile.length() > CHANNELS_COPYING_LIMIT) {
                try {
                    transfer(fis, fos);
                    success = true;
                } finally {
                    fis.close();
                    fos.close();
                }
            } else {
                FileChannel fromChannel = fis.getChannel();
                FileChannel toChannel = fos.getChannel();
                try {
                    fromChannel.transferTo(0, Long.MAX_VALUE, toChannel);
                    success = true;
                } finally {
                    fromChannel.close();
                    toChannel.close();
                }
            }
            if (syncTimestamp) {
                toFile.setLastModified(fromFile.lastModified());
            }
        } catch (IOException e) {
            error = e;
            throw e;
        } finally {
            if (success) {
                log.debug("copyFile{}:[{}]->[{}] OK", syncTimestamp ? "(syncTimestamp)" : "", fromFile, toFile);
            } else {
                log.debug("copyFile{}:[{}]->[{}] ERROR", syncTimestamp ? "(syncTimestamp)" : "", fromFile, toFile, error);
            }
        }
    }

    public static void copyDir(File fromDir, File toDir) throws IOException {
        copyDir(fromDir, toDir, defaultCopyFromFileAcceptFilter, defaultCopyToFileExistAcceptFilter, defaultCopySyncTimestamp);
    }

    public static void copyDir(File fromDir, File toDir, FileFilter fromFileAcceptFilter, FileFilter toFileExistFilter, boolean syncTimestamp) throws IOException {
        if (fromFileAcceptFilter != null && !fromFileAcceptFilter.accept(fromDir)) {// 不符合来源文件夹过滤器规则，直接忽略
            return;
        }
        if (toDir.isFile()) {// 说明toDir已经存在，而且已是个文件
            // 文件夹 ----> 文件 不允许
            throw new IllegalAccessError("sourceFile isDir[" + fromDir + "],while targetFile existed and isFile[" + toDir + "]");
        }
        toDir.mkdirs();// 这里直接认为toDir是一个文件夹形式
        if (syncTimestamp) {
            toDir.setLastModified(fromDir.lastModified());
        }

        if (isAncestor(fromDir, toDir, true)) {
            log.error("{} is ancestor of {}. Can't copy to itself.", new Object[] {
                fromDir.getAbsolutePath(),
                toDir.getAbsolutePath()
            });
            return;
        }
        File[] files = fromDir.listFiles();
        if (files == null) {
            throw new IOException("directory is invalid:" + fromDir.getPath()); // 发生io错误，或者根本不是 文件夹时
        }
        if (!fromDir.canRead()) {
            throw new IOException("directory is not readable:" + fromDir.getPath());
        }

        log.debug("copyDir{}:[{}]->[{}] begin", syncTimestamp ? "(syncTimestamp)" : "", fromDir, toDir);
        for (File file : files) {
            if (file.isDirectory()) {
                copyDir(file, new File(toDir, file.getName()), fromFileAcceptFilter, toFileExistFilter, syncTimestamp);
            } else {
                copyFile(file, new File(toDir, file.getName()), fromFileAcceptFilter, toFileExistFilter, syncTimestamp);
            }
        }
    }

    /**
     * <pre>
     * 其他待选方法名：moveFile org.apache.uima.pear.util.FileUtils public static boolean moveFile(File source, File target) throws IOException {
     */
    // com.intellij.openapi.util.io.FileUtils.moveDirWithContent
    // public static boolean moveDirWithContent(File fromDir, File toDir) {
    // if (!toDir.exists())
    // return fromDir.renameTo(toDir);
    //
    // File[] files = fromDir.listFiles();
    // if (files == null)
    // return false;
    //
    // boolean success = true;
    //
    // for (File fromFile : files) {
    // File toFile = new File(toDir, fromFile.getName());
    // success = success && fromFile.renameTo(toFile);
    // }
    // fromDir.delete();
    //
    // return success;
    // }
    public static void move(File source, File target) throws IOException {
        move(source, target, defaultCopyFromFileAcceptFilter, defaultCopyToFileExistAcceptFilter);
    }

    public static void move(File source, File target, FileFilter fromFileAcceptFilter, FileFilter toFileExistFilter) throws IOException {
        /**
         * <pre>
         * org.apache.hadoop.fs.FileUtils renameTo() has two limitations on Windows platform. src.renameTo(target) fails if 1) If target already exists OR 2) If target is already open for
         * reading/writing.
         */
        String info = "";
        if (source.renameTo(target)) {
            log.debug("move:[{}]->[{}] By renameTo OK", new Object[] {
                source,
                target
            });
        } else if (!source.exists()) {
            log.warn("move:[{}]->[{}] FOUND SOURCE NONEXIST", new Object[] {
                source,
                target
            });
        } else {
            // 如果重命名不成功，则先复制再删除原文件，此实现待讨论
            try {
                log.debug("move:[{}]->[{}] by copy-delete BEGIN", new Object[] {
                    source,
                    target
                });
                copy(source, target, fromFileAcceptFilter, toFileExistFilter, true);// 这里移动操作时默认是要复制时间戳的
                deleteForce(source);
                log.debug("move:[{}]->[{}] by copy-delete END", new Object[] {
                    source,
                    target
                });
            } catch (IOException e) {// TODO: 复制失败，要把复制的内容清掉，这里要考虑原来target就有的情况，不能把原来的删除掉
                // delete(target);
                throw e;
            }
        }
    }

    /*
     * ---------------------临时文件操作------------------------------------------------------------------------------------------
     */
    // public static File createTempFile(File directory) {
    // File f = null;
    //
    // try {
    // f = File.createTempFile(TMPFILENAME, "jar", directory);
    // } catch (IOException ioe) {
    // // Bug 4677074 ioe.printStackTrace();
    // // Bug 4677074 begin
    // // _logger.log(Level.SEVERE, "iplanet_util.io_exception", ioe);
    // // Bug 4677074 end
    // }
    //
    // f.deleteOnExit(); // just in case
    // return f;
    // }

    // public static File createTempFile(String prefix, String suffix) throws IOException {
    // String tempDirPath = System.getProperty("java.io.tmpdir");
    // if (tempDirPath == null)
    // tempDirPath = System.getProperty("user.home");
    // if (tempDirPath == null)
    // throw new IOException("could not find temporary directory");
    // File tempDir = new File(tempDirPath);
    // if (!tempDir.isDirectory())
    // throw new IOException("temporary directory not available");
    // return File.createTempFile(prefix, suffix, tempDir);
    // }

    /**
     * <pre>
     * org.nuiton.util.FileUtils Permet de creer un nouveu repertoire temporaire, l'effacement du répertoire est a la charge de l'appelant
     *
     * @param prefix le prefix du fichier
     * @param suffix le suffix du fichier
     * @param tmpdir le répertoire temporaire ou il faut creer le repertoire si null on utilise java.io.tmpdir
     * @return le fichier pointant sur le nouveau repertoire
     * @throws IOException if any io pb
     */
    static public File createTempDirectory(String prefix, String suffix, File tmpdir) throws IOException {
        if (tmpdir == null) {
            tmpdir = new File(System.getProperty("java.io.tmpdir"));
        }
        File result = new File(tmpdir, prefix + System.currentTimeMillis() + suffix);
        while (result.exists()) {
            result = new File(tmpdir, prefix + System.currentTimeMillis() + suffix);
        }
        if (!result.mkdirs()) {
            throw new IOException("Can't create temporary directory: " + result);
        }
        return result;
    }

    // org.xerial.util.FileUtils

    /**
     * Create a temporary folder with the given prefix
     *
     * @param dir target folder
     * @param prefix prefix of the directory name
     *
     * @return path to the created folder
     */
    public static File createTempDir(File dir, String prefix) {
        File tmpDir = new File(dir, prefix);
        int suffixNum = 1;
        while (tmpDir.exists()) {
            tmpDir = new File(dir, prefix + suffixNum++);
        }
        tmpDir.mkdirs();
        return tmpDir;
    }

    public static File createTempFile(File dir, String prefix, String suffix) throws IOException {
        return File.createTempFile(prefix, suffix, dir);
    }

    // org.jmeld.util.file.FileUtils
    public static File createTempFile(String prefix, String suffix) throws IOException {
        File file;
        file = File.createTempFile(prefix, suffix);
        file.deleteOnExit();
        return file;
    }

    /*
     * ---------------------文件夹列举------------------------------------------------------------------------------------------
     */
    /**
     * <pre>
     * 其他参考： org.apache.uima.pear.util.FileUtils public static File[] getDirectoryListing(File dir) //遍历列出所有文件 org.apache.maven.mercury.util.FileUtils public List<String> dirToList( File dir, boolean
     * includeDirs, boolean includeFiles )
     */

    /**
     * <pre>
     * 参考类：org.apache.ivy.util.FileUtils Returns a collection of all Files being contained in the given directory, recursively, including directories.
     *
     * @param dir The directory from which all files, including files in subdirectory) are extracted.
     * @param ignore a Collection of filenames which must be excluded from listing
     * @return A collectoin containing all the files of the given directory and it's subdirectories.
     */
    public static Collection<File> listFiles(File dir) {
        return listFiles(dir, new ArrayList<>(), allWithTrueFilter);
    }

    /**
     */
    public static Collection<File> listFiles(File dir, Collection<File> list, FileFilter acceptFilter) {
        File[] found = dir.listFiles(acceptFilter);
        if (found != null) {
            for (File file : found) {
                if (file.isDirectory()) {
                    list.add(file);
                    listFiles(file, list, acceptFilter);
                } else {
                    list.add(file);
                }
            }
        }
        return list;
    }

    /*
     * ---------------------文件特殊搜索功能------------------------------------------------------------------------------------------
     */
    // org.aspectj.util.FileUtils
    // /**
    // * Do line-based search for literal text in source file,
    // * returning line where found as a String
    // * in the form {sourcePath}:line:column submitted to the
    // * collecting parameter sink.
    // * Any error is rendered to String and returned as the result.
    // *
    // * @param sought the String text to seek in the file
    // * @param sources the List of String paths to the source files
    // * @param listAll if false, only list first match in file
    // * @param List sink the List for String entries of the form {sourcePath}:line:column
    // * @return String error if any, or add String entries to sink
    // */
    // public static String lineSeek(String sought, String sourcePath, boolean listAll,ArrayList sink) {

    /**
     * Use the linePattern to break the given CharBuffer into lines, applying the input pattern to each line to see if we have a match Code taken from :
     * http://java.sun.com/javase/6/docs/technotes/guides/io/example/Grep.java
     *
     * @param regex regex to search into file
     * @param cb nio buffer
     *
     * @return matching lines (or {code null} if no matching lines)
     * @since 1.1.2
     */
    public static List<CharSequence> grep(String regex, CharBuffer cb) {
        List<CharSequence> linesList = null;
        Pattern pattern = Pattern.compile(regex);
        Pattern linePattern = Pattern.compile(".*\r?\n");
        Matcher lm = linePattern.matcher(cb); // Line matcher
        Matcher pm = null; // Pattern matcher
        // int lines = 0;
        while (lm.find()) {
            // lines++;
            CharSequence cs = lm.group(); // The current line
            if (pm == null) {
                pm = pattern.matcher(cs);
            } else {
                pm.reset(cs);
            }
            if (pm.find()) {
                // init
                if (linesList == null) {
                    linesList = new ArrayList<CharSequence>();
                }
                linesList.add(cs);
            }
            if (lm.end() == cb.limit()) {
                break;
            }
        }
        return linesList;
    }

    /**
     * Java implementation for the unix grep command. Code taken from : http://java.sun.com/javase/6/docs/technotes/guides/io/example/Grep.java
     *
     * @param searchRegex regex to search into file
     * @param f file to search into
     * @param encoding encoding to use
     *
     * @return matching lines (or {code null} if no matching lines)
     * @since 1.1.2
     */
    public static List<CharSequence> grep(String searchRegex, File f, String encoding) throws IOException {
        List<CharSequence> lines = null;
        FileInputStream fis = null;
        FileChannel fc = null;
        try {
            // Open the file and then get a channel from the stream
            fis = new FileInputStream(f);
            fc = fis.getChannel();
            // Get the file's size and then map it into memory
            int sz = (int) fc.size();
            MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, sz);
            // Decode the file into a char buffer
            Charset charset = Charset.forName(encoding);
            CharsetDecoder decoder = charset.newDecoder();
            CharBuffer cb = decoder.decode(bb);
            // Perform the search
            lines = grep(searchRegex, cb);
        } finally {
            // Close the channel and the stream
            if (fc != null) {
                fc.close();
            }
            if (fis != null) {
                fis.close();
            }
        }

        return lines;
    }

    /**
     * Sed implementation for a single file. Oginal source code from http://kickjava.com/src/org/apache/lenya/util/SED.java.htm.
     *
     * @param searchRegex Prefix which shall be replaced
     * @param replace Prefix which is going to replace the original
     * @param file File which sed shall be applied
     * @param encoding charset encoding
     *
     * @since 1.1.2
     */
    public static void sed(String searchRegex, String replace, File file, String encoding) throws IOException {
        Pattern pattern = Pattern.compile(searchRegex);
        FileInputStream fis = null;
        String outString = null;
        try {
            // Open the file and then get a channel from the stream
            fis = new FileInputStream(file);
            FileChannel fc = fis.getChannel();
            // Get the file's size and then map it into memory
            int sz = (int) fc.size();
            MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, sz);
            // Decode the file into a char buffer
            // Charset and decoder for encoding
            Charset charset = Charset.forName(encoding);
            CharsetDecoder decoder = charset.newDecoder();
            CharBuffer cb = decoder.decode(bb);
            Matcher matcher = pattern.matcher(cb);
            outString = matcher.replaceAll(replace);
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
        if (outString != null) {
            PrintStream ps = null;
            try {
                ps = new PrintStream(new FileOutputStream(file));
                ps.print(outString);
                ps.close();
            } finally {
                if (ps != null) {
                    ps.close();
                }
            }
        }
    }

    private FileUtils() {
    }

    public static void main(String[] args) {
        // 文件-> 文件
        // String from = "C:\\a.txt";
        // String to = "C:\\b.txt";
        // copyFile(new File(from), new File(to), null, toFileErrorWhenExistFilter, false);

        // 文件 -> 文件夹
        // String from = "C:\\a.txt";
        // String to = "C:\\Windows";
        // copy(new File(from), new File(to), null, toFileErrorWhenExistFilter, false);

        // 文件夹 -> 文件
        // 直接报错
        // String from = "C:\\a";
        // String to = "C:\\b.txt";
        // copy(new File(from), new File(to), null, null, false);

        // 文件夹 -> 文件夹
        // String from = "C:\\a";
        // String to = "C:\\b";
        // copy(new File(from), new File(to), null, toFileIgnoreWhenExistFilter, false);
        //
        // delete(new File("C:\\c"));

        // move(new File("C:\\a"), new File("D:\\c"));
        // move(new File("C:\\a.txt"), new File("D:\\c.txt"));
        // move(new File("D:\\c.txt"), new File("C:\\c.txt"));

        // System.out.println(getFile("c:\\"));
        // System.out.println(removeFileExtension("c:\\xccx\\暮云春树asd.txt"));
        // System.out.println(getBaseName("c:\\xccx\\暮云春树asd.txt/"));
        // System.out.println(getBaseName("c:\\xccx\\暮云春树asd.txt"));
        // System.out.println(getFileName("c:\\xccx/暮云春树asd.txt\\asdfdsa/.txt"));
        // System.out.println(new File("c:\\xccx\\暮云春树asd.txt\\").getName());
        // System.out.println(getDirDepth(new File("J:\\Projects\\XLServerFramework\\ServerUtil\\logs")));

        // List<String> l = new ArrayList<String>();
        // l.add("adsfddsf");
        // l.add("sadffdassdfa");
        // appendLines(new File("c:/a.bb"), l, CharsetTools.UTF_8);

        // ensureFileExists(new File("c:/afdsf.a"));

        // System.out.println(new File("c://xcvxc/asdfasd.file").createNewFile());
        System.out.println(ensureFileExists(new File("c://xcvxc/asdfasd.file")));
        System.out.println(new File("c:\\", "d:/a/../b"));
        System.out.println(getNameWithoutExtension("zengdong"));
        System.out.println(getNameWithoutExtension("zengdong.txt"));
        System.out.println(getNameWithoutExtension(".zengdong"));
    }
    // /////////////////////////未知，或者不用
    // // org.apache.ivy.util.FileUtils
    // public static File resolveFile(File file, String filename) {
    // /*
    // * very simple resolveFile algorithm compared to what is done in Ant. It must be enough in most common cases though.
    // */
    // File f = new File(filename);
    // if (f.isAbsolute()) {
    // return f;
    // }
    // return new File(file, filename);
    // }
    //
    // // net.sf.jour.util.FileUtils
    // public static URL getFileLocation(String resource) {
    // return getFileLocation(resource, null);
    // }
    //
    // public static URL getFileLocation(String resource, ClassLoader loader) {
    // URL url = null;
    // if (loader != null) {
    // url = loader.getResource(resource);
    // }
    // if (url == null) {
    // ClassLoader loader2 = Thread.currentThread().getContextClassLoader();
    // if (loader2 != null) {
    // url = loader2.getResource(resource);
    // }
    // }
    // if (url == null) {
    // ClassLoader loader3 = FileUtils.class.getClassLoader();
    // url = loader3.getResource(resource);
    // }
    // if (url == null) {
    // url = FileUtils.class.getResource(resource);
    // }
    // if (url != null) {
    // log.debug("rc[" + resource + "] -> " + url.getFile());
    // }
    // return url;
    // }
    //
    // public static URL getFile(String fileName) {
    // return getFile(fileName, FileUtils.class.getClassLoader());
    // }
    //
    // public static URL getFile(String fileName, Object owner) {
    // URL url = owner.getClass().getResource(fileName);
    // if (url != null) {
    // return url;
    // }
    // return getFile(fileName, owner.getClass().getClassLoader());
    // }
    //
    // public static URL getFile(String fileName, ClassLoader loader) {
    // File file = new File(fileName);
    // if (!file.canRead()) {
    // URL location = getFileLocation(fileName, loader);
    // if (location != null) {
    // return location;
    // } else {
    // log.debug("[" + fileName + "] -> not found");
    // return null;
    // }
    // } else {
    // log.debug(fileName + "->" + file.getAbsolutePath());
    // try {
    // return new URL("file:" + file.getAbsolutePath());
    // } catch (MalformedURLException e) {
    // log.error("Error", e);
    // return null;
    // }
    // }
    // }
}

/**
 * <pre>
 *  参考的类：(http://grepcode.com/)
 *  已参考到：http://grepcode.com/search/?start=60&query=fileUtil&entity=type
 *
 *  org.aspectj.util.FileUtils
 *  fitnesse.util.FileUtils
 *  org.databene.commons.FileUtils
 *  org.nuiton.util.FileUtils
 *  org.openide.filesystems.FileUtils
 *  com.sun.enterprise.util.io.FileUtils
 *  org.apache.ivy.util.FileUtils
 *  org.aspectj.util.FileUtils
 *  org.apache.uima.pear.util.FileUtils
 *  org.hsqldb.lib.FileUtils
 *  net.sf.jour.util.FileUtils
 *  com.mysql.management.util.FileUtils
 *  com.jayway.maven.plugins.lab.FileUtils
 *  com.googlecode.mad.mvntools.common.core.FileUtils
 *  org.xerial.util.FileUtils
 *  org.sonatype.aether.util.FileUtils
 *  org.jmeld.util.file.FileUtils
 *  org.jboss.dna.common.util.FileUtils
 *  com.threerings.util.FileUtils
 *  com.samskivert.util.FileUtils
 *  net.sf.mmm.util.file.base.FileUtilImpl
 *  com.pyx4j.jni.FileUtils
 *  org.apache.maven.mercury.util.FileUtils
 *
 *
 *  其他没有引入的方法节选(去重后)
 *       org.aspectj.util.FileUtils
 *       创建临时文件夹 File createTempDirectory(String prefix,  String suffix) throws IOException {
 *       异步删除文件 （如果跟临时文件夹是同一个磁盘，就会异步删除，感觉用途不大）void asyncDelete( File file) {
 *       创建指定的有顺序的文件(原内部实现效率不高，用途也不大) String createSequentFileName(File aParentFolder, String aFilePrefix, String aExtension) {
 *       把url转成可以当成文件路径的形式(内部实现不是用UrlDecoder,测试是可以用UrlDecoder的,用途也不大) String unquote(String urlString) {
 *      private static void collectMatchedFiles(final File absoluteRoot, final File root, final Pattern pattern, final List<File> files) {
 *      public static String sanitizeFileName(String name) {
 *      public static boolean canExecute(File file) {
 *      public static void setReadOnlyAttribute(String path, boolean readOnlyStatus) throws IOException {
 *      public static void setExecutableAttribute(String path, boolean executableFlag) throws IOException {
 *      public static boolean processFilesRecursively(final File root, final Processor<File> processor) {
 *      public static File findFirstThatExist(String... paths) {
 *      public static List<File> findFilesByMask(Pattern pattern, File dir) {
 *
 *      org.databene.commons.FileUtils
 *      public static List<File> listFiles(File dir, String regex, boolean recursive, boolean acceptingFiles, boolean acceptingFolders) {
 *      private static List<File> addFilenames(File dir, FileFilter filter, boolean recursive, List<File> buffer) {
 *      public static String normalizeFilename(String rawName) {
 *
 *
 *      org.nuiton.util.FileUtils
 *      static public File getTempFile(String content, String fileSuffix) throws IOException {
 *      public static Map<File, List<CharSequence>> grep(String searchRegex, File rootDirectory, String fileRegex, String encoding) throws IOException {
 *      public static Map<File, List<CharSequence>> grep(String searchRegex, String fileRegex, String encoding) throws IOException {
 *      public static void sed(String searchRegex, String replace, File rootDirectory, String fileRegex, String encoding) throws IOException {
 *      public static void sed(String searchRegex, String replace, String fileRegex, String encoding) throws IOException {
 *
 *      org.openide.filesystems.FileUtils
 *      public static void copyAttributes (FileObject source, FileObject dest) throws IOException {
 *      public static void extractJar (final FileObject fo, final InputStream is) throws IOException {
 *      private static File normalizeFileOnWindows(final File file) {
 *      private static File normalizeSymLinkOnMac(final File file) throws IOException {
 *      private static File normalizeFileOnUnixAlike(File file) {
 *      public static FileObject getArchiveRoot (FileObject fo) {
 *      public static boolean isArchiveFile(FileObject fo) {
 *
 *      com.sun.enterprise.util.io.FileUtils
 *      public static String makeFriendlyFilenameExtension(String filename) {
 *      public static boolean isJar(String filename) isZip,isEar
 *      public static boolean whack(File parent) {
 *      private static boolean whackResolvedDirectory(File parent, Collection<File> undeletedFiles) {
 *      public static Set getAllFilesUnder(File directory, FilenameFilter filenameFilter) throws IOException { 方法名不易猜其含义
 *      public static File createTempFile(File directory) {
 *      public static File[] listAllFiles(File dirName, String ext) {
 *      public static void copyTree(File din, File dout)
 *      private static int doWithRetry(RetriableWork work) { 这个方法是用于内部如删除文件时，如果不成功sleep一段时间后再retry openFileOutputStream(File out)也用了此重试机制
 *
 *
 *      org.apache.ivy.util.FileUtils
 *      public static boolean copy(File src, File dest, CopyProgressListener l, boolean overwrite) 这个copy方法特别之处在于其有进度监听，一般情况下不需要
 *      public static String readEntirely(InputStream is) throws IOException { 也就是 读文件成字符串方法，方法名起得不好
 *
 *      org.aspectj.util.FileUtils 此FileUtil提供了很多字符串方法
 *      public static String flatten(String[] paths, String infix) {
 *      public static String normalizedPath(File file, File basedir) {
 *      public static String[] getPaths(List files) {
 *      public static String weakNormalize(String path) { 其实就是 让//变规范化
 *      public static File getBestFile(String[] paths) {
 *      public static void extractJar(String zipFile, String outDir) throws IOException {
 *      public static InputStream getStreamFromZip(String zipFile, String name) {
 *      static String randomFileString() {
 *      public static File getTempDir(String name) {
 *      public static File makeNewChildDir(File parent, String child) {
 *      public static File[] getBaseDirFiles(
 *      public static String fileToClassName(File basedir, File classFile) {
 *      public static void copyValidFiles(File fromFile, File toFile) throws IOException {
 *
 *
 *      org.apache.uima.pear.util.FileUtils
 *      public static Collection<File> createDirList(JarFile archive) throws IOException {其实就是 listFiles
 *      public static long extractDirectoryFromJar(JarFile jarFile, String dirPath, File targetDir)
 *      public static long extractFilesFromJar(JarFile jarFile, File targetDir, FileFilter filter) throws IOException {
 *      public static boolean isAsciiFile(File textFile) throws IOException {
 *      public static String[] loadListOfStrings(BufferedReader iStream) throws IOException {
 *      public static String[] loadListOfStrings(URL textFileURL) throws IOException {
 *      public static Properties loadPropertiesFromJar(String propFilePath, JarFile jarFile)
 *      public static String loadTextFileFromJar(String filePath, JarFile jarFile) throws IOException {
 *      public static int replaceStringInFile(File textFile, String subStringRegex, String replacement) throws IOException {
 *      public static SortedSet<File> sortFileListByTime(Collection<File> fileList) { 这个方法是对文件按时间戳进行排序
 *      public static File zipDirectory(File dir2zip) throws IOException {
 *      public static File zipFile(File file2zip) throws IOException {
 *
 *      org.hsqldb.lib.FileUtils
 *      public void removeElement(java.lang.String filename) 就是删除文件
 *      public static boolean exists(String fileName, boolean resource,Class cla) {
 *      public static File canonicalFile(File f) throws IOException { 提供了一组获得文件规范化的方法
 *
 *      net.sf.jour.util.FileUtils
 *      static public File[] sortFileListByDate(File[] children) { 也是时间戳排序
 *      public static URL getFileLocation(String resource) { 一组通过classLoader拿到url的方法
 *
 *      com.mysql.management.util.FileUtils
 *      public void addExecutableRights(File executable, PrintStream out,  PrintStream err) { 是内部调用Linux chmod方法，不通用
 *      public static boolean fileContains(String aFileName, String aSearchString) 文件搜索功能
 *
 *      com.googlecode.mad.mvntools.common.core.FileUtils
 *      public String loadFileFromJar(final String name, final Class root) {
 *
 *      org.xerial.util.FileUtils
 *      public static String md5sum(InputStream in) throws IOException { 此方法没必要在FileUtil上面做
 *      public static byte[] sha1hash(InputStream input) throws IOException {
 *
 *      org.jmeld.util.file.FileUtils
 *      public static void copy2(File src, File dst) inputOutputStream版的copy
 *      public static void copy(File src, File dst)  fileChannel版的copy
 *
 *      com.threerings.util.FileUtils
 *      public static long getOldestLastModified (File dir) 获得文件夹里面最老的文件
 *
 *      com.samskivert.util.FileUtils
 *      public static boolean unpackJar (JarFile jar, File target)
 *
 *      net.sf.mmm.util.file.base.FileUtilImpl
 *      private boolean tokenizePath(String path, List<PathSegment> list, PatternCompiler patternCompiler) {
 *      private void collectMatchingFiles(File cwd, PathSegment[] segments, int segmentIndex,FileType fileType, List<File> list) {
 *      public void setPermissions(File file, FileAccessPermissions permissions) {
 *      public FileAccessPermissions getPermissions(File file, FileAccessClass accessClass) {
 *      private String normalizePathInternal(String path, char slash) {
 *      public String getBasename(String filename) {
 *      public String getDirname(String filename) {
 *
 *      com.pyx4j.jni.FileUtils
 *      public static native long getFileCreationTimeStamp(String fileName); //不是原生的
 *      public static native int setFileCreationTimeStamp(String fileName, long sec);
 *      public static native int getLastError();
 *
 *      org.apache.hadoop.fs.FileUtils
 *      public static int chmod(String filename, String perm) throws IOException, InterruptedException {//内部是通过调用linux命令chmod来实现的，不通用
 *      public static String makeShellPath(String filename) throws IOException { //判断在windows里用cygwin的path
 *      public static void createHardLink(File target, File linkName) throws IOException {
 *      public static void unZip(File inFile, File unzipDir) throws IOException {
 *      public static void unTar(File inFile, File untarDir) throws IOException {
 *      public static int symLink(String target, String linkname) throws IOException{ // ln -s
 *      public static final File createLocalTempFile(final File basefile,final String prefix,final boolean isDeleteOnExit)
 *      public static void replaceFile(File src, File target) throws IOException {
 *
 *      org.apache.maven.mercury.util.FileUtils
 *      public static void writeAndSign( String fName, InputStream in, Set<StreamVerifierFactory> vFacs ) throws IOException, StreamObserverException 貌似是跟文件写后还加上了签名用于校验
 *      public static void sign( File f, Set<StreamVerifierFactory> vFacs, boolean recurse, boolean force ) throws IOException, StreamObserverException
 *      public static void verify( File f, Set<StreamVerifierFactory> vFacs, boolean recurse, boolean force ) throws IOException, StreamObserverException
 *      public static FileLockBundle lockDir( String dir, long millis, long sleepFor ) //新建一个dir.lock的文件，内部wile来锁一段时间，不清楚用途
 *      public static FileLockBundle lockDirNio( String dir, long millis, long sleepFor )
 *      public static synchronized void unlockDir( String dir )
 *      public static final void unZip( InputStream zipInputStream, File destDir )
 *
 *     fmpp.util.FileUtils
 *     public static String compressPath(String path, int maxPathLength) { 把path字符串按一定规则展示出来，如果过长，中间用...，不常用
 *     public static String removeSlashPrefix(String path) {
 *     public static String pathPatternToPerl5Regex(String text) {
 * </pre>
 **/

// 没有必要这么折腾，其实就是 new File(path).lastModified,另外默认值也应该跟着jdk走，是0
// /**
// * Gets file date and time.
// *
// * @param url The URL of the file for which date and time will be returned.
// * @return Returns long value which is the date and time of the file. If any error occures returns -1 (=no file date and time available).
// */
// // com.izforge.izpack.util.FileUtils
// public static long getFileDateTime(URL url) {
// if (url == null) {
// return -1;
// }
// String fileName = url.getFile();
// if (fileName.charAt(0) == '/' || fileName.charAt(0) == '\\') {
// fileName = fileName.substring(1, fileName.length());
// }
//
// try {
// File file = new File(fileName);
// // File name must be a file or a directory.
// if (!file.isDirectory() && !file.isFile()) {
// return -1;
// }
//
// return file.lastModified();
// } catch (java.lang.Exception e) { // Trap all Exception based exceptions and return -1.
// return -1;
// }
// }
// ----------------------------------------------------------------------------------------------------------------------
// 没有必要这么折腾，其实就是 file.getParent
// //org.aspectj.util.FileUtils
// public static File getDirectory(File f) {
// String filename = f.getAbsolutePath();
// return new File((new File(filename)).getParent());
// }
// ----------------------------------------------------------------------------------------------------------------------
// 没有看出此方法的价值
// /**
// * Gets an absolute file from a filename. In difference to File.isAbsolute() this method bases relative file names on a given base directory.
// *
// * @param filename The filename to build an absolute file from
// * @param basedir The base directory for a relative filename
// * @return The absolute file according to the described algorithm
// */
// com.izforge.izpack.util.FileUtils
// public static File getAbsoluteFile(String filename, String basedir) {
// if (filename == null) {
// return null;
// }
// File file = new File(filename);
// if (file.isAbsolute()) {
// return file;
// } else {
// return new File(basedir, file.getPath());
// }
// }
// ----------------------------------------------------------------------------------------------------------------------
// 不清楚用处,感觉同public static File getAbsoluteFile(String filename, String basedir) { 差不多，用处不大
// /**
// * Constructs an absolute path of a given object, located in a given root directory, based on its relative path in this directory.
// *
// * @param rootDir The given root directory.
// * @param relativePath The given relative path of the object.
// * @return The absolute path for the given object, located in the given root directory.
// */
// // org.apache.uima.pear.util.FileUtils
// @Deprecated
// public static String getAbsolutePath(File rootDir, String relativePath) {
// File object = new File(rootDir, relativePath);
// return object.getAbsolutePath();
// }
// ----------------------------------------------------------------------------------------------------------------------
// 用处不大，看实现应该是为了兼容jdk1.1而写的，罗嗦了
// org.hsqldb.lib.FileUtils
// /**
// * Retrieves the absolute path, given some path specification.
// *
// * @param path the path for which to retrieve the absolute path
// * @return the absolute path
// */
// public String getAbsolutePath(String path) { // 原名叫absolutePath// org.hsqldb.lib.FileUtils
// if (path == null)
// return null;
// return (new File(path)).getAbsolutePath();
// }
//
// /**
// * Retrieves the canonical file for the given file, in a JDK 1.1 complaint way.
// *
// * @param f the File for which to retrieve the absolute File
// * @return the canonical File
// */
// public File getCanonicalFile(File f) throws IOException {// 原名叫canonicalFile// org.hsqldb.lib.FileUtils
// if (f == null)
// return null;
// // return new File(f.getCanonicalPath());
// return f.getCanonicalFile();
// }
//
// /**
// * Retrieves the canonical file for the given path, in a JDK 1.1 complaint way.
// *
// * @param path the path for which to retrieve the canonical File
// * @return the canonical File
// */
// public File canonicalFile(String path) throws IOException {
// return new File(new File(path).getCanonicalPath());
// }
//
// /**
// * Retrieves the canonical path for the given File, in a JDK 1.1 complaint way.
// *
// * @param f the File for which to retrieve the canonical path
// * @return the canonical path
// */
// public String canonicalPath(File f) throws IOException {
// return f.getCanonicalPath();
// }
// /**
// * Retrieves the canonical path for the given path, in a JDK 1.1 complaint way.
// *
// * @param path the path for which to retrieve the canonical path
// * @return the canonical path
// */
// public String canonicalPath(String path) throws IOException {
// return new File(path).getCanonicalPath();
// }
// /**
// * Retrieves the canonical path for the given path, or the absolute path if attemting to retrieve the canonical path fails.
// *
// * @param path the path for which to retrieve the canonical or absolute path
// * @return the canonical or absolute path
// */
// public String canonicalOrAbsolutePath(String path) {
//
// try {
// return canonicalPath(path);
// } catch (Exception e) {
// return absolutePath(path);
// }
// }
// // org.hsqldb.lib.FileUtils
// /**
// * Return true or false based on whether the named file exists.
// */
// public boolean exists(String filename) {
// return (new File(filename)).exists();
// }
//
// public boolean exists(String fileName, boolean resource, Class cla) {
//
// if (fileName == null || fileName.length() == 0) {
// return false;
// }
//
// return resource ? null != cla.getResource(fileName) : exists(fileName);
// }
// ----------------------------------------------------------------------------------------------------------------------
// 用处不大，返回一个file列表，此方法没有想到用处在何方，待定
// /**
// * <pre>
// * Returns a list of Files composed of all directories being parent of file and child of root +
// * file and root themselves. Example: getPathFiles(new File("test"), new
// * File("test/dir1/dir2/file.txt")) => {new File("test/dir1"), new File("test/dir1/dir2"), new
// * File("test/dir1/dir2/file.txt") } Note that if root is not an ancester of file, or if root is
// * null, all directories from the file system root will be returned.
// */
// // org.apache.ivy.util.FileUtils
// public static List<File> getPathFiles(File root, File file) {
// List<File> ret = new ArrayList<File>();
// while (file != null && !file.getAbsolutePath().equals(root.getAbsolutePath())) {
// ret.add(file);
// file = file.getParentFile();
// }
// if (root != null) {
// ret.add(root);
// }
// Collections.reverse(ret);
// return ret;
// }
// ----------------------------------------------------------------------------------------------------------------------
// 完全没有看明白下方法是干嘛
// org.nuiton.util.FileUtils
// static public String extension(String name, String... extchars) {
// String result = "";
//
// if (extchars.length == 0) {
// extchars = new String[] { "." };
// }
// for (String extchar : extchars) {
// int pos = name.lastIndexOf(extchar);
// if (pos != -1) {
// result = name.substring(pos + extchar.length());
// break;
// }
// }
// return result;
// }
// static public String changeExtension(String name, String newExtension, String... extchars) throws IOException {
// String extension = extension(name, extchars);
// if (extension == null) {
// throw new IOException("Could not find extension for name " + name + " within " + Arrays.toString(extchars));
// }
// String nameWithoutExtension = name.substring(0, name.length() - extension.length());
// String newName = nameWithoutExtension + newExtension;
// return newName;
// }
// public static boolean walkAfter(File f, FileAction fileAction) {
// boolean result = fileAction.doAction(f);
// if (f.isDirectory()) {
// File list[] = f.listFiles();
// for (File aList : list) {
// result = result && walkAfter(aList, fileAction);
// }
// }
// return result;
// }
//
// public static boolean walkBefore(File f, FileAction fileAction) {
// boolean result = true;
// if (f.isDirectory()) {
// File list[] = f.listFiles();
// for (File aList : list) {
// result = result && walkBefore(aList, fileAction);
// }
// }
// return result && fileAction.doAction(f);
// }
// ----------------------------------------------------------------------------------------------------------------------
// 用处不大
// //com.intellij.openapi.util.io.FileUtils
//
// public static String nameToCompare( String name) {
// return (isFileSystemCaseSensitive ? name : name.toLowerCase()).replace('\\', '/');
// }
// public static boolean pathsEqual(String path1, String path2) {
// return isFileSystemCaseSensitive ? path1.equals(path2) : path1.equalsIgnoreCase(path2);
// }
// public static int comparePaths(String path1, String path2) {
// return isFileSystemCaseSensitive ? path1.compareTo(path2) : path1.compareToIgnoreCase(path2);
// }
// public static int pathHashCode(String path) {
// return isFileSystemCaseSensitive ? path.hashCode() : path.toLowerCase().hashCode();
// }
