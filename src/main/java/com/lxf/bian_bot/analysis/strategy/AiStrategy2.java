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

/**
 * AI 策略 2。
 *
 * <p>整体交易决策逻辑与 AiStrategy 保持一致，唯一的保护逻辑区别是：
 * 止盈固定为本次订单实现 0.2 USDT 盈利，止损仍然使用 AI 返回的结果。</p>
 */
@Slf4j
@Component
public class AiStrategy2 implements Strategy {

    /** 每笔订单达到 0.2 USDT 实际盈利后止盈。 */
    private static final BigDecimal TAKE_PROFIT_PROFIT = new BigDecimal("0.2");

    private static final Duration NO_POSITION_INTERVAL = Duration.ofMinutes(5);
    private static final Duration POSITION_INTERVAL = Duration.ofMinutes(3);

    private final IndicatorDeepSeek deepSeek;
    private final BinanceMarketService marketService;
    private final TradingState tradingState;
    private final TradingConfig tradingConfig;
    private final ConcurrentMap<String, Instant> lastExecuteTimes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, IndicatorResult> lastAnalysisResults = new ConcurrentHashMap<>();

    public AiStrategy2(IndicatorDeepSeek deepSeek,
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
        return "AISTRATEGY2";
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

        BigDecimal takeProfitPrice = result.getTakeProfitPrice();

        // 已有仓位执行 ADJUST_PROTECTION 时，数量和真实开仓价已经存在，
        // 因此可以直接计算固定 0.2 USDT 盈利对应的止盈价格。
        if (action == IndicatorAction.ADJUST_PROTECTION && hasPosition(position)) {
            takeProfitPrice = calculateTakeProfitPrice(
                    direction,
                    position.getPositionAmt().abs(),
                    position.getEntryPrice());
        }

        TradingDecision decision = TradingDecision.builder()
                .symbol(symbol)
                .decision(toDecisionType(action, result))
                .direction(direction)
                .currentPrice(priceTicker == null ? null : priceTicker.getPrice())
                .minEntryPrice(result.getMinEntryPrice())
                .maxEntryPrice(result.getMaxEntryPrice())
                .entryPrice(result.getEntryPrice())
                .takeProfitPrice(takeProfitPrice)
                .stopLossPrice(result.getStopLossPrice())
                .reason(result.getReason())
                .build();

        // 保存本次分析结果。开仓成交后的 calculateProtection() 必须使用
        // 这一次 AI 分析得到的止损，而不是重新请求 DeepSeek。
        lastAnalysisResults.put(symbol, result);

        log.info("AI策略2决策: symbol={}, decision={}, direction={}, entryPrice={}, tp={}, sl={}",
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
            log.info("AI策略2未启用止盈止损: symbol={}", symbol);
            return ProtectionConfig.disabled();
        }

        IndicatorResult result = lastAnalysisResults.get(symbol);
        if (result == null) {
            log.warn("AI策略2没有找到本次开仓对应的分析结果: symbol={}", symbol);
            return ProtectionConfig.disabled();
        }

        BigDecimal stopLossPrice = result.getStopLossPrice();
        if (stopLossPrice == null || stopLossPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("AI策略2未提供有效的止损价格: symbol={}, sl={}", symbol, stopLossPrice);
            return ProtectionConfig.disabled();
        }

        BigDecimal takeProfitPrice = calculateTakeProfitPrice(direction, quantity, entryPrice);
        if (takeProfitPrice == null || takeProfitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("AI策略2无法计算有效的止盈价格: symbol={}, direction={}, quantity={}, entryPrice={}",
                    symbol, direction, quantity, entryPrice);
            return ProtectionConfig.disabled();
        }

        log.info("AI策略2保护价格: symbol={}, entryPrice={}, quantity={}, profitTarget={}, tp={}, sl={}",
                symbol, entryPrice, quantity, TAKE_PROFIT_PROFIT, takeProfitPrice, stopLossPrice);

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
                .reason("AiStrategy2：保护单无法创建且价格已越过保护价，立即退出")
                .build();
    }

    private IndicatorResult analyze(String symbol) {
        IndicatorResult result = deepSeek.calculateWithContext(symbol, buildStrategyContext(symbol));
        if (result == null) {
            throw new IllegalStateException("DeepSeek 未返回 IndicatorResult: symbol=" + symbol);
        }

        log.info("AI策略2分析完成: symbol={}, action={}, signal={}, confidence={}, entryPrice={}, tp={}, sl={}, reason={}",
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

    /**
     * 根据实际持仓数量和开仓均价，计算实现 0.2 USDT 盈利时的止盈价格。
     *
     * <p>LONG：TP = entryPrice + 0.2 / quantity</p>
     * <p>SHORT：TP = entryPrice - 0.2 / quantity</p>
     */
    private BigDecimal calculateTakeProfitPrice(TradingDecision.Direction direction,
                                                 BigDecimal quantity,
                                                 BigDecimal entryPrice) {
        if (direction == null || quantity == null || entryPrice == null
                || quantity.compareTo(BigDecimal.ZERO) <= 0
                || entryPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal priceOffset = TAKE_PROFIT_PROFIT.divide(quantity, 16, java.math.RoundingMode.HALF_UP);

        if (direction == TradingDecision.Direction.LONG) {
            return entryPrice.add(priceOffset);
        }

        return entryPrice.subtract(priceOffset);
    }

    private boolean hasPosition(PositionInfo position) {
        return position != null && position.hasPosition();
    }
}
