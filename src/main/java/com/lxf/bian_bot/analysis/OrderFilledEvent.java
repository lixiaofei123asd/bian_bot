package com.lxf.bian_bot.analysis;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import java.math.BigDecimal;

/** 限价开仓订单成交事件。只描述成交事实，不包含任何 TP/SL 决策。 */
public record OrderFilledEvent(
        String symbol,
        long orderId,
        Side openSide,
        BigDecimal quantity,
        BigDecimal entryPrice
) {
}
