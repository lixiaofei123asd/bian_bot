package com.lxf.bian_bot.thirdparty.binance.service;


import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.thirdparty.binance.model.LeverageInfo;
import com.lxf.bian_bot.thirdparty.binance.model.OrderInfo;
import com.lxf.bian_bot.thirdparty.binance.enums.TimeInForce;
import com.lxf.bian_bot.thirdparty.binance.enums.TradingMarginType;

public interface BinanceTradeService {


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
     * 限价开仓，并按照指定价格设置止盈止损
     *
     * @param symbol 交易对，例如 BTCUSDT
     * @param side 方向，多还是空
     * @param timeInForce 这个限价单挂出去以后，币安允许它活多久、怎么处理没成交的部分。
     * @param quantity 数量
     * @param price 开仓价格
     * @param tp 止盈价格
     * @param sl 止损价格
     * @return 订单结果
     */
    OrderInfo createLimitOrderAndTPSLForPrice(String symbol, Side side, TimeInForce timeInForce, double quantity, double price, double tp, double sl);

    /**
     * 限价开仓，并按照盈利/亏损金额设置止盈止损
     *
     * @param symbol 交易对，例如 BTCUSDT
     * @param side 方向，多还是空
     * @param timeInForce 这个限价单挂出去以后，币安允许它活多久、怎么处理没成交的部分。
     * @param quantity 数量
     * @param price 开仓价格
     * @param profitAmount 盈利金额，单位 USDT
     * @param lossAmount 亏损金额，单位 USDT
     * @return 订单结果
     */
    OrderInfo createLimitOrderAndTPSLForProfitAndLoss(String symbol, Side side, TimeInForce timeInForce, double quantity, double price, double profitAmount, double lossAmount);

    /**
     * 限价开仓，并按照盈利/亏损百分比设置止盈止损
     *
     * @param symbol 交易对，例如 BTCUSDT
     * @param side 方向，多还是空
     * @param timeInForce 这个限价单挂出去以后，币安允许它活多久、怎么处理没成交的部分。
     * @param quantity 数量
     * @param price 开仓价格
     * @param profitPercentage 盈利百分比，例如 10 表示 10%
     * @param lossPercentage 亏损百分比，例如 5 表示 5%
     * @return 订单结果
     */
    OrderInfo createLimitOrderAndTPSLForPercentage(String symbol, Side side, TimeInForce timeInForce, double quantity, double price, double profitPercentage, double lossPercentage);

    /**
     * 市价全部平仓
     *
     * @param symbol 交易对，例如 BTCUSDT
     * @return 订单结果
     */
    OrderInfo closeMarketOrder(String symbol);
}
