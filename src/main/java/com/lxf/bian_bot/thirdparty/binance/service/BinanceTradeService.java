package com.lxf.bian_bot.thirdparty.binance.service;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.thirdparty.binance.enums.TimeInForce;
import com.lxf.bian_bot.thirdparty.binance.enums.TradingMarginType;
import com.lxf.bian_bot.thirdparty.binance.model.LeverageInfo;
import com.lxf.bian_bot.thirdparty.binance.model.OrderInfo;
import com.lxf.bian_bot.thirdparty.binance.model.PositionInfo;
import java.math.BigDecimal;
import java.util.List;

public interface BinanceTradeService {

    List<PositionInfo> getPositions();

    PositionInfo getPositionInfo(String symbol);

    String getMarginType(String symbol);

    boolean setMarginType(String symbol, TradingMarginType tradingMarginType);

    LeverageInfo setLeverage(String symbol, Long leverage);

    OrderInfo createMarketOrder(String symbol, Side side, BigDecimal usdtAmount);

    OrderInfo createLimitOrder(String symbol, Side side, TimeInForce timeInForce,
                               BigDecimal usdtAmount, BigDecimal price);

    OrderInfo createLimitOrderAndTPSLForPrice(String symbol, Side side, TimeInForce timeInForce,
                                               BigDecimal quantity, BigDecimal price,
                                               BigDecimal tp, BigDecimal sl);

    OrderInfo createLimitOrderAndTPSLForProfitAndLoss(String symbol, Side side, TimeInForce timeInForce,
                                                      BigDecimal quantity, BigDecimal price,
                                                      BigDecimal profitAmount, BigDecimal lossAmount);

    OrderInfo createLimitOrderAndTPSLForPercentage(String symbol, Side side, TimeInForce timeInForce,
                                                   BigDecimal quantity, BigDecimal price,
                                                   BigDecimal profitPercentage, BigDecimal lossPercentage);

    OrderInfo closeMarketOrder(String symbol);
}
