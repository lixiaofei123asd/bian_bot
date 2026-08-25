package com.lxf.bian_bot.analysis;

import com.lxf.bian_bot.cache.TickerCache;
import com.lxf.bian_bot.config.strategy.StrategyConfig;
import com.lxf.bian_bot.model.TradingDecision;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 周期性询问所有策略，由策略自己决定当前是否执行。 */
@Slf4j
@Component
public class StrategyScheduler {

    private final StrategyRegistry strategyRegistry;
    private final StrategyConfig strategyConfig;
    private final TickerCache tickerCache;
    private final StrategyExecutionService executionService;

    public StrategyScheduler(StrategyRegistry strategyRegistry,
                             StrategyConfig strategyConfig,
                             TickerCache tickerCache,
                             StrategyExecutionService executionService) {
        this.strategyRegistry = strategyRegistry;
        this.strategyConfig = strategyConfig;
        this.tickerCache = tickerCache;
        this.executionService = executionService;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    public void scheduledExecute() {
        for (Map.Entry<String, String> entry : strategyConfig.getSymbols().entrySet()) {
            String symbol = entry.getKey();
            String strategyName = entry.getValue();
            if (!tickerCache.contains(symbol)) continue;

            Strategy strategy = strategyRegistry.getStrategy(strategyName);
            if (strategy == null) {
                log.error("找不到配置的 Strategy: symbol={}, strategy={}", symbol, strategyName);
                continue;
            }

            try {
                if (!strategy.shouldExecute(symbol)) continue;
                TradingDecision decision = strategy.decide(symbol);
                if (decision == null) {
                    log.warn("Strategy 未返回交易决策: strategy={}, symbol={}", strategyName, symbol);
                    continue;
                }
                executionService.execute(decision);
            } catch (Exception e) {
                log.error("Strategy执行异常: strategy={}, symbol={}", strategyName, symbol, e);
            }
        }
    }
}
