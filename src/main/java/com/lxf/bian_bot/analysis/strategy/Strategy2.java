package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.indicator.IndicatorContinuous;
import com.lxf.bian_bot.analysis.indicator.IndicatorMa25;
import java.util.List;

public class Strategy2 extends BaseStrategy {

    private final IndicatorMa25 ma25 = new IndicatorMa25();
    private final IndicatorContinuous continuous = new IndicatorContinuous();

    @Override
    public String getName() {
        return "STRATEGY2";
    }

    // 获取策略使用的指标
    @Override
    public List<Indicator> getIndicators() {
        return List.of(ma25, continuous);
    }

    // 满足两个指标才触发
    @Override
    public int getRequiredMatchCount() {
        return 2;
    }
}
