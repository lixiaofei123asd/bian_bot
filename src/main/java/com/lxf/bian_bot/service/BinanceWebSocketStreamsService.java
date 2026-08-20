package com.lxf.bian_bot.service;

import com.lxf.bian_bot.model.BinanceBookTicker;
import com.lxf.bian_bot.model.BinanceKline;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface BinanceWebSocketStreamsService {

    /**
     * 建立连接
     */
    void connect();

    /**
     * 订阅单个交易对
     */
    void subscribeBookTicker(String symbol);

    /**
     * 批量订阅
     */
    void subscribeBookTicker(List<String> symbols);

    /**
     * 取消订阅
     */
    void unsubscribeBookTicker(String symbol);

    /**
     * 订阅K线
     */
    void subscribeKline(List<String> symbols, String interval);

    /**
     * 获取当前订阅
     */
    Set<String> getSubscribedSymbols();

    /**
     * 添加行情监听器
     */
    void addBookTickerListener(Consumer<BinanceBookTicker> listener);

    /**
     * 添加K线监听器
     */
    void addKlineListener(Consumer<BinanceKline> listener);

    /**
     * 是否连接
     */
    boolean isConnected();

    /**
     * 关闭
     */
    void close();
}
