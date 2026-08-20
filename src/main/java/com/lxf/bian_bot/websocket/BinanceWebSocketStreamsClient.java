package com.lxf.bian_bot.websocket;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lxf.bian_bot.model.BinanceBookTicker;
import com.lxf.bian_bot.model.BinanceStreamMessage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
public class BinanceWebSocketStreamsClient implements WebSocket.Listener {

    private final String url;
    private final boolean proxyEnabled;
    private final String proxyHost;
    private final int proxyPort;
    private final ObjectMapper objectMapper;
    private volatile WebSocket webSocket;
    @Getter
    private volatile boolean connected = false;
    @Setter
    private Consumer<BinanceBookTicker> bookTickerListener;
    private final Set<String> subscribedSymbols = ConcurrentHashMap.newKeySet();
    private volatile long requestId = 1;


    public BinanceWebSocketStreamsClient(String url, boolean proxyEnabled, String proxyHost, int proxyPort) {
        this.url = url;
        this.proxyEnabled = proxyEnabled;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.objectMapper = new ObjectMapper();
    }


    /**
     * 连接 Binance
     */
    public synchronized void connect() {
        if (connected) {
            log.info("Binance WebSocket 已经连接，无需重复连接");
            return;
        }
        log.info("连接 Binance WebSocket Streams: {}", url);
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
        if (proxyEnabled) {
            log.info("使用 HTTP 代理: {}:{}", proxyHost, proxyPort);
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
        } else {
            log.info("WebSocket 不使用代理");
        }

        HttpClient client = builder.build();
        client.newWebSocketBuilder()
                .buildAsync(URI.create(url), this)
                .thenAccept(socket -> {
                    this.webSocket = socket;
                    this.connected = true;
                    log.info("Binance WebSocket Streams 连接成功");
                    //自动恢复订阅
                    restoreSubscriptions();
                })
                .exceptionally(throwable -> {
                    this.connected = false;
                    log.error("Binance WebSocket Streams 连接失败", throwable);
                    return null;
                });
    }


    /**
     * 订阅单个交易对
     */
    public void subscribeBookTicker(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol 不能为空");
        }
        symbol = symbol.trim().toUpperCase();
        if (subscribedSymbols.contains(symbol)) {
            log.info("{} 已经订阅，跳过", symbol);
            return;
        }


        // 先记录订阅 这样即使断线， 重连以后也知道需要恢复。
        subscribedSymbols.add(symbol);

        // 当前没有连接
        if (!connected || webSocket == null) {
            log.warn("Binance WebSocket 尚未连接，{} 已加入订阅列表，连接后自动订阅", symbol);
            return;
        }
        sendSubscribe(symbol);
    }

    // 批量订阅
    public void subscribeBookTicker(Iterable<String> symbols) {
        if (symbols == null) {
            return;
        }
        for (String symbol : symbols) {
            subscribeBookTicker(symbol);
        }
    }

    /**
     * 发送 SUBSCRIBE
     */
    private void sendSubscribe(String symbol) {
        String stream = symbol.toLowerCase() + "@bookTicker";
        long id = requestId++;
        String request =
                """
                        {
                          "method": "SUBSCRIBE",
                          "params": ["%s"],
                          "id": %d
                        }
                        """
                        .formatted(stream, id);
        log.info("发送 Binance SUBSCRIBE: {}", request);
        webSocket.sendText(request, true);
    }

    /**
     * 恢复所有订阅
     */
    private void restoreSubscriptions() {
        if (subscribedSymbols.isEmpty()) {
            log.info("没有需要恢复的订阅");
            return;
        }
        log.info("开始恢复 {} 个订阅", subscribedSymbols.size());
        for (String symbol : subscribedSymbols) {
            sendSubscribe(symbol);
        }
    }

    /**
     * 获取当前订阅
     */
    public Set<String> getSubscribedSymbols() {
        return Collections.unmodifiableSet(subscribedSymbols);
    }

    /**
     * 取消订阅
     */
    public void unsubscribeBookTicker(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        symbol = symbol.trim().toUpperCase();
        subscribedSymbols.remove(symbol);
        if (!connected || webSocket == null) {
            return;
        }
        String stream = symbol.toLowerCase() + "@bookTicker";
        long id = requestId++;
        String request =
                """
                        {
                          "method": "UNSUBSCRIBE",
                          "params": ["%s"],
                          "id": %d
                        }
                        """
                        .formatted(stream, id);
        log.info("发送 Binance UNSUBSCRIBE: {}", request);


        webSocket.sendText(request, true);
    }


    /**
     * WebSocket建立
     */
    @Override
    public void onOpen(WebSocket webSocket) {
        log.info("Binance WebSocket onOpen");
        webSocket.request(1);
    }

    /**
     * 收到消息
     */
    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        String message = data.toString();
        try {
            log.debug("Binance WebSocket 收到消息: {}", message);
            // 订阅响应
            if (message.contains("\"result\"")) {
                log.info("Binance 订阅响应: {}", message);
                webSocket.request(1);
                return null;
            }

            /**
             * Combined Stream
             */
            JavaType type = objectMapper.getTypeFactory().constructParametricType(BinanceStreamMessage.class, BinanceBookTicker.class);
            BinanceStreamMessage<BinanceBookTicker> streamMessage = objectMapper.readValue(message, type);
            BinanceBookTicker ticker = streamMessage.getData();
            if (ticker != null && bookTickerListener != null) {
                bookTickerListener.accept(ticker);
            }
        } catch (Exception e) {
            log.error("解析 Binance WebSocket 消息失败: {}", message, e);
        }
        webSocket.request(1);
        return null;
    }

    /**
     * WebSocket错误
     */
    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        connected = false;
        log.error("Binance WebSocket 发生错误", error);
    }

    /**
     * WebSocket关闭
     */
    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        connected = false;
        log.warn("Binance WebSocket 关闭: code={}, reason={}", statusCode, reason);
        return null;
    }

    /**
     * 关闭
     */
    public void close() {
        subscribedSymbols.clear();
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "client close");
            webSocket = null;
        }
        connected = false;
    }
}
