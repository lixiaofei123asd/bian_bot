package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.indicator.IndicatorThreeKLineTrend;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Strategy1 extends BaseStrategy {

    private final IndicatorThreeKLineTrend indicatorThreeKLineTrend = new IndicatorThreeKLineTrend();

    @Override
    public String getName() {
        return "STRATEGY1";
    }

    // 获取策略使用的指标
    @Override
    public List<Indicator> getIndicators() {
        return List.of(indicatorThreeKLineTrend);
    }

    // 满足两个指标才触发
    @Override
    public int getRequiredMatchCount() {
        return 1;
    }
}
