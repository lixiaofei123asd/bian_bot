package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.Strategy;
import com.lxf.bian_bot.analysis.enums.TradingSignal;
import com.lxf.bian_bot.analysis.indicator.IndicatorSecondKlineTrend;
import com.lxf.bian_bot.model.TradingDecision;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class Strategy1 implements Strategy {

    private static final Duration EXECUTION_INTERVAL = Duration.ofSeconds(10);

    private final IndicatorSecondKlineTrend trend;
    private final ConcurrentMap<String, Instant> lastExecuteTimes = new ConcurrentHashMap<>();

    public Strategy1(IndicatorSecondKlineTrend trend) {
        this.trend = trend;
    }

    @Override
    public String getName() {
        return "STRATEGY1";
    }

    @Override
    public List<Indicator> getIndicators() {
        return List.of(trend);
    }

    @Override
    public int getRequiredMatchCount() {
        return 1;
    }

    @Override
    public boolean shouldExecute(String symbol) {
        Instant last = lastExecuteTimes.get(symbol);
        if (last == null) return true;
        return Duration.between(last, Instant.now()).compareTo(EXECUTION_INTERVAL) >= 0;
    }

    @Override
    public TradingSignal execute(String symbol) {
        TradingSignal signal = Strategy.super.execute(symbol);
        lastExecuteTimes.put(symbol, Instant.now());
        return signal;
    }

    @Override
    public TradingDecision onProtectionTrigger(String symbol,
                                                TradingDecision.Direction direction,
                                                BigDecimal quantity,
                                                BigDecimal takeProfitPrice,
                                                BigDecimal stopLossPrice) {
        return TradingDecision.builder()
                .symbol(symbol)
                .direction(direction)
                .decision(TradingDecision.DecisionType.EXIT)
                .reason("Strategy1：保护单无法创建且价格已越过保护价，立即退出")
                .build();
    }
}
