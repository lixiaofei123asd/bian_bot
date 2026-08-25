package com.lxf.bian_bot.analysis;

import com.binance.connector.client.common.ApiException;
import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.analysis.model.ProtectionConfig;
import com.lxf.bian_bot.cache.TickerCache;
import com.lxf.bian_bot.config.SymbolRuleCache;
import com.lxf.bian_bot.config.trading.TradingConfig;
import com.lxf.bian_bot.model.TradingDecision;
import com.lxf.bian_bot.thirdparty.binance.enums.TimeInForce;
import com.lxf.bian_bot.thirdparty.binance.enums.TradingMarginType;
import com.lxf.bian_bot.thirdparty.binance.model.OrderInfo;
import com.lxf.bian_bot.thirdparty.binance.model.PositionInfo;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceTradeService;
import com.lxf.bian_bot.trading.state.TradingExecutionState;
import com.lxf.bian_bot.trading.state.TradingState;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 策略通用交易执行器。
 *
 * <p>本类不做任何交易决策：不决定方向、不决定 TP/SL、不决定撤单时间。
 * 它只负责把 Strategy 已经产生的 TradingDecision 执行到 Binance。</p>
 */
@Slf4j
@Service
public class StrategyExecutionService {

    private static final String IMMEDIATE_TRIGGER_CODE = "-2021";

    private final BinanceTradeService tradeService;
    private final TradingState tradingState;
    private final TradingConfig tradingConfig;
    private final SymbolRuleCache symbolRuleCache;
    private final TickerCache tickerCache;
    private final ApplicationEventPublisher eventPublisher;

    public StrategyExecutionService(BinanceTradeService tradeService,
                                     TradingState tradingState,
                                     TradingConfig tradingConfig,
                                     SymbolRuleCache symbolRuleCache,
                                     TickerCache tickerCache,
                                     ApplicationEventPublisher eventPublisher) {
        this.tradeService = tradeService;
        this.tradingState = tradingState;
        this.tradingConfig = tradingConfig;
        this.symbolRuleCache = symbolRuleCache;
        this.tickerCache = tickerCache;
        this.eventPublisher = eventPublisher;
    }

    /** 执行 Strategy 已经决定好的交易动作。 */
    public void execute(TradingDecision decision) {
        if (decision == null || decision.getDecision() == null
                || TradingDecision.DecisionType.WAIT.equals(decision.getDecision())
                || TradingDecision.DecisionType.HOLD.equals(decision.getDecision())) {
            return;
        }

        String symbol = decision.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("交易决策缺少 symbol");
        }

        tradingState.updateExecutionState(symbol, TradingExecutionState.CHECKING);
        try {
            switch (decision.getDecision()) {
                case EXIT -> executeExit(symbol);
                case ADJUST_PROTECTION -> log.info("Strategy 请求调整保护单: symbol={}", symbol);
                case ENTER_LONG, ENTER_SHORT -> executeEntry(decision);
                default -> {
                    return;
                }
            }
            tradingState.updateExecutionState(symbol, TradingExecutionState.READY);
        } catch (Exception e) {
            tradingState.finishOrderSubmitting(symbol);
            tradingState.updateExecutionState(symbol, TradingExecutionState.FAILED);
            throw e;
        }
    }

    /**
     * 执行 Strategy 已经计算好的 TP/SL。
     * 这里只调用 Binance，不计算任何保护价格。
     */
    public void executeProtection(String symbol,
                                  Side openSide,
                                  BigDecimal quantity,
                                  ProtectionConfig protectionConfig) {
        if (protectionConfig == null || !protectionConfig.enabled()) {
            log.info("策略未启用仓位保护: symbol={}", symbol);
            return;
        }
        if (protectionConfig.takeProfitPrice() == null || protectionConfig.stopLossPrice() == null) {
            throw new IllegalArgumentException("策略保护配置缺少 TP/SL: symbol=" + symbol);
        }

        try {
            tradeService.createProtectionOrders(
                    symbol,
                    openSide,
                    quantity,
                    protectionConfig.takeProfitPrice(),
                    protectionConfig.stopLossPrice());
        } catch (ApiException e) {
            if (e.getMessage() != null && e.getMessage().contains(IMMEDIATE_TRIGGER_CODE)) {
                eventPublisher.publishEvent(new ProtectionTriggerEvent(
                        symbol,
                        openSide,
                        quantity,
                        protectionConfig.takeProfitPrice(),
                        protectionConfig.stopLossPrice()));
                return;
            }
            throw e;
        }
    }

    /** 执行策略决定的开仓动作。 */
    private void executeEntry(TradingDecision decision) {
        String symbol = decision.getSymbol();

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
                log.info("交易执行未启用，收到交易信号但不实际下单: symbol={}, decision={}",
                        symbol, decision.getDecision());
                tradingState.updateExecutionState(symbol, TradingExecutionState.READY);
                return;
            }

            prepareTrading(symbol);
            OrderInfo orderInfo = createEntryOrder(decision);
            if (orderInfo == null) {
                throw new IllegalStateException("未创建订单: symbol=" + symbol);
            }

            tradingState.updateOrder(orderInfo);
            BigDecimal executedQty = orderInfo.getExecutedQty();
            boolean filled = executedQty != null && executedQty.compareTo(BigDecimal.ZERO) > 0;
            tradingState.updateExecutionState(symbol,
                    filled ? TradingExecutionState.POSITION_OPEN : TradingExecutionState.ORDER_OPEN);

            if (filled) {
                BigDecimal entryPrice = orderInfo.getAvgPrice();
                if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    entryPrice = orderInfo.getPrice();
                }
                if (entryPrice != null && entryPrice.compareTo(BigDecimal.ZERO) > 0) {
                    Side openSide = TradingDecision.DecisionType.ENTER_LONG.equals(decision.getDecision())
                            ? Side.BUY : Side.SELL;
                    eventPublisher.publishEvent(new OrderFilledEvent(
                            symbol,
                            orderInfo.getOrderId(),
                            openSide,
                            executedQty,
                            entryPrice));
                }
            }
        } finally {
            tradingState.finishOrderSubmitting(symbol);
        }
    }

    private void executeExit(String symbol) {
        if (!tradingConfig.isEnabled()) {
            log.info("交易执行未启用，忽略策略平仓决策: symbol={}", symbol);
            return;
        }
        tradeService.closeMarketOrder(symbol);
    }

    private OrderInfo createEntryOrder(TradingDecision decision) {
        String symbol = decision.getSymbol();
        Side side = TradingDecision.DecisionType.ENTER_LONG.equals(decision.getDecision())
                ? Side.BUY : Side.SELL;

        BigDecimal price = decision.getEntryPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            price = getLimitOrderPrice(symbol, side);
        }

        BigDecimal marginAmount = decision.getMarginAmount();
        if (marginAmount == null || marginAmount.compareTo(BigDecimal.ZERO) <= 0) {
            marginAmount = tradingConfig.getMarginAmount();
        }
        if (marginAmount == null || marginAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("保证金金额必须大于 0: symbol=" + symbol + ", marginAmount=" + marginAmount);
        }

        BigDecimal positionNotional = marginAmount.multiply(BigDecimal.valueOf(getEffectiveLeverage(symbol)));

        if ("MARKET".equalsIgnoreCase(tradingConfig.getOrderType())) {
            return tradeService.createMarketOrder(symbol, side, positionNotional);
        }
        if (!"LIMIT".equalsIgnoreCase(tradingConfig.getOrderType())) {
            throw new IllegalArgumentException("不支持的下单模式: " + tradingConfig.getOrderType());
        }
        return tradeService.createLimitOrder(symbol, side, TimeInForce.GTC, positionNotional, price);
    }

    private long getEffectiveLeverage(String symbol) {
        long leverage = tradingConfig.isUseMaxLeverage()
                ? symbolRuleCache.getMaxLeverage(symbol)
                : tradingConfig.getLeverage();
        if (leverage <= 0) {
            throw new IllegalArgumentException("杠杆必须大于 0: symbol=" + symbol + ", leverage=" + leverage);
        }
        return leverage;
    }

    private void prepareTrading(String symbol) {
        TradingMarginType target = TradingMarginType.valueOf(tradingConfig.getMarginType().toUpperCase());
        String current = tradeService.getMarginType(symbol).toUpperCase();
        if (!target.name().contains(current) && !tradeService.setMarginType(symbol, target)) {
            throw new IllegalStateException("设置保证金模式失败: symbol=" + symbol + ", target=" + target);
        }
        if (tradingConfig.isAutoSetLeverage()) {
            tradeService.setLeverage(symbol, getEffectiveLeverage(symbol));
        }
    }

    private BigDecimal getLimitOrderPrice(String symbol, Side side) {
        if (!"LATEST_PRICE".equalsIgnoreCase(tradingConfig.getLimitPriceSource())) {
            throw new IllegalArgumentException("不支持的限价单价格来源: " + tradingConfig.getLimitPriceSource());
        }
        var ticker = tickerCache.get(symbol);
        if (ticker == null) {
            throw new IllegalStateException("WebSocket 尚未收到最新 BookTicker: symbol=" + symbol);
        }
        BigDecimal price = Side.BUY.equals(side) ? ticker.getAskPrice() : ticker.getBidPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("WebSocket 最新价格无效: symbol=" + symbol + ", price=" + price);
        }
        return price;
    }
}
