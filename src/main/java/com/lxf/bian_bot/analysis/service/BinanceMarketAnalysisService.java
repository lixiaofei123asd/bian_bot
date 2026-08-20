package com.lxf.bian_bot.analysis.service;

import com.lxf.bian_bot.model.TradingDecision;

public interface BinanceMarketAnalysisService {

    /**
     * 分析指定交易对
     *
     * @param symbol 交易对
     * @return 交易决策
     */
    TradingDecision analyze(String symbol);
}
