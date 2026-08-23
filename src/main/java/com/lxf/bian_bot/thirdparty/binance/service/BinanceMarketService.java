package com.lxf.bian_bot.thirdparty.binance.service;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Interval;
import com.lxf.bian_bot.thirdparty.binance.model.Binance24hTicker;
import com.lxf.bian_bot.thirdparty.binance.model.BinanceBookTicker;
import com.lxf.bian_bot.thirdparty.binance.model.BinanceKline;
import com.lxf.bian_bot.thirdparty.binance.model.BinanceMarkPrice;
import com.lxf.bian_bot.thirdparty.binance.model.BinanceOrderBook;
import com.lxf.bian_bot.thirdparty.binance.model.BinancePriceTicker;
import com.lxf.bian_bot.thirdparty.binance.model.BinanceRecentTrade;
import java.util.List;

public interface BinanceMarketService {

    /**
     * 获取 K 线。
     *
     * @param symbol   交易对，例如 BTCUSDT
     * @param interval K线周期，例如 1s、1m、5m、15m、1h
     * @param limit    获取数量
     */
    List<BinanceKline> getKlines(String symbol, Interval interval, Long limit);

    /**
     * 强制从 Binance 获取最新 K 线，不读取本地缓存
     */
    List<BinanceKline> refreshLatestKlines(String symbol, Interval interval, Long limit);

    /**
     * 获取合约最新价格。
     *
     * @param symbol 交易对
     */
    BinancePriceTicker getPrice(String symbol);

    /**
     * 获取合约标记价格、指数价格和资金费率信息。
     *
     * @param symbol 交易对
     */
    BinanceMarkPrice getMarkPrice(String symbol);

    /**
     * 获取合约24小时价格变动统计。
     *
     * @param symbol 交易对
     */
    Binance24hTicker get24hTicker(String symbol);

    /**
     * 获取合约最优买卖盘口。
     *
     * @param symbol 交易对
     */
    BinanceBookTicker getBookTicker(String symbol);

    /**
     * 获取合约订单簿。
     *
     * @param symbol 交易对
     * @param limit  盘口档数，例如 5、10、20、50、100、500、1000
     */
    BinanceOrderBook getOrderBook(String symbol, Long limit);

    /**
     * 获取合约最近成交。
     *
     * @param symbol 交易对
     * @param limit  获取数量
     */
    List<BinanceRecentTrade> getRecentTrades(String symbol, Long limit);
}
