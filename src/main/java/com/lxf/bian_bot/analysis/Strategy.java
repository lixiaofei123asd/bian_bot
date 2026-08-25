package com.lxf.bian_bot.analysis;

import com.lxf.bian_bot.analysis.enums.TradingSignal;
import com.lxf.bian_bot.model.TradingDecision;
import java.math.BigDecimal;
import java.util.List;

public interface Strategy {

    String getName();

    List<Indicator> getIndicators();

    int getRequiredMatchCount();

    /** 策略自己决定当前是否到执行时间。 */
    boolean shouldExecute(String symbol);

    /** 策略产生交易信号。 */
    TradingSignal execute(String symbol);

    /** 策略产生最终交易决策。默认按 execute() 的方向信号生成开仓决策。 */
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
     * TP/SL 因当前价格已经越过触发价而无法创建时，由具体策略决定如何处理。
     * 默认不平仓，避免保护服务自行做交易决策。
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
                .reason("保护单触发条件异常，由策略自行决定后续动作")
                .build();
    }
}
