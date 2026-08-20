package com.lxf.bian_bot.service;


import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.model.ExchangeInfo;
import com.lxf.bian_bot.model.LeverageInfo;
import com.lxf.bian_bot.model.TradingMarginType;
import com.lxf.bian_bot.model.OrderInfo;
import com.lxf.bian_bot.model.PositionInfo;

import com.lxf.bian_bot.model.TimeInForce;
import java.util.List;

public interface BinanceTradeService {


    ExchangeInfo getExchangeInfo();


    /**
     * 获取当前保证金模式
     *
     * @param symbol 交易对，例如 BTCUSDT
     * @return 逐仓或者全仓
     */
    String getMarginType(String symbol);

    /**
     * 设置保证金模式
     *
     * @param symbol 交易对，例如 BTCUSDT
     * @param tradingMarginType 全仓，逐仓
     * @return 是否设置成功
     */
    boolean setMarginType(String symbol, TradingMarginType tradingMarginType);

    /**
     * 设置指定交易对杠杆
     *
     * @param symbol 交易对，例如 BTCUSDT
     * @param leverage 杠杆倍数，例如 20
     */
    LeverageInfo setLeverage(String symbol, Long leverage);


    /**
     * 市价开仓
     *
     * @param symbol 交易对，例如 BTCUSDT
     * @param side 方向，多还是空
     * @param usdtAmount 数量
     * @return 订单结果
     */
    OrderInfo createMarketOrder(String symbol, Side side, double usdtAmount);

    /**
     * 限价开仓
     *
     * @param symbol 交易对，例如 BTCUSDT
     * @param side 方向，多还是空
     * @param timeInForce 这个限价单挂出去以后，币安允许它活多久、怎么处理没成交的部分。
     * @param usdtAmount 数量
     * @param price 单价
     * @return 订单结果
     */
    OrderInfo createLimitOrder(String symbol, Side side, TimeInForce timeInForce, double usdtAmount, double price);

    /**
     * 限价开仓，并设置止盈止损
     *
     * @param symbol 交易对，例如 BTCUSDT
     * @param side 方向，多还是空
     * @param timeInForce 这个限价单挂出去以后，币安允许它活多久、怎么处理没成交的部分。
     * @param quantity 数量
     * @param price 单价
     * @param tp 止盈
     * @param sl 止损
     * @return 订单结果
     */
    OrderInfo createLimitOrderAndTPSL(String symbol, Side side, TimeInForce timeInForce, double quantity, double price, double tp, double sl);

    /**
     * 市价全部平仓
     *
     * @param symbol 交易对，例如 BTCUSDT
     * @return 订单结果
     */
    OrderInfo closeMarketOrder(String symbol);

    /**
     * 查询指定交易对当前持仓
     *
     * @param symbol 交易对
     * @return 持仓信息
     */
    PositionInfo getPosition(String symbol);

    /**
     * 查询全部持仓
     */
    List<PositionInfo> getAllPositions();

    /**
     * 查询订单
     */
    OrderInfo getOrder(String symbol, long orderId);

    /**
     * 取消订单
     */
    OrderInfo cancelOrder(String symbol, long orderId);
}
