package com.lxf.bian_bot.thirdparty.binance.service;

import com.lxf.bian_bot.thirdparty.binance.model.BinanceBookTicker;
import com.lxf.bian_bot.thirdparty.binance.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceSecondKline;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface BinanceWebSocketStreamsService {

    void connect();

    void subscribeBookTicker(String symbol);

    void subscribeBookTicker(List<String> symbols);

    void unsubscribeBookTicker(String symbol);

    void subscribeKline(List<String> symbols, String interval);

    /**
     * 订阅聚合成交，用于本地生成 1 秒 K 线。
     */
    void subscribeAggTrade(List<String> symbols);

    /**
     * 获取最近已经完成的 1 秒 K 线。
     */
    List<BinanceSecondKline> getRecentSecondKlines(String symbol, int limit);

    /**
     * 获取最近 1 秒 K 线，同时包含当前正在形成的 K 线。
     */
    List<BinanceSecondKline> getRecentSecondKlinesIncludingCurrent(String symbol, int limit);

    Set<String> getSubscribedSymbols();

    void addBookTickerListener(Consumer<BinanceBookTicker> listener);

    void addKlineListener(Consumer<BinanceKline> listener);

    boolean isConnected();

    void close();
}
