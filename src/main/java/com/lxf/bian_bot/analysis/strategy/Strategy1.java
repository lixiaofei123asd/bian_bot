package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.indicator.IndicatorContinuous;
import com.lxf.bian_bot.analysis.indicator.IndicatorMa7;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Strategy1 extends BaseStrategy {

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
}
