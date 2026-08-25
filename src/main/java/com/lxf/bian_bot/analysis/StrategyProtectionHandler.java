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
 * 策略生命周期事件桥接器。
 *
 * <p>本类负责把成交/保护异常事件交给对应 Strategy 做决策，
 * 然后把 Strategy 返回的决策交给执行层。本类不计算 TP/SL，也不直接调用 Binance。</p>
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

    /** 开仓成交后，由 Strategy 决定 TP/SL。 */
    @EventListener
    public void handleOrderFilled(OrderFilledEvent event) {
        Strategy strategy = getStrategy(event.symbol());
        if (strategy == null) return;

        TradingDecision.Direction direction = event.openSide() == Side.BUY
                ? TradingDecision.Direction.LONG
                : TradingDecision.Direction.SHORT;

        try {
            ProtectionConfig protection = strategy.calculateProtection(
                    event.symbol(), direction, event.quantity(), event.entryPrice());

            log.info("策略保护配置已生成: strategy={}, symbol={}, enabled={}, tp={}, sl={}",
                    strategy.getName(), event.symbol(), protection != null && protection.enabled(),
                    protection == null ? null : protection.takeProfitPrice(),
                    protection == null ? null : protection.stopLossPrice());

            strategyExecutionService.executeProtection(
                    event.symbol(), event.openSide(), event.quantity(), protection);
        } catch (Exception e) {
            log.error("执行策略保护失败: strategy={}, symbol={}", strategy.getName(), event.symbol(), e);
            tradingState.updateExecutionState(event.symbol(), TradingExecutionState.FAILED);
        }
    }

    /** 保护单无法创建且价格已经越过触发价，由 Strategy 决定后续动作。 */
    @EventListener
    public void handleProtectionTrigger(ProtectionTriggerEvent event) {
        Strategy strategy = getStrategy(event.symbol());
        if (strategy == null) return;

        TradingDecision.Direction direction = event.openSide() == Side.BUY
                ? TradingDecision.Direction.LONG
                : TradingDecision.Direction.SHORT;

        try {
            TradingDecision decision = strategy.onProtectionTrigger(
                    event.symbol(),
                    direction,
                    event.quantity(),
                    event.takeProfitPrice(),
                    event.stopLossPrice());

            if (decision == null) {
                log.warn("Strategy 未返回保护触发决策: strategy={}, symbol={}",
                        strategy.getName(), event.symbol());
                return;
            }

            log.warn("保护触发交给策略决策: strategy={}, symbol={}, decision={}",
                    strategy.getName(), event.symbol(), decision.getDecision());
            strategyExecutionService.execute(decision);
        } catch (Exception e) {
            log.error("处理保护单触发事件失败: strategy={}, symbol={}",
                    strategy.getName(), event.symbol(), e);
            tradingState.updateExecutionState(event.symbol(), TradingExecutionState.FAILED);
        }
    }

    private Strategy getStrategy(String symbol) {
        String strategyName = strategyConfig.getStrategyName(symbol);
        Strategy strategy = strategyRegistry.getStrategy(strategyName);
        if (strategy == null) {
            log.error("无法找到 symbol 对应策略: symbol={}, strategy={}", symbol, strategyName);
        }
        return strategy;
    }
}
