package org.etnaframework.plugin.websocket;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.UpgradeResponse;
import org.eclipse.jetty.websocket.api.WebSocketBehavior;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.server.WebSocketServerFactory;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.etnaframework.core.spring.annotation.OnContextInited;
import org.etnaframework.core.util.ThreadUtils;
import org.etnaframework.core.web.HttpEvent;
import org.etnaframework.core.web.cmd.HttpCmd;
import org.springframework.http.HttpStatus;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

/**
 * Websocket的父类
 * <p>
 * 按实现的需要覆盖相应的方法
 * <p>
 * 路径通过在class上的@CmdPath来指定
 * <p>
 * Created by yuanhaoliang on 2016-09-24.
 */
public abstract class WebsocketCmd extends HttpCmd implements WebSocketCreator {

    protected static final int KB = 1024;

    /**
     * 消息最大支持大小，单位：字节
     * <p>
     * Default: 65536 (64 K)
     */
    private static final long Default_MaxMessageSize = 64 * KB;

    /**
     * websocket的空闲超时时间，单位：毫秒
     * <p>
     * Default: 300000 (ms)
     */
    private static final long Default_IdleTimeoutMS = 300 * 1000;

    /**
     * 从网络层读取的缓存大小，单位：字节
     * <p>
     * Default: 4096 (4 K)
     */
    private static final int Default_InputBufferSize = 4 * KB;

    /** 维护session对应关系map,有可能同一个userId会有多个连接，所以用了multimap */
    protected Multimap<Object, WebsocketEvent> sessionTokenId2EventMap = Multimaps.newMultimap(Maps.newConcurrentMap(), () -> Sets.newConcurrentHashSet());

    /** websocket 连接生成工厂类 */
    private WebSocketServerFactory webSocketServerFactory;

    /**
     * 消息最大支持大小，如需自定义，子类覆盖返回
     * 单位：字节
     */
    protected long getMaxMessageSize() {
        return Default_MaxMessageSize;
    }

    /**
     * 空闲的websocket超时时间，如需自定义，子类覆盖返回
     * 单位：毫秒
     */
    protected long getIdleTimeoutMS() {
        return Default_IdleTimeoutMS;
    }

    /**
     * 读取缓存大小，如需自定义，子类覆盖返回
     * 单位：字节
     */
    protected int getInputBufferSize() {
        return Default_InputBufferSize;
    }

    @OnContextInited
    protected final void init() {
        try {
            WebSocketPolicy policy = new WebSocketPolicy(WebSocketBehavior.SERVER);

            // 初始化
            policy.setIdleTimeout(getIdleTimeoutMS());
            policy.setMaxMessageSize(getMaxMessageSize());
            policy.setInputBufferSize(getInputBufferSize());

            webSocketServerFactory = new WebSocketServerFactory(policy);
            webSocketServerFactory.setCreator(this);
            webSocketServerFactory.init();

            // 每5分钟检查连接是否有效，无效则移除
            ThreadUtils.getCron().scheduleWithFixedDelay(() -> {
                Iterator<WebsocketEvent> iterator = sessionTokenId2EventMap.values().iterator();

                while (iterator.hasNext()) {
                    WebsocketEvent event = iterator.next();
                    try {
                        if (event.isNotConnected()) {
                            iterator.remove();
                            event.close();
                        }
                    } catch (Exception ignore) {
                    }
                }
            }, 5L, 5L, TimeUnit.MINUTES);
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * websocket请求处理
     */
    @Override
    public final void index(HttpEvent he) throws Throwable {

        HttpServletRequest request = he.getHttpServletRequest();
        HttpServletResponse response = he.getHttpServletResponse();
        if (webSocketServerFactory.isUpgradeRequest(request, response)) {
            // 接收到一个要求upgrade的请求
            if (webSocketServerFactory.acceptWebSocket(request, response)) {
                // 成功建立websocket实例
                he.setAccessLogContent("websocket_created.");
                return;
            }
            // 如果到达这里，表示获取到一个upgrade请求
            // 但可能不是一个正确的websocket upgrade，或者被拒绝连接
            // 这是由于WebSocketCreator（本类的createWebSocket方法）限制的请求

            if (response.isCommitted()) {
                // 一般到达不了这里
                return;
            }
        }
        // 默认返回一个错误的请求
        he.setStatus(HttpStatus.BAD_REQUEST);
        he.writeText("Bad Request.");
    }

    /**
     * 创建新的websocket连接
     * 这里可以获取到连接的HttpHeader、Cookie、HttpParam等信息，
     * 在创建连接前，可以用于鉴权，如果返回null，则拒绝连接。
     */
    @Override
    public Object createWebSocket(UpgradeRequest req, UpgradeResponse resp) {
        return new WebsocketEvent(req, resp, this);
    }

    /**
     * 接收到websocket关闭事件
     *
     * @param statusCode 关闭事件的状态码 . (See {@link StatusCode})
     * @param reason 可选的关闭事件原因，可能为null
     */
    public void onWebSocketClose(WebsocketEvent event, int statusCode, String reason) {
        this.sessionTokenId2EventMap.remove(event.getTokenId(), event);
    }

    /**
     * 接收到websocket连接事件
     */
    public void onWebSocketConnect(Session session, WebsocketEvent event) {
        try {
            event.setTokenId(session2TokenId(session, event));
        } catch (Throwable t) {
            // 任何错误都关闭连接
            try {
                session.close(-1, t.getMessage());
            } catch (IOException ignore) {
            }
            return;
        }
        this.sessionTokenId2EventMap.put(event.getTokenId(), event);
    }

    /**
     * 接收到一个websocket的二进制数据帧
     *
     * @param payload 原生的二进制数据
     * @param offset 数据的下标开始点
     * @param len 二进制数据中数据的个数
     */
    public void onWebSocketBinary(WebsocketEvent event, byte[] payload, int offset, int len) {
    }

    /**
     * websocket发生错误
     * <p>
     * 在处理websocket时发生的错误
     * 通常是因为不正确的数据包导致（例如：非UTF8数据，数据帧过大，空间分配不足等原因）
     * 会导致session断开。触发onWebSocketClose事件
     *
     * @param cause 发生的错误
     */
    public void onWebSocketError(WebsocketEvent event, Throwable cause) {
    }

    /**
     * 接收到一个字符串
     */
    public void onWebSocketText(WebsocketEvent event, String message) {
    }

    /**
     * session转代表session的识别符，例如userId等
     * <p>
     * 覆盖此方法，实现自定义token
     */
    public Object session2TokenId(Session session, WebsocketEvent event) {
        return session;
    }
}
