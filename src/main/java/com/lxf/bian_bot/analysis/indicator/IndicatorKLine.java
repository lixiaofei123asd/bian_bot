package com.lxf.bian_bot.analysis.indicator;

import com.lxf.bian_bot.analysis.Indicator;
import com.lxf.bian_bot.analysis.MarketDataProvider;
import com.lxf.bian_bot.analysis.enums.IndicatorSignal;
import com.lxf.bian_bot.model.BinanceKline;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IndicatorKLine implements Indicator {

    private final MarketDataProvider marketDataProvider;

    public IndicatorKLine(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    @Override
    public String getName() {
        return "KLINE";
    }

    /**
     * 根据最新 K 线判断涨跌方向。
     */
    @Override
    public IndicatorSignal calculate(String symbol) {
        List<BinanceKline> klines = marketDataProvider.getKlines(symbol, "1m", 1);
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
