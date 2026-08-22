package com.lxf.bian_bot.analysis.indicator;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.MarketDataProvider;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.model.BinanceKline;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class IndicatorMa7 implements Indicator {

    private static final int PERIOD = 7;

    private final MarketDataProvider marketDataProvider;

    public IndicatorMa7(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    @Override
    public String getName() {
        return "MA7";
    }

    /**
     * 计算最新价格相对于 MA7 的方向。
     */
    @Override
    public IndicatorSignal calculate(String symbol) {
        List<BinanceKline> klines = marketDataProvider.getKlines(symbol, "1m", PERIOD);
        if (klines == null || klines.size() < PERIOD) {
            return IndicatorSignal.NEUTRAL;
        }

        BigDecimal ma7 = klines.stream()
                .map(BinanceKline::getClosePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(PERIOD), 8, RoundingMode.HALF_UP);

        BigDecimal currentPrice = klines.get(klines.size() - 1).getClosePrice();

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
