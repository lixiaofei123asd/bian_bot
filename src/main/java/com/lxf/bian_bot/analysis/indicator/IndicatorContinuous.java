package com.lxf.bian_bot.analysis.indicator;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.MarketDataProvider;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.model.BinanceKline;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class IndicatorContinuous implements Indicator {

    private static final int LOOKBACK = 20;
    private final MarketDataProvider marketDataProvider;

    public IndicatorContinuous(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    @Override
    public String getName() {
        return "CONTINUOUS";
    }

    @Override
    public IndicatorSignal calculate(String symbol) {
        List<BinanceKline> klines = marketDataProvider.getKlines(symbol, "1m", LOOKBACK);
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
