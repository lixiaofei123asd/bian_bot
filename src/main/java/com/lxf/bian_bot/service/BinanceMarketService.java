package com.lxf.bian_bot.service;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Interval;
import com.lxf.bian_bot.model.BinanceKline;
import java.util.List;

public interface BinanceMarketService {

    /**
     * 获取 K 线
     *
     * @param symbol   交易对，例如 BTCUSDT
     * @param interval K线周期，例如 1m、5m、15m、1h
     * @param limit    获取数量
     */
    List<BinanceKline> getKlines(String symbol, Interval interval, Long limit);
}
