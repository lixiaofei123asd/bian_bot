package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.Strategy;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.analysis.enums.TradingSignal;
import com.lxf.bian_bot.analysis.indicator.IndicatorContinuous;
import com.lxf.bian_bot.analysis.indicator.IndicatorMa7;
import com.lxf.bian_bot.model.BinanceKline;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Strategy1 implements Strategy {

    private final IndicatorMa7 ma7 = new IndicatorMa7();
    private final IndicatorContinuous continuous = new IndicatorContinuous();

    @Override
    public String getName() {
        return "STRATEGY1";
    }

    // 获取策略使用的指标
    @Override
    public List<Indicator> getIndicators() {
        return List.of(ma7, continuous);
    }

    // 满足两个指标才触发
    @Override
    public int getRequiredMatchCount() {
        return 2;
    }

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
