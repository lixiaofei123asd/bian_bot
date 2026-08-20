package com.lxf.bian_bot.service.impl;

import com.lxf.bian_bot.cache.TickerCache;
import com.lxf.bian_bot.config.BinanceProperties;
import com.lxf.bian_bot.model.BinanceBookTicker;
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
    private final BinanceWebSocketStreamsClient webSocketClient;
    private final BinanceProperties properties;
    private final TickerCache tickerCache;
    private final List<Consumer<BinanceBookTicker>> listeners = new CopyOnWriteArrayList<>();

    public BinanceWebSocketStreamsServiceImpl(BinanceWebSocketStreamsClient webSocketClient, BinanceProperties properties, TickerCache tickerCache) {
        this.webSocketClient = webSocketClient;
        this.properties = properties;
        this.tickerCache = tickerCache;
        this.webSocketClient.setBookTickerListener(this::handleBookTicker);
    }

    @PostConstruct
    public void init() {
        webSocketClient.connect();
        webSocketClient.subscribeBookTicker(properties.getSymbols());
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
     * 获取当前订阅
     */
    @Override
    public Set<String> getSubscribedSymbols() {
        return webSocketClient.getSubscribedSymbols();
    }

    /**
     * 添加监听器
     */
    @Override
    public void addBookTickerListener(Consumer<BinanceBookTicker> listener) {
        listeners.add(listener);
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
     * Binance行情处理
     * <p>
     * Client
     * ↓
     * Service
     * ↓
     * Cache
     * ↓
     * Listener
     */
    private void handleBookTicker(BinanceBookTicker ticker) {
        if (ticker == null) {
            return;
        }

        //1. 更新最新行情
        tickerCache.update(ticker);

        //2.通知其他业务
        for (Consumer<BinanceBookTicker> listener : listeners) {
            try {
                listener.accept(ticker);
            } catch (Exception e) {
                log.error("BookTicker Listener 执行异常", e);
            }
        }
    }
}
