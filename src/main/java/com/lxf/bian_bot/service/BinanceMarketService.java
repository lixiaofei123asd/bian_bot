package com.lxf.bian_bot.service;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Interval;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Period;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceLongShortRatio;
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

    /**
     * 获取指定时间范围 K 线
     */
    List<BinanceKline> getKlines(String symbol, Interval interval, Long startTime, Long endTime, Long limit);

    /**
     * 获取最新 K 线
     */
    BinanceKline getLatestKline(String symbol, Interval interval);

    /**
     * 强制从 Binance 获取最新 K 线，不读取本地缓存
     */
    List<BinanceKline> refreshLatestKlines(String symbol, Interval interval, Long limit);

    /**
     * 获取多空账户比
     *
     * @param symbol 交易对
     * @param period 统计周期
     * @param limit  返回数量
     */
    List<BinanceLongShortRatio> getLongShortRatio(String symbol, Period period, Long limit);
}
