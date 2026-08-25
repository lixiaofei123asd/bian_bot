package com.lxf.bian_bot.analysis;

import com.lxf.bian_bot.trading.state.TradingState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 保护单异常事件处理器。
 *
 * <p>这里只负责接收保护触发事件，并把事件交给策略执行器。
 * 不直接依赖 BinanceTradeService，避免形成：
 * BinanceTradeService -> PendingOrderProtectionService -> StrategyProtectionHandler -> BinanceTradeService
 * 的循环依赖。</p>
 */
@Slf4j
@Service
public class StrategyProtectionHandler {

    private final StrategyExecutionService strategyExecutionService;
    private final TradingState tradingState;

    public StrategyProtectionHandler(StrategyExecutionService strategyExecutionService,
                                     TradingState tradingState) {
        this.strategyExecutionService = strategyExecutionService;
        this.tradingState = tradingState;
    }

    @EventListener
    public void handle(ProtectionTriggerEvent event) {
        log.warn("收到保护单触发事件: symbol={}, side={}, quantity={}, tp={}, sl={}",
                event.symbol(), event.openSide(), event.quantity(),
                event.takeProfitPrice(), event.stopLossPrice());

        try {
            strategyExecutionService.handleProtectionTrigger(
                    event.symbol(),
                    event.openSide() == com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side.BUY
                            ? com.lxf.bian_bot.model.TradingDecision.Direction.LONG
                            : com.lxf.bian_bot.model.TradingDecision.Direction.SHORT,
                    event.quantity(),
                    event.takeProfitPrice(),
                    event.stopLossPrice());
        } catch (Exception e) {
            log.error("处理保护单触发事件失败: symbol={}", event.symbol(), e);
            tradingState.updateExecutionState(
                    event.symbol(),
                    com.lxf.bian_bot.trading.state.TradingExecutionState.FAILED);
        }
    }
}
