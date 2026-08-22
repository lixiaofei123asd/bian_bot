package com.lxf.bian_bot.analysis.indicator;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.MarketDataProvider;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.model.BinanceKline;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class IndicatorThreeKLineTrend implements Indicator {

    private static final int MIN_KLINE_COUNT = 3;
    private static final int MAX_KLINE_COUNT = 20;
    private final MarketDataProvider marketDataProvider;

    public IndicatorThreeKLineTrend(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    @Override
    public String getName() {
        return "THREE_KLINE_TREND";
    }

    @Override
    public IndicatorSignal calculate(String symbol) {
        List<BinanceKline> klines = marketDataProvider.getKlines(symbol, "1m", MAX_KLINE_COUNT + 1);
        if (klines == null || klines.size() < MIN_KLINE_COUNT + 1) {
            return IndicatorSignal.NEUTRAL;
        }

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
        if (upCount > downCount && netDirection > 0) {
            return IndicatorSignal.UP;
        }
        if (downCount > upCount && netDirection < 0) {
            return IndicatorSignal.DOWN;
        }
        return IndicatorSignal.NEUTRAL;
    }
}
