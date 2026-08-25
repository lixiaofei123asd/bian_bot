package com.lxf.bian_bot.analysis;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import java.math.BigDecimal;

/**
 * 保护单创建时发现价格已经越过触发价的事件。
 *
 * <p>这里只描述事实，不执行平仓。是否退出由具体 Strategy 决定。</p>
 */
public record ProtectionTriggerEvent(
        String symbol,
        Side openSide,
        BigDecimal quantity,
        BigDecimal takeProfitPrice,
        BigDecimal stopLossPrice
) {
}
