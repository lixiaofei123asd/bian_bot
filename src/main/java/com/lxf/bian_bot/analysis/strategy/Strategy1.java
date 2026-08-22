package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.indicator.IndicatorThreeKLineTrend;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Strategy1 extends BaseStrategy {

    private final IndicatorThreeKLineTrend indicatorThreeKLineTrend;

    public Strategy1(IndicatorThreeKLineTrend indicatorThreeKLineTrend) {
        this.indicatorThreeKLineTrend = indicatorThreeKLineTrend;
    }

    @Override
    public String getName() {
        return "STRATEGY1";
    }

    @Override
    public List<Indicator> getIndicators() {
        return List.of(indicatorThreeKLineTrend);
    }

    @Override
    public int getRequiredMatchCount() {
        return 1;
    }
}
