package com.lxf.bian_bot.analysis;

import com.lxf.bian_bot.analysis.enums.TradingSignal;
import com.lxf.bian_bot.analysis.model.ProtectionConfig;
import com.lxf.bian_bot.model.TradingDecision;

import java.math.BigDecimal;
import java.util.List;

/**
 * 交易策略。
 *
 * <p>Strategy 负责所有交易决策；执行层只负责把 Strategy 的决定执行到 Binance。</p>
 */
public interface Strategy {

    String getName();

    List<Indicator> getIndicators();

    int getRequiredMatchCount();

    /** 策略自己决定当前是否到执行时间。 */
    boolean shouldExecute(String symbol);

    /** 策略产生交易信号。 */
    TradingSignal execute(String symbol);

    /** 策略产生最终交易决策。 */
    default TradingDecision decide(String symbol) {
        TradingSignal signal = execute(symbol);
        TradingDecision.DecisionType decisionType = switch (signal) {
            case LONG -> TradingDecision.DecisionType.ENTER_LONG;
            case SHORT -> TradingDecision.DecisionType.ENTER_SHORT;
            case WAIT -> TradingDecision.DecisionType.WAIT;
        };
        TradingDecision.Direction direction = switch (signal) {
            case LONG -> TradingDecision.Direction.LONG;
            case SHORT -> TradingDecision.Direction.SHORT;
            case WAIT -> null;
        };
        return TradingDecision.builder()
                .symbol(symbol)
                .decision(decisionType)
                .direction(direction)
                .reason("策略: " + getName() + ", 信号: " + signal)
                .build();
    }

    /**
     * 开仓订单成交后，策略决定是否设置 TP/SL，以及具体保护价格。
     */
    default ProtectionConfig calculateProtection(String symbol,
                                                 TradingDecision.Direction direction,
                                                 BigDecimal quantity,
                                                 BigDecimal entryPrice) {
        return ProtectionConfig.disabled();
    }

    /**
     * TP/SL 因当前价格已经越过触发价而无法创建时，由策略决定后续动作。
     */
    default TradingDecision onProtectionTrigger(String symbol,
                                                TradingDecision.Direction direction,
                                                BigDecimal quantity,
                                                BigDecimal takeProfitPrice,
                                                BigDecimal stopLossPrice) {
        return TradingDecision.builder()
                .symbol(symbol)
                .decision(TradingDecision.DecisionType.WAIT)
                .direction(direction)
                .reason("保护单触发条件异常，由策略决定后续动作")
                .build();
    }

    /**
     * 限价开仓单超过策略规定的等待时间后，由策略决定如何处理。
     *
     * <p>这里只返回决策，不直接调用 Binance。</p>
     */
    default PendingOrderDecision onPendingOrderTimeout(String symbol,
                                                       long orderId,
                                                       BigDecimal quantity,
                                                       BigDecimal entryPrice,
                                                       long waitingSeconds) {
        return PendingOrderDecision.CANCEL;
    }

    enum PendingOrderDecision {
        /** 撤销未成交订单。 */
        CANCEL,
        /** 继续等待。 */
        KEEP_WAITING,
        /** 重新定价，后续由执行层按策略提供的新决策执行。 */
        REPRICE
    }
}
