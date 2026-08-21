package com.lxf.bian_bot.trading.service;

import com.lxf.bian_bot.model.TradingDecision;

public interface BinanceTradingService {

    // 执行交易决策
    void execute(TradingDecision decision);
}
