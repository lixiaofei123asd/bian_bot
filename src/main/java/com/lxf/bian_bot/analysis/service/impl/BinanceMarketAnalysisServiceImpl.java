package com.lxf.bian_bot.analysis.service.impl;

import com.lxf.bian_bot.analysis.Strategy;
import com.lxf.bian_bot.analysis.StrategyRegistry;
import com.lxf.bian_bot.analysis.enums.TradingSignal;
import com.lxf.bian_bot.analysis.service.BinanceMarketAnalysisService;
import com.lxf.bian_bot.cache.KlineCache;
import com.lxf.bian_bot.config.model.StrategyConfig;
import com.lxf.bian_bot.model.BinanceKline;
import com.lxf.bian_bot.model.TradingDecision;
import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Service
@Validated
public class BinanceMarketAnalysisServiceImpl implements BinanceMarketAnalysisService {

    private static final String KLINE_INTERVAL = "1m";
    private final KlineCache klineCache;
    private final StrategyConfig strategyConfig;
    private final StrategyRegistry strategyRegistry;

    public BinanceMarketAnalysisServiceImpl(KlineCache klineCache, StrategyConfig strategyConfig, StrategyRegistry strategyRegistry) {
        this.klineCache = klineCache;
        this.strategyConfig = strategyConfig;
        this.strategyRegistry = strategyRegistry;
    }

    @Override
    public TradingDecision analyze(String symbol) {
        log.info("========== 开始市场分析 {} ==========", symbol);

        List<BinanceKline> klines = klineCache.get(symbol, KLINE_INTERVAL);
        if (klines.isEmpty()) {
            log.warn("{} KlineCache 中没有 K线数据", symbol);
            return buildWaitDecision(symbol);
        }

        BigDecimal currentPrice = klines.getLast().getClosePrice();
        String strategyName = strategyConfig.getStrategyName(symbol);
        Strategy strategy = strategyRegistry.getStrategy(strategyName);

        if (strategy == null) {
            log.warn("未找到策略: symbol={}, strategy={}", symbol, strategyName);
            return buildWaitDecision(symbol);
        }

        TradingSignal signal = strategy.execute(symbol);
        TradingDecision decision = buildDecision(symbol, currentPrice, signal, strategyName);

        log.info("市场分析完成: symbol={}, strategy={}, decision={}", symbol, strategyName, decision.getDecision());
        return decision;
    }

    private TradingDecision buildWaitDecision(String symbol) {
        return TradingDecision.builder()
                .symbol(symbol)
                .decision(TradingDecision.DecisionType.WAIT)
                .currentPrice(null)
                .reason("KlineCache 中没有 K线数据")
                .build();
    }

    private TradingDecision buildDecision(String symbol, BigDecimal currentPrice, TradingSignal signal, String strategyName) {
        TradingDecision.DecisionType decisionType = switch (signal) {
            case LONG -> TradingDecision.DecisionType.ENTER_LONG;
            case SHORT -> TradingDecision.DecisionType.ENTER_SHORT;
            case WAIT -> TradingDecision.DecisionType.WAIT;
        };

        TradingDecision.Direction direction = switch (signal) {
            case LONG -> TradingDecision.Direction.LONG;
            case SHORT -> TradingDecision.Direction.SHORT;
            case WAIT -> null;
        };

        return TradingDecision.builder()
                .symbol(symbol)
                .decision(decisionType)
                .direction(direction)
                .currentPrice(currentPrice)
                .reason("策略: " + strategyName + ", 信号: " + signal)
                .build();
    }
}
