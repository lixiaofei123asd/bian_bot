package com.lxf.bian_bot.analysis.indicator;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.model.BinanceKline;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class IndicatorMa25 implements Indicator {

    private static final int PERIOD = 25;

    // 获取指标名称
    @Override
    public String getName() {
        return "MA25";
    }

    // 计算MA25
    @Override
    public BigDecimal calculate(List<BinanceKline> klines) {
        if (klines == null || klines.size() < PERIOD) {
            return null;
        }

        return klines.subList(klines.size() - PERIOD, klines.size()).stream()
                .map(BinanceKline::getClosePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(PERIOD), 8, RoundingMode.HALF_UP);
    }
}
