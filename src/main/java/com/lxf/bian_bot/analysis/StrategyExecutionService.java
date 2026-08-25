package com.lxf.bian_bot.analysis;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
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
import com.lxf.bian_bot.trading.service.PendingOrderProtectionService;
import com.lxf.bian_bot.trading.state.TradingExecutionState;
import com.lxf.bian_bot.trading.state.TradingState;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 策略通用交易执行器：策略只产生 TradingDecision，本类负责真正执行。 */
@Slf4j
@Service
public class StrategyExecutionService {

    private final BinanceTradeService tradeService;
    private final TradingState tradingState;
    private final TradingConfig tradingConfig;
    private final SymbolRuleCache symbolRuleCache;
    private final TickerCache tickerCache;
    private final PendingOrderProtectionService pendingOrderProtectionService;
    private final StrategyRegistry strategyRegistry;
    private final StrategyConfig strategyConfig;

    public StrategyExecutionService(
            BinanceTradeService tradeService,
            TradingState tradingState,
            TradingConfig tradingConfig,
            SymbolRuleCache symbolRuleCache,
            TickerCache tickerCache,
            PendingOrderProtectionService pendingOrderProtectionService,
            StrategyRegistry strategyRegistry,
            StrategyConfig strategyConfig) {
        this.tradeService = tradeService;
        this.tradingState = tradingState;
        this.tradingConfig = tradingConfig;
        this.symbolRuleCache = symbolRuleCache;
        this.tickerCache = tickerCache;
        this.pendingOrderProtectionService = pendingOrderProtectionService;
        this.strategyRegistry = strategyRegistry;
        this.strategyConfig = strategyConfig;
    }

    public void execute(TradingDecision decision) {
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
                log.info("Strategy 请求调整保护单: symbol={}", symbol);
                tradingState.updateExecutionState(symbol, TradingExecutionState.READY);
                return;
            }

            PositionInfo position = tradingState.getPosition(symbol);
            if (position != null && position.hasPosition()) {
                tradingState.updateExecutionState(symbol, TradingExecutionState.POSITION_OPEN);
                return;
            }
            if (!tradingState.getOpenOrders(symbol).isEmpty()) {
                tradingState.updateExecutionState(symbol, TradingExecutionState.ORDER_OPEN);
                return;
            }
            if (!tradingState.startOrderSubmitting(symbol)) {
                tradingState.updateExecutionState(symbol, TradingExecutionState.SUBMITTING);
                return;
            }

            try {
                if (!tradingConfig.isEnabled()) {
                    log.info("交易执行未启用，收到交易信号但不实际下单: symbol={}, decision={}", symbol, decision.getDecision());
                    tradingState.updateExecutionState(symbol, TradingExecutionState.READY);
                    return;
                }

                prepareTrading(symbol);
                OrderInfo orderInfo = createEntryOrder(decision);
                if (orderInfo == null) throw new IllegalStateException("未创建订单: symbol=" + symbol);
                tradingState.updateOrder(orderInfo);
                BigDecimal executedQty = orderInfo.getExecutedQty();
                tradingState.updateExecutionState(symbol,
                        executedQty != null && executedQty.compareTo(BigDecimal.ZERO) > 0
                                ? TradingExecutionState.POSITION_OPEN : TradingExecutionState.ORDER_OPEN);
            } finally {
                tradingState.finishOrderSubmitting(symbol);
            }
        } catch (Exception e) {
            tradingState.finishOrderSubmitting(symbol);
            tradingState.updateExecutionState(symbol, TradingExecutionState.FAILED);
            throw e;
        }
    }

    /** 保护单无法创建且价格已越过触发价时，由具体策略决定是否退出。 */
    public void handleProtectionTrigger(String symbol, TradingDecision.Direction direction,
                                        BigDecimal quantity, BigDecimal takeProfitPrice, BigDecimal stopLossPrice) {
        String strategyName = strategyConfig.getStrategyName(symbol);
        Strategy strategy = strategyRegistry.getStrategy(strategyName);
        if (strategy == null) {
            log.error("无法找到保护触发对应策略: symbol={}, strategy={}", symbol, strategyName);
            return;
        }
        TradingDecision decision = strategy.onProtectionTrigger(symbol, direction, quantity, takeProfitPrice, stopLossPrice);
        if (decision == null) {
            log.warn("Strategy 未返回保护触发决策: strategy={}, symbol={}", strategyName, symbol);
            return;
        }
        log.warn("保护触发交给策略决策: strategy={}, symbol={}, decision={}", strategyName, symbol, decision.getDecision());
        execute(decision);
    }

    private OrderInfo createEntryOrder(TradingDecision decision) {
        String symbol = decision.getSymbol();
        Side side = TradingDecision.DecisionType.ENTER_LONG.equals(decision.getDecision()) ? Side.BUY : Side.SELL;
        BigDecimal price = decision.getEntryPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) price = getLimitOrderPrice(symbol, side);

        BigDecimal positionNotional = decision.getUsdtAmount();
        if (positionNotional == null || positionNotional.compareTo(BigDecimal.ZERO) <= 0) {
            positionNotional = calculatePositionNotional(symbol);
        }

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

        BigDecimal quantity = positionNotional.divide(price, 20, RoundingMode.DOWN);
        if (decision.getTakeProfitPrice() != null && decision.getStopLossPrice() != null) {
            return tradeService.createLimitOrderAndTPSLForPrice(symbol, side, TimeInForce.GTC, quantity, price,
                    decision.getTakeProfitPrice(), decision.getStopLossPrice());
        }
        if (tradingConfig.isTakeProfitStopLossEnabled()) {
            return tradeService.createLimitOrderAndTPSLForProfitAndLoss(symbol, side, TimeInForce.GTC, quantity, price,
                    tradingConfig.getTakeProfitAmount(), tradingConfig.getStopLossAmount());
        }
        return tradeService.createLimitOrder(symbol, side, TimeInForce.GTC, positionNotional, price);
    }

    private BigDecimal calculatePositionNotional(String symbol) {
        BigDecimal marginAmount = tradingConfig.getMarginAmount();
        if (marginAmount == null || marginAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("保证金金额必须大于 0：" + marginAmount);
        }
        return marginAmount.multiply(BigDecimal.valueOf(getEffectiveLeverage(symbol)));
    }

    private long getEffectiveLeverage(String symbol) {
        long leverage = tradingConfig.isUseMaxLeverage() ? symbolRuleCache.getMaxLeverage(symbol) : tradingConfig.getLeverage();
        if (leverage <= 0) throw new IllegalArgumentException("杠杆必须大于 0：symbol=" + symbol + ", leverage=" + leverage);
        return leverage;
    }

    private void prepareTrading(String symbol) {
        TradingMarginType target = TradingMarginType.valueOf(tradingConfig.getMarginType().toUpperCase());
        String current = tradeService.getMarginType(symbol).toUpperCase();
        if (!target.name().contains(current) && !tradeService.setMarginType(symbol, target)) {
            throw new IllegalStateException("设置保证金模式失败: symbol=" + symbol + ", target=" + target);
        }
        if (tradingConfig.isAutoSetLeverage()) tradeService.setLeverage(symbol, getEffectiveLeverage(symbol));
    }

    private BigDecimal getLimitOrderPrice(String symbol, Side side) {
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
}
