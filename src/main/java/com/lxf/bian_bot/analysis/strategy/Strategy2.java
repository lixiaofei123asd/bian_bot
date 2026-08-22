package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Strategy2 extends BaseStrategy {

    // TODO 暂时空，现在只用策略1

    public Strategy2() {

    }

    @Override
    public String getName() {
        return "STRATEGY2";
    }

    @Override
    public List<Indicator> getIndicators() {
        return List.of();
    }

    @Override
    public int getRequiredMatchCount() {
        return 1;
    }
}
