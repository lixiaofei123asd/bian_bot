package com.lxf.bian_bot.analysis.strategy;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.model.IndicatorResult;
import com.lxf.bian_bot.analysis.enums.IndicatorAction;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.analysis.enums.TradingSignal;
import com.lxf.bian_bot.analysis.indicator.IndicatorDeepSeek;
import com.lxf.bian_bot.cache.TickerCache;
import com.lxf.bian_bot.config.SymbolRuleCache;
import com.lxf.bian_bot.config.strategy.StrategyConfig;
import com.lxf.bian_bot.config.trading.TradingConfig;
import com.lxf.bian_bot.model.TradingDecision;
import com.lxf.bian_bot.thirdparty.binance.enums.TimeInForce;
import com.lxf.bian_bot.thirdparty.binance.model.AccountInfo;
import com.lxf.bian_bot.thirdparty.binance.model.BinancePriceTicker;
import com.lxf.bian_bot.thirdparty.binance.model.OrderInfo;
import com.lxf.bian_bot.thirdparty.binance.model.PositionInfo;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceMarketService;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceTradeService;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceWebSocketStreamsService;
import com.lxf.bian_bot.trading.service.PendingOrderProtectionService;
import com.lxf.bian_bot.trading.state.TradingState;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 交易策略。
 *
 * <p>AI 自己决定执行周期、方向、限价、下单金额以及止盈止损。
 * BaseStrategy 只提供通用执行驱动和 Binance 交易准备。</p>
 */
@Slf4j
@Component
public class AiStrategy extends BaseStrategy {

    private static final Duration NO_POSITION_INTERVAL = Duration.ofMinutes(5);
    private static final Duration POSITION_INTERVAL = Duration.ofMinutes(3);

    private final IndicatorDeepSeek deepSeek;
    private final BinanceMarketService marketService;

    public AiStrategy(
            IndicatorDeepSeek deepSeek,
            BinanceMarketService marketService,
            StrategyConfig strategyConfig,
            BinanceWebSocketStreamsService webSocketStreamsService,
            TickerCache tickerCache,
            BinanceTradeService tradeService,
            TradingState tradingState,
            TradingConfig tradingConfig,
            SymbolRuleCache symbolRuleCache,
            PendingOrderProtectionService pendingOrderProtectionService) {
        super(strategyConfig, webSocketStreamsService, tickerCache, tradeService,
                tradingState, tradingConfig, symbolRuleCache, pendingOrderProtectionService);
        this.deepSeek = deepSeek;
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

    /** AI 自己决定执行周期：无仓位 5 分钟一次；有仓位 3 分钟一次。 */
    @Override
    protected Duration getExecutionInterval(String symbol) {
        PositionInfo position = tradingState.getPosition(symbol);
        return hasPosition(position) ? POSITION_INTERVAL : NO_POSITION_INTERVAL;
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

    @Override
    public TradingDecision decide(String symbol) {
        IndicatorResult result = analyze(symbol);
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

    /**
     * AI 的交易执行规则：始终使用 AI 返回的限价、金额、TP、SL。
     * BinanceTradeService 这里只负责把参数发送给 Binance，不决定策略参数。
     */
    @Override
    protected OrderInfo createEntryOrder(TradingDecision decision) {
        String symbol = decision.getSymbol();
        if (decision.getDirection() == null) {
            throw new IllegalArgumentException("AI 交易决策缺少方向: symbol=" + symbol);
        }
        if (decision.getEntryPrice() == null || decision.getEntryPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("AI 未返回有效限价: symbol=" + symbol);
        }
        if (decision.getUsdtAmount() == null || decision.getUsdtAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("AI 未返回有效下单金额: symbol=" + symbol);
        }

        Side side = decision.getDirection() == TradingDecision.Direction.LONG ? Side.BUY : Side.SELL;
        double price = decision.getEntryPrice().doubleValue();
        double usdtAmount = decision.getUsdtAmount().doubleValue();
        double quantity = usdtAmount / price;

        log.info("AI执行限价单: symbol={}, side={}, amount={}, price={}, tp={}, sl={}",
                symbol, side, usdtAmount, price,
                decision.getTakeProfitPrice(), decision.getStopLossPrice());

        if (decision.getTakeProfitPrice() != null && decision.getStopLossPrice() != null) {
            return tradeService.createLimitOrderAndTPSLForPrice(
                    symbol,
                    side,
                    TimeInForce.GTC,
                    quantity,
                    price,
                    decision.getTakeProfitPrice().doubleValue(),
                    decision.getStopLossPrice().doubleValue());
        }

        return tradeService.createLimitOrder(
                symbol,
                side,
                TimeInForce.GTC,
                usdtAmount,
                price);
    }

    private IndicatorResult analyze(String symbol) {
        IndicatorResult result = deepSeek.calculateWithContext(symbol, buildStrategyContext(symbol));
        if (result == null) {
            throw new IllegalStateException("DeepSeek 未返回 IndicatorResult: symbol=" + symbol);
        }

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
        if (result.getAction() != null) {
            return result.getAction();
        }
        if (!hasPosition(position)) {
            return result.getSignal() == IndicatorSignal.NEUTRAL
                    ? IndicatorAction.WAIT : IndicatorAction.OPEN;
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
            return position.isLong() ? TradingDecision.Direction.LONG : TradingDecision.Direction.SHORT;
        }
        return null;
    }

    private TradingDecision.DecisionType toDecisionType(IndicatorAction action, IndicatorResult result) {
        return switch (action) {
            case OPEN -> result.getSignal() == IndicatorSignal.DOWN
                    ? TradingDecision.DecisionType.ENTER_SHORT
                    : TradingDecision.DecisionType.ENTER_LONG;
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
