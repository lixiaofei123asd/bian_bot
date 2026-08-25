package com.lxf.bian_bot.analysis;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.config.strategy.StrategyConfig;
import com.lxf.bian_bot.model.TradingDecision;
import com.lxf.bian_bot.thirdparty.binance.service.BinanceTradeService;
import com.lxf.bian_bot.trading.state.TradingState;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 保护单异常事件处理器：由 Strategy 决定是否退出，由交易服务执行退出。 */
@Slf4j
@Service
public class StrategyProtectionHandler {

    private final StrategyRegistry strategyRegistry;
    private final StrategyConfig strategyConfig;
    private final BinanceTradeService tradeService;
    private final TradingState tradingState;

    public StrategyProtectionHandler(StrategyRegistry strategyRegistry,
                                     StrategyConfig strategyConfig,
                                     BinanceTradeService tradeService,
                                     TradingState tradingState) {
        this.strategyRegistry = strategyRegistry;
        this.strategyConfig = strategyConfig;
        this.tradeService = tradeService;
        this.tradingState = tradingState;
    }

    public void handle(String symbol, Side openSide,
                       BigDecimal quantity, BigDecimal takeProfitPrice, BigDecimal stopLossPrice) {
        String strategyName = strategyConfig.getStrategyName(symbol);
        Strategy strategy = strategyRegistry.getStrategy(strategyName);
        if (strategy == null) {
            log.error("无法找到保护触发对应策略: symbol={}, strategy={}", symbol, strategyName);
            return;
        }

        TradingDecision.Direction direction = openSide == Side.BUY
                ? TradingDecision.Direction.LONG : TradingDecision.Direction.SHORT;
        TradingDecision decision = strategy.onProtectionTrigger(symbol, direction, quantity, takeProfitPrice, stopLossPrice);
        if (decision == null || decision.getDecision() == null) {
            log.warn("Strategy 未返回保护触发决策: strategy={}, symbol={}", strategyName, symbol);
            return;
        }

        log.warn("保护异常由策略决定: strategy={}, symbol={}, decision={}, reason={}",
                strategyName, symbol, decision.getDecision(), decision.getReason());

        if (TradingDecision.DecisionType.EXIT.equals(decision.getDecision())) {
            tradeService.closeMarketOrder(symbol);
            tradingState.clearSymbolOrders(symbol);
            tradingState.refreshExecutionState(symbol);
        }
    }
}
