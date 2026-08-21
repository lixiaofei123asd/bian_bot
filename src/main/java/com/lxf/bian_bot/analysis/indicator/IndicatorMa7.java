package com.lxf.bian_bot.analysis.indicator;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.model.BinanceKline;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class IndicatorMa7 implements Indicator {

    private static final int PERIOD = 7;

    // 获取指标名称
    @Override
    public String getName() {
        return "MA7";
    }

    // 计算价格相对于MA7的方向
    @Override
    public IndicatorSignal calculate(List<BinanceKline> klines) {
        if (klines == null || klines.size() < PERIOD) {
            return IndicatorSignal.NEUTRAL;
        }

        BigDecimal ma7 = klines.subList(klines.size() - PERIOD, klines.size()).stream()
                .map(BinanceKline::getClosePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(PERIOD), 8, RoundingMode.HALF_UP);
        BigDecimal currentPrice = klines.getLast().getClosePrice();

        int result = currentPrice.compareTo(ma7);
        if (result > 0) {
            return IndicatorSignal.UP;
        }
        if (result < 0) {
            return IndicatorSignal.DOWN;
        }
        return IndicatorSignal.NEUTRAL;
    }
}
