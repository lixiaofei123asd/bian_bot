package com.lxf.bian_bot.thirdparty.binance.service.impl;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Interval;
import com.lxf.bian_bot.cache.KlineCache;
import com.lxf.bian_bot.cache.SecondKlineAggregator;
import com.lxf.bian_bot.cache.SecondKlineCache;
import com.lxf.bian_bot.cache.TickerCache;
import com.lxf.bian_bot.config.binance.BinanceProperties;
import com.lxf.bian_bot.config.SymbolRuleCache;
import com.lxf.bian_bot.model.BinanceAggTrade;
import com.lxf.bian_bot.thirdparty.binance.model.BinanceBookTicker;
import com.lxf.bian_bot.thirdparty.binance.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceSecondKline;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceMarketService;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceWebSocketStreamsService;
import com.lxf.bian_bot.thirdparty.binance.websocket.BinanceWebSocketStreamsClient;
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
    private final SymbolRuleCache symbolRuleCache;
    private final TickerCache tickerCache;
    private final KlineCache klineCache;
    private final SecondKlineAggregator secondKlineAggregator;
    private final SecondKlineCache secondKlineCache;
    private final List<Consumer<BinanceBookTicker>> bookTickerListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<BinanceKline>> klineListeners = new CopyOnWriteArrayList<>();

    public BinanceWebSocketStreamsServiceImpl(
            BinanceWebSocketStreamsClient webSocketClient,
            BinanceProperties properties,
            BinanceMarketService marketService,
            SymbolRuleCache symbolRuleCache,
            TickerCache tickerCache,
            KlineCache klineCache,
            SecondKlineAggregator secondKlineAggregator,
            SecondKlineCache secondKlineCache) {
        this.webSocketClient = webSocketClient;
        this.properties = properties;
        this.marketService = marketService;
        this.symbolRuleCache = symbolRuleCache;
        this.tickerCache = tickerCache;
        this.klineCache = klineCache;
        this.secondKlineAggregator = secondKlineAggregator;
        this.secondKlineCache = secondKlineCache;
        this.webSocketClient.setBookTickerListener(this::handleBookTicker);
        this.webSocketClient.setKlineListener(this::handleKline);
        this.webSocketClient.setAggTradeListener(this::handleAggTrade);
    }

    @PostConstruct
    public void init() {
        List<String> symbols = getValidSymbols();
        initializeKlineCache(symbols);
        webSocketClient.connect();
        webSocketClient.subscribeBookTicker(symbols);
        webSocketClient.subscribeKline(symbols, KLINE_INTERVAL);
        webSocketClient.subscribeAggTrade(symbols);
        log.info("Binance WebSocket Streams 启动完成，订阅交易对: {}", symbols);
        log.info("1秒K线缓存已启用，数据来源: Binance aggTrade Stream");
    }

    @Override
    public void connect() {
        webSocketClient.connect();
    }

    @Override
    public void subscribeBookTicker(String symbol) {
        if (!isValidSymbol(symbol)) {
            log.warn("交易对不存在，跳过 BookTicker 订阅: {}", symbol);
            return;
        }
        webSocketClient.subscribeBookTicker(symbol);
    }

    @Override
    public void subscribeBookTicker(List<String> symbols) {
        webSocketClient.subscribeBookTicker(symbolRuleCache.filterValidSymbols(symbols));
    }

    @Override
    public void unsubscribeBookTicker(String symbol) {
        webSocketClient.unsubscribeBookTicker(symbol);
    }

    @Override
    public void subscribeKline(List<String> symbols, String interval) {
        webSocketClient.subscribeKline(symbolRuleCache.filterValidSymbols(symbols), interval);
    }

    @Override
    public void subscribeAggTrade(List<String> symbols) {
        webSocketClient.subscribeAggTrade(symbolRuleCache.filterValidSymbols(symbols));
    }

    @Override
    public List<BinanceSecondKline> getRecentSecondKlines(String symbol, int limit) {
        return secondKlineCache.getRecent(symbol, limit);
    }

    @Override
    public List<BinanceSecondKline> getRecentSecondKlinesIncludingCurrent(String symbol, int limit) {
        return secondKlineCache.getRecentIncludingCurrent(symbol, limit);
    }

    @Override
    public Set<String> getSubscribedSymbols() {
        return webSocketClient.getSubscribedSymbols();
    }

    @Override
    public void addBookTickerListener(Consumer<BinanceBookTicker> listener) {
        bookTickerListeners.add(listener);
    }

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

    private void initializeKlineCache(List<String> symbols) {
        log.info("========================================");
        log.info("开始初始化 Binance K线缓存");
        log.info("========================================");
        for (String symbol : symbols) {
            try {
                List<BinanceKline> klines = marketService.getKlines(symbol, Interval.INTERVAL_1m, (long) KLINE_CACHE_SIZE);
                klineCache.put(symbol, KLINE_INTERVAL, klines);
                log.info("{} {} K线缓存初始化完成: {} 条", symbol, KLINE_INTERVAL, klines.size());
            } catch (Exception e) {
                log.error("{} {} K线缓存初始化失败", symbol, KLINE_INTERVAL, e);
            }
        }
    }

    private List<String> getValidSymbols() {
        List<String> configuredSymbols = properties.getSymbols();
        List<String> validSymbols = symbolRuleCache.filterValidSymbols(configuredSymbols);
        List<String> invalidSymbols = configuredSymbols.stream()
                .map(String::trim)
                .filter(symbol -> !symbol.isEmpty())
                .filter(symbol -> !validSymbols.contains(symbol.toUpperCase()))
                .distinct()
                .toList();
        if (!invalidSymbols.isEmpty()) {
            log.warn("以下交易对在 Binance 交易规则中不存在，已跳过: {}", invalidSymbols);
        }
        return validSymbols;
    }

    private boolean isValidSymbol(String symbol) {
        return symbol != null && symbolRuleCache.get(symbol.toUpperCase()) != null;
    }

    private void handleBookTicker(BinanceBookTicker ticker) {
        if (ticker == null) return;
        tickerCache.update(ticker);
        for (Consumer<BinanceBookTicker> listener : bookTickerListeners) {
            try {
                listener.accept(ticker);
            } catch (Exception e) {
                log.error("BookTicker Listener 执行异常", e);
            }
        }
    }

    private void handleKline(BinanceKline kline) {
        if (kline == null || kline.getSymbol() == null) return;
        klineCache.update(kline.getSymbol(), KLINE_INTERVAL, kline, KLINE_CACHE_SIZE);
        log.debug("Binance K线缓存更新: symbol={}, openTime={}, closePrice={}", kline.getSymbol(), kline.getOpenTime(), kline.getClosePrice());
        for (Consumer<BinanceKline> listener : klineListeners) {
            try {
                listener.accept(kline);
            } catch (Exception e) {
                log.error("Kline Listener 执行异常", e);
            }
        }
    }

    /**
     * Binance aggTrade -> 本地 1 秒 K线。
     */
    private void handleAggTrade(BinanceAggTrade trade) {
        if (trade == null || trade.getSymbol() == null) return;
        secondKlineAggregator.accept(trade);
        log.trace("1秒K线成交更新: symbol={}, price={}, quantity={}, tradeTime={}",
                trade.getSymbol(), trade.getPrice(), trade.getQuantity(), trade.getTradeTime());
    }
}
