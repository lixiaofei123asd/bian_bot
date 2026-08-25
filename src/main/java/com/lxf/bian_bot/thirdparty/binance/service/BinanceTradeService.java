package com.lxf.bian_bot.thirdparty.binance.service;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.thirdparty.binance.enums.TimeInForce;
import com.lxf.bian_bot.thirdparty.binance.enums.TradingMarginType;
import com.lxf.bian_bot.thirdparty.binance.model.LeverageInfo;
import com.lxf.bian_bot.thirdparty.binance.model.OrderInfo;
import com.lxf.bian_bot.thirdparty.binance.model.PositionInfo;

import java.math.BigDecimal;
import java.util.List;

/** Binance 合约交易适配器：只负责执行已经确定的交易参数。 */
public interface BinanceTradeService {

    List<PositionInfo> getPositions();

    PositionInfo getPositionInfo(String symbol);

    String getMarginType(String symbol);

    boolean setMarginType(String symbol, TradingMarginType tradingMarginType);

    LeverageInfo setLeverage(String symbol, Long leverage);

    /** positionNotional 是仓位名义价值，不是保证金。 */
    OrderInfo createMarketOrder(String symbol, Side side, BigDecimal positionNotional);

    /** positionNotional 是仓位名义价值，不是保证金。 */
    OrderInfo createLimitOrder(String symbol, Side side, TimeInForce timeInForce,
                               BigDecimal positionNotional, BigDecimal price);

    /** 创建策略已经计算好的 TP/SL；本方法不计算保护价格。 */
    void createProtectionOrders(String symbol, Side openSide, BigDecimal quantity,
                                BigDecimal takeProfitPrice, BigDecimal stopLossPrice);

    /** 市价平掉当前仓位。 */
    OrderInfo closeMarketOrder(String symbol);
}
