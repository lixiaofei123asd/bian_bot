package com.lxf.bian_bot.thirdparty.binance.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Binance Futures User Data Stream WebSocket 客户端。
 *
 * <p>与市场行情 Streams 分开建立连接，专门接收账户、持仓和订单成交事件。
 */
@Slf4j
public class BinanceUserDataStreamClient implements WebSocket.Listener {

    private final String baseUrl;
    private final boolean proxyEnabled;
    private final String proxyHost;
    private final int proxyPort;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile WebSocket webSocket;
    private volatile boolean connected;
    private volatile Consumer<JsonNode> messageListener;
    private volatile Consumer<Throwable> errorListener;
    private volatile Runnable closeListener;

    public BinanceUserDataStreamClient(String baseUrl, boolean proxyEnabled,
                                       String proxyHost, int proxyPort) {
        this.baseUrl = baseUrl;
        this.proxyEnabled = proxyEnabled;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    public synchronized void connect(String listenKey) {
        close();

        if (listenKey == null || listenKey.isBlank()) {
            throw new IllegalArgumentException("listenKey 不能为空");
        }

        String url = buildUrl(listenKey);
        log.info("连接 Binance Futures User Data Stream: {}", url);

        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10));
        if (proxyEnabled) {
            log.info("User Data Stream 使用 HTTP 代理: {}:{}", proxyHost, proxyPort);
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
        }

        builder.build()
                .newWebSocketBuilder()
                .buildAsync(URI.create(url), this)
                .thenAccept(socket -> {
                    this.webSocket = socket;
                    this.connected = true;
                    log.info("Binance Futures User Data Stream 连接成功");
                })
                .exceptionally(throwable -> {
                    connected = false;
                    log.error("Binance Futures User Data Stream 连接失败", throwable);
                    Consumer<Throwable> listener = errorListener;
                    if (listener != null) {
                        listener.accept(throwable);
                    }
                    return null;
                });
    }

    private String buildUrl(String listenKey) {
        String normalized = baseUrl;
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // 市场 Streams 常用 /stream；Futures User Data Stream 使用 /ws/<listenKey>。
        if (normalized.endsWith("/stream")) {
            normalized = normalized.substring(0, normalized.length() - "/stream".length()) + "/ws";
        } else if (!normalized.endsWith("/ws")) {
            normalized = normalized + "/ws";
        }
        return normalized + "/" + listenKey;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        log.info("Binance User Data Stream onOpen");
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            JsonNode root = objectMapper.readTree(data.toString());
            Consumer<JsonNode> listener = messageListener;
            if (listener != null) {
                listener.accept(root);
            }
        } catch (Exception e) {
            log.error("解析 Binance User Data Stream 消息失败: {}", data, e);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        connected = false;
        log.error("Binance User Data Stream 发生错误", error);
        Consumer<Throwable> listener = errorListener;
        if (listener != null) {
            listener.accept(error);
        }
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        connected = false;
        log.warn("Binance User Data Stream 关闭: code={}, reason={}", statusCode, reason);
        Runnable listener = closeListener;
        if (listener != null) {
            listener.run();
        }
        return null;
    }

    public void close() {
        WebSocket socket = this.webSocket;
        this.webSocket = null;
        this.connected = false;
        if (socket != null) {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "client close");
            } catch (Exception e) {
                log.debug("关闭 User Data Stream WebSocket 异常", e);
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public void setMessageListener(Consumer<JsonNode> messageListener) {
        this.messageListener = messageListener;
    }

    public void setErrorListener(Consumer<Throwable> errorListener) {
        this.errorListener = errorListener;
    }

    public void setCloseListener(Runnable closeListener) {
        this.closeListener = closeListener;
    }
}
