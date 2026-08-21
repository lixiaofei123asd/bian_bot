package com.lxf.bian_bot.analysis.indicator;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
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

    // 计算价格相对于MA25的方向
    @Override
    public IndicatorSignal calculate(List<BinanceKline> klines) {
        if (klines == null || klines.size() < PERIOD) {
            return IndicatorSignal.NEUTRAL;
        }

        BigDecimal ma25 = klines.subList(klines.size() - PERIOD, klines.size()).stream()
                .map(BinanceKline::getClosePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(PERIOD), 8, RoundingMode.HALF_UP);
        BigDecimal currentPrice = klines.get(klines.size() - 1).getClosePrice();

        int result = currentPrice.compareTo(ma25);
        if (result > 0) {
            return IndicatorSignal.UP;
        }
        if (result < 0) {
            return IndicatorSignal.DOWN;
        }
        return IndicatorSignal.NEUTRAL;
    }
}
