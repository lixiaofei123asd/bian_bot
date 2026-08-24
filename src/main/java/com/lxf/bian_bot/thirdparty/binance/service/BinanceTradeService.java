package com.lxf.bian_bot.thirdparty.binance.service;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.thirdparty.binance.model.LeverageInfo;
import com.lxf.bian_bot.thirdparty.binance.model.OrderInfo;
import com.lxf.bian_bot.thirdparty.binance.model.PositionInfo;
import com.lxf.bian_bot.thirdparty.binance.enums.TimeInForce;
import com.lxf.bian_bot.thirdparty.binance.enums.TradingMarginType;
import java.util.List;

public interface BinanceTradeService {

    /** 程序启动时从 Binance 获取全部持仓，用于初始化 TradingState。 */
    List<PositionInfo> getPositions();

    /** 获取指定交易对当前真实持仓。 */
    PositionInfo getPositionInfo(String symbol);

    String getMarginType(String symbol);

    boolean setMarginType(String symbol, TradingMarginType tradingMarginType);

    LeverageInfo setLeverage(String symbol, Long leverage);

    OrderInfo createMarketOrder(String symbol, Side side, double usdtAmount);

    OrderInfo createLimitOrder(String symbol, Side side, TimeInForce timeInForce, double usdtAmount, double price);

    OrderInfo createLimitOrderAndTPSLForPrice(String symbol, Side side, TimeInForce timeInForce, double quantity, double price, double tp, double sl);

    OrderInfo createLimitOrderAndTPSLForProfitAndLoss(String symbol, Side side, TimeInForce timeInForce, double quantity, double price, double profitAmount, double lossAmount);

    OrderInfo createLimitOrderAndTPSLForPercentage(String symbol, Side side, TimeInForce timeInForce, double quantity, double price, double profitPercentage, double lossPercentage);

    OrderInfo closeMarketOrder(String symbol);
}
