package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.Strategy;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.analysis.enums.TradingSignal;
import com.lxf.bian_bot.analysis.indicator.IndicatorSecondKlineTrend;
import com.lxf.bian_bot.analysis.model.IndicatorResult;
import com.lxf.bian_bot.analysis.model.ProtectionConfig;
import com.lxf.bian_bot.config.trading.TradingConfig;
import com.lxf.bian_bot.model.TradingDecision;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final TradingConfig tradingConfig;
    private final ConcurrentMap<String, Instant> lastExecuteTimes = new ConcurrentHashMap<>();

    public Strategy1(IndicatorSecondKlineTrend trend, TradingConfig tradingConfig) {
        this.trend = trend;
        this.tradingConfig = tradingConfig;
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
        int upCount = 0;
        int downCount = 0;
        for (Indicator indicator : getIndicators()) {
            IndicatorResult result = indicator.calculate(symbol);
            if (result == null || result.getSignal() == null) continue;
            if (result.getSignal() == IndicatorSignal.UP) upCount++;
            else if (result.getSignal() == IndicatorSignal.DOWN) downCount++;
        }
        lastExecuteTimes.put(symbol, Instant.now());
        if (upCount >= getRequiredMatchCount()) return TradingSignal.LONG;
        if (downCount >= getRequiredMatchCount()) return TradingSignal.SHORT;
        return TradingSignal.WAIT;
    }

    /** Strategy1 自己决定本策略使用的保证金。 */
    @Override
    public TradingDecision decide(String symbol) {
        TradingSignal signal = execute(symbol);
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
                .marginAmount(tradingConfig.getMarginAmount())
                .reason("Strategy1 信号: " + signal)
                .build();
    }

    /** Strategy1 自己决定保护方式：按盈利/亏损金额反推价格。 */
    @Override
    public ProtectionConfig calculateProtection(String symbol,
                                                TradingDecision.Direction direction,
                                                BigDecimal quantity,
                                                BigDecimal entryPrice) {
        if (!tradingConfig.isTakeProfitStopLossEnabled()) return ProtectionConfig.disabled();
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) return ProtectionConfig.disabled();
        if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) <= 0) return ProtectionConfig.disabled();

        BigDecimal tpAmount = tradingConfig.getTakeProfitAmount();
        BigDecimal slAmount = tradingConfig.getStopLossAmount();
        if (tpAmount == null || tpAmount.compareTo(BigDecimal.ZERO) <= 0
                || slAmount == null || slAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ProtectionConfig.disabled();
        }

        BigDecimal tpMove = tpAmount.divide(quantity, 20, RoundingMode.HALF_UP);
        BigDecimal slMove = slAmount.divide(quantity, 20, RoundingMode.HALF_UP);

        if (direction == TradingDecision.Direction.LONG) {
            return ProtectionConfig.of(entryPrice.add(tpMove), entryPrice.subtract(slMove));
        }
        if (direction == TradingDecision.Direction.SHORT) {
            return ProtectionConfig.of(entryPrice.subtract(tpMove), entryPrice.add(slMove));
        }
        return ProtectionConfig.disabled();
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
