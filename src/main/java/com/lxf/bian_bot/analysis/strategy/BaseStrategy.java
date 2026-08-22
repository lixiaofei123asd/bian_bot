package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.Strategy;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.analysis.enums.TradingSignal;
import java.util.List;

public abstract class BaseStrategy implements Strategy {

    @Override
    public TradingSignal execute(String symbol) {
        int upCount = 0;
        int downCount = 0;

        for (Indicator indicator : getIndicators()) {
            IndicatorSignal signal = indicator.calculate(symbol);
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
