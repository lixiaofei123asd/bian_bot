package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.indicator.Indicator;
import com.lxf.bian_bot.analysis.indicator.IndicatorContinuous;
import com.lxf.bian_bot.analysis.indicator.IndicatorMa25;
import com.lxf.bian_bot.model.BinanceKline;
import java.math.BigDecimal;
import java.util.List;

public class Strategy2 implements Strategy {

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

    // 两个条件都满足才触发
    @Override
    public int getRequiredMatchCount() {
        return 2;
    }

    // 连续两根下跌，并且价格在MA25下方，做空
    @Override
    public TradingSignal execute(List<BinanceKline> klines) {
        if (klines == null || klines.isEmpty()) {
            return TradingSignal.WAIT;
        }

        BigDecimal ma25Value = ma25.calculate(klines);
        BigDecimal continuousValue = continuous.calculate(klines);
        BigDecimal currentPrice = klines.get(klines.size() - 1).getClosePrice();

        if (ma25Value == null || continuousValue == null) {
            return TradingSignal.WAIT;
        }

        int matchedCount = 0;

        if (continuousValue.compareTo(BigDecimal.valueOf(-2)) <= 0) {
            matchedCount++;
        }

        if (currentPrice.compareTo(ma25Value) < 0) {
            matchedCount++;
        }

        return matchedCount >= getRequiredMatchCount() ? TradingSignal.SHORT : TradingSignal.WAIT;
    }
}
