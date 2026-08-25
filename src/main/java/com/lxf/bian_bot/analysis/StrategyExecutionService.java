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
import com.lxf.bian_bot.trading.order.OrderParameterCalculator;
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
 * 保证金来自 TradingConfig；本类根据实际杠杆和入场价格计算仓位名义价值及最终数量。</p>
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
    private final OrderParameterCalculator orderParameterCalculator;
    private final ApplicationEventPublisher eventPublisher;

    public StrategyExecutionService(BinanceTradeService tradeService,
                                     TradingState tradingState,
                                     TradingConfig tradingConfig,
                                     SymbolRuleCache symbolRuleCache,
                                     TickerCache tickerCache,
                                     OrderParameterCalculator orderParameterCalculator,
                                     ApplicationEventPublisher eventPublisher) {
        this.tradeService = tradeService;
        this.tradingState = tradingState;
        this.tradingConfig = tradingConfig;
        this.symbolRuleCache = symbolRuleCache;
        this.tickerCache = tickerCache;
        this.orderParameterCalculator = orderParameterCalculator;
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
                case ADJUST_PROTECTION -> executeAdjustProtection(decision);
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
     * 执行 Strategy 已经计算好的 TP/SL；这里只调用 Binance，不计算保护价格。
     * 该方法既用于刚开仓后的保护，也用于程序启动后已有持仓的保护恢复/调整。
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

    /**
     * 执行策略对已有持仓提出的 ADJUST_PROTECTION 决策。
     *
     * <p>此场景通常发生在程序启动前仓位已经存在时，因此不能依赖
     * TradingDecision.entryPrice 或重新计算开仓数量。直接读取当前持仓的真实数量，
     * 并使用 Strategy 给出的 TP/SL 创建保护单。</p>
     */
    private void executeAdjustProtection(TradingDecision decision) {
        String symbol = decision.getSymbol();

        if (!tradingConfig.isEnabled()) {
            log.info("交易执行未启用，忽略策略保护调整: symbol={}", symbol);
            return;
        }

        if (decision.getTakeProfitPrice() == null || decision.getStopLossPrice() == null) {
            throw new IllegalArgumentException("ADJUST_PROTECTION 缺少 TP/SL: symbol=" + symbol);
        }

        PositionInfo position = tradingState.getPosition(symbol);
        if (position == null || !position.hasPosition()) {
            log.info("策略要求调整保护，但当前没有持仓: symbol={}", symbol);
            return;
        }

        BigDecimal positionAmount = position.getPositionAmt();
        BigDecimal quantity = orderParameterCalculator.normalizeMarketQuantity(
                symbol, positionAmount.abs(), BigDecimal.ZERO);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("已有持仓数量无效，无法设置保护单: symbol=" + symbol
                    + ", positionAmt=" + positionAmount);
        }

        Side openSide = positionAmount.compareTo(BigDecimal.ZERO) > 0 ? Side.BUY : Side.SELL;
        ProtectionConfig protection = new ProtectionConfig(
                true,
                decision.getTakeProfitPrice(),
                decision.getStopLossPrice());

        log.info("执行已有持仓保护: symbol={}, side={}, quantity={}, entryPrice={}, tp={}, sl={}",
                symbol,
                openSide,
                quantity,
                position.getEntryPrice(),
                protection.takeProfitPrice(),
                protection.stopLossPrice());

        executeProtection(symbol, openSide, quantity, protection);
    }

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

    /**
     * 根据交易配置的保证金和实际杠杆计算最终数量。
     *
     * <p>positionNotional = marginAmount × leverage</p>
     * <p>quantity = positionNotional ÷ entryPrice</p>
     * <p>随后由 OrderParameterCalculator 按 Binance 交易规则修正数量。</p>
     */
    private OrderInfo createEntryOrder(TradingDecision decision) {
        String symbol = decision.getSymbol();
        Side side = TradingDecision.DecisionType.ENTER_LONG.equals(decision.getDecision())
                ? Side.BUY : Side.SELL;

        BigDecimal price = decision.getEntryPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            price = getLimitOrderPrice(symbol, side);
        }

        BigDecimal marginAmount = tradingConfig.getMarginAmount();
        if (marginAmount == null || marginAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("保证金金额必须大于 0: symbol=" + symbol + ", marginAmount=" + marginAmount);
        }

        long leverage = getEffectiveLeverage(symbol);
        BigDecimal positionNotional = marginAmount.multiply(BigDecimal.valueOf(leverage));
        BigDecimal quantity;

        if ("MARKET".equalsIgnoreCase(tradingConfig.getOrderType())) {
            BigDecimal marketPrice = getMarketOrderPrice(symbol, side);
            quantity = orderParameterCalculator.calculateMarketQuantity(symbol, positionNotional, marketPrice);
            log.info("开仓数量计算: symbol={}, marginAmount={}, leverage={}, positionNotional={}, price={}, quantity={}",
                    symbol, marginAmount, leverage, positionNotional, marketPrice, quantity);
            return tradeService.createMarketOrder(symbol, side, quantity);
        }

        if (!"LIMIT".equalsIgnoreCase(tradingConfig.getOrderType())) {
            throw new IllegalArgumentException("不支持的下单模式: " + tradingConfig.getOrderType());
        }

        BigDecimal normalizedPrice = orderParameterCalculator.normalizeEntryPrice(
                symbol, price, Side.BUY.equals(side));
        quantity = orderParameterCalculator.calculateLimitQuantity(symbol, positionNotional, normalizedPrice);
        log.info("开仓数量计算: symbol={}, marginAmount={}, leverage={}, positionNotional={}, price={}, quantity={}",
                symbol, marginAmount, leverage, positionNotional, normalizedPrice, quantity);
        return tradeService.createLimitOrder(symbol, side, TimeInForce.GTC, quantity, normalizedPrice);
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

    private BigDecimal getMarketOrderPrice(String symbol, Side side) {
        var ticker = tickerCache.get(symbol);
        if (ticker == null) {
            throw new IllegalStateException("WebSocket 尚未收到最新 BookTicker: symbol=" + symbol);
        }
        BigDecimal price = Side.BUY.equals(side) ? ticker.getAskPrice() : ticker.getBidPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("WebSocket 最新盘口价格无效: symbol=" + symbol + ", price=" + price);
        }
        return price;
    }
}
