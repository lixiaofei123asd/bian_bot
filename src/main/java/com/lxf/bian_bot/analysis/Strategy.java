package com.lxf.bian_bot.analysis;

import com.lxf.bian_bot.analysis.enums.TradingSignal;
import com.lxf.bian_bot.model.TradingDecision;
import java.math.BigDecimal;
import java.util.List;

public interface Strategy {

    // 获取策略名称，用于配置文件匹配
    String getName();

    // 获取策略使用的指标
    List<Indicator> getIndicators();

    // 获取满足条件的数量
    int getRequiredMatchCount();

    /**
     * 判断当前策略是否需要在此时执行。
     *
     * <p>执行频率由具体 Strategy 自己决定，外部只负责周期性询问，
     * 不应该知道策略内部到底是 10 秒、3 分钟还是 5 分钟执行一次。</p>
     */
    default boolean shouldExecute(String symbol) {
        return true;
    }

    /**
     * 执行策略，策略内部的指标自行获取市场数据。
     */
    TradingSignal execute(String symbol);

    /**
     * 获取策略最终交易决策。
     *
     * <p>普通策略默认根据 TradingSignal 转换；AI 等高级策略可以覆盖此方法，
     * 返回自己的入场价格、止盈、止损、金额以及持仓管理决策。</p>
     */
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
}
