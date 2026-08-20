package com.lxf.bian_bot.service.impl;

import com.lxf.bian_bot.analysis.indicator.IndicatorMa25;
import com.lxf.bian_bot.analysis.indicator.IndicatorMa7;
import com.lxf.bian_bot.analysis.strategy.Strategy;
import com.lxf.bian_bot.analysis.strategy.Strategy1;
import com.lxf.bian_bot.analysis.strategy.Strategy2;
import com.lxf.bian_bot.analysis.strategy.TradingSignal;
import com.lxf.bian_bot.cache.KlineCache;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceMarketAnalysis;
import com.lxf.bian_bot.service.BinanceMarketAnalysisService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceMarketAnalysisServiceImpl implements BinanceMarketAnalysisService {

    private static final String KLINE_INTERVAL = "1m";

    private final KlineCache klineCache;
    private final IndicatorMa7 indicatorMa7;
    private final IndicatorMa25 indicatorMa25;
    private final List<Strategy> strategies;

    public BinanceMarketAnalysisServiceImpl(KlineCache klineCache) {
        this.klineCache = klineCache;
        this.indicatorMa7 = new IndicatorMa7();
        this.indicatorMa25 = new IndicatorMa25();
        this.strategies = List.of(new Strategy1(), new Strategy2());
    }

    @Override
    public BinanceMarketAnalysis analyze(String symbol) {
        log.info("========== 开始分析 {} ==========", symbol);

        // 1. 从本地K线缓存获取数据
        List<BinanceKline> klines = klineCache.get(symbol, KLINE_INTERVAL);
        log.debug("从 KlineCache 获取 K线: symbol={}, interval={}, size={}", symbol, KLINE_INTERVAL, klines.size());

        if (klines.isEmpty()) {
            log.warn("{} KlineCache 中没有 K线数据", symbol);
            return new BinanceMarketAnalysis();
        }

        // 2. 创建分析结果
        BinanceMarketAnalysis analysis = new BinanceMarketAnalysis();
        analysis.setSymbol(symbol);
        analysis.setCurrentPrice(klines.get(klines.size() - 1).getClosePrice());
        analysis.setEma7(indicatorMa7.calculate(klines));
        analysis.setEma25(indicatorMa25.calculate(klines));

        // 3. 执行策略
        TradingSignal signal = TradingSignal.WAIT;

        for (Strategy strategy : strategies) {
            TradingSignal strategySignal = strategy.execute(klines);
            log.info("策略计算结果: symbol={}, strategy={}, signal={}, requiredMatchCount={}", symbol, strategy.getClass().getSimpleName(), strategySignal, strategy.getRequiredMatchCount());

            if (strategySignal == TradingSignal.LONG) {
                signal = TradingSignal.LONG;
                break;
            }

            if (strategySignal == TradingSignal.SHORT) {
                signal = TradingSignal.SHORT;
            }
        }

        // 4. 保存最终信号
        analysis.setSignal(signal);

        log.info("========== 分析结束 {}，最终信号={} ==========", symbol, signal);
        return analysis;
    }
}
