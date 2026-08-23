package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.IndicatorResult;
import com.lxf.bian_bot.analysis.Strategy;
import com.lxf.bian_bot.analysis.enums.IndicatorAction;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.analysis.enums.TradingSignal;
import com.lxf.bian_bot.analysis.indicator.IndicatorDeepSeek;
import com.lxf.bian_bot.model.BinancePriceTicker;
import com.lxf.bian_bot.model.OrderInfo;
import com.lxf.bian_bot.model.PositionInfo;
import com.lxf.bian_bot.model.TradingDecision;
import com.lxf.bian_bot.service.BinanceMarketService;
import com.lxf.bian_bot.trading.state.TradingState;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 交易策略。
 *
 * <p>AI 策略自己决定多久重新分析一次。外部只负责周期性询问 shouldExecute()。</p>
 * <p>无仓位默认 5 分钟分析一次；有仓位默认 3 分钟分析一次。</p>
 */
@Slf4j
@Component
public class AiStrategy implements Strategy {

    private static final Duration NO_POSITION_INTERVAL = Duration.ofMinutes(5);
    private static final Duration POSITION_INTERVAL = Duration.ofMinutes(3);

    private final IndicatorDeepSeek deepSeek;
    private final TradingState tradingState;
    private final BinanceMarketService marketService;

    private final Map<String, Instant> lastAnalysisTimes = new ConcurrentHashMap<>();
    private final Map<String, IndicatorResult> latestResults = new ConcurrentHashMap<>();

    public AiStrategy(
            IndicatorDeepSeek deepSeek,
            TradingState tradingState,
            BinanceMarketService marketService) {
        this.deepSeek = deepSeek;
        this.tradingState = tradingState;
        this.marketService = marketService;
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
        Instant last = lastAnalysisTimes.get(symbol);
        if (last == null) {
            return true;
        }

        PositionInfo position = tradingState.getPosition(symbol);
        Duration interval = hasPosition(position)
                ? POSITION_INTERVAL
                : NO_POSITION_INTERVAL;

        return Duration.between(last, Instant.now()).compareTo(interval) >= 0;
    }

    @Override
    public TradingSignal execute(String symbol) {
        IndicatorResult result = analyze(symbol);

        if (result.getSignal() == IndicatorSignal.UP) {
            return TradingSignal.LONG;
        }
        if (result.getSignal() == IndicatorSignal.DOWN) {
            return TradingSignal.SHORT;
        }
        return TradingSignal.WAIT;
    }

    /**
     * AI 策略自己的最终交易决策。
     *
     * <p>这里才把 IndicatorResult 转换成 OPEN/HOLD/CLOSE/ADJUST_PROTECTION。
     * Indicator 本身不控制交易。</p>
     */
    @Override
    public TradingDecision decide(String symbol) {
        IndicatorResult result = analyze(symbol);
        PositionInfo position = tradingState.getPosition(symbol);

        IndicatorAction action = resolveAction(result, position);
        BinancePriceTicker priceTicker = marketService.getPrice(symbol);

        TradingDecision.Direction direction = resolveDirection(result, position);

        return TradingDecision.builder()
                .symbol(symbol)
                .decision(toDecisionType(action))
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

    /** 获取最近一次 AI 分析结果，便于日志、监控和测试。 */
    public IndicatorResult getLatestResult(String symbol) {
        return latestResults.get(symbol);
    }

    private IndicatorResult analyze(String symbol) {
        lastAnalysisTimes.put(symbol, Instant.now());

        Map<String, Object> context = buildStrategyContext(symbol);
        IndicatorResult result = deepSeek.calculateWithContext(symbol, context);
        latestResults.put(symbol, result);

        log.info("AI策略分析完成: symbol={}, action={}, signal={}, confidence={}, reason={}",
                symbol,
                result.getAction(),
                result.getSignal(),
                result.getConfidence(),
                result.getReason());

        return result;
    }

    private Map<String, Object> buildStrategyContext(String symbol) {
        Map<String, Object> context = new LinkedHashMap<>();

        PositionInfo position = tradingState.getPosition(symbol);
        List<OrderInfo> openOrders = tradingState.getOpenOrders(symbol);

        context.put("position", position);
        context.put("openOrders", openOrders);
        context.put("executionState", tradingState.getExecutionState(symbol));
        context.put("hasPosition", hasPosition(position));

        return context;
    }

    private IndicatorAction resolveAction(IndicatorResult result, PositionInfo position) {
        if (result.getAction() != null) {
            return result.getAction();
        }

        if (!hasPosition(position)) {
            return result.getSignal() == IndicatorSignal.NEUTRAL
                    ? IndicatorAction.WAIT
                    : IndicatorAction.OPEN;
        }

        return IndicatorAction.HOLD;
    }

    private TradingDecision.Direction resolveDirection(IndicatorResult result, PositionInfo position) {
        if (result.getSignal() == IndicatorSignal.UP) {
            return TradingDecision.Direction.LONG;
        }
        if (result.getSignal() == IndicatorSignal.DOWN) {
            return TradingDecision.Direction.SHORT;
        }
        if (hasPosition(position)) {
            return position.isLong()
                    ? TradingDecision.Direction.LONG
                    : TradingDecision.Direction.SHORT;
        }
        return null;
    }

    private TradingDecision.DecisionType toDecisionType(IndicatorAction action) {
        return switch (action) {
            case OPEN -> {
                // 最终方向由 signal 决定；调用方根据 direction 区分多空。
                yield TradingDecision.DecisionType.ENTER_LONG;
            }
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
