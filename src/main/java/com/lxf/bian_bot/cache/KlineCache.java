package com.lxf.bian_bot.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lxf.bian_bot.model.BinanceKline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class KlineCache {

    private final Cache<String, List<BinanceKline>> cache = Caffeine.newBuilder().build();

    public void put(String symbol, String interval, List<BinanceKline> klines) {
        if (symbol == null || symbol.isBlank() || interval == null || interval.isBlank() || klines == null) {
            return;
        }
        cache.put(buildKey(symbol, interval), new ArrayList<>(klines));
    }

    public List<BinanceKline> get(String symbol, String interval) {
        if (symbol == null || symbol.isBlank() || interval == null || interval.isBlank()) {
            return Collections.emptyList();
        }
        List<BinanceKline> klines = cache.getIfPresent(buildKey(symbol, interval));
        if (klines == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(klines);
    }

    public void update(String symbol, String interval, BinanceKline kline, int maxSize) {
        if (symbol == null || symbol.isBlank() || interval == null || interval.isBlank() || kline == null) {
            return;
        }
        String key = buildKey(symbol, interval);
        List<BinanceKline> klines = new ArrayList<>(get(symbol, interval));
        if (!klines.isEmpty() && klines.getLast().getOpenTime().equals(kline.getOpenTime())) {
            klines.set(klines.size() - 1, kline);
        } else {
            klines.add(kline);
        }
        if (klines.size() > maxSize) {
            klines = new ArrayList<>(klines.subList(klines.size() - maxSize, klines.size()));
        }
        cache.put(key, klines);
    }

    public boolean contains(String symbol, String interval) {
        return cache.getIfPresent(buildKey(symbol, interval)) != null;
    }

    public void remove(String symbol, String interval) {
        cache.invalidate(buildKey(symbol, interval));
    }

    public void clear() {
        cache.invalidateAll();
        log.info("KlineCache 已清空");
    }

    private String buildKey(String symbol, String interval) {
        return symbol.trim().toUpperCase() + ":" + interval.trim().toLowerCase();
    }
}
