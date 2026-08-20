package com.lxf.bian_bot.analysis.indicator;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.model.BinanceKline;
import java.util.List;

public class IndicatorContinuous implements Indicator {

    // 获取指标名称
    @Override
    public String getName() {
        return "CONTINUOUS";
    }

    // 根据连续涨跌方向返回结果
    @Override
    public IndicatorSignal calculate(List<BinanceKline> klines) {
        if (klines == null || klines.isEmpty()) {
            return IndicatorSignal.NEUTRAL;
        }

        int direction = 0;
        int count = 0;

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

        if (count == 0) {
            return IndicatorSignal.NEUTRAL;
        }
        return direction > 0 ? IndicatorSignal.UP : IndicatorSignal.DOWN;
    }
}
