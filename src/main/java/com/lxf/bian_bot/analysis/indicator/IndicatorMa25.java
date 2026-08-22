package com.lxf.bian_bot.analysis.indicator;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.MarketDataProvider;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.model.BinanceKline;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class IndicatorMa25 implements Indicator {

    private static final int PERIOD = 25;
    private final MarketDataProvider marketDataProvider;

    public IndicatorMa25(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    @Override
    public String getName() {
        return "MA25";
    }

    @Override
    public IndicatorSignal calculate(String symbol) {
        List<BinanceKline> klines = marketDataProvider.getKlines(symbol, "1m", PERIOD);
        if (klines == null || klines.size() < PERIOD) {
            return IndicatorSignal.NEUTRAL;
        }

        BigDecimal ma25 = klines.stream()
                .map(BinanceKline::getClosePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(PERIOD), 8, RoundingMode.HALF_UP);
        BigDecimal currentPrice = klines.getLast().getClosePrice();

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
