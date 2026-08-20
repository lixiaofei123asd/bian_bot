package com.lxf.bian_bot.analysis.indicator;

import com.lxf.bian_bot.model.BinanceKline;
import java.math.BigDecimal;
import java.util.List;

public class IndicatorContinuous implements Indicator {

    // 获取指标名称
    @Override
    public String getName() {
        return "CONTINUOUS";
    }

    // 计算连续涨跌数量，正数表示连续上涨，负数表示连续下跌
    @Override
    public BigDecimal calculate(List<BinanceKline> klines) {
        if (klines == null || klines.isEmpty()) {
            return BigDecimal.ZERO;
        }

        int count = 0;
        int direction = 0;

        for (int i = klines.size() - 1; i >= 0; i--) {
            BinanceKline kline = klines.get(i);
            int currentDirection = kline.getClosePrice().compareTo(kline.getOpenPrice());

            if (currentDirection == 0) {
                break;
            }

            if (direction == 0) {
                direction = currentDirection;
            }

            if (currentDirection != direction) {
                break;
            }

            count++;
        }

        return BigDecimal.valueOf(direction * count);
    }
}
