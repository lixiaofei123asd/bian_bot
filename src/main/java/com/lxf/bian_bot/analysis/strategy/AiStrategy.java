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
    private final ConcurrentMap<String, IndicatorResult> lastAnalysisResults = new ConcurrentHashMap<>();

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

        TradingDecision decision = TradingDecision.builder()
                .symbol(symbol)
                .decision(toDecisionType(action, result))
                .direction(direction)
                .currentPrice(priceTicker == null ? null : priceTicker.getPrice())
                .minEntryPrice(result.getMinEntryPrice())
                .maxEntryPrice(result.getMaxEntryPrice())
                .entryPrice(result.getEntryPrice())
                .takeProfitPrice(result.getTakeProfitPrice())
                .stopLossPrice(result.getStopLossPrice())
                .reason(result.getReason())
                .build();

        // 保存本次策略决定使用的 AI 结果。
        // 开仓成交后由 StrategyProtectionHandler 调用 calculateProtection()，
        // 必须使用这一次分析得到的 TP/SL，而不是重新请求一次 DeepSeek。
        lastAnalysisResults.put(symbol, result);

        log.info("AI策略决策: symbol={}, decision={}, direction={}, entryPrice={}, tp={}, sl={}",
                symbol,
                decision.getDecision(),
                decision.getDirection(),
                decision.getEntryPrice(),
                decision.getTakeProfitPrice(),
                decision.getStopLossPrice());

        return decision;
    }

    @Override
    public ProtectionConfig calculateProtection(String symbol,
                                                TradingDecision.Direction direction,
                                                BigDecimal quantity,
                                                BigDecimal entryPrice) {
        if (!tradingConfig.isTakeProfitStopLossEnabled()) {
            log.info("AI策略未启用止盈止损: symbol={}", symbol);
            return ProtectionConfig.disabled();
        }

        // 不重新调用 DeepSeek，直接使用本次 decide() 保存的策略结果。
        IndicatorResult result = lastAnalysisResults.get(symbol);
        if (result == null) {
            log.warn("AI策略没有找到本次开仓对应的分析结果: symbol={}", symbol);
            return ProtectionConfig.disabled();
        }

        BigDecimal takeProfitPrice = result.getTakeProfitPrice();
        BigDecimal stopLossPrice = result.getStopLossPrice();

        if (takeProfitPrice == null || stopLossPrice == null
                || takeProfitPrice.compareTo(BigDecimal.ZERO) <= 0
                || stopLossPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("AI未提供有效的 TP/SL: symbol={}, tp={}, sl={}",
                    symbol, takeProfitPrice, stopLossPrice);
            return ProtectionConfig.disabled();
        }

        log.info("AI策略保护价格: symbol={}, entryPrice={}, quantity={}, tp={}, sl={}",
                symbol, entryPrice, quantity, takeProfitPrice, stopLossPrice);
        return ProtectionConfig.of(takeProfitPrice, stopLossPrice);
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
        log.info("AI策略分析完成: symbol={}, action={}, signal={}, confidence={}, entryPrice={}, tp={}, sl={}, reason={}",
                symbol, result.getAction(), result.getSignal(), result.getConfidence(),
                result.getEntryPrice(), result.getTakeProfitPrice(), result.getStopLossPrice(), result.getReason());
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
