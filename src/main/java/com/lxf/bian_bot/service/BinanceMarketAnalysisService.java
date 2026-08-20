package com.lxf.bian_bot.service;

import com.lxf.bian_bot.model.BinanceMarketAnalysis;

public interface BinanceMarketAnalysisService {

    /**
     * 分析指定交易对
     *
     * @param symbol 交易对
     */
    BinanceMarketAnalysis analyze(String symbol);
}
