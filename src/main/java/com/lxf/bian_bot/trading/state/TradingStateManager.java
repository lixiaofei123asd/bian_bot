package com.lxf.bian_bot.trading.state;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * TradingState 访问入口。
 *
 * <p>账户、持仓和订单的实时状态由 Binance User Data Stream 更新到 TradingState。
 * 本类不再定时调用 REST 查询账户、持仓和委托，避免与 User Data Stream 产生两套状态来源。
 */
@Service
@RequiredArgsConstructor
public class TradingStateManager {

    private final TradingState tradingState;

    public TradingState getState() {
        return tradingState;
    }
}
