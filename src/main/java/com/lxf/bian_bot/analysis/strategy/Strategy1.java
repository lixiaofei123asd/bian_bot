package com.lxf.bian_bot.analysis.strategy;

import com.lxf.bian_bot.analysis.indicator.Indicator;
import com.lxf.bian_bot.analysis.indicator.IndicatorContinuous;
import com.lxf.bian_bot.analysis.indicator.IndicatorMa7;
import com.lxf.bian_bot.model.BinanceKline;
import java.math.BigDecimal;
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

    // 两个条件都满足才触发
    @Override
    public int getRequiredMatchCount() {
        return 2;
    }

    // 连续两根上涨，并且价格在MA7上方，做多
    @Override
    public TradingSignal execute(List<BinanceKline> klines) {
        if (klines == null || klines.isEmpty()) {
            return TradingSignal.WAIT;
        }

        BigDecimal ma7Value = ma7.calculate(klines);
        BigDecimal continuousValue = continuous.calculate(klines);
        BigDecimal currentPrice = klines.get(klines.size() - 1).getClosePrice();

        if (ma7Value == null || continuousValue == null) {
            return TradingSignal.WAIT;
        }

        int matchedCount = 0;

        if (continuousValue.compareTo(BigDecimal.valueOf(2)) >= 0) {
            matchedCount++;
        }

        if (currentPrice.compareTo(ma7Value) > 0) {
            matchedCount++;
        }

        return matchedCount >= getRequiredMatchCount() ? TradingSignal.LONG : TradingSignal.WAIT;
    }
}
