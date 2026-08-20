package com.lxf.bian_bot.analysis.indicator;

import com.lxf.bian_bot.model.BinanceKline;
import java.math.BigDecimal;
import java.util.List;

public class IndicatorKLine implements Indicator {

    // 获取指标名称
    @Override
    public String getName() {
        return "KLINE";
    }

    // 获取最新K线涨跌结果，1为上涨，-1为下跌，0为平盘
    @Override
    public BigDecimal calculate(List<BinanceKline> klines) {
        if (klines == null || klines.isEmpty()) {
            return null;
        }

        BinanceKline kline = klines.get(klines.size() - 1);
        int result = kline.getClosePrice().compareTo(kline.getOpenPrice());
        return BigDecimal.valueOf(result);
    }
}
