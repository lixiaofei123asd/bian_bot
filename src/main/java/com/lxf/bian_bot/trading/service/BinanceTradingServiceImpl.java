package com.lxf.bian_bot.trading.service;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.config.TradingProperties;
import com.lxf.bian_bot.model.PositionInfo;
import com.lxf.bian_bot.model.TradingDecision;
import com.lxf.bian_bot.model.OrderInfo;
import com.lxf.bian_bot.service.BinanceTradeService;
import com.lxf.bian_bot.trading.state.TradingState;
import com.lxf.bian_bot.trading.state.TradingStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BinanceTradingServiceImpl implements BinanceTradingService {

    private final BinanceTradeService tradeService;
    private final TradingStateManager stateManager;
    private final TradingState tradingState;
    private final TradingProperties properties;

    public BinanceTradingServiceImpl(BinanceTradeService tradeService, TradingStateManager stateManager, TradingState tradingState, TradingProperties properties) {
        this.tradeService = tradeService;
        this.stateManager = stateManager;
        this.tradingState = tradingState;
        this.properties = properties;
    }

    @Override
    public void execute(TradingDecision decision) {
        if (decision == null || decision.getDecision() == null) {
            return;
        }

        if (TradingDecision.DecisionType.WAIT.equals(decision.getDecision())) {
            return;
        }

        String symbol = decision.getSymbol();
        if (symbol == null || symbol.isBlank()) {
            log.warn("交易决策缺少 symbol");
            return;
        }

        try {
            // 下单前主动刷新一次，避免使用过期持仓状态
            stateManager.refreshSymbol(symbol);

            PositionInfo position = tradingState.getPosition(symbol);
            if (position != null && position.hasPosition()) {
                log.info("{} 已存在持仓，忽略重复交易信号: positionAmt={}", symbol, position.getPositionAmt());
                return;
            }

            // 同一个币种同一时间只允许一个下单请求
            if (!tradingState.startOrderSubmitting(symbol)) {
                log.info("{} 正在提交订单，忽略重复交易信号", symbol);
                return;
            }

            try {
                if (!properties.isEnabled()) {
                    log.info("交易执行未启用，收到交易信号但不实际下单: symbol={}, decision={}", symbol, decision.getDecision());
                    return;
                }

                if (properties.isAutoSetLeverage()) {
                    tradeService.setLeverage(symbol, properties.getLeverage());
                }

                Side side = TradingDecision.DecisionType.ENTER_LONG.equals(decision.getDecision()) ? Side.BUY : Side.SELL;
                OrderInfo orderInfo = tradeService.createMarketOrder(symbol, side, properties.getUsdtAmount());
                tradingState.updateOrder(orderInfo);

                log.info("交易订单已提交: symbol={}, decision={}, orderId={}", symbol, decision.getDecision(), orderInfo.getOrderId());
            } finally {
                tradingState.finishOrderSubmitting(symbol);
            }
        } catch (Exception e) {
            tradingState.finishOrderSubmitting(symbol);
            log.error("交易执行失败: symbol={}, decision={}", symbol, decision.getDecision(), e);
        }
    }
}
