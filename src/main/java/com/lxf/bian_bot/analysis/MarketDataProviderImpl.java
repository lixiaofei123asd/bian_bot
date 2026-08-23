package com.lxf.bian_bot.analysis;

import com.lxf.bian_bot.cache.KlineCache;
import com.lxf.bian_bot.cache.SecondKlineCache;
import com.lxf.bian_bot.thirdparty.binance.model.BinanceKline;
import com.lxf.bian_bot.model.BinanceSecondKline;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MarketDataProviderImpl implements MarketDataProvider {

    private final KlineCache klineCache;
    private final SecondKlineCache secondKlineCache;

    public MarketDataProviderImpl(KlineCache klineCache, SecondKlineCache secondKlineCache) {
        this.klineCache = klineCache;
        this.secondKlineCache = secondKlineCache;
    }

    @Override
    public List<BinanceKline> getKlines(String symbol, String interval, int limit) {
        List<BinanceKline> klines = klineCache.get(symbol, interval);
        if (klines == null || klines.isEmpty() || limit <= 0) {
            return List.of();
        }
        if (klines.size() <= limit) {
            return klines;
        }
        return List.copyOf(klines.subList(klines.size() - limit, klines.size()));
    }

    @Override
    public List<BinanceSecondKline> getSecondKlinesIncludingCurrent(String symbol, int limit) {
        return secondKlineCache.getRecentIncludingCurrent(symbol, limit);
    }
}
