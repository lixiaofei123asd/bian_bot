package com.lxf.bian_bot.service.impl;

import com.lxf.bian_bot.cache.KlineCache;
import com.lxf.bian_bot.cache.TickerCache;
import com.lxf.bian_bot.config.BinanceProperties;
import com.lxf.bian_bot.model.BinanceBookTicker;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.service.BinanceMarketService;
import com.lxf.bian_bot.service.BinanceWebSocketStreamsService;
import com.lxf.bian_bot.websocket.BinanceWebSocketStreamsClient;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceWebSocketStreamsServiceImpl implements BinanceWebSocketStreamsService {

    private static final String KLINE_INTERVAL = "1m";
    private static final int KLINE_CACHE_SIZE = 200;
    private final BinanceWebSocketStreamsClient webSocketClient;
    private final BinanceProperties properties;
    private final BinanceMarketService marketService;
    private final TickerCache tickerCache;
    private final KlineCache klineCache;
    private final List<Consumer<BinanceBookTicker>> bookTickerListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<BinanceKline>> klineListeners = new CopyOnWriteArrayList<>();

    public BinanceWebSocketStreamsServiceImpl(BinanceWebSocketStreamsClient webSocketClient, BinanceProperties properties, BinanceMarketService marketService, TickerCache tickerCache, KlineCache klineCache) {
        this.webSocketClient = webSocketClient;
        this.properties = properties;
        this.marketService = marketService;
        this.tickerCache = tickerCache;
        this.klineCache = klineCache;
        this.webSocketClient.setBookTickerListener(this::handleBookTicker);
        this.webSocketClient.setKlineListener(this::handleKline);
    }

    @PostConstruct
    public void init() {
        initializeKlineCache();
        webSocketClient.connect();
        webSocketClient.subscribeBookTicker(properties.getSymbols());
        webSocketClient.subscribeKline(properties.getSymbols(), KLINE_INTERVAL);
        log.info("Binance WebSocket Streams 启动完成，订阅交易对: {}", properties.getSymbols());
    }

    @Override
    public void connect() {
        webSocketClient.connect();
    }

    /**
     * 单币种订阅
     */
    @Override
    public void subscribeBookTicker(String symbol) {
        webSocketClient.subscribeBookTicker(symbol);
    }

    /**
     * 多币种订阅
     */
    @Override
    public void subscribeBookTicker(List<String> symbols) {
        webSocketClient.subscribeBookTicker(symbols);
    }

    /**
     * 取消订阅
     */
    @Override
    public void unsubscribeBookTicker(String symbol) {
        webSocketClient.unsubscribeBookTicker(symbol);
    }

    /**
     * 订阅K线
     */
    @Override
    public void subscribeKline(List<String> symbols, String interval) {
        webSocketClient.subscribeKline(symbols, interval);
    }

    /**
     * 获取当前订阅
     */
    @Override
    public Set<String> getSubscribedSymbols() {
        return webSocketClient.getSubscribedSymbols();
    }

    /**
     * 添加行情监听器
     */
    @Override
    public void addBookTickerListener(Consumer<BinanceBookTicker> listener) {
        bookTickerListeners.add(listener);
    }

    /**
     * 添加K线监听器
     */
    @Override
    public void addKlineListener(Consumer<BinanceKline> listener) {
        klineListeners.add(listener);
    }

    @Override
    public boolean isConnected() {
        return webSocketClient.isConnected();
    }

    @Override
    public void close() {
        webSocketClient.close();
    }

    /**
     * 初始化K线缓存
     */
    private void initializeKlineCache() {
        log.info("========================================");
        log.info("开始初始化 Binance K线缓存");
        log.info("========================================");
        for (String symbol : properties.getSymbols()) {
            try {
                List<BinanceKline> klines = marketService.getKlines(symbol, com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Interval.INTERVAL_1m, (long) KLINE_CACHE_SIZE);
                klineCache.put(symbol, KLINE_INTERVAL, klines);
                log.info("{} {} K线缓存初始化完成: {} 条", symbol, KLINE_INTERVAL, klines.size());
            } catch (Exception e) {
                log.error("{} {} K线缓存初始化失败", symbol, KLINE_INTERVAL, e);
            }
        }
    }

    /**
     * Binance行情处理
     */
    private void handleBookTicker(BinanceBookTicker ticker) {
        if (ticker == null) {
            return;
        }
        tickerCache.update(ticker);
        for (Consumer<BinanceBookTicker> listener : bookTickerListeners) {
            try {
                listener.accept(ticker);
            } catch (Exception e) {
                log.error("BookTicker Listener 执行异常", e);
            }
        }
    }

    /**
     * Binance K线处理
     */
    private void handleKline(BinanceKline kline) {
        if (kline == null) {
            return;
        }
        String symbol = kline.getSymbol();
    }
}
