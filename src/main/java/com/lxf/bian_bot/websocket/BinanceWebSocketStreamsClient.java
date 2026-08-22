package com.lxf.bian_bot.websocket;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lxf.bian_bot.model.BinanceAggTrade;
import com.lxf.bian_bot.model.BinanceBookTicker;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceStreamMessage;
import java.math.BigDecimal;
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
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

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
    @Setter
    private Consumer<BinanceKline> klineListener;
    @Setter
    private Consumer<BinanceAggTrade> aggTradeListener;
    private final Set<String> subscribedSymbols = ConcurrentHashMap.newKeySet();
    private final Set<String> subscribedKlineStreams = ConcurrentHashMap.newKeySet();
    private final Set<String> subscribedAggTradeStreams = ConcurrentHashMap.newKeySet();
    private volatile long requestId = 1;

    public BinanceWebSocketStreamsClient(String url, boolean proxyEnabled, String proxyHost, int proxyPort) {
        this.url = url;
        this.proxyEnabled = proxyEnabled;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.objectMapper = new ObjectMapper();
    }

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
        client.newWebSocketBuilder().buildAsync(URI.create(url), this).thenAccept(socket -> {
            this.webSocket = socket;
            this.connected = true;
            log.info("Binance WebSocket Streams 连接成功");
            restoreSubscriptions();
        }).exceptionally(throwable -> {
            this.connected = false;
            log.error("Binance WebSocket Streams 连接失败", throwable);
            return null;
        });
    }

    public void subscribeBookTicker(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol 不能为空");
        }
        symbol = symbol.trim().toUpperCase();
        if (subscribedSymbols.contains(symbol)) {
            log.info("{} 已经订阅，跳过", symbol);
            return;
        }
        subscribedSymbols.add(symbol);
        if (!connected || webSocket == null) {
            log.warn("Binance WebSocket 尚未连接，{} 已加入订阅列表，连接后自动订阅", symbol);
            return;
        }
        sendSubscribe(symbol.toLowerCase() + "@bookTicker");
    }

    public void subscribeBookTicker(Iterable<String> symbols) {
        if (symbols == null) return;
        for (String symbol : symbols) subscribeBookTicker(symbol);
    }

    public void subscribeKline(Iterable<String> symbols, String interval) {
        if (symbols == null || interval == null || interval.isBlank()) return;
        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) continue;
            String stream = symbol.trim().toLowerCase() + "@kline_" + interval.trim().toLowerCase();
            if (!subscribedKlineStreams.add(stream)) continue;
            if (!connected || webSocket == null) {
                log.warn("Binance WebSocket 尚未连接，K线订阅已加入列表: {}", stream);
                continue;
            }
            sendSubscribe(stream);
        }
    }

    /**
     * 订阅聚合成交流，用于本地生成 1 秒 K 线。
     */
    public void subscribeAggTrade(Iterable<String> symbols) {
        if (symbols == null) return;
        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) continue;
            String stream = symbol.trim().toLowerCase() + "@aggTrade";
            if (!subscribedAggTradeStreams.add(stream)) continue;
            if (!connected || webSocket == null) {
                log.warn("Binance WebSocket 尚未连接，aggTrade订阅已加入列表: {}", stream);
                continue;
            }
            sendSubscribe(stream);
        }
    }

    private void sendSubscribe(String stream) {
        long id = requestId++;
        String request = """
                {
                  "method": "SUBSCRIBE",
                  "params": ["%s"],
                  "id": %d
                }
                """.formatted(stream, id);
        log.info("发送 Binance SUBSCRIBE: {}", request);
        webSocket.sendText(request, true);
    }

    private void restoreSubscriptions() {
        if (!subscribedSymbols.isEmpty()) {
            log.info("开始恢复 {} 个BookTicker订阅", subscribedSymbols.size());
            for (String symbol : subscribedSymbols) sendSubscribe(symbol.toLowerCase() + "@bookTicker");
        }
        if (!subscribedKlineStreams.isEmpty()) {
            log.info("开始恢复 {} 个K线订阅", subscribedKlineStreams.size());
            for (String stream : subscribedKlineStreams) sendSubscribe(stream);
        }
        if (!subscribedAggTradeStreams.isEmpty()) {
            log.info("开始恢复 {} 个aggTrade订阅", subscribedAggTradeStreams.size());
            for (String stream : subscribedAggTradeStreams) sendSubscribe(stream);
        }
    }

    public Set<String> getSubscribedSymbols() {
        return Collections.unmodifiableSet(subscribedSymbols);
    }

    public void unsubscribeBookTicker(String symbol) {
        if (symbol == null || symbol.isBlank()) return;
        symbol = symbol.trim().toUpperCase();
        subscribedSymbols.remove(symbol);
        if (!connected || webSocket == null) return;
        String stream = symbol.toLowerCase() + "@bookTicker";
        long id = requestId++;
        String request = """
                {
                  "method": "UNSUBSCRIBE",
                  "params": ["%s"],
                  "id": %d
                }
                """.formatted(stream, id);
        log.info("发送 Binance UNSUBSCRIBE: {}", request);
        webSocket.sendText(request, true);
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        log.info("Binance WebSocket onOpen");
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        String message = data.toString();
        try {
            log.debug("Binance WebSocket 收到消息: {}", message);
            if (message.contains("\"result\"")) {
                log.info("Binance 订阅响应: {}", message);
                webSocket.request(1);
                return null;
            }
            JsonNode root = objectMapper.readTree(message);
            JsonNode dataNode = root.get("data");
            if (dataNode == null) {
                webSocket.request(1);
                return null;
            }
            String eventType = dataNode.path("e").asText();
            if ("bookTicker".equals(eventType)) {
                JavaType type = objectMapper.getTypeFactory().constructParametricType(BinanceStreamMessage.class, BinanceBookTicker.class);
                BinanceStreamMessage<BinanceBookTicker> streamMessage = objectMapper.readValue(message, type);
                BinanceBookTicker ticker = streamMessage.getData();
                if (ticker != null && bookTickerListener != null) bookTickerListener.accept(ticker);
            } else if ("kline".equals(eventType)) {
                BinanceKline kline = parseKline(dataNode.path("k"));
                if (kline != null && klineListener != null) klineListener.accept(kline);
            } else if ("aggTrade".equals(eventType)) {
                BinanceAggTrade trade = parseAggTrade(dataNode);
                if (trade != null && aggTradeListener != null) aggTradeListener.accept(trade);
            }
        } catch (Exception e) {
            log.error("解析 Binance WebSocket 消息失败: {}", message, e);
        }
        webSocket.request(1);
        return null;
    }

    private BinanceAggTrade parseAggTrade(JsonNode node) {
        if (node == null || node.isMissingNode()) return null;
        BinanceAggTrade trade = new BinanceAggTrade();
        trade.setSymbol(node.path("s").asText());
        trade.setAggregateTradeId(node.path("a").asLong());
        trade.setPrice(new BigDecimal(node.path("p").asText()));
        trade.setQuantity(new BigDecimal(node.path("q").asText()));
        trade.setTradeTime(node.path("T").asLong());
        return trade;
    }

    private BinanceKline parseKline(JsonNode node) {
        if (node == null || node.isMissingNode()) return null;
        BinanceKline kline = new BinanceKline();
        kline.setSymbol(node.path("s").asText());
        kline.setOpenTime(node.path("t").asLong());
        kline.setOpenPrice(new BigDecimal(node.path("o").asText()));
        kline.setHighPrice(new BigDecimal(node.path("h").asText()));
        kline.setLowPrice(new BigDecimal(node.path("l").asText()));
        kline.setClosePrice(new BigDecimal(node.path("c").asText()));
        kline.setVolume(new BigDecimal(node.path("v").asText()));
        kline.setCloseTime(node.path("T").asLong());
        kline.setQuoteAssetVolume(new BigDecimal(node.path("q").asText()));
        kline.setNumberOfTrades(node.path("n").asLong());
        kline.setTakerBuyBaseAssetVolume(new BigDecimal(node.path("V").asText()));
        kline.setTakerBuyQuoteAssetVolume(new BigDecimal(node.path("Q").asText()));
        return kline;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        connected = false;
        log.error("Binance WebSocket 发生错误", error);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        connected = false;
        log.warn("Binance WebSocket 关闭: code={}, reason={}", statusCode, reason);
        return null;
    }

    public void close() {
        subscribedSymbols.clear();
        subscribedKlineStreams.clear();
        subscribedAggTradeStreams.clear();
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "client close");
            webSocket = null;
        }
        connected = false;
    }
}
