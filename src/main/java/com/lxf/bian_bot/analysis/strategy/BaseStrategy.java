package com.lxf.bian_bot.analysis.strategy;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.Strategy;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.analysis.enums.TradingSignal;
import com.lxf.bian_bot.analysis.model.IndicatorResult;
import com.lxf.bian_bot.cache.TickerCache;
import com.lxf.bian_bot.config.SymbolRuleCache;
import com.lxf.bian_bot.config.strategy.StrategyConfig;
import com.lxf.bian_bot.config.trading.TradingConfig;
import com.lxf.bian_bot.model.TradingDecision;
import com.lxf.bian_bot.thirdparty.binance.enums.TimeInForce;
import com.lxf.bian_bot.thirdparty.binance.enums.TradingMarginType;
import com.lxf.bian_bot.thirdparty.binance.model.OrderInfo;
import com.lxf.bian_bot.thirdparty.binance.model.PositionInfo;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceTradeService;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceWebSocketStreamsService;
import com.lxf.bian_bot.trading.service.PendingOrderProtectionService;
import com.lxf.bian_bot.trading.state.TradingExecutionState;
import com.lxf.bian_bot.trading.state.TradingState;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/** 所有策略的基础类。 */
@Slf4j
public abstract class BaseStrategy implements Strategy {

    private final StrategyConfig strategyConfig;
    private final BinanceWebSocketStreamsService webSocketStreamsService;
    private final TickerCache tickerCache;
    protected final BinanceTradeService tradeService;
    protected final TradingState tradingState;
    protected final TradingConfig tradingConfig;
    protected final SymbolRuleCache symbolRuleCache;
    protected final PendingOrderProtectionService pendingOrderProtectionService;

    private final Map<String, Instant> lastExecuteTimes = new ConcurrentHashMap<>();

    protected BaseStrategy(
            StrategyConfig strategyConfig,
            BinanceWebSocketStreamsService webSocketStreamsService,
            TickerCache tickerCache,
            BinanceTradeService tradeService,
            TradingState tradingState,
            TradingConfig tradingConfig,
            SymbolRuleCache symbolRuleCache,
            PendingOrderProtectionService pendingOrderProtectionService) {
        this.strategyConfig = strategyConfig;
        this.webSocketStreamsService = webSocketStreamsService;
        this.tickerCache = tickerCache;
        this.tradeService = tradeService;
        this.tradingState = tradingState;
        this.tradingConfig = tradingConfig;
        this.symbolRuleCache = symbolRuleCache;
        this.pendingOrderProtectionService = pendingOrderProtectionService;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    public final void scheduledExecute() {
        if (!webSocketStreamsService.isConnected()) return;

        for (String symbol : strategyConfig.getSymbolsForStrategy(getName())) {
            try {
                if (!tickerCache.contains(symbol) || !shouldExecute(symbol)) continue;

                TradingDecision decision = decide(symbol);
                if (decision == null) {
                    log.warn("Strategy 未返回交易决策: strategy={}, symbol={}", getName(), symbol);
                    continue;
                }

                executeDecision(decision);
                lastExecuteTimes.put(symbol, Instant.now());

                log.info("Strategy执行完成: strategy={}, symbol={}, decision={}, direction={}, entry={}, tp={}, sl={}",
                        getName(), symbol, decision.getDecision(), decision.getDirection(),
                        decision.getEntryPrice(), decision.getTakeProfitPrice(), decision.getStopLossPrice());
            } catch (Exception e) {
                log.error("Strategy执行异常: strategy={}, symbol={}", getName(), symbol, e);
            }
        }
    }

    protected void executeDecision(TradingDecision decision) {
        if (decision == null || decision.getDecision() == null
                || TradingDecision.DecisionType.WAIT.equals(decision.getDecision())
                || TradingDecision.DecisionType.HOLD.equals(decision.getDecision())) return;

        String symbol = decision.getSymbol();
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("交易决策缺少 symbol");

        tradingState.updateExecutionState(symbol, TradingExecutionState.CHECKING);

        try {
            if (TradingDecision.DecisionType.EXIT.equals(decision.getDecision())) {
                if (tradingConfig.isEnabled()) tradeService.closeMarketOrder(symbol);
                tradingState.updateExecutionState(symbol, TradingExecutionState.READY);
                return;
            }

            if (TradingDecision.DecisionType.ADJUST_PROTECTION.equals(decision.getDecision())) {
                log.info("Strategy 请求调整保护单，当前由具体 Strategy 自行实现: symbol={}", symbol);
                tradingState.updateExecutionState(symbol, TradingExecutionState.READY);
                return;
            }

            PositionInfo position = tradingState.getPosition(symbol);
            if (position != null && position.hasPosition()) {
                log.info("{} 已存在持仓，忽略重复交易信号: positionAmt={}", symbol, position.getPositionAmt());
                tradingState.updateExecutionState(symbol, TradingExecutionState.POSITION_OPEN);
                return;
            }

            if (!tradingState.getOpenOrders(symbol).isEmpty()) {
                log.info("{} 已存在当前委托，忽略重复交易信号", symbol);
                tradingState.updateExecutionState(symbol, TradingExecutionState.ORDER_OPEN);
                return;
            }

            if (!tradingState.startOrderSubmitting(symbol)) {
                log.info("{} 正在提交订单，忽略重复交易信号", symbol);
                tradingState.updateExecutionState(symbol, TradingExecutionState.SUBMITTING);
                return;
            }

            tradingState.updateExecutionState(symbol, TradingExecutionState.SUBMITTING);
            try {
                if (!tradingConfig.isEnabled()) {
                    log.info("交易执行未启用，收到交易信号但不实际下单: symbol={}, decision={}", symbol, decision.getDecision());
                    tradingState.updateExecutionState(symbol, TradingExecutionState.READY);
                    return;
                }

                prepareTrading(symbol);
                OrderInfo orderInfo = createEntryOrder(decision);
                if (orderInfo == null) throw new IllegalStateException("Strategy 未创建订单: symbol=" + symbol);

                tradingState.updateOrder(orderInfo);
                log.info("交易订单已提交: strategy={}, symbol={}, decision={}, orderId={}",
                        getName(), symbol, decision.getDecision(), orderInfo.getOrderId());

                BigDecimal executedQty = orderInfo.getExecutedQty();
                if (executedQty != null && executedQty.compareTo(BigDecimal.ZERO) > 0) {
                    tradingState.updateExecutionState(symbol, TradingExecutionState.POSITION_OPEN);
                } else {
                    tradingState.updateExecutionState(symbol, TradingExecutionState.ORDER_OPEN);
                }
            } finally {
                tradingState.finishOrderSubmitting(symbol);
            }
        } catch (Exception e) {
            tradingState.finishOrderSubmitting(symbol);
            tradingState.updateExecutionState(symbol, TradingExecutionState.FAILED);
            throw e;
        }
    }

    protected OrderInfo createEntryOrder(TradingDecision decision) {
        String symbol = decision.getSymbol();
        Side side = TradingDecision.DecisionType.ENTER_LONG.equals(decision.getDecision()) ? Side.BUY : Side.SELL;
        BigDecimal positionNotional = calculatePositionNotional(symbol);

        if ("MARKET".equalsIgnoreCase(tradingConfig.getOrderType())) {
            OrderInfo orderInfo = tradeService.createMarketOrder(symbol, side, positionNotional);
            if (tradingConfig.isTakeProfitStopLossEnabled()) {
                pendingOrderProtectionService.register(symbol, orderInfo.getOrderId(), side,
                        tradingConfig.getTakeProfitAmount(), tradingConfig.getStopLossAmount());
            }
            return orderInfo;
        }

        if (!"LIMIT".equalsIgnoreCase(tradingConfig.getOrderType())) {
            throw new IllegalArgumentException("不支持的下单模式: " + tradingConfig.getOrderType());
        }

        BigDecimal price = getLimitOrderPrice(symbol, side);
        BigDecimal quantity = positionNotional.divide(price, 20, java.math.RoundingMode.DOWN);

        if (tradingConfig.isTakeProfitStopLossEnabled()) {
            return tradeService.createLimitOrderAndTPSLForProfitAndLoss(
                    symbol, side, TimeInForce.GTC, quantity, price,
                    tradingConfig.getTakeProfitAmount(), tradingConfig.getStopLossAmount());
        }

        return tradeService.createLimitOrder(symbol, side, TimeInForce.GTC,
                positionNotional, price);
    }

    /**
     * 根据保证金金额和实际使用的杠杆计算仓位名义价值。
     * 例如保证金 5 USDT、20 倍杠杆，则实际下单名义价值为 100 USDT。
     */
    protected BigDecimal calculatePositionNotional(String symbol) {
        BigDecimal marginAmount = tradingConfig.getMarginAmount();
        if (marginAmount == null || marginAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("保证金金额必须大于 0：" + marginAmount);
        }

        long leverage = getEffectiveLeverage(symbol);
        return marginAmount.multiply(BigDecimal.valueOf(leverage));
    }

    /** 获取本次交易实际使用的杠杆，与 prepareTrading 保持完全一致。 */
    protected long getEffectiveLeverage(String symbol) {
        long leverage = tradingConfig.isUseMaxLeverage()
                ? symbolRuleCache.getMaxLeverage(symbol)
                : tradingConfig.getLeverage();
        if (leverage <= 0) {
            throw new IllegalArgumentException("杠杆必须大于 0：symbol=" + symbol + ", leverage=" + leverage);
        }
        return leverage;
    }

    protected void prepareTrading(String symbol) {
        ensureMarginType(symbol);
        ensureLeverage(symbol);
    }

    private void ensureMarginType(String symbol) {
        TradingMarginType target = TradingMarginType.valueOf(tradingConfig.getMarginType().toUpperCase());
        String current = tradeService.getMarginType(symbol).toUpperCase();
        if (target.name().contains(current)) return;
        if (!tradeService.setMarginType(symbol, target)) {
            throw new IllegalStateException("设置保证金模式失败: symbol=" + symbol + ", target=" + target);
        }
    }

    private void ensureLeverage(String symbol) {
        if (!tradingConfig.isAutoSetLeverage()) return;
        tradeService.setLeverage(symbol, getEffectiveLeverage(symbol));
    }

    protected BigDecimal getLimitOrderPrice(String symbol, Side side) {
        if (!"LATEST_PRICE".equalsIgnoreCase(tradingConfig.getLimitPriceSource())) {
            throw new IllegalArgumentException("不支持的限价单价格来源: " + tradingConfig.getLimitPriceSource());
        }
        var ticker = tickerCache.get(symbol);
        if (ticker == null) throw new IllegalStateException("WebSocket 尚未收到最新 BookTicker: symbol=" + symbol);

        BigDecimal price = Side.BUY.equals(side) ? ticker.getAskPrice() : ticker.getBidPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("WebSocket 最新价格无效: symbol=" + symbol + ", price=" + price);
        }
        return price;
    }

    @Override
    public boolean shouldExecute(String symbol) {
        Instant last = lastExecuteTimes.get(symbol);
        if (last == null) return true;
        Duration interval = getExecutionInterval(symbol);
        if (interval == null || interval.isNegative() || interval.isZero()) return true;
        return Duration.between(last, Instant.now()).compareTo(interval) >= 0;
    }

    protected Duration getExecutionInterval(String symbol) { return Duration.ofSeconds(10); }

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
        if (upCount >= getRequiredMatchCount()) return TradingSignal.LONG;
        if (downCount >= getRequiredMatchCount()) return TradingSignal.SHORT;
        return TradingSignal.WAIT;
    }

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
                .reason("策略: " + getName() + ", 信号: " + signal)
                .build();
    }
}
