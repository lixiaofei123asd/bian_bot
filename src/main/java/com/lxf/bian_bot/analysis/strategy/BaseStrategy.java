package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.IndicatorResult;
import com.lxf.bian_bot.analysis.Strategy;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.analysis.enums.TradingSignal;
import java.util.List;

public abstract class BaseStrategy implements Strategy {

    /**
     * 普通策略默认允许每次被外部驱动时执行。
     *
     * <p>需要自定义执行频率的策略直接覆盖 shouldExecute() 即可。</p>
     */
    @Override
    public boolean shouldExecute(String symbol) {
        return true;
    }

    @Override
    public TradingSignal execute(String symbol) {
        int upCount = 0;
        int downCount = 0;

        for (Indicator indicator : getIndicators()) {
            IndicatorResult result = indicator.calculate(symbol);
            if (result == null || result.getSignal() == null) {
                continue;
            }

            IndicatorSignal signal = result.getSignal();
            if (signal == IndicatorSignal.UP) {
                upCount++;
            } else if (signal == IndicatorSignal.DOWN) {
                downCount++;
            }
        }

        if (upCount >= getRequiredMatchCount()) {
            return TradingSignal.LONG;
        }
        if (downCount >= getRequiredMatchCount()) {
            return TradingSignal.SHORT;
        }
        return TradingSignal.WAIT;
    }

    @Override
    public abstract List<Indicator> getIndicators();
}
