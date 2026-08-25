package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.Strategy;
import com.lxf.bian_bot.analysis.enums.IndicatorAction;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.analysis.enums.TradingSignal;
import com.lxf.bian_bot.analysis.indicator.IndicatorDeepSeek;
import com.lxf.bian_bot.analysis.model.IndicatorResult;
import com.lxf.bian_bot.analysis.model.ProtectionConfig;
import com.lxf.bian_bot.config.trading.TradingConfig;
import com.lxf.bian_bot.model.TradingDecision;
import com.lxf.bian_bot.thirdparty.binance.model.AccountInfo;
import com.lxf.bian_bot.thirdparty.binance.model.BinancePriceTicker;
import com.lxf.bian_bot.thirdparty.binance.model.OrderInfo;
import com.lxf.bian_bot.thirdparty.binance.model.PositionInfo;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceMarketService;
import com.lxf.bian_bot.trading.state.TradingState;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiStrategy implements Strategy {

    private static final Duration NO_POSITION_INTERVAL = Duration.ofMinutes(5);
    private static final Duration POSITION_INTERVAL = Duration.ofMinutes(3);

    private final IndicatorDeepSeek deepSeek;
    private final BinanceMarketService marketService;
    private final TradingState tradingState;
    private final TradingConfig tradingConfig;
    private final ConcurrentMap<String, Instant> lastExecuteTimes = new ConcurrentHashMap<>();

    public AiStrategy(IndicatorDeepSeek deepSeek,
                      BinanceMarketService marketService,
                      TradingState tradingState,
                      TradingConfig tradingConfig) {
        this.deepSeek = deepSeek;
        this.marketService = marketService;
        this.tradingState = tradingState;
        this.tradingConfig = tradingConfig;
    }

    @Override
    public String getName() {
        return "AISTRATEGY";
    }

    @Override
    public List<Indicator> getIndicators() {
        return List.of(deepSeek);
    }

    @Override
    public int getRequiredMatchCount() {
        return 1;
    }

    @Override
    public boolean shouldExecute(String symbol) {
        Instant last = lastExecuteTimes.get(symbol);
        if (last == null) return true;
        Duration interval = hasPosition(tradingState.getPosition(symbol)) ? POSITION_INTERVAL : NO_POSITION_INTERVAL;
        return Duration.between(last, Instant.now()).compareTo(interval) >= 0;
    }

    @Override
    public TradingSignal execute(String symbol) {
        IndicatorResult result = analyze(symbol);
        lastExecuteTimes.put(symbol, Instant.now());
        if (result.getSignal() == IndicatorSignal.UP) return TradingSignal.LONG;
        if (result.getSignal() == IndicatorSignal.DOWN) return TradingSignal.SHORT;
        return TradingSignal.WAIT;
    }

    @Override
    public TradingDecision decide(String symbol) {
        IndicatorResult result = analyze(symbol);
        lastExecuteTimes.put(symbol, Instant.now());
        PositionInfo position = tradingState.getPosition(symbol);
        IndicatorAction action = resolveAction(result, position);
        BinancePriceTicker priceTicker = marketService.getPrice(symbol);
        TradingDecision.Direction direction = resolveDirection(result, position);

        return TradingDecision.builder()
                .symbol(symbol)
                .decision(toDecisionType(action, result))
                .direction(direction)
                .currentPrice(priceTicker == null ? null : priceTicker.getPrice())
                .minEntryPrice(result.getMinEntryPrice())
                .maxEntryPrice(result.getMaxEntryPrice())
                .entryPrice(result.getEntryPrice())
                .usdtAmount(result.getUsdtAmount())
                .takeProfitPrice(result.getTakeProfitPrice())
                .stopLossPrice(result.getStopLossPrice())
                .reason(result.getReason())
                .build();
    }

    /** AI 策略自己决定保护价格；优先使用 AI 分析结果中的 TP/SL。 */
    @Override
    public ProtectionConfig calculateProtection(String symbol,
                                                TradingDecision.Direction direction,
                                                BigDecimal quantity,
                                                BigDecimal entryPrice) {
        if (!tradingConfig.isTakeProfitStopLossEnabled()) return ProtectionConfig.disabled();
        IndicatorResult result = analyze(symbol);
        if (result.getTakeProfitPrice() != null && result.getStopLossPrice() != null
                && result.getTakeProfitPrice().compareTo(BigDecimal.ZERO) > 0
                && result.getStopLossPrice().compareTo(BigDecimal.ZERO) > 0) {
            return ProtectionConfig.of(result.getTakeProfitPrice(), result.getStopLossPrice());
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
                .reason("AiStrategy：保护单无法创建且价格已越过保护价，立即退出")
                .build();
    }

    private IndicatorResult analyze(String symbol) {
        IndicatorResult result = deepSeek.calculateWithContext(symbol, buildStrategyContext(symbol));
        if (result == null) throw new IllegalStateException("DeepSeek 未返回 IndicatorResult: symbol=" + symbol);
        log.info("AI策略分析完成: symbol={}, action={}, signal={}, confidence={}, reason={}",
                symbol, result.getAction(), result.getSignal(), result.getConfidence(), result.getReason());
        return result;
    }

    private Map<String, Object> buildStrategyContext(String symbol) {
        Map<String, Object> context = new LinkedHashMap<>();
        AccountInfo account = tradingState.getAccountInfo();
        PositionInfo position = tradingState.getPosition(symbol);
        List<OrderInfo> openOrders = tradingState.getOpenOrders(symbol);
        context.put("account", account);
        context.put("position", position);
        context.put("openOrders", openOrders);
        context.put("executionState", tradingState.getExecutionState(symbol));
        context.put("hasPosition", hasPosition(position));
        return context;
    }

    private IndicatorAction resolveAction(IndicatorResult result, PositionInfo position) {
        if (result.getAction() != null) return result.getAction();
        if (!hasPosition(position)) {
            return result.getSignal() == IndicatorSignal.NEUTRAL ? IndicatorAction.WAIT : IndicatorAction.OPEN;
        }
        return IndicatorAction.HOLD;
    }

    private TradingDecision.Direction resolveDirection(IndicatorResult result, PositionInfo position) {
        if (result.getSignal() == IndicatorSignal.UP) return TradingDecision.Direction.LONG;
        if (result.getSignal() == IndicatorSignal.DOWN) return TradingDecision.Direction.SHORT;
        if (hasPosition(position)) return position.isLong() ? TradingDecision.Direction.LONG : TradingDecision.Direction.SHORT;
        return null;
    }

    private TradingDecision.DecisionType toDecisionType(IndicatorAction action, IndicatorResult result) {
        return switch (action) {
            case OPEN -> result.getSignal() == IndicatorSignal.DOWN
                    ? TradingDecision.DecisionType.ENTER_SHORT : TradingDecision.DecisionType.ENTER_LONG;
            case HOLD -> TradingDecision.DecisionType.HOLD;
            case CLOSE -> TradingDecision.DecisionType.EXIT;
            case WAIT -> TradingDecision.DecisionType.WAIT;
            case ADJUST_PROTECTION -> TradingDecision.DecisionType.ADJUST_PROTECTION;
        };
    }

    private boolean hasPosition(PositionInfo position) {
        return position != null && position.hasPosition();
    }
}
