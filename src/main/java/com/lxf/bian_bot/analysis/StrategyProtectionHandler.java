package com.lxf.bian_bot.analysis;

import com.binance.connector.client.derivatives_trading_usds_futures.rest.model.Side;
import com.lxf.bian_bot.analysis.model.ProtectionConfig;
import com.lxf.bian_bot.config.strategy.StrategyConfig;
import com.lxf.bian_bot.model.TradingDecision;
import com.lxf.bian_bot.trading.state.TradingExecutionState;
import com.lxf.bian_bot.trading.state.TradingState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 策略保护事件处理器。
 *
 * <p>开仓成交后，把成交事实交给对应 Strategy，由 Strategy 决定 TP/SL；
 * 保护单触发异常后，也由 Strategy 决定是否退出。这里不计算 TP/SL。</p>
 */
@Slf4j
@Service
public class StrategyProtectionHandler {

    private final StrategyExecutionService strategyExecutionService;
    private final StrategyRegistry strategyRegistry;
    private final StrategyConfig strategyConfig;
    private final TradingState tradingState;

    public StrategyProtectionHandler(StrategyExecutionService strategyExecutionService,
                                     StrategyRegistry strategyRegistry,
                                     StrategyConfig strategyConfig,
                                     TradingState tradingState) {
        this.strategyExecutionService = strategyExecutionService;
        this.strategyRegistry = strategyRegistry;
        this.strategyConfig = strategyConfig;
        this.tradingState = tradingState;
    }

    /** 限价/市价开仓成交后，由 Strategy 决定 TP/SL。 */
    @EventListener
    public void handleOrderFilled(OrderFilledEvent event) {
        String strategyName = strategyConfig.getStrategyName(event.symbol());
        Strategy strategy = strategyRegistry.getStrategy(strategyName);
        if (strategy == null) {
            log.error("无法找到成交对应策略: symbol={}, strategy={}", event.symbol(), strategyName);
            return;
        }

        TradingDecision.Direction direction = event.openSide() == Side.BUY
                ? TradingDecision.Direction.LONG
                : TradingDecision.Direction.SHORT;

        try {
            ProtectionConfig protection = strategy.calculateProtection(
                    event.symbol(), direction, event.quantity(), event.entryPrice());

            log.info("策略保护配置已生成: strategy={}, symbol={}, enabled={}, tp={}, sl={}",
                    strategyName, event.symbol(), protection != null && protection.enabled(),
                    protection == null ? null : protection.takeProfitPrice(),
                    protection == null ? null : protection.stopLossPrice());

            strategyExecutionService.executeProtection(
                    event.symbol(), event.openSide(), event.quantity(), protection);
        } catch (Exception e) {
            log.error("执行策略保护失败: strategy={}, symbol={}", strategyName, event.symbol(), e);
            tradingState.updateExecutionState(event.symbol(), TradingExecutionState.FAILED);
        }
    }

    /** 保护单无法创建且价格已经越过触发价，由 Strategy 决定后续动作。 */
    @EventListener
    public void handleProtectionTrigger(ProtectionTriggerEvent event) {
        log.warn("收到保护单触发事件: symbol={}, side={}, quantity={}, tp={}, sl={}",
                event.symbol(), event.openSide(), event.quantity(),
                event.takeProfitPrice(), event.stopLossPrice());

        try {
            strategyExecutionService.handleProtectionTrigger(
                    event.symbol(),
                    event.openSide() == Side.BUY
                            ? TradingDecision.Direction.LONG
                            : TradingDecision.Direction.SHORT,
                    event.quantity(),
                    event.takeProfitPrice(),
                    event.stopLossPrice());
        } catch (Exception e) {
            log.error("处理保护单触发事件失败: symbol={}", event.symbol(), e);
            tradingState.updateExecutionState(event.symbol(), TradingExecutionState.FAILED);
        }
    }
}
