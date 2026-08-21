package com.lxf.bian_bot.trading.service;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.config.TradingConfig;
import com.lxf.bian_bot.model.OrderInfo;
import com.lxf.bian_bot.model.PositionInfo;
import com.lxf.bian_bot.model.TradingDecision;
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

    public BinanceTradingServiceImpl(BinanceTradeService tradeService, TradingState tradingState, TradingConfig tradingConfig) {
        this.tradeService = tradeService;
        this.tradingState = tradingState;
        this.tradingConfig = tradingConfig;
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
            // 账户、持仓和委托状态由 Binance User Data Stream 实时维护，不再在下单前主动 REST 刷新。
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

                if (tradingConfig.isAutoSetLeverage()) {
                    tradeService.setLeverage(symbol, tradingConfig.getLeverage());
                }

                Side side = TradingDecision.DecisionType.ENTER_LONG.equals(decision.getDecision()) ? Side.BUY : Side.SELL;
                double currentPrice = tradeService.getPosition(symbol).getMarkPrice();
                OrderInfo orderInfo = tradeService.createLimitOrderAndTPSLForProfitAndLoss(
                        symbol,
                        side,
                        com.lxf.bian_bot.model.TimeInForce.GTC,
                        tradingConfig.getUsdtAmount() / currentPrice,
                        currentPrice,
                        0.02,
                        0.03);
                tradingState.updateOrder(orderInfo);

                log.info("交易订单已提交: symbol={}, decision={}, orderId={}", symbol, decision.getDecision(), orderInfo.getOrderId());

                // 开仓订单提交后的最终状态由 User Data Stream 的 ORDER_TRADE_UPDATE / ACCOUNT_UPDATE 更新。
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
}
