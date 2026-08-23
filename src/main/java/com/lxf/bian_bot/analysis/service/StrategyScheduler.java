package com.lxf.bian_bot.analysis.service;

import com.lxf.bian_bot.analysis.ScheduledStrategy;
import com.lxf.bian_bot.analysis.Strategy;
import com.lxf.bian_bot.analysis.StrategyRegistry;
import com.lxf.bian_bot.config.BinanceProperties;
import com.lxf.bian_bot.config.model.StrategyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Strategy 定时调度器。
 *
 * <p>调度器本身是全局的，但只有当前交易对正在使用的 Strategy 实现了
 * {@link ScheduledStrategy} 时，才会执行该策略的定时逻辑。</p>
 */
@Slf4j
@Service
public class StrategyScheduler {

    private final StrategyRegistry strategyRegistry;
    private final StrategyConfig strategyConfig;
    private final BinanceProperties binanceProperties;

    public StrategyScheduler(
            StrategyRegistry strategyRegistry,
            StrategyConfig strategyConfig,
            BinanceProperties binanceProperties) {
        this.strategyRegistry = strategyRegistry;
        this.strategyConfig = strategyConfig;
        this.binanceProperties = binanceProperties;
    }

    /**
     * 默认每 5 分钟检查一次。
     *
     * <p>具体间隔通过 strategy.scheduling.interval-ms 配置。</p>
     */
    @Scheduled(fixedDelayString = "${strategy.scheduling.interval-ms:300000}")
    public void scheduledExecute() {
        for (String symbol : binanceProperties.getSymbols()) {
            try {
                executeForSymbol(symbol);
            } catch (Exception e) {
                log.error("Strategy 定时任务执行异常: symbol={}", symbol, e);
            }
        }
    }

    private void executeForSymbol(String symbol) {
        String strategyName = strategyConfig.getStrategyName(symbol);
        if (strategyName == null || strategyName.isBlank()) {
            log.debug("{} 未配置 Strategy，跳过定时任务", symbol);
            return;
        }

        Strategy strategy = strategyRegistry.getStrategy(strategyName);
        if (strategy == null) {
            log.warn("{} 未找到 Strategy: {}，跳过定时任务", symbol, strategyName);
            return;
        }

        if (!(strategy instanceof ScheduledStrategy scheduledStrategy)) {
            log.debug("{} 当前 Strategy={} 不需要定时任务", symbol, strategyName);
            return;
        }

        log.debug("{} 执行定时 Strategy: {}", symbol, strategyName);
        scheduledStrategy.scheduledExecute(symbol);
    }
}
