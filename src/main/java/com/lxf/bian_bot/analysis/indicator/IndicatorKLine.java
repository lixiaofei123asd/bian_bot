package com.lxf.bian_bot.analysis.indicator;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.model.BinanceKline;
import java.util.List;

public class IndicatorKLine implements Indicator {

    // 获取指标名称
    @Override
    public String getName() {
        return "KLINE";
    }

    // 根据最新K线判断涨跌方向
    @Override
    public IndicatorSignal calculate(List<BinanceKline> klines) {
        if (klines == null || klines.isEmpty()) {
            return IndicatorSignal.NEUTRAL;
        }

        BinanceKline kline = klines.get(klines.size() - 1);
        int result = kline.getClosePrice().compareTo(kline.getOpenPrice());
        if (result > 0) {
            return IndicatorSignal.UP;
        }
        if (result < 0) {
            return IndicatorSignal.DOWN;
        }
        return IndicatorSignal.NEUTRAL;
    }
}
