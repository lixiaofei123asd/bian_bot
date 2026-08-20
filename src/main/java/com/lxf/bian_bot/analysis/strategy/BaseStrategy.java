package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.Strategy;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.analysis.enums.TradingSignal;
import com.lxf.bian_bot.model.BinanceKline;
import java.util.List;

public abstract class BaseStrategy implements Strategy {

    // 获取所有指标结果，根据满足数量做出最终判断
    @Override
    public TradingSignal execute(List<BinanceKline> klines) {
        int upCount = 0;
        int downCount = 0;

        for (Indicator indicator : getIndicators()) {
            IndicatorSignal signal = indicator.calculate(klines);
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
}
