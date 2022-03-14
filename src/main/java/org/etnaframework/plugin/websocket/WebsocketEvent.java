package org.etnaframework.plugin.websocket;

import java.io.Closeable;
import java.io.IOException;
import java.net.HttpCookie;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.UpgradeResponse;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.etnaframework.core.logging.Log;
import org.etnaframework.core.util.CollectionTools;
import org.etnaframework.core.util.DatetimeUtils;
import org.etnaframework.core.util.DatetimeUtils.Datetime;
import org.etnaframework.core.util.KeyValueGetter;
import org.etnaframework.core.util.StringTools;
import org.etnaframework.core.web.exception.ParamEmptyException;
import org.etnaframework.core.web.exception.ParamInvalidFormatException;
import org.slf4j.Logger;

/**
 * websocket 的请求封装类
 * Created by yuanhaoliang on 2016-09-24.
 */
public class WebsocketEvent implements WebSocketListener, Closeable, KeyValueGetter {

    private static final Logger logger = Log.getLogger();

    private WebsocketCmd cmd;

    /** websocket的session */
    private volatile Session session;

    private Object tokenId;

    private UpgradeRequest req;

    private UpgradeResponse resp;

    /** 请求中的cookies */
    private WrappedMap<String, HttpCookie> cookies;

    public WebsocketEvent(UpgradeRequest req, UpgradeResponse resp, WebsocketCmd cmd) {
        this.cmd = cmd;
        this.req=req;
        this.resp=resp;

        List<HttpCookie> co = req.getCookies();
        if (CollectionTools.isNotEmpty(co)) {
            cookies = new WrappedMap<String, HttpCookie>() {

                @Override
                public String getString(String key) {
                    HttpCookie c = get(key);
                    return null == c ? null : c.getValue();
                }
            };
            for (HttpCookie c : co) {
                cookies.put(c.getName(), c);
            }
        } else {
            cookies = WrappedMap.emptyMap();
        }
    }

    /**
     * <pre>
     * 获取请求参数及其值，可用于遍历，包含可从URL里提取的queryString和从POST CONTENT中提取的参数
     * 注意如果同一个key在请求参数中出现了多次，比如?key=1&key=2&key=3这样的请求，将只取第一个
     * </pre>
     */
    public Map<String, String> getRequestMap() {
        Map<String, String> map = new HashMap<String, String>();
        for (Entry<String, String[]> e : req.getParameterMap().entrySet()) {
            map.put(e.getKey(), e.getValue()[0].replace('\u00a0', ' '));
        }
        return map;
    }

    private String _getString(String key) {
        // 空格的ascii码值有两个：从键盘输入的空格ascii值为0x20；从网页上的&nbsp;字符表单提交而来的空格ascii值为0xa0
        // 为了能处理网页表单提交的空格0xa0，这里将其替换成普通的0x20空格方便接下来的处理
        String[] s = req.getParameterMap().get(key);
        String str = s == null || s.length == 0 ? null : s[0];
        return null == str ? null : str.replace('\u00a0', ' ');
    }

    /**
     * 从请求参数中获取字符串，要求参数中必须有key，值允许为空字符串("")，否则就提示错误
     */
    @Override
    public String getString(String key) {
        String value = _getString(key);
        if (null == value) {
            throw new ParamEmptyException(key);
        }
        return value.trim();
    }

    /**
     * 从请求参数中获取字符串，如果参数中没有key，就使用defaultValue
     */
    @Override
    public String getString(String key, String defaultValue) {
        String value = _getString(key);
        if (null == value) {
            return defaultValue;
        }
        return value.trim();
    }

    /**
     * 从请求参数中获取布尔值，要求参数中必须要有key，如果key对应的值为true/Y/y/1将返回true，其他情况返回false
     */
    @Override
    public Boolean getBool(String key) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("y") || value.equals("1")) {
            return true;
        }
        return false;
    }

    @Override
    public Boolean getBool(String key, Boolean defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        if (value.equals("true") || value.equalsIgnoreCase("y") || value.equals("1")) {
            return true;
        }
        return false;
    }

    @Override
    public Byte getByte(String key) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        try {
            return Byte.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidFormatException(key);
        }
    }

    @Override
    public Byte getByte(String key, Byte defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Byte.valueOf(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Character getChar(String key) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        return value.charAt(0);
    }

    @Override
    public Character getChar(String key, Character defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        return value.charAt(0);
    }

    @Override
    public Short getShort(String key) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        try {
            return Short.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidFormatException(key);
        }
    }

    @Override
    public Short getShort(String key, Short defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Short.valueOf(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Integer getInt(String key) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidFormatException(key);
        }
    }

    @Override
    public Integer getInt(String key, Integer defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Long getLong(String key) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidFormatException(key);
        }
    }

    @Override
    public Long getLong(String key, Long defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Float getFloat(String key) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        try {
            return Float.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidFormatException(key);
        }
    }

    @Override
    public Float getFloat(String key, Float defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Float.valueOf(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Double getDouble(String key) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ParamInvalidFormatException(key);
        }
    }

    @Override
    public Double getDouble(String key, Double defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Datetime getDate(String key) {
        return getDate(key, DatetimeUtils.getDefaultDatetimeFormat());
    }

    @Override
    public Datetime getDate(String key, Datetime defaultValue) {
        return getDate(key, DatetimeUtils.getDefaultDatetimeFormat(), defaultValue);
    }

    @Override
    public Datetime getDate(String key, String dateFormat) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            throw new ParamEmptyException(key);
        }
        Date date = DatetimeUtils.parse(value, dateFormat);
        if (null == date) {
            throw new ParamInvalidFormatException(key);
        }
        return new Datetime(date);
    }

    @Override
    public Datetime getDate(String key, String dateFormat, Datetime defaultValue) {
        String value = _getString(key);
        if (StringTools.isEmpty(value)) {
            return defaultValue;
        }
        Date date = DatetimeUtils.parse(value, dateFormat);
        if (null == date) {
            return defaultValue;
        }
        return new Datetime(date);
    }

    /**
     * 获取请求中的cookies，注意这里不会包含addCookie方法加入的内容！
     */
    public WrappedMap<String, HttpCookie> getCookies() {
        return cookies;
    }

    public RemoteEndpoint getRemote() {
        Session sess = this.session;
        return sess == null ? null : session.getRemote();
    }

    public Session getSession() {
        return session;
    }

    public boolean isConnected() {
        Session sess = this.session;
        return (sess != null) && (sess.isOpen());
    }

    public boolean isNotConnected() {
        Session sess = this.session;
        return (sess == null) || (!sess.isOpen());
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        cmd.onWebSocketBinary(this, payload, offset, len);
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        cmd.onWebSocketClose(this, statusCode, reason);
        this.session = null;
    }

    @Override
    public void onWebSocketConnect(Session session) {
        this.session = session; // 安全转换，jetty里只有这个实现类
        cmd.onWebSocketConnect(this.session, this);
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        cmd.onWebSocketError(this, cause);
    }

    @Override
    public void onWebSocketText(String message) {
        cmd.onWebSocketText(this, message);
    }

    /**
     * 关闭会话，指定Code和reason
     */
    public void close(int statusCode,String reason){
        try {
            getSession().close(statusCode,reason);
        } catch (IOException e) {
            logger.error("",e);
        }
        // 会触发 onWebSocketClose
    }

    /**
     * 关门会话，默认code=StatusCode.NORMAL=1000,reason=""
     */
    @Override
    public void close() {
        try {

            getSession().close();
        } catch (IOException e) {
            logger.error("",e);
        }
        // 会触发 onWebSocketClose
    }

    /**
     * 强行断开链接
     */
    public void disconnect(){
        try {
            getSession().disconnect();
        } catch (IOException e) {
            logger.error("",e);
        }
    }

    /**
     * [同步]向客户端发送文本
     *
     * @return true:发送成功,false:发送失败
     */
    public boolean sendString(String text) {
        try {

            if (isConnected()) {
                getRemote().sendString(text);
                return true;
            }else{
                close();
            }
        } catch (Exception e) {
            logger.error("",e);
            // 发送异常就关闭连接
            close();
        }
        return false;
    }

    /**
     * [异步]向客户端发送文本
     */
    public Future<Void> sendStringByFuture(String text) {
        if (isConnected()) {
            Future<Void> future = getRemote().sendStringByFuture(text);
            return future;
        }
        return null;
    }

    /**
     * [同步] 分块发送文本(大文本）
     *
     * @param fragment 文本内容
     * @param last 是否为文本的最后一段，true:是最后一段，整段文本已发送完；false:不是最后一段，还有要发
     *
     * @return true:发送成功,false:发送失败
     * <p>
     * <pre>
     * example:
     *     sendPartialString("aaaa",false);  // 还没发完
     *     sendPartialString("bbbb",true);   // 结束发送
     * </pre>
     */
    public boolean sendPartialString(String fragment, boolean last) {
        try {

            if (isConnected()) {
                getRemote().sendPartialString(fragment, last);
                return true;
            }else{
                close();
            }
        } catch (Exception e) {
            logger.error("",e);
            // 发送异常就关闭连接
            close();
        }
        return false;
    }

    /**
     * 获取event识别符
     */
    public Object getTokenId() {
        return tokenId;
    }

    /**
     * 设置event识别符
     */
    public void setTokenId(Object tokenId) {
        if (this.tokenId == null) {
            // 只初始化一次
            this.tokenId = tokenId;
        }
    }

    @Override
    public int hashCode() {
        return session != null ? session.hashCode() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        WebsocketEvent that = (WebsocketEvent) o;

        return session != null ? session.equals(that.session) : that.session == null;
    }
}
