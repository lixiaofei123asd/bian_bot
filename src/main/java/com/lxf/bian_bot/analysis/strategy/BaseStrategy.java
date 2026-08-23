package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.IndicatorResult;
import com.lxf.bian_bot.analysis.Strategy;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.analysis.enums.TradingSignal;
import com.lxf.bian_bot.cache.TickerCache;
import com.lxf.bian_bot.config.model.StrategyConfig;
import com.lxf.bian_bot.model.TradingDecision;
import com.lxf.bian_bot.service.BinanceWebSocketStreamsService;
import com.lxf.bian_bot.trading.service.BinanceTradingService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 所有策略的基础类。
 *
 * <p>这里放所有策略通用的执行驱动、执行时间控制以及普通指标策略的投票逻辑。
 * 具体 Strategy 只需要覆盖自己的策略规则，不需要重复编写定时执行代码。</p>
 */
@Slf4j
public abstract class BaseStrategy implements Strategy {

    private final StrategyConfig strategyConfig;
    private final BinanceWebSocketStreamsService webSocketStreamsService;
    private final TickerCache tickerCache;
    private final BinanceTradingService tradingService;

    /** 每个交易对最后一次真正执行 Strategy 的时间。 */
    private final Map<String, Instant> lastExecuteTimes = new ConcurrentHashMap<>();

    protected BaseStrategy(
            StrategyConfig strategyConfig,
            BinanceWebSocketStreamsService webSocketStreamsService,
            TickerCache tickerCache,
            BinanceTradingService tradingService) {
        this.strategyConfig = strategyConfig;
        this.webSocketStreamsService = webSocketStreamsService;
        this.tickerCache = tickerCache;
        this.tradingService = tradingService;
    }

    /**
     * Strategy 的公共执行驱动。
     *
     * <p>这里只提供很轻的心跳检查，真正的执行间隔由具体 Strategy 自己通过
     * {@link #getExecutionInterval(String)} 决定。外部不再存在独立的
     * BinanceMarketScheduledService。</p>
     */
    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    public final void scheduledExecute() {
        if (!webSocketStreamsService.isConnected()) {
            return;
        }

        for (String symbol : strategyConfig.getSymbolsForStrategy(getName())) {
            try {
                if (!tickerCache.contains(symbol)) {
                    continue;
                }

                if (!shouldExecute(symbol)) {
                    continue;
                }

                TradingDecision decision = decide(symbol);
                if (decision == null) {
                    log.warn("Strategy 未返回交易决策: strategy={}, symbol={}", getName(), symbol);
                    continue;
                }

                tradingService.execute(decision);
                lastExecuteTimes.put(symbol, Instant.now());

                log.info("Strategy执行完成: strategy={}, symbol={}, decision={}, direction={}, entry={}, tp={}, sl={}",
                        getName(),
                        symbol,
                        decision.getDecision(),
                        decision.getDirection(),
                        decision.getEntryPrice(),
                        decision.getTakeProfitPrice(),
                        decision.getStopLossPrice());
            } catch (Exception e) {
                log.error("Strategy执行异常: strategy={}, symbol={}", getName(), symbol, e);
            }
        }
    }

    /**
     * 判断当前策略现在是否应该执行。
     *
     * <p>默认策略使用 {@link #getExecutionInterval(String)} 控制执行周期。
     * AI 等高级策略只需要覆盖 getExecutionInterval()，不用重复实现计时器。</p>
     */
    @Override
    public boolean shouldExecute(String symbol) {
        Instant last = lastExecuteTimes.get(symbol);
        if (last == null) {
            return true;
        }

        Duration interval = getExecutionInterval(symbol);
        if (interval == null || interval.isNegative() || interval.isZero()) {
            return true;
        }

        return Duration.between(last, Instant.now()).compareTo(interval) >= 0;
    }

    /**
     * 获取当前 Strategy 的执行间隔。
     * 默认普通策略每 10 秒执行一次。
     */
    protected Duration getExecutionInterval(String symbol) {
        return Duration.ofSeconds(10);
    }

    @Override
    public TradingSignal execute(String symbol) {
        int upCount = 0;
        int downCount = 0;

        for (Indicator indicator : getIndicators()) {
            IndicatorResult result = indicator.calculate(symbol);
            if (result == null || result.getSignal() == null) {
                continue;
            }

            IndicatorSignal signal = result.getSignal();
            if (signal == IndicatorSignal.UP) {
                upCount++;
            } else if (signal == IndicatorSignal.DOWN) {
                downCount++;
            }
        }

        if (upCount >= getRequiredMatchCount()) {
            return TradingSignal.LONG;
        }
        if (downCount >= getRequiredMatchCount()) {
            return TradingSignal.SHORT;
        }
        return TradingSignal.WAIT;
    }

    @Override
    public abstract List<Indicator> getIndicators();
}
