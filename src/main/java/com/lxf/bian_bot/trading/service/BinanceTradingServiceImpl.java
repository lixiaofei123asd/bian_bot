package com.lxf.bian_bot.trading.service;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.config.TradingConfig;
import com.lxf.bian_bot.model.OrderInfo;
import com.lxf.bian_bot.model.PositionInfo;
import com.lxf.bian_bot.model.TradingDecision;
import com.lxf.bian_bot.service.BinanceTradeService;
import com.lxf.bian_bot.trading.state.TradingExecutionState;
import com.lxf.bian_bot.trading.state.TradingState;
import com.lxf.bian_bot.trading.state.TradingStateManager;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceTradingServiceImpl implements BinanceTradingService {

    private final BinanceTradeService tradeService;
    private final TradingStateManager stateManager;
    private final TradingState tradingState;
    private final TradingConfig tradingConfig;

    public BinanceTradingServiceImpl(BinanceTradeService tradeService, TradingStateManager stateManager, TradingState tradingState, TradingConfig tradingConfig) {
        this.tradeService = tradeService;
        this.stateManager = stateManager;
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
            // 下单前重新同步一次真实账户状态
            stateManager.refreshSymbol(symbol);

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
                OrderInfo orderInfo = tradeService.createMarketOrder(symbol, side, tradingConfig.getUsdtAmount());
                tradingState.updateOrder(orderInfo);

                log.info("交易订单已提交: symbol={}, decision={}, orderId={}", symbol, decision.getDecision(), orderInfo.getOrderId());

                // 下单后立即重新查询一次，判断订单是否仍在委托中或者已经形成持仓
                stateManager.refreshSymbol(symbol);
                PositionInfo latestPosition = tradingState.getPosition(symbol);
                List<OrderInfo> latestOpenOrders = tradingState.getOpenOrders(symbol);

                if (latestPosition != null && Double.compare(latestPosition.getPositionAmt(), 0D) != 0) {
                    tradingState.updateExecutionState(symbol, TradingExecutionState.POSITION_OPEN);
                } else if (!latestOpenOrders.isEmpty()) {
                    tradingState.updateExecutionState(symbol, TradingExecutionState.ORDER_OPEN);
                } else {
                    tradingState.updateExecutionState(symbol, TradingExecutionState.READY);
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
