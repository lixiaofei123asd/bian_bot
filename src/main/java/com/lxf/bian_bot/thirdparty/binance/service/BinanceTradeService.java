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

    /**
     * 兼容旧调用方。所有 double 参数都会在进入实现层之前转换为 BigDecimal，
     * 实际交易规则修正统一由 BinanceTradeServiceImpl 完成。
     */
    @Deprecated
    default OrderInfo createMarketOrder(String symbol, Side side, double usdtAmount) {
        return createMarketOrder(symbol, side, BigDecimal.valueOf(usdtAmount));
    }

    @Deprecated
    default OrderInfo createLimitOrder(String symbol, Side side, TimeInForce timeInForce,
                                       double usdtAmount, double price) {
        return createLimitOrder(symbol, side, timeInForce,
                BigDecimal.valueOf(usdtAmount), BigDecimal.valueOf(price));
    }

    @Deprecated
    default OrderInfo createLimitOrderAndTPSLForPrice(String symbol, Side side, TimeInForce timeInForce,
                                                       double quantity, double price, double tp, double sl) {
        return createLimitOrderAndTPSLForPrice(symbol, side, timeInForce,
                BigDecimal.valueOf(quantity), BigDecimal.valueOf(price),
                BigDecimal.valueOf(tp), BigDecimal.valueOf(sl));
    }

    @Deprecated
    default OrderInfo createLimitOrderAndTPSLForProfitAndLoss(String symbol, Side side, TimeInForce timeInForce,
                                                               double quantity, double price,
                                                               double profitAmount, double lossAmount) {
        return createLimitOrderAndTPSLForProfitAndLoss(symbol, side, timeInForce,
                BigDecimal.valueOf(quantity), BigDecimal.valueOf(price),
                BigDecimal.valueOf(profitAmount), BigDecimal.valueOf(lossAmount));
    }

    @Deprecated
    default OrderInfo createLimitOrderAndTPSLForPercentage(String symbol, Side side, TimeInForce timeInForce,
                                                            double quantity, double price,
                                                            double profitPercentage, double lossPercentage) {
        return createLimitOrderAndTPSLForPercentage(symbol, side, timeInForce,
                BigDecimal.valueOf(quantity), BigDecimal.valueOf(price),
                BigDecimal.valueOf(profitPercentage), BigDecimal.valueOf(lossPercentage));
    }
}
