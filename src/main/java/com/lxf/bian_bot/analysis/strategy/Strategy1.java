package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.indicator.IndicatorSecondKlineTrend;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Strategy1 extends BaseStrategy {

    private final IndicatorSecondKlineTrend trend;

    public Strategy1(IndicatorSecondKlineTrend trend) {
        this.trend = trend;
    }

    @Override
    public String getName() {
        return "STRATEGY1";
    }

    @Override
    public List<Indicator> getIndicators() {
        return List.of(trend);
    }

    @Override
    public int getRequiredMatchCount() {
        return 1;
    }
}
