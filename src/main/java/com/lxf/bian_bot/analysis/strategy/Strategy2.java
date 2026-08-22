package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.indicator.IndicatorContinuous;
import com.lxf.bian_bot.analysis.indicator.IndicatorMa25;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Strategy2 extends BaseStrategy {

    private final IndicatorMa25 ma25;
    private final IndicatorContinuous continuous;

    public Strategy2(IndicatorMa25 ma25, IndicatorContinuous continuous) {
        this.ma25 = ma25;
        this.continuous = continuous;
    }

    @Override
    public String getName() {
        return "STRATEGY2";
    }

    @Override
    public List<Indicator> getIndicators() {
        return List.of(ma25, continuous);
    }

    @Override
    public int getRequiredMatchCount() {
        return 2;
    }
}
