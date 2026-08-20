package com.lxf.bian_bot.service.impl;

import com.lxf.bian_bot.analysis.indicator.IndicatorMa25;
import com.lxf.bian_bot.analysis.indicator.IndicatorMa7;
import com.lxf.bian_bot.analysis.strategy.Strategy;
import com.lxf.bian_bot.analysis.strategy.Strategy1;
import com.lxf.bian_bot.analysis.strategy.Strategy2;
import com.lxf.bian_bot.analysis.strategy.TradingSignal;
import com.lxf.bian_bot.cache.KlineCache;
import com.lxf.bian_bot.config.StrategyProperties;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceMarketAnalysis;
import com.lxf.bian_bot.service.BinanceMarketAnalysisService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceMarketAnalysisServiceImpl implements BinanceMarketAnalysisService, InitializingBean {

    private static final String KLINE_INTERVAL = "1m";

    private final KlineCache klineCache;
    private final IndicatorMa7 indicatorMa7;
    private final IndicatorMa25 indicatorMa25;
    private final StrategyProperties strategyProperties;

    private Map<String, Strategy> strategies = Map.of();

    public BinanceMarketAnalysisServiceImpl(
            KlineCache klineCache,
            StrategyProperties strategyProperties) {
        this.klineCache = klineCache;
        this.indicatorMa7 = new IndicatorMa7();
        this.indicatorMa25 = new IndicatorMa25();
        this.strategyProperties = strategyProperties;
    }

    @Override
    public void afterPropertiesSet() {
        // 程序启动时自动实例化所有Strategy实现类
        List<Strategy> strategyList = new ArrayList<>();
        strategyList.add(new Strategy1());
        strategyList.add(new Strategy2());

        // 根据Strategy名称自动建立策略Map
        this.strategies = strategyList.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        strategy -> strategy.getName().toUpperCase(Locale.ROOT),
                        strategy -> strategy));

        log.info("Strategy初始化完成: {}", strategies.keySet());
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

        // 3. 根据币种获取唯一策略
        String strategyName = strategyProperties.getSymbols().get(symbol.toUpperCase(Locale.ROOT));
        if (strategyName == null || strategyName.isBlank()) {
            log.warn("{} 未配置交易策略，跳过策略分析", symbol);
            analysis.setSignal(TradingSignal.WAIT);
            return analysis;
        }

        Strategy strategy = strategies.get(strategyName.trim().toUpperCase(Locale.ROOT));
        if (strategy == null) {
            log.warn("{} 配置了不存在的策略: {}，跳过策略分析", symbol, strategyName);
            analysis.setSignal(TradingSignal.WAIT);
            return analysis;
        }

        // 4. 同一个币种只执行配置的一个策略
        TradingSignal signal = strategy.execute(klines);
        log.info("策略计算结果: symbol={}, strategy={}, signal={}, requiredMatchCount={}",
                symbol, strategyName, signal, strategy.getRequiredMatchCount());

        // 5. 保存最终信号
        analysis.setSignal(signal);

        log.info("========== 分析结束 {}，最终信号={} ==========", symbol, signal);
        return analysis;
    }
}
