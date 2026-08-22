package com.lxf.bian_bot.trading.service;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.cache.TickerCache;
import com.lxf.bian_bot.config.SymbolRuleCache;
import com.lxf.bian_bot.config.model.TradingConfig;
import com.lxf.bian_bot.model.OrderInfo;
import com.lxf.bian_bot.model.PositionInfo;
import com.lxf.bian_bot.model.TimeInForce;
import com.lxf.bian_bot.model.TradingDecision;
import com.lxf.bian_bot.model.TradingMarginType;
import com.lxf.bian_bot.service.BinanceTradeService;
import com.lxf.bian_bot.trading.state.TradingExecutionState;
import com.lxf.bian_bot.trading.state.TradingState;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceTradingServiceImpl implements BinanceTradingService {

    private final BinanceTradeService tradeService;
    private final TradingState tradingState;
    private final TradingConfig tradingConfig;
    private final SymbolRuleCache symbolRuleCache;
    private final TickerCache tickerCache;
    private final PendingOrderProtectionService pendingOrderProtectionService;

    public BinanceTradingServiceImpl(BinanceTradeService tradeService,
                                     TradingState tradingState,
                                     TradingConfig tradingConfig,
                                     SymbolRuleCache symbolRuleCache,
                                     TickerCache tickerCache,
                                     PendingOrderProtectionService pendingOrderProtectionService) {
        this.tradeService = tradeService;
        this.tradingState = tradingState;
        this.tradingConfig = tradingConfig;
        this.symbolRuleCache = symbolRuleCache;
        this.tickerCache = tickerCache;
        this.pendingOrderProtectionService = pendingOrderProtectionService;
    }

    @Override
    public void execute(TradingDecision decision) {
        if (decision == null || decision.getDecision() == null || TradingDecision.DecisionType.WAIT.equals(decision.getDecision())) {
            return;
        }

        String symbol = decision.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            log.warn("交易决策缺少 symbol");
            return;
        }

        tradingState.updateExecutionState(symbol, TradingExecutionState.CHECKING);

        try {
            PositionInfo position = tradingState.getPosition(symbol);
            if (position != null && Double.compare(position.getPositionAmt(), 0D) != 0) {
                log.info("{} 已存在持仓，忽略重复交易信号: positionAmt={}", symbol, position.getPositionAmt());
                tradingState.updateExecutionState(symbol, TradingExecutionState.POSITION_OPEN);
                return;
            }

            List<OrderInfo> openOrders = tradingState.getOpenOrders(symbol);
            if (!openOrders.isEmpty()) {
                log.info("{} 已存在当前委托，忽略重复交易信号: openOrders={}", symbol, openOrders.size());
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

                ensureMarginType(symbol);
                ensureLeverage(symbol);

                Side side = TradingDecision.DecisionType.ENTER_LONG.equals(decision.getDecision()) ? Side.BUY : Side.SELL;
                OrderInfo orderInfo = createOrder(symbol, side);
                tradingState.updateOrder(orderInfo);

                log.info("交易订单已提交: symbol={}, decision={}, orderType={}, orderId={}",
                        symbol, decision.getDecision(), tradingConfig.getOrderType(), orderInfo.getOrderId());

                if (orderInfo.getExecutedQty() > 0D) {
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
            log.error("交易执行失败: symbol={}, decision={}", symbol, decision.getDecision(), e);
        }
    }

    /**
     * 确保交易对使用配置的保证金模式。
     * 设置前先读取当前模式，已经一致时不再调用修改接口。
     */
    private void ensureMarginType(String symbol) {
        TradingMarginType target = TradingMarginType.valueOf(tradingConfig.getMarginType().toUpperCase());
        String current = tradeService.getMarginType(symbol).toUpperCase();

        if (target.name().contains(current)) {
            log.info("保证金模式已符合配置: symbol={}, marginType={}", symbol, current);
            return;
        }

        boolean success = tradeService.setMarginType(symbol, target);
        if (!success) {
            throw new IllegalStateException("设置保证金模式失败: symbol=" + symbol + ", target=" + target);
        }
        log.info("保证金模式已修改: symbol={}, from={}, to={}", symbol, current, target);
    }

    /**
     * 根据配置决定是否自动设置杠杆。
     * useMaxLeverage=true 时，从 SymbolRuleCache 按需获取交易对允许的最大杠杆。
     */
    private void ensureLeverage(String symbol) {
        if (!tradingConfig.isAutoSetLeverage()) {
            return;
        }

        long leverage = tradingConfig.isUseMaxLeverage()
                ? symbolRuleCache.getMaxLeverage(symbol)
                : tradingConfig.getLeverage();

        log.info("准备设置杠杆: symbol={}, leverage={}, useMaxLeverage={}",
                symbol, leverage, tradingConfig.isUseMaxLeverage());
        tradeService.setLeverage(symbol, leverage);
    }

    private OrderInfo createOrder(String symbol, Side side) {
        if ("MARKET".equalsIgnoreCase(tradingConfig.getOrderType())) {
            OrderInfo orderInfo = tradeService.createMarketOrder(symbol, side, tradingConfig.getUsdtAmount());
            if (tradingConfig.isTakeProfitStopLossEnabled()) {
                pendingOrderProtectionService.register(
                        symbol,
                        orderInfo.getOrderId(),
                        side,
                        tradingConfig.getTakeProfitAmount(),
                        tradingConfig.getStopLossAmount());
            }
            return orderInfo;
        }

        if (!"LIMIT".equalsIgnoreCase(tradingConfig.getOrderType())) {
            throw new IllegalArgumentException("不支持的下单模式: " + tradingConfig.getOrderType());
        }

        double price = getLimitOrderPrice(symbol, side);
        double quantity = tradingConfig.getUsdtAmount() / price;

        if (tradingConfig.isTakeProfitStopLossEnabled()) {
            return tradeService.createLimitOrderAndTPSLForProfitAndLoss(
                    symbol,
                    side,
                    TimeInForce.GTC,
                    quantity,
                    price,
                    tradingConfig.getTakeProfitAmount(),
                    tradingConfig.getStopLossAmount());
        }

        return tradeService.createLimitOrder(
                symbol,
                side,
                TimeInForce.GTC,
                tradingConfig.getUsdtAmount(),
                price);
    }

    /**
     * 限价单价格来源。
     * LATEST_PRICE 使用 WebSocket Streams 持续更新的 BookTicker，不再额外调用 REST 查询价格。
     */
    private double getLimitOrderPrice(String symbol, Side side) {
        if (!"LATEST_PRICE".equalsIgnoreCase(tradingConfig.getLimitPriceSource())) {
            throw new IllegalArgumentException("不支持的限价单价格来源: " + tradingConfig.getLimitPriceSource());
        }

        var ticker = tickerCache.get(symbol);
        if (ticker == null) {
            throw new IllegalStateException("WebSocket 尚未收到最新 BookTicker: symbol=" + symbol);
        }

        double price = Side.BUY.equals(side)
                ? ticker.getAskPrice().doubleValue()
                : ticker.getBidPrice().doubleValue();

        if (price <= 0D) {
            throw new IllegalStateException("WebSocket 最新价格无效: symbol=" + symbol + ", price=" + price);
        }

        log.info("使用 WebSocket 最新盘口价格: symbol={}, side={}, price={}, bid={}, ask={}",
                symbol, side, price, ticker.getBidPrice(), ticker.getAskPrice());
        return price;
    }
}
