package com.lxf.bian_bot.analysis.service.impl;

import com.lxf.bian_bot.analysis.Strategy;
import com.lxf.bian_bot.analysis.StrategyRegistry;
import com.lxf.bian_bot.analysis.service.BinanceMarketAnalysisService;
import com.lxf.bian_bot.config.model.StrategyConfig;
import com.lxf.bian_bot.model.TradingDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Service
@Validated
public class BinanceMarketAnalysisServiceImpl implements BinanceMarketAnalysisService {

    private final StrategyConfig strategyConfig;
    private final StrategyRegistry strategyRegistry;

    public BinanceMarketAnalysisServiceImpl(
            StrategyConfig strategyConfig,
            StrategyRegistry strategyRegistry) {
        this.strategyConfig = strategyConfig;
        this.strategyRegistry = strategyRegistry;
    }

    @Override
    public TradingDecision analyze(String symbol) {
        log.info("========== 开始市场分析 {} ==========", symbol);

        String strategyName = strategyConfig.getStrategyName(symbol);
        Strategy strategy = strategyRegistry.getStrategy(strategyName);

        if (strategy == null) {
            log.warn("未找到策略: symbol={}, strategy={}", symbol, strategyName);
            return buildWaitDecision(symbol, "未找到对应 Strategy");
        }

        // 外部驱动只负责周期性询问，策略自己决定当前是否执行。
        if (!strategy.shouldExecute(symbol)) {
            log.debug("Strategy 暂不需要执行: symbol={}, strategy={}", symbol, strategyName);
            return buildWaitDecision(symbol, "Strategy 当前未到执行时间");
        }

        // 最终交易决策由 Strategy 自己生成。
        TradingDecision decision = strategy.decide(symbol);
        if (decision == null) {
            log.warn("Strategy 未返回交易决策: symbol={}, strategy={}", symbol, strategyName);
            return buildWaitDecision(symbol, "Strategy 未返回交易决策");
        }

        log.info("市场分析完成: symbol={}, strategy={}, decision={}, direction={}, entryPrice={}, tp={}, sl={}, reason={}",
                symbol,
                strategyName,
                decision.getDecision(),
                decision.getDirection(),
                decision.getEntryPrice(),
                decision.getTakeProfitPrice(),
                decision.getStopLossPrice(),
                decision.getReason());

        return decision;
    }

    private TradingDecision buildWaitDecision(String symbol, String reason) {
        return TradingDecision.builder()
                .symbol(symbol)
                .decision(TradingDecision.DecisionType.WAIT)
                .reason(reason)
                .build();
    }
}
