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

    String getMarginType(String symbol);

    boolean setMarginType(String symbol, TradingMarginType tradingMarginType);

    LeverageInfo setLeverage(String symbol, Long leverage);

    OrderInfo createMarketOrder(String symbol, Side side, double usdtAmount);

    OrderInfo createLimitOrder(String symbol, Side side, TimeInForce timeInForce, double usdtAmount, double price);

    OrderInfo createLimitOrderAndTPSL(String symbol, Side side, TimeInForce timeInForce, double quantity, double price, double tp, double sl);

    OrderInfo closeMarketOrder(String symbol);

    PositionInfo getPosition(String symbol);

    List<PositionInfo> getAllPositions();

    List<OrderInfo> getOpenOrders(String symbol);

    OrderInfo getOrder(String symbol, long orderId);

    OrderInfo cancelOrder(String symbol, long orderId);
}
