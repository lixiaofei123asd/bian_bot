package com.lxf.bian_bot.analysis.indicator;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.model.BinanceKline;
import java.util.List;

public class IndicatorThreeKLineTrend implements Indicator {

    private static final int MIN_KLINE_COUNT = 3;
    private static final int MAX_KLINE_COUNT = 20;

    // 获取指标名称
    @Override
    public String getName() {
        return "THREE_KLINE_TREND";
    }

    // 从最近3根已完成K线开始判断整体趋势，最多向前扩展到20根
    @Override
    public IndicatorSignal calculate(List<BinanceKline> klines) {
        if (klines == null || klines.size() < MIN_KLINE_COUNT) {
            return IndicatorSignal.NEUTRAL;
        }

        // 最后一根K线可能还未结束，因此从倒数第二根开始判断
        int completedEndIndex = klines.size() - 2;
        int availableCount = completedEndIndex + 1;
        int maxCount = Math.min(MAX_KLINE_COUNT, availableCount);

        for (int count = MIN_KLINE_COUNT; count <= maxCount; count++) {
            IndicatorSignal signal = calculateTrend(klines, completedEndIndex, count);
            if (signal != IndicatorSignal.NEUTRAL) {
                return signal;
            }
        }

        return IndicatorSignal.NEUTRAL;
    }

    // 判断指定数量已完成K线的整体趋势
    private IndicatorSignal calculateTrend(List<BinanceKline> klines, int endIndex, int count) {
        int startIndex = endIndex - count + 1;
        int upCount = 0;
        int downCount = 0;

        for (int i = startIndex + 1; i <= endIndex; i++) {
            int direction = klines.get(i).getClosePrice().compareTo(klines.get(i - 1).getClosePrice());
            if (direction > 0) {
                upCount++;
            } else if (direction < 0) {
                downCount++;
            }
        }

        int netDirection = klines.get(endIndex).getClosePrice().compareTo(klines.get(startIndex).getClosePrice());

        // 上涨次数更多，并且整体价格最终高于起点，认为处于上涨阶段
        if (upCount > downCount && netDirection > 0) {
            return IndicatorSignal.UP;
        }

        // 下跌次数更多，并且整体价格最终低于起点，认为处于下跌阶段
        if (downCount > upCount && netDirection < 0) {
            return IndicatorSignal.DOWN;
        }

        return IndicatorSignal.NEUTRAL;
    }
}
